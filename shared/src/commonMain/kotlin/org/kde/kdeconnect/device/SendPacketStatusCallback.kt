package org.kde.kdeconnect.device

interface SendPacketStatusCallback {
    fun onSuccess()
    fun onFailure(e: Throwable)
    fun onPayloadProgressChanged(percent: Int)
}