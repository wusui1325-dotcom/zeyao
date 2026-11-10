package com.wusper.zeyao.intercept

import com.wusper.zeyao.data.NewsHosts
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.util.ArrayDeque
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

/**
 * Userspace IPv4 TCP/UDP forwarder for the local VpnService.
 * HTTPS is never decrypted. News hosts may have their first flight held
 * until the user chooses 進入 or 返回 on the intercept page.
 */
class PacketForwarder(
    private val vpn: ZeyaoVpnService,
    tun: ParcelFileDescriptor,
) {
    private val tunIn = FileInputStream(tun.fileDescriptor)
    private val tunOut = FileOutputStream(tun.fileDescriptor)
    private val selector: Selector = Selector.open()
    private val tcp = ConcurrentHashMap<ConnKey, TcpPipe>()
    private val udp = ConcurrentHashMap<ConnKey, UdpPipe>()
    private val tasks = ConcurrentLinkedQueue<() -> Unit>()
    private val writeLock = Any()
    private val random = Random()
    @Volatile private var running = false
    private var tunThread: Thread? = null
    private var selectThread: Thread? = null

    fun start() {
        running = true
        tunThread = Thread({ tunLoop() }, "zeyao-tun").also { it.isDaemon = true; it.start() }
        selectThread = Thread({ selectLoop() }, "zeyao-select").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        running = false
        runCatching { selector.wakeup() }
        runCatching { tunIn.close() }
        runCatching { tunOut.close() }
        tcp.values.forEach { runCatching { it.channel.close() } }
        udp.values.forEach { runCatching { it.channel.close() } }
        tcp.clear()
        udp.clear()
        runCatching { selector.close() }
    }

    private fun post(task: () -> Unit) {
        tasks.add(task)
        runCatching { selector.wakeup() }
    }

    private fun tunLoop() {
        val buf = ByteArray(32767)
        while (running) {
            val n = try {
                tunIn.read(buf)
            } catch (_: IOException) {
                break
            } ?: break
            if (n <= 0) continue
            try {
                handlePacket(buf, n)
            } catch (_: Exception) {
            }
        }
    }

    private fun selectLoop() {
        val readBuf = ByteBuffer.allocate(32 * 1024)
        while (running) {
            while (true) {
                val task = tasks.poll() ?: break
                runCatching { task() }
            }
            val now = SystemClock.elapsedRealtime()
            tcp.values.forEach { pipe ->
                if (pipe.holding && pipe.holdDeadline > 0 && now > pipe.holdDeadline) {
                    pipe.releaseHold()
                }
            }
            val selected = try {
                selector.select(250)
            } catch (_: IOException) {
                break
            }
            if (!running) break
            if (selected == 0) continue
            val it = selector.selectedKeys().iterator()
            while (it.hasNext()) {
                val key = it.next()
                it.remove()
                try {
                    when (val att = key.attachment()) {
                        is TcpPipe -> handleTcpKey(key, att, readBuf)
                        is UdpPipe -> handleUdpKey(key, att, readBuf)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handlePacket(buf: ByteArray, length: Int) {
        if (length < 20) return
        val version = (buf[0].toInt() ushr 4) and 0xF
        if (version != 4) return
        val ihl = (buf[0].toInt() and 0xF) * 4
        if (ihl < 20 || length < ihl) return
        val proto = buf[9].toInt() and 0xFF
        when (proto) {
            PROTO_TCP -> handleTcpPacket(buf, length, ihl)
            PROTO_UDP -> handleUdpPacket(buf, length, ihl)
        }
    }

    private fun handleUdpPacket(buf: ByteArray, length: Int, ihl: Int) {
        if (length < ihl + 8) return
        val srcIp = Ip.readInt(buf, 12)
        val dstIp = Ip.readInt(buf, 16)
        val srcPort = Ip.readU16(buf, ihl)
        val dstPort = Ip.readU16(buf, ihl + 2)
        val udpLen = Ip.readU16(buf, ihl + 4)
        val payloadOffset = ihl + 8
        val payloadLen = min(length - payloadOffset, (udpLen - 8).coerceAtLeast(0))
        val key = ConnKey(srcIp, srcPort, dstIp, dstPort)
        val pipe = udp[key] ?: createUdp(key)
        if (payloadLen > 0) {
            val payload = ByteBuffer.wrap(buf, payloadOffset, payloadLen).slice()
            runCatching { pipe.channel.write(payload) }
        }
    }

    private fun createUdp(key: ConnKey): UdpPipe {
        val channel = DatagramChannel.open()
        channel.configureBlocking(false)
        vpn.protectDatagram(channel.socket())
        channel.connect(InetSocketAddress(Ip.toAddr(key.dstIp), key.dstPort))
        val pipe = UdpPipe(key, channel)
        udp[key] = pipe
        post {
            runCatching { channel.register(selector, SelectionKey.OP_READ, pipe) }
        }
        return pipe
    }

    private fun handleUdpKey(key: SelectionKey, pipe: UdpPipe, buf: ByteBuffer) {
        if (!key.isReadable) return
        buf.clear()
        val n = try {
            pipe.channel.read(buf)
        } catch (_: IOException) {
            closeUdp(pipe)
            return
        }
        if (n <= 0) return
        buf.flip()
        val payload = ByteArray(buf.remaining())
        buf.get(payload)
        injectUdp(pipe.key, payload)
    }

    private fun closeUdp(pipe: UdpPipe) {
        udp.remove(pipe.key)
        runCatching { pipe.channel.close() }
    }

    private fun injectUdp(key: ConnKey, payload: ByteArray) {
        val total = 20 + 8 + payload.size
        val pkt = ByteArray(total)
        Ip.writeIpv4Header(pkt, total, PROTO_UDP, key.dstIp, key.srcIp)
        Ip.writeU16(pkt, 20, key.dstPort)
        Ip.writeU16(pkt, 22, key.srcPort)
        Ip.writeU16(pkt, 24, 8 + payload.size)
        Ip.writeU16(pkt, 26, 0) // checksum optional on IPv4
        System.arraycopy(payload, 0, pkt, 28, payload.size)
        writeTun(pkt)
    }

    private fun handleTcpPacket(buf: ByteArray, length: Int, ihl: Int) {
        if (length < ihl + 20) return
        val srcIp = Ip.readInt(buf, 12)
        val dstIp = Ip.readInt(buf, 16)
        val srcPort = Ip.readU16(buf, ihl)
        val dstPort = Ip.readU16(buf, ihl + 2)
        val seq = Ip.readU32(buf, ihl + 4)
        val ack = Ip.readU32(buf, ihl + 8)
        val dataOff = ((buf[ihl + 12].toInt() and 0xF0) ushr 4) * 4
        val flags = buf[ihl + 13].toInt() and 0xFF
        val payloadOffset = ihl + dataOff
        val payloadLen = (length - payloadOffset).coerceAtLeast(0)
        val key = ConnKey(srcIp, srcPort, dstIp, dstPort)
        val syn = flags and FLAG_SYN != 0
        val ackFlag = flags and FLAG_ACK != 0
        val rst = flags and FLAG_RST != 0
        val fin = flags and FLAG_FIN != 0

        if (rst) {
            tcp.remove(key)?.let { runCatching { it.channel.close() } }
            return
        }

        var pipe = tcp[key]
        if (syn && !ackFlag) {
            if (pipe != null) {
                // retransmission of SYN — resend SYN-ACK
                injectTcp(pipe, FLAG_SYN or FLAG_ACK, payload = Empty, seqOverride = (pipe.serverSeq - 1) and 0xFFFFFFFFL)
                return
            }
            pipe = createTcp(key, seq)
            injectTcp(pipe, FLAG_SYN or FLAG_ACK, payload = Empty, seqOverride = (pipe.serverSeq - 1) and 0xFFFFFFFFL)
            return
        }
        if (pipe == null) {
            // unexpected segment — RST
            injectRawRst(key, seq, ack)
            return
        }
        if (payloadLen > 0) {
            val expected = pipe.clientSeq
            if (seq == expected) {
                val payload = buf.copyOfRange(payloadOffset, payloadOffset + payloadLen)
                pipe.clientSeq = (pipe.clientSeq + payloadLen) and 0xFFFFFFFFL
                injectTcp(pipe, FLAG_ACK, Empty)
                pipe.onClientData(payload)
            } else if (unsignedLt(seq, expected)) {
                // already ACKed; ACK again
                injectTcp(pipe, FLAG_ACK, Empty)
            }
        }
        if (fin) {
            val finSeq = if (payloadLen > 0) (seq + payloadLen) and 0xFFFFFFFFL else seq
            if (finSeq == pipe.clientSeq) {
                pipe.clientSeq = (pipe.clientSeq + 1) and 0xFFFFFFFFL
                injectTcp(pipe, FLAG_ACK, Empty)
                pipe.onClientFin()
            }
        }
    }

    private fun createTcp(key: ConnKey, clientSynSeq: Long): TcpPipe {
        val channel = SocketChannel.open()
        channel.configureBlocking(false)
        vpn.protectSocket(channel.socket())
        val iss = random.nextInt().toLong() and 0xFFFFFFFFL
        val pipe = TcpPipe(
            key = key,
            channel = channel,
            clientSeq = (clientSynSeq + 1) and 0xFFFFFFFFL,
            serverSeq = (iss + 1) and 0xFFFFFFFFL,
        )
        tcp[key] = pipe
        val connected = try {
            channel.connect(InetSocketAddress(Ip.toAddr(key.dstIp), key.dstPort))
        } catch (_: Exception) {
            false
        }
        post {
            try {
                val ops = if (connected) SelectionKey.OP_READ else SelectionKey.OP_CONNECT or SelectionKey.OP_READ
                channel.register(selector, ops, pipe)
                pipe.connected = connected || channel.finishConnect()
            } catch (_: Exception) {
                resetAndDrop(pipe)
            }
        }
        return pipe
    }

    private fun handleTcpKey(key: SelectionKey, pipe: TcpPipe, buf: ByteBuffer) {
        if (key.isConnectable) {
            try {
                if (pipe.channel.finishConnect()) {
                    pipe.connected = true
                    var ops = SelectionKey.OP_READ
                    if (pipe.hasOutbound()) ops = ops or SelectionKey.OP_WRITE
                    key.interestOps(ops)
                    pipe.flushOutbound()
                }
            } catch (_: Exception) {
                resetAndDrop(pipe)
                return
            }
        }
        if (key.isWritable) {
            pipe.flushOutbound()
            if (!pipe.hasOutbound() && key.isValid) {
                key.interestOps(SelectionKey.OP_READ)
            }
        }
        if (key.isReadable) {
            buf.clear()
            val n = try {
                pipe.channel.read(buf)
            } catch (_: IOException) {
                resetAndDrop(pipe)
                return
            }
            if (n < 0) {
                // server closed
                if (!pipe.sentFin) {
                    injectTcp(pipe, FLAG_FIN or FLAG_ACK, Empty)
                    pipe.serverSeq = (pipe.serverSeq + 1) and 0xFFFFFFFFL
                    pipe.sentFin = true
                }
                return
            }
            if (n > 0) {
                buf.flip()
                val data = ByteArray(buf.remaining())
                buf.get(data)
                var offset = 0
                while (offset < data.size) {
                    val end = min(data.size, offset + MAX_PAYLOAD)
                    val chunk = if (offset == 0 && end == data.size) data else data.copyOfRange(offset, end)
                    injectTcp(pipe, FLAG_PSH or FLAG_ACK, chunk)
                    pipe.serverSeq = (pipe.serverSeq + chunk.size) and 0xFFFFFFFFL
                    offset = end
                }
            }
        }
    }

    private fun injectTcp(pipe: TcpPipe, flags: Int, payload: ByteArray, seqOverride: Long? = null) {
        val payloadLen = payload.size
        val total = 20 + 20 + payloadLen
        val pkt = ByteArray(total)
        val key = pipe.key
        Ip.writeIpv4Header(pkt, total, PROTO_TCP, key.dstIp, key.srcIp)
        val seq = seqOverride ?: pipe.serverSeq
        Ip.writeU16(pkt, 20, key.dstPort)
        Ip.writeU16(pkt, 22, key.srcPort)
        Ip.writeU32(pkt, 24, seq)
        Ip.writeU32(pkt, 28, pipe.clientSeq)
        pkt[32] = (5 shl 4).toByte()
        pkt[33] = flags.toByte()
        Ip.writeU16(pkt, 34, 65535)
        Ip.writeU16(pkt, 36, 0)
        Ip.writeU16(pkt, 38, 0)
        if (payloadLen > 0) System.arraycopy(payload, 0, pkt, 40, payloadLen)
        val checksum = Ip.tcpChecksum(pkt, 20, 20 + payloadLen, key.dstIp, key.srcIp)
        Ip.writeU16(pkt, 36, checksum)
        writeTun(pkt)
    }

    private fun injectRawRst(key: ConnKey, seq: Long, ack: Long) {
        val pkt = ByteArray(40)
        Ip.writeIpv4Header(pkt, 40, PROTO_TCP, key.dstIp, key.srcIp)
        Ip.writeU16(pkt, 20, key.dstPort)
        Ip.writeU16(pkt, 22, key.srcPort)
        Ip.writeU32(pkt, 24, ack) // our seq
        Ip.writeU32(pkt, 28, (seq + 1) and 0xFFFFFFFFL)
        pkt[32] = (5 shl 4).toByte()
        pkt[33] = (FLAG_RST or FLAG_ACK).toByte()
        Ip.writeU16(pkt, 34, 0)
        val checksum = Ip.tcpChecksum(pkt, 20, 20, key.dstIp, key.srcIp)
        Ip.writeU16(pkt, 36, checksum)
        writeTun(pkt)
    }

    private fun resetAndDrop(pipe: TcpPipe) {
        injectTcp(pipe, FLAG_RST or FLAG_ACK, Empty)
        tcp.remove(pipe.key)
        runCatching { pipe.channel.close() }
    }

    private fun writeTun(pkt: ByteArray) {
        synchronized(writeLock) {
            try {
                tunOut.write(pkt)
                tunOut.flush()
            } catch (_: IOException) {
            }
        }
    }

    inner class TcpPipe(
        val key: ConnKey,
        val channel: SocketChannel,
        var clientSeq: Long,
        var serverSeq: Long,
    ) {
        @Volatile var connected: Boolean = false
        @Volatile var holding: Boolean = false
        @Volatile var sniInspected: Boolean = false
        var holdDeadline: Long = 0
        var sentFin: Boolean = false
        private val outbound = ArrayDeque<ByteArray>()
        private val holdBuf = ArrayDeque<ByteArray>()

        fun onClientData(payload: ByteArray) {
            if (!sniInspected) {
                sniInspected = true
                val host = when (key.dstPort) {
                    443 -> SniParser.parse(payload)
                    80 -> SniParser.parseHttpHost(payload)
                    else -> null
                }
                if (host != null && shouldHold(host)) {
                    holding = true
                    holdDeadline = SystemClock.elapsedRealtime() + HOLD_TIMEOUT_MS
                    holdBuf.addLast(payload)
                    InterceptController.registerHold(
                        host,
                        object : HeldConnection {
                            override fun release() = post { releaseHold() }
                            override fun reject() = post { rejectHold() }
                        },
                    )
                    return
                }
            }
            if (holding) {
                holdBuf.addLast(payload)
                return
            }
            enqueue(payload)
        }

        fun onClientFin() {
            if (holding) {
                holdBuf.addLast(FIN_MARK)
                return
            }
            runCatching { channel.shutdownOutput() }
        }

        fun releaseHold() {
            if (!holding) return
            holding = false
            while (holdBuf.isNotEmpty()) {
                val item = holdBuf.removeFirst()
                if (item === FIN_MARK) {
                    runCatching { channel.shutdownOutput() }
                } else {
                    enqueue(item)
                }
            }
        }

        fun rejectHold() {
            holding = false
            holdBuf.clear()
            resetAndDrop(this)
        }

        fun enqueue(data: ByteArray) {
            outbound.addLast(data)
            if (connected) {
                flushOutbound()
                interestWriteIfNeeded()
            }
        }

        fun hasOutbound(): Boolean = outbound.isNotEmpty()

        fun flushOutbound() {
            if (!connected) return
            while (outbound.isNotEmpty()) {
                val first = outbound.first()
                val buf = ByteBuffer.wrap(first)
                val written = try {
                    channel.write(buf)
                } catch (_: IOException) {
                    resetAndDrop(this)
                    return
                }
                if (written <= 0 && first.isNotEmpty()) break
                if (buf.hasRemaining()) {
                    outbound.removeFirst()
                    val rest = ByteArray(buf.remaining())
                    buf.get(rest)
                    outbound.addFirst(rest)
                    break
                } else {
                    outbound.removeFirst()
                }
            }
        }

        private fun interestWriteIfNeeded() {
            val keySel = channel.keyFor(selector) ?: return
            if (!keySel.isValid) return
            val ops = if (hasOutbound()) {
                SelectionKey.OP_READ or SelectionKey.OP_WRITE
            } else {
                SelectionKey.OP_READ
            }
            keySel.interestOps(ops)
            selector.wakeup()
        }
    }

    class UdpPipe(val key: ConnKey, val channel: DatagramChannel)

    data class ConnKey(val srcIp: Int, val srcPort: Int, val dstIp: Int, val dstPort: Int)

    companion object {
        private const val PROTO_TCP = 6
        private const val PROTO_UDP = 17
        private const val FLAG_FIN = 0x01
        private const val FLAG_SYN = 0x02
        private const val FLAG_RST = 0x04
        private const val FLAG_PSH = 0x08
        private const val FLAG_ACK = 0x10
        private const val MAX_PAYLOAD = 1360
        private const val HOLD_TIMEOUT_MS = 60_000L
        private val Empty = ByteArray(0)
        private val FIN_MARK = ByteArray(0) // identity compared with ===; use a unique instance
            get() = field

        private fun shouldHold(host: String): Boolean {
            if (!InterceptController.interceptArmedCached) return false
            if (!NewsHosts.isNewsHost(host)) return false
            if (InterceptController.isHostPassthrough(host)) return false
            return true
        }

        private fun unsignedLt(a: Long, b: Long): Boolean {
            return ((a - b) and 0xFFFFFFFFL) > 0x80000000L
        }
    }
}

internal object Ip {
    fun readInt(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    fun readU16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    fun readU32(b: ByteArray, o: Int): Long = readInt(b, o).toLong() and 0xFFFFFFFFL

    fun writeU16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 8).toByte()
        b[o + 1] = v.toByte()
    }

    fun writeU32(b: ByteArray, o: Int, v: Long) {
        val n = v.toInt()
        b[o] = (n ushr 24).toByte()
        b[o + 1] = (n ushr 16).toByte()
        b[o + 2] = (n ushr 8).toByte()
        b[o + 3] = n.toByte()
    }

    fun writeIpv4Header(pkt: ByteArray, total: Int, proto: Int, srcIp: Int, dstIp: Int) {
        pkt[0] = 0x45.toByte()
        pkt[1] = 0
        writeU16(pkt, 2, total)
        writeU16(pkt, 4, 0)
        writeU16(pkt, 6, 0x4000) // don't fragment
        pkt[8] = 64
        pkt[9] = proto.toByte()
        writeU16(pkt, 10, 0)
        writeU32(pkt, 12, srcIp.toLong() and 0xFFFFFFFFL)
        writeU32(pkt, 16, dstIp.toLong() and 0xFFFFFFFFL)
        val sum = checksum(pkt, 0, 20, 0)
        writeU16(pkt, 10, sum)
    }

    fun tcpChecksum(pkt: ByteArray, tcpOff: Int, tcpLen: Int, srcIp: Int, dstIp: Int): Int {
        var sum = 0
        sum += (srcIp ushr 16) and 0xFFFF
        sum += srcIp and 0xFFFF
        sum += (dstIp ushr 16) and 0xFFFF
        sum += dstIp and 0xFFFF
        sum += PROTO_TCP_CONST
        sum += tcpLen
        return checksum(pkt, tcpOff, tcpLen, sum)
    }

    fun checksum(buf: ByteArray, offset: Int, len: Int, initial: Int): Int {
        var sum = initial
        var i = offset
        val end = offset + len
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (buf[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    fun toAddr(ip: Int): java.net.InetAddress {
        val b = byteArrayOf(
            (ip ushr 24).toByte(),
            (ip ushr 16).toByte(),
            (ip ushr 8).toByte(),
            ip.toByte(),
        )
        return java.net.InetAddress.getByAddress(b)
    }

    private const val PROTO_TCP_CONST = 6
}
