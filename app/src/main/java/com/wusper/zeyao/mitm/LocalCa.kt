package com.wusper.zeyao.mitm

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.security.KeyChain
import androidx.core.content.FileProvider
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Local user CA for optional HTTPS decryption. The private key never leaves
 * this device and is stored with EncryptedFile. Do not log or export the key.
 */
object LocalCa {
    const val COMMON_NAME = "擇要 CA"
    const val DOWNLOAD_NAME = "擇要CA.crt"
    const val FILE_PROVIDER = "com.wusper.zeyao.files"

    @Volatile private var ready = false
    @Volatile private var cert: X509Certificate? = null
    @Volatile private var key: PrivateKey? = null

    private val leafs = ConcurrentHashMap<String, Pair<X509Certificate, PrivateKey>>()
    private val lock = Any()

    fun ensure(context: Context) {
        if (ready && cert != null && key != null) return
        synchronized(lock) {
            if (ready && cert != null && key != null) return
            val app = context.applicationContext
            val dir = File(app.filesDir, "ca").apply { mkdirs() }
            val certFile = File(dir, "zeyao-ca.crt")
            val keyFile = File(dir, "zeyao-ca.key")
            if (certFile.isFile && keyFile.isFile) {
                runCatching {
                    val loadedCert = certFromDerOrPem(certFile.readBytes())
                    val loadedKey = readEncryptedKey(app, keyFile)
                    cert = loadedCert
                    key = loadedKey
                    ready = true
                }
            }
            if (!ready) {
                val pair = generateCa()
                certFile.writeBytes(pair.first.encoded)
                writeEncryptedKey(app, keyFile, pair.second)
                // Public PEM copy for the user to install.
                File(dir, "擇要CA.pem").writeText(toPem("CERTIFICATE", pair.first.encoded))
                cert = pair.first
                key = pair.second
                ready = true
            }
        }
    }

    fun certificate(): X509Certificate = cert ?: error("LocalCa not initialised")

    fun daysUntilExpiry(): Int {
        val c = cert ?: return -1
        val now = System.currentTimeMillis()
        val exp = c.notAfter.time
        return ((exp - now) / 86_400_000L).toInt().coerceAtLeast(0)
    }

    fun pem(): String = toPem("CERTIFICATE", certificate().encoded)

    fun der(): ByteArray = certificate().encoded

    fun publicCrtFile(context: Context): File {
        ensure(context)
        val dir = File(context.applicationContext.cacheDir, "certs").apply { mkdirs() }
        val out = File(dir, "擇要CA.crt")
        out.writeText(pem())
        return out
    }

    fun publicCrtUri(context: Context): Uri {
        val file = publicCrtFile(context)
        return FileProvider.getUriForFile(context, FILE_PROVIDER, file)
    }

    fun isInstalled(context: Context): Boolean {
        val ours = runCatching { certificate() }.getOrNull() ?: return false
        return runCatching {
            val ks = KeyStore.getInstance("AndroidCAStore")
            ks.load(null)
            val aliases = ks.aliases()
            while (aliases.hasMoreElements()) {
                val c = ks.getCertificate(aliases.nextElement()) as? X509Certificate ?: continue
                if (c.encoded.contentEquals(ours.encoded)) return@runCatching true
                if (c.subjectX500Principal == ours.subjectX500Principal &&
                    c.publicKey.encoded.contentEquals(ours.publicKey.encoded)
                ) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    fun installIntent(context: Context): Intent {
        ensure(context)
        val der = der()
        if (Build.VERSION.SDK_INT < 30) {
            return KeyChain.createInstallIntent().apply {
                putExtra(KeyChain.EXTRA_CERTIFICATE, der)
                putExtra(KeyChain.EXTRA_NAME, COMMON_NAME)
            }
        }
        // Android 11+ no longer installs a CA via KeyChain.createInstallIntent.
        // Export the .crt and open security settings; the user picks CA certificate.
        val uri = publicCrtUri(context)
        return Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("zeyao_crt", uri.toString())
        }
    }

    fun shareCrtIntent(context: Context): Intent {
        val uri = publicCrtUri(context)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/x-x509-ca-cert"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, COMMON_NAME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    data class ExportedCrt(val uri: Uri, val displayPath: String)

    /**
     * Writes 擇要CA.crt into the public Downloads folder so the user can
     * pick it when installing a CA certificate.
     */
    fun exportToDownloads(context: Context): ExportedCrt {
        ensure(context)
        val pem = pem().toByteArray(Charsets.UTF_8)
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = app.contentResolver
            val existing = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(DOWNLOAD_NAME),
                null,
            )
            existing?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                    resolver.delete(uri, null, null)
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, DOWNLOAD_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "application/x-x509-ca-cert")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("無法寫入下載資料夾")
            resolver.openOutputStream(uri)?.use { it.write(pem) }
                ?: error("無法寫入下載資料夾")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return ExportedCrt(uri, "下載 / $DOWNLOAD_NAME")
        }
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = java.io.File(dir, DOWNLOAD_NAME)
        file.writeBytes(pem)
        return ExportedCrt(Uri.fromFile(file), file.absolutePath)
    }

