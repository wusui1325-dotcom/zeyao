package com.wusper.zeyao.mitm

import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.Status
import javax.net.ssl.TrustManagerFactory

/**
 * Terminates TLS from the device browser with a local CA leaf, and opens a
 * second TLS session to the origin. Application data is HTTP/1.1 (ALPN).
 */
class TlsMitm(
    host: String,
    leafCert: X509Certificate,
    leafKey: PrivateKey,
    caCert: X509Certificate,
    private val emitToClient: (ByteArray) -> Unit,
    private val emitToOrigin: (ByteArray) -> Unit,
    private val onClientPlain: (ByteArray) -> ByteArray?,
    private val onServerPlain: (ByteArray) -> ByteArray?,
    private val onError: () -> Unit,
) {
    private val serverEngine = createServerEngine(host, leafCert, leafKey, caCert)
    private val clientEngine = createClientEngine(host)

    private val fromClientNet = GrowBuf()
    private val fromOriginNet = GrowBuf()
    private var serverPlain = ByteBuffer.allocate(32 * 1024)
    private var clientPlain = ByteBuffer.allocate(32 * 1024)
    private var toClientNet = ByteBuffer.allocate(32 * 1024)
    private var toOriginNet = ByteBuffer.allocate(32 * 1024)
    private val toOriginPlain = ArrayDeque<ByteArray>()
    private val toClientPlain = ArrayDeque<ByteArray>()
    private var failed = false
    private var closed = false

    @Synchronized
    fun start() {
        pump()
    }

    @Synchronized
    fun onClientTls(data: ByteArray) {
        if (failed || closed || data.isEmpty()) return
        fromClientNet.append(data)
        pump()
    }

    @Synchronized
    fun onOriginTls(data: ByteArray) {
        if (failed || closed || data.isEmpty()) return
        fromOriginNet.append(data)
        pump()
    }

    @Synchronized
    fun sendPlainToClient(data: ByteArray) {
        if (failed || closed || data.isEmpty()) return
        toClientPlain.addLast(data)
        pump()
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        runCatching { serverEngine.closeOutbound() }
        runCatching { clientEngine.closeOutbound() }
    }

    private fun pump() {
        if (failed || closed) return
        var progress = true
        var steps = 0
        while (progress && steps < 80 && !failed && !closed) {
            steps++
            progress = false
            progress = runTasks(serverEngine) || progress
            progress = runTasks(clientEngine) || progress
            progress = unwrap(serverEngine, fromClientNet, fromClient = true) || progress
            progress = unwrap(clientEngine, fromOriginNet, fromClient = false) || progress
            if (serverEngine.handshakeStatus == HandshakeStatus.NEED_WRAP) {
                progress = wrap(serverEngine, toClientPlain, toClientNet, emitToClient) || progress
            }
            if (clientEngine.handshakeStatus == HandshakeStatus.NEED_WRAP) {
                progress = wrap(clientEngine, toOriginPlain, toOriginNet, emitToOrigin) || progress
            }
            if (toOriginPlain.isNotEmpty()) {
                progress = wrap(clientEngine, toOriginPlain, toOriginNet, emitToOrigin) || progress
            }
            if (toClientPlain.isNotEmpty()) {
                progress = wrap(serverEngine, toClientPlain, toClientNet, emitToClient) || progress
            }
        }
    }

    private fun unwrap(engine: SSLEngine, net: GrowBuf, fromClient: Boolean): Boolean {
        if (net.pending() == 0) return false
        val src = net.forRead()
        var progressed = false
        try {
            while (src.hasRemaining() && !failed && !closed) {
                var plain = if (fromClient) serverPlain else clientPlain
                plain.clear()
                val result = try {
                    engine.unwrap(src, plain)
                } catch (_: Exception) {
                    fail()
                    return true
                }
                when (result.status) {
                    Status.BUFFER_UNDERFLOW -> break
                    Status.BUFFER_OVERFLOW -> {
                        plain = growApp(plain)
                        if (fromClient) serverPlain = plain else clientPlain = plain
                        progressed = true
                        continue
                    }
                    Status.CLOSED -> {
                        close()
                        return true
                    }
                    Status.OK -> {
                        progressed = true
                        plain.flip()
                        if (plain.hasRemaining()) {
                            val data = ByteArray(plain.remaining())
                            plain.get(data)
                            handlePlain(fromClient, data)
                        }
                    }
                    else -> break
                }
                if (result.handshakeStatus == HandshakeStatus.NEED_TASK) {
                    runTasks(engine)
                }
            }
        } finally {
            net.afterRead()
        }
        return progressed
    }

    private fun handlePlain(fromClient: Boolean, data: ByteArray) {
        if (fromClient) {
            val forward = try {
                onClientPlain(data)
            } catch (_: Exception) {
                data
            }
            if (forward != null && forward.isNotEmpty()) toOriginPlain.addLast(forward)
        } else {
            val forward = try {
                onServerPlain(data)
            } catch (_: Exception) {
                data
            }
            if (forward != null && forward.isNotEmpty()) toClientPlain.addLast(forward)
        }
    }

    private fun wrap(
        engine: SSLEngine,
        queue: ArrayDeque<ByteArray>,
        packetIn: ByteBuffer,
        emit: (ByteArray) -> Unit,
    ): Boolean {
        var packet = packetIn
        var assigned: ByteBuffer = packet
        var progressed = false

        fun store() {
            if (engine === serverEngine) toClientNet = assigned else toOriginNet = assigned
        }

        if (engine.handshakeStatus == HandshakeStatus.NEED_WRAP && queue.isEmpty()) {
            val empty = EMPTY
            assigned.clear()
            val result = try {
                engine.wrap(empty, assigned)
            } catch (_: Exception) {
                fail()
                return true
            }
            if (result.status == Status.BUFFER_OVERFLOW) {
                assigned = growNet(assigned)
                empty.rewind()
                val retry = try {
                    engine.wrap(empty, assigned)
                } catch (_: Exception) {
                    fail()
                    store()
                    return true
                }
                progressed = emitPacket(retry, assigned, emit) || progressed
            } else {
                progressed = emitPacket(result, assigned, emit) || progressed
            }
            if (result.handshakeStatus == HandshakeStatus.NEED_TASK) runTasks(engine)
            store()
            return progressed
        }

        while (queue.isNotEmpty() && !failed && !closed) {
            val first = queue.first()
            val app = ByteBuffer.wrap(first)
            assigned.clear()
            val result = try {
                engine.wrap(app, assigned)
            } catch (_: Exception) {
                fail()
                store()
                return true
            }
            when (result.status) {
                Status.BUFFER_OVERFLOW -> {
                    assigned = growNet(assigned)
                    progressed = true
                    store()
                    continue
                }
                Status.CLOSED -> {
                    close()
                    store()
                    return true
                }
                Status.OK, Status.BUFFER_UNDERFLOW -> {
                    progressed = emitPacket(result, assigned, emit) || progressed
                    if (app.hasRemaining()) {
                        val rest = ByteArray(app.remaining())
                        app.get(rest)
                        queue.removeFirst()
                        if (rest.isNotEmpty()) queue.addFirst(rest)
                    } else {
                        queue.removeFirst()
                    }
                }
                else -> {
                    store()
                    return progressed
                }
            }
            if (result.handshakeStatus == HandshakeStatus.NEED_TASK) runTasks(engine)
        }
        store()
        return progressed
    }

    private fun emitPacket(result: javax.net.ssl.SSLEngineResult, packet: ByteBuffer, emit: (ByteArray) -> Unit): Boolean {
        packet.flip()
        if (packet.hasRemaining()) {
            val out = ByteArray(packet.remaining())
            packet.get(out)
            emit(out)
            return true
        }
        return result.bytesProduced() > 0 || result.bytesConsumed() > 0
    }

    private fun runTasks(engine: SSLEngine): Boolean {
        var ran = false
        var task = engine.delegatedTask
        while (task != null) {
            try {
                task.run()
            } catch (_: Exception) {
                fail()
                return true
            }
            ran = true
            task = engine.delegatedTask
        }
        return ran
    }

    private fun fail() {
        if (failed) return
        failed = true
        onError()
    }

    private class GrowBuf {
        var buf: ByteBuffer = ByteBuffer.allocate(32 * 1024)

        fun pending(): Int = buf.position()

        fun append(data: ByteArray) {
            if (buf.remaining() < data.size) {
                buf.flip()
                val n = ByteBuffer.allocate((buf.remaining() + data.size + 16 * 1024).coerceAtLeast(buf.capacity() * 2))
                n.put(buf)
                buf = n
            }
            buf.put(data)
        }

        fun forRead(): ByteBuffer {
            buf.flip()
            return buf
        }

        fun afterRead() {
            buf.compact()
        }
    }

    companion object {
        private val EMPTY = ByteBuffer.allocate(0)

        private fun createServerEngine(
            host: String,
            leaf: X509Certificate,
            key: PrivateKey,
            ca: X509Certificate,
        ): SSLEngine {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null)
            ks.setKeyEntry("leaf", key, CharArray(0), arrayOf(leaf, ca))
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, CharArray(0))
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(kmf.keyManagers, null, SecureRandom())
            return ctx.createSSLEngine(host, 443).apply {
                useClientMode = false
                wantClientAuth = false
                needClientAuth = false
                applyAlpn(this)
                beginHandshake()
            }
        }

        private fun createClientEngine(host: String): SSLEngine {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, tmf.trustManagers, SecureRandom())
            return ctx.createSSLEngine(host, 443).apply {
                useClientMode = true
                applyAlpn(this)
                try {
                    sslParameters = sslParameters.apply {
                        serverNames = listOf(SNIHostName(host))
                    }
                } catch (_: Exception) {
                }
                beginHandshake()
            }
        }

        private fun applyAlpn(engine: SSLEngine) {
            try {
                engine.sslParameters = engine.sslParameters.apply {
                    applicationProtocols = arrayOf("http/1.1")
                }
            } catch (_: Throwable) {
            }
        }

        private fun growApp(buf: ByteBuffer): ByteBuffer {
            val n = ByteBuffer.allocate(buf.capacity() * 2)
            return n
        }

        private fun growNet(buf: ByteBuffer): ByteBuffer {
            return ByteBuffer.allocate(buf.capacity() * 2)
        }
    }
}
