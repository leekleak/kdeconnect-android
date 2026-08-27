/*
 * SPDX-FileCopyrightText: 2015 Vineet Garg <grg.vineet@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.helpers.security

import kotlinx.coroutines.flow.first
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x500.style.IETFUtils
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.kde.kdeconnect.datastore.SettingsDataStore
import org.kde.kdeconnect.device.DeviceInfo
import org.kde.kdeconnect.helpers.DeviceSettings
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.RandomHelper
import org.kde.kdeconnect.helpers.security.EcHelper.getPrivateKey
import org.kde.kdeconnect.helpers.security.EcHelper.getPublicKey
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Formatter
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class SslHelper(
    private val settingsDataStore: SettingsDataStore,
    private val deviceSettings: DeviceSettings,
) {
    lateinit var certificate: Certificate //my device's certificate
    private val factory: CertificateFactory = CertificateFactory.getInstance("X.509")

    private val trustAllCerts: Array<TrustManager> = arrayOf(object : X509TrustManager {
        private val issuers = emptyArray<X509Certificate>()
        override fun getAcceptedIssuers(): Array<X509Certificate> = issuers
        override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) = Unit
    })

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun initialiseCertificate(platformContext: Any? = null) {
        val privateKey: PrivateKey = getPrivateKey()
        val publicKey: PublicKey = getPublicKey()

        LoggerTagged.i { "Key algorithm: " + publicKey.algorithm }

        var needsToGenerateCertificate = false
        val deviceId: String = settingsDataStore.deviceId.first()
        val certificateBase64 = settingsDataStore.certificate.first()

        if (certificateBase64.isNotEmpty()) {
            val currDate = Date()
            try {
                val certificateBytes = Base64.decode(certificateBase64)
                val cert = parseCertificate(certificateBytes) as X509Certificate

                val certDeviceId = getCommonNameFromCertificate(cert)
                if (certDeviceId != deviceId) {
                    LoggerTagged.e { "The certificate stored is from a different device id! (found: $certDeviceId expected:$deviceId)" }
                    needsToGenerateCertificate = true
                } else if (cert.notAfter.time < currDate.time) {
                    LoggerTagged.e { "The certificate expired: " + cert.notAfter }
                    needsToGenerateCertificate = true
                } else if (cert.notBefore.time > currDate.time) {
                    LoggerTagged.e { "The certificate is not effective yet: " + cert.notBefore }
                    needsToGenerateCertificate = true
                } else {
                    certificate = cert
                }
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Exception reading own certificate" }
                needsToGenerateCertificate = true
            }
        } else {
            needsToGenerateCertificate = true
        }

        if (needsToGenerateCertificate) {
            deviceSettings.removeAllTrustedDevices()
            LoggerTagged.i { "Generating a certificate" }

            val nameBuilder = X500NameBuilder(BCStyle.INSTANCE)
            nameBuilder.addRDN(BCStyle.CN, deviceId)
            nameBuilder.addRDN(BCStyle.OU, "KDE Connect")
            nameBuilder.addRDN(BCStyle.O, "KDE")
            val localDate = LocalDate.now()
            val notBefore = localDate.minusYears(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val notAfter = localDate.plusYears(10).atStartOfDay(ZoneId.systemDefault()).toInstant()
            val certificateBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                nameBuilder.build(),
                BigInteger.ONE,
                Date.from(notBefore),
                Date.from(notAfter),
                nameBuilder.build(),
                publicKey
            )
            val keyAlgorithm = privateKey.algorithm
            val signatureAlgorithm = if ("RSA" == keyAlgorithm) "SHA512withRSA" else "SHA256withECDSA"
            val contentSigner = JcaContentSignerBuilder(signatureAlgorithm).build(privateKey)
            val certificateBytes = certificateBuilder.build(contentSigner).encoded
            certificate = parseCertificate(certificateBytes)

            settingsDataStore.setCertificate(Base64.encode(certificateBytes))
        }
    }

    private fun getSslContextForDevice(deviceInfo: DeviceInfo?, isDeviceTrusted: Boolean): SSLContext {
        // TODO: This method is called for each payload that is sent. Cache the result.
        val privateKey = getPrivateKey()

        // Setup keystore
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("key", privateKey, "".toCharArray(), arrayOf(certificate))

        // Add device certificate if device trusted
        if (isDeviceTrusted && deviceInfo != null) {
            val remoteDeviceCertificate = parseCertificate(deviceInfo.certificate)
            keyStore.setCertificateEntry(deviceInfo.id, remoteDeviceCertificate)
        }

        // Setup key manager factory
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, "".toCharArray())

        // Setup default trust manager
        val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)

        // Setup custom trust manager if device not trusted
        val tlsContext = SSLContext.getInstance("TLS")
        if (isDeviceTrusted) {
            tlsContext.init(keyManagerFactory.keyManagers, trustManagerFactory.trustManagers, RandomHelper.secureRandom)
        } else {
            tlsContext.init(keyManagerFactory.keyManagers, trustAllCerts, RandomHelper.secureRandom)
        }
        return tlsContext
    }

    private fun configureSslSocket(socket: SSLSocket, isDeviceTrusted: Boolean, isClient: Boolean) {
        socket.soTimeout = 10000
        if (isClient) {
            socket.useClientMode = true
        } else {
            socket.useClientMode = false
            if (isDeviceTrusted) {
                socket.needClientAuth = true
            } else {
                socket.wantClientAuth = true
            }
        }
    }

    @Throws(CertificateException::class)
    fun convertToSslSocket(socket: Socket, deviceInfo: DeviceInfo?, isDeviceTrusted: Boolean, clientMode: Boolean): SSLSocket {
        val sslSocketFactory = getSslContextForDevice(deviceInfo, isDeviceTrusted).socketFactory
        val sslSocket = sslSocketFactory.createSocket(socket, socket.inetAddress.hostAddress, socket.port, true) as SSLSocket
        configureSslSocket(sslSocket, isDeviceTrusted, clientMode)
        return sslSocket
    }

    fun getCertificateHash(certificate: Certificate): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        val formatter = Formatter()
        for (b in hash) {
            formatter.format("%02x:", b)
        }
        return formatter.toString()
    }

    fun parseCertificate(certificateBytes: ByteArray): Certificate {
        return factory.generateCertificate(ByteArrayInputStream(certificateBytes))
    }

    fun getCommonNameFromCertificate(cert: X509Certificate): String {
        val principal = cert.subjectX500Principal
        val x500name = X500Name(principal.name)
        val rdn = x500name.getRDNs(BCStyle.CN).first()
        return IETFUtils.valueToString(rdn.getFirst().value)
    }

    companion object {
        private val factory: CertificateFactory = CertificateFactory.getInstance("X.509")

        fun parseCertificate(certificateBytes: ByteArray): Certificate {
            return factory.generateCertificate(ByteArrayInputStream(certificateBytes))
        }
    }
}