    fun issueLeaf(host: String): Pair<X509Certificate, PrivateKey> {
        val h = host.trim().lowercase().trimEnd('.')
        leafs[h]?.let { return it }
        synchronized(lock) {
            leafs[h]?.let { return it }
            val caCert = certificate()
            val caKey = key ?: error("LocalCa not initialised")
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val pair = kpg.genKeyPair()
            val now = System.currentTimeMillis()
            val notBefore = Date(now - 86_400_000L)
            val notAfter = Date(now + 400L * 86_400_000L)
            val serial = BigInteger(80, SecureRandom())
            val subject = X500Name("CN=$h")
            val issuer = X500Name(caCert.subjectX500Principal.name)
            val builder = JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, pair.public,
            )
            val ext = JcaX509ExtensionUtils()
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            builder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
            )
            builder.addExtension(
                Extension.extendedKeyUsage,
                false,
                ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
            )
            builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(pair.public))
            builder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                ext.createAuthorityKeyIdentifier(caCert.publicKey),
            )
            builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(GeneralName(GeneralName.dNSName, h)),
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(caKey)
            val encoded = builder.build(signer).encoded
            val leaf = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
            val issued = leaf to pair.private
            if (leafs.size > 64) {
                val first = leafs.keys.first()
                leafs.remove(first)
            }
            leafs[h] = issued
            return issued
        }
    }

    private fun generateCa(): Pair<X509Certificate, PrivateKey> {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val pair: KeyPair = kpg.genKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 86_400_000L)
        val notAfter = Date(now + 10L * 365 * 86_400_000L)
        val serial = BigInteger(80, SecureRandom())
        val name = X500Name("CN=$COMMON_NAME, O=Wusper, OU=Zeyao")
        val builder = JcaX509v3CertificateBuilder(
            name, serial, notBefore, notAfter, name, pair.public,
        )
        val ext = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
        )
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(pair.public))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
        val encoded = builder.build(signer).encoded
        val ca = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
        return ca to pair.private
    }

    private fun writeEncryptedKey(context: Context, file: File, privateKey: PrivateKey) {
        if (file.exists()) file.delete()
        val master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val ef = EncryptedFile.Builder(
            file,
            context,
            master,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
        ef.openFileOutput().use { it.write(privateKey.encoded) }
    }

    private fun readEncryptedKey(context: Context, file: File): PrivateKey {
        val master = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val ef = EncryptedFile.Builder(
            file,
            context,
            master,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
        val bytes = ef.openFileInput().use { it.readBytes() }
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
    }

    private fun certFromDerOrPem(bytes: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }

    private fun toPem(type: String, der: ByteArray): String {
        val b64 = android.util.Base64.encodeToString(der, android.util.Base64.NO_WRAP)
        val lines = b64.chunked(64).joinToString("\n")
        return "-----BEGIN " + type + "-----\n" + lines + "\n-----END " + type + "-----\n"
    }
}
