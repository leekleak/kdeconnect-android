/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.put
import org.bouncycastle.util.Arrays
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.compose.resources.StringResource
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.device.PairState
import org.kde.kdeconnect.device.SendPacketStatusCallback
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.error_already_paired
import org.kde.kdeconnect.generated.resources.error_canceled_by_other_peer
import org.kde.kdeconnect.generated.resources.error_canceled_by_user
import org.kde.kdeconnect.generated.resources.error_clocks_not_match
import org.kde.kdeconnect.generated.resources.error_not_reachable
import org.kde.kdeconnect.generated.resources.error_timed_out
import org.kde.kdeconnect.generated.resources.runcommand_notreachable
import org.kde.kdeconnect.helpers.LoggerTagged
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.Formatter
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class PairingHandler(
    private val device: Device,
    private val callback: PairingCallback,
) {
    private val pairingJob = SupervisorJob()
    private val pairingScope = CoroutineScope(Dispatchers.IO + pairingJob)
    private val pairingTimestamp = MutableStateFlow(0L)
    private val state: PairState get() = device.state.value.pairState

    fun updateState(newState: PairState) {
        runBlocking { device.updatePairState(newState, pairingTimestamp.value) }
    }

    interface PairingCallback {
        fun incomingPairRequest()

        fun pairingFailed(error: StringResource)

        fun pairingSuccessful()

        fun unpaired(device: Device)
    }

    fun packetReceived(np: NetworkPacket) {
        cancelTimer()
        val wantsPair = np.getBoolean("pair")
        if (wantsPair == true) {
            when (state) {
                PairState.Requested -> pairingDone()
                PairState.RequestedByPeer -> {
                    LoggerTagged.w { "Ignoring second pairing request before the first one timed out" }
                }

                PairState.Paired, PairState.NotPaired -> {
                    if (state == PairState.Paired) {
                        LoggerTagged.w { "Received pairing request from a device we already trusted." }
                        // It would be nice to auto-accept the pairing request here, but since the pairing accept and pairing request
                        // messages are identical, this could create an infinite loop if both devices are "accepting" each other pairs.
                        // Instead, unpair and handle as if "NotPaired". TODO: No longer true in protocol version 8
                        updateState(PairState.NotPaired)
                        callback.unpaired(device)
                    }

                    if (device.protocolVersion >= 8) {
                        pairingTimestamp.value = np.getLong("timestamp", -1L)
                        if (pairingTimestamp.value == -1L) {
                            LoggerTagged.w { "Unpairing due to invalid timestamp." }
                            updateState(PairState.NotPaired)
                            callback.unpaired(device)
                            return
                        }
                        val currentTimestamp = System.currentTimeMillis() / 1000L
                        if (abs(pairingTimestamp.value - currentTimestamp) > ALLOWED_TIMESTAMP_DIFFERENCE_SECONDS) {
                            updateState(PairState.NotPaired)
                            callback.pairingFailed(Res.string.error_clocks_not_match)
                            return
                        }
                    }

                    updateState(PairState.RequestedByPeer)

                    pairingScope.launch {
                        delay(25.seconds)
                        LoggerTagged.w { "Unpairing (timeout after we started pairing)" }
                        updateState(PairState.NotPaired)
                        callback.pairingFailed(Res.string.error_timed_out)
                    } // Time to show notification, waiting for user to accept (peer will timeout in 30 seconds)

                    callback.incomingPairRequest()
                }
            }
        } else {
            LoggerTagged.i { "Unpair request received" }
            when (state) {
                PairState.NotPaired -> LoggerTagged.i { "Ignoring unpair request for already unpaired device" }
                // Requested: We started pairing and got rejected
                // RequestedByPeer: They stared pairing, then cancelled
                PairState.Requested, PairState.RequestedByPeer -> {
                    updateState(PairState.NotPaired)
                    callback.pairingFailed(Res.string.error_canceled_by_other_peer)
                }

                PairState.Paired -> {
                    updateState(PairState.NotPaired)
                    callback.unpaired(device)
                }
            }
        }
    }

    suspend fun requestPairing() {
        cancelTimer()

        if (state == PairState.Paired) {
            LoggerTagged.w { "requestPairing was called on an already paired device" }
            callback.pairingFailed(Res.string.error_already_paired)
            return
        }

        if (state == PairState.RequestedByPeer) {
            LoggerTagged.w { "Pairing already started by the other end, accepting their request." }
            acceptPairing()
            return
        }

        if (!device.isReachable) {
            callback.pairingFailed(Res.string.error_not_reachable)
            return
        }

        pairingTimestamp.value = System.currentTimeMillis() / 1000L
        updateState(PairState.Requested)

        pairingScope.launch {
            delay(30.seconds)
            LoggerTagged.w { "Unpairing (timeout after receiving pair request)" }
            updateState(PairState.NotPaired)
            callback.pairingFailed(Res.string.error_timed_out)
        } // Time to wait for the other to accept

        val statusCallback = object : SendPacketStatusCallback {
            override fun onSuccess() {}

            override fun onFailure(e: Throwable) {
                cancelTimer()
                LoggerTagged.e(e) { "Exception sending pairing request" }
                updateState(PairState.NotPaired)
                callback.pairingFailed(Res.string.runcommand_notreachable)
            }

            override fun onPayloadProgressChanged(percent: Int) {}
        }
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR).update {
            put("pair", true)
            put("timestamp", pairingTimestamp.value)
        }
        device.sendPacket(np, statusCallback)
    }

    suspend fun acceptPairing() {
        cancelTimer()
        val stateCallback = object : SendPacketStatusCallback {
            override fun onSuccess() {
                pairingDone()
            }

            override fun onFailure(e: Throwable) {
                LoggerTagged.e(e) { "Exception sending accept pairing packet" }
                updateState(PairState.NotPaired)
                callback.pairingFailed(Res.string.error_not_reachable)
            }

            override fun onPayloadProgressChanged(percent: Int) {}
        }
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR).update {
            put("pair", true)
        }

        device.sendPacket(np, stateCallback)
    }

    suspend fun cancelPairing() {
        cancelTimer()
        updateState(PairState.NotPaired)
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR).update {
            put("pair", false)
        }
        device.sendPacket(np)
        callback.pairingFailed(Res.string.error_canceled_by_user)
    }

    @VisibleForTesting
    fun pairingDone() {
        LoggerTagged.i { "Pairing done" }
        updateState(PairState.Paired)
        runCatching {
            callback.pairingSuccessful()
        }.onFailure { e ->
            LoggerTagged.e(e) { "Exception in pairingSuccessful callback, unpairing" }
            updateState(PairState.NotPaired)
        }
    }

    suspend fun unpair() {
        updateState(PairState.NotPaired)
        if (device.isReachable) {
            val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR).update {
                put("pair", false)
            }
            device.sendPacket(np)
        }
        callback.unpaired(device)
    }

    private fun cancelTimer() {
        pairingJob.cancelChildren()
    }

    companion object {
        private const val ALLOWED_TIMESTAMP_DIFFERENCE_SECONDS = 1_800 // 30 minutes

        // Concatenate in a deterministic order so on both devices the result is the same
        private fun sortedConcat(a: ByteArray, b: ByteArray): ByteArray {
            return if (Arrays.compareUnsigned(a, b) < 0) {
                b + a
            } else {
                a + b
            }
        }

        private fun humanReadableHash(bytes: ByteArray): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            val formatter = Formatter()
            for (value in hash) {
                formatter.format("%02x", value)
            }
            return formatter.toString().substring(0, 8).uppercase()
        }
        fun getVerificationKey(certificateA: Certificate, certificateB: Certificate, timestamp: Long): String {
            val certsConcat = sortedConcat(certificateA.publicKey.encoded, certificateB.publicKey.encoded)
            return humanReadableHash(certsConcat + timestamp.toString().toByteArray())
        }

        fun getVerificationKeyV7(certificateA: Certificate, certificateB: Certificate): String {
            val certsConcat = sortedConcat(certificateA.publicKey.encoded, certificateB.publicKey.encoded)
            return humanReadableHash(certsConcat)
        }
    }

}