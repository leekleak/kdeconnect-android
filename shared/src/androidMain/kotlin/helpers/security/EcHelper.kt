package org.kde.kdeconnect.helpers.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec

actual object EcHelper {
    private const val ALIAS = "connect_identity"
    private const val KEYSTORE = "AndroidKeyStore"

    actual fun ensureKeyPair() {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (ks.containsAlias(ALIAS)) return

        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, KEYSTORE
        )
        generator.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
                .build()
        )
        generator.generateKeyPair()
    }

    actual fun getPublicKey(): PublicKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return ks.getCertificate(ALIAS).publicKey
    }

    actual fun getPrivateKey(): PrivateKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
    }
}
