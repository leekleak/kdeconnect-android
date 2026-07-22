/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect

import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.bouncycastle.util.Arrays
import org.kde.kdeconnect.helpers.security.SslHelper
import org.kde.kdeconnect_tp.R
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.Formatter
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class PairingHandler(
    private val device: Device,
    private val callback: PairingCallback,
    startState: PairState,
) {
    private val _state: MutableStateFlow<PairState> = MutableStateFlow(startState)
    val state: StateFlow<PairState> = _state.asStateFlow()

    fun updateState(newState: PairState) {
        _state.value = newState
    }

    enum class PairState {
        NotPaired,
        Requested,
        RequestedByPeer,
        Paired
    }

    interface PairingCallback {
        fun incomingPairRequest()

        fun pairingFailed(error: Int)

        fun pairingSuccessful()

        fun unpaired(device: Device)
    }

    private val pairingJob = SupervisorJob()
    private val pairingScope = CoroutineScope(Dispatchers.IO + pairingJob)
    private var pairingTimestamp = 0L

    fun packetReceived(np: NetworkPacket) {
        cancelTimer()
        val wantsPair = np.getBoolean("pair")
        if (wantsPair) {
            when (state.value) {
                PairState.Requested -> pairingDone()
                PairState.RequestedByPeer -> {
                    Log.w(
                        "PairingHandler",
                        "Ignoring second pairing request before the first one timed out"
                    )
                }

                PairState.Paired, PairState.NotPaired -> {
                    if (state.value == PairState.Paired) {
                        Log.w("PairingHandler", "Received pairing request from a device we already trusted.")
                        // It would be nice to auto-accept the pairing request here, but since the pairing accept and pairing request
                        // messages are identical, this could create an infinite loop if both devices are "accepting" each other pairs.
                        // Instead, unpair and handle as if "NotPaired". TODO: No longer true in protocol version 8
                        updateState(PairState.NotPaired)
                        callback.unpaired(device)
                    }

                    if (device.protocolVersion >= 8) {
                        pairingTimestamp = np.getLong("timestamp", -1L)
                        if (pairingTimestamp == -1L) {
                            updateState(PairState.NotPaired)
                            callback.unpaired(device)
                            return
                        }
                        val currentTimestamp = System.currentTimeMillis() / 1000L
                        if (abs(pairingTimestamp - currentTimestamp) > ALLOWED_TIMESTAMP_DIFFERENCE_SECONDS) {
                            updateState(PairState.NotPaired)
                            callback.pairingFailed(R.string.error_clocks_not_match)
                            return
                        }
                    }

                    updateState(PairState.RequestedByPeer)

                    pairingScope.launch {
                        delay(25.seconds)
                        Log.w("PairingHandler", "Unpairing (timeout after we started pairing)")
                        updateState(PairState.NotPaired)
                        callback.pairingFailed(R.string.error_timed_out)
                    } // Time to show notification, waiting for user to accept (peer will timeout in 30 seconds)

                    callback.incomingPairRequest()
                }
            }
        } else {
            Log.i("PairingHandler", "Unpair request received")
            when (state.value) {
                PairState.NotPaired -> Log.i("PairingHandler", "Ignoring unpair request for already unpaired device")
                // Requested: We started pairing and got rejected
                // RequestedByPeer: They stared pairing, then cancelled
                PairState.Requested, PairState.RequestedByPeer -> {
                    updateState(PairState.NotPaired)
                    callback.pairingFailed(R.string.error_canceled_by_other_peer)
                }

                PairState.Paired -> {
                    updateState(PairState.NotPaired)
                    callback.unpaired(device)
                }
            }
        }
    }

    val verificationKey: Flow<String?> = state.map {
        if (device.protocolVersion >= 8) {
            if (state.value != PairState.Requested && state.value != PairState.RequestedByPeer) {
                null
            } else {
                getVerificationKey(SslHelper.certificate, device.certificate, pairingTimestamp)
            }
        } else {
            getVerificationKeyV7(SslHelper.certificate, device.certificate)
        }
    }

    fun requestPairing() {
        cancelTimer()

        if (state.value == PairState.Paired) {
            Log.w("PairingHandler", "requestPairing was called on an already paired device")
            callback.pairingFailed(R.string.error_already_paired)
            return
        }

        if (state.value == PairState.RequestedByPeer) {
            Log.w("PairingHandler", "Pairing already started by the other end, accepting their request.")
            acceptPairing()
            return
        }

        if (!device.isReachable) {
            callback.pairingFailed(R.string.error_not_reachable)
            return
        }

        updateState(PairState.Requested)

        pairingScope.launch {
            delay(30.seconds)
            Log.w("PairingHandler", "Unpairing (timeout after receiving pair request)")
            updateState(PairState.NotPaired)
            callback.pairingFailed(R.string.error_timed_out)
        } // Time to wait for the other to accept

        val statusCallback: Device.SendPacketStatusCallback = object : Device.SendPacketStatusCallback() {
            override fun onSuccess() {}

            override fun onFailure(e: Throwable) {
                cancelTimer()
                Log.e("PairingHandler", "Exception sending pairing request", e)
                updateState(PairState.NotPaired)
                callback.pairingFailed(R.string.runcommand_notreachable)
            }
        }
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
        np["pair"] = true
        pairingTimestamp = System.currentTimeMillis() / 1000L
        np["timestamp"] = pairingTimestamp
        device.sendPacket(np, statusCallback)
    }

    fun acceptPairing() {
        cancelTimer()
        val stateCallback = object : Device.SendPacketStatusCallback() {
            override fun onSuccess() {
                pairingDone()
            }

            override fun onFailure(e: Throwable) {
                Log.e("PairingHandler", "Exception sending accept pairing packet", e)
                updateState(PairState.NotPaired)
                callback.pairingFailed(R.string.error_not_reachable)
            }
        }
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
        np["pair"] = true
        device.sendPacket(np, stateCallback)
    }

    fun cancelPairing() {
        cancelTimer()
        updateState(PairState.NotPaired)
        val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
        np["pair"] = false
        device.sendPacket(np)
        callback.pairingFailed(R.string.error_canceled_by_user)
    }

    @VisibleForTesting
    fun pairingDone() {
        Log.i("PairingHandler", "Pairing done")
        updateState(PairState.Paired)
        kotlin.runCatching {
            callback.pairingSuccessful()
        }.onFailure { e ->
            Log.e("PairingHandler", "Exception in pairingSuccessful callback, unpairing", e)
            updateState(PairState.NotPaired)
        }
    }

    fun unpair() {
        updateState(PairState.NotPaired)
        if (device.isReachable) {
            val np = NetworkPacket(NetworkPacket.PACKET_TYPE_PAIR)
            np["pair"] = false
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
