package org.kde.kdeconnect.helpers.security

import java.security.PrivateKey
import java.security.PublicKey

expect object EcHelper {
    fun ensureKeyPair()
    fun getPublicKey(): PublicKey
    fun getPrivateKey(): PrivateKey
}
