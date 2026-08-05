package com.myhome.admin

import android.content.Context
import android.os.Build
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class MyAdbConnectionManager private constructor(
    private val mPrivateKey: PrivateKey,
    private val mCertificate: Certificate,
) : io.github.muntashirakon.adb.AbsAdbConnectionManager() {

    override fun getPrivateKey(): PrivateKey = mPrivateKey
    override fun getCertificate(): Certificate = mCertificate
    override fun getDeviceName(): String = "MyHome"

    companion object {
        @Volatile private var INSTANCE: MyAdbConnectionManager? = null

        fun getInstance(context: Context): MyAdbConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context).also { INSTANCE = it }
            }
        }

        private fun create(context: Context): MyAdbConnectionManager {
            ensureBouncyCastleProvider()
            val keyFile = File(context.filesDir, "adb_private.key")
            val certFile = File(context.filesDir, "adb_cert.pem")

            val existingKey = readPrivateKey(keyFile)
            val existingCert = readCertificate(certFile)
            if (existingKey != null && existingCert != null) {
                return MyAdbConnectionManager(existingKey, existingCert)
            }

            val keyPair = generateRsaKeyPair()
            val cert = generateSelfSignedCert(keyPair)
            writePrivateKey(keyFile, keyPair.private)
            writeCertificate(certFile, cert)
            return MyAdbConnectionManager(keyPair.private, cert)
        }

        private fun ensureBouncyCastleProvider() {
            // Android historically ships a frozen, stripped-down "BC" provider that's missing
            // many algorithms (including Signature.SHA256WITHRSA on some devices). Replace it
            // with our bundled full BC at the highest priority so libadb-android's pairing
            // code finds the algorithms it expects.
            val name = BouncyCastleProvider.PROVIDER_NAME
            Security.removeProvider(name)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }

        private fun generateRsaKeyPair(): KeyPair {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048)
            return gen.generateKeyPair()
        }

        private fun generateSelfSignedCert(keyPair: KeyPair): Certificate {
            val now = Date()
            val expiry = Date(now.time + 365L * 24 * 60 * 60 * 1000)
            val name = X500Name("CN=MyHome ADB")
            val serial = BigInteger.valueOf(System.currentTimeMillis())
            val builder = JcaX509v3CertificateBuilder(
                name, serial, now, expiry, name, keyPair.public,
            )
            // Don't pin to BC: on some devices its SHA256WITHRSA registration is missing or
            // shadowed. Let JCA pick whichever provider handles it (Conscrypt/AndroidOpenSSL
            // on Android always supports SHA256withRSA).
            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.private)
            val holder = builder.build(signer)
            return JcaX509CertificateConverter()
                .getCertificate(holder)
        }

        private fun readPrivateKey(file: File): PrivateKey? {
            if (!file.exists()) return null
            val bytes = file.readBytes()
            val spec = PKCS8EncodedKeySpec(bytes)
            return KeyFactory.getInstance("RSA").generatePrivate(spec)
        }

        private fun readCertificate(file: File): Certificate? {
            if (!file.exists()) return null
            FileInputStream(file).use { input ->
                return CertificateFactory.getInstance("X.509").generateCertificate(input)
            }
        }

        private fun writePrivateKey(file: File, key: PrivateKey) {
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { it.write(key.encoded) }
        }

        private fun writeCertificate(file: File, cert: Certificate) {
            file.parentFile?.mkdirs()
            val base64 = Base64.encodeToString(cert.encoded, Base64.DEFAULT)
            file.writeText(
                "-----BEGIN CERTIFICATE-----\n$base64-----END CERTIFICATE-----\n",
            )
        }
    }
}
