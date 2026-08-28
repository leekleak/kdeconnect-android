/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.app.Notification
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import org.kde.kdeconnect.device.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.R
import org.kde.kdeconnect.async.DataTransferJob
import org.kde.kdeconnect.async.JobCallback
import org.kde.kdeconnect.device.SendPacketStatusCallback
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A type of [DataTransferJob] that sends Files to another device.
 *
 * We represent the individual upload requests as [NetworkPacket]s.
 *
 * Each packet should have a 'filename' property and a payload. If the payload is
 * missing, we'll just send an empty file. You can add new packets anytime via
 * [.addNetworkPacket].
 *
 * The I/O-part of this file sending is handled by
 * [Device.sendPacket].
 *
 * @see SendPacketStatusCallback
 */
@OptIn(ExperimentalAtomicApi::class)
class CompositeUploadFileJob(
    override val id: Int,
    private val device: Device,
    private val context: Context,
    private val callback: JobCallback
) : DataTransferJob {
    private val isRunning: AtomicBoolean = AtomicBoolean(false)
    private var currentFileName: String? = ""
    private var currentFileNum = 0
    private val updatePacketPending: AtomicBoolean = AtomicBoolean(false)
    private var totalSend: Long = 0
    private var prevProgressPercentage = 0
    private val uploadNotification: UploadNotification = UploadNotification(device, context, id)

    private val networkPacketList: CopyOnWriteArrayList<NetworkPacket> = CopyOnWriteArrayList()
    private var currentNetworkPacket: NetworkPacket? = null
    private val sendPacketStatusCallback = object : SendPacketStatusCallback {
        override fun onPayloadProgressChanged(percent: Int) {
            val packet = currentNetworkPacket ?: return
            val send = totalSend + (packet.payloadSize * (percent.toFloat() / 100))
            val progress = ((send * 100) / totalPayloadSize.load()).toInt()

            if (progress != prevProgressPercentage) {
                setProgress(progress)
                prevProgressPercentage = progress
            }
        }

        override fun onSuccess() {
            val packet = currentNetworkPacket ?: return
            if (packet.payloadSize == 0L) {
                if (networkPacketList.isEmpty()) {
                    setProgress(100)
                }
            }

            totalSend += packet.payloadSize
        }

        override fun onFailure(e: Throwable) {
            // Handled in the run() function when sendPacketBlocking returns false
        }
    }

    private val totalNumFiles: AtomicInt = AtomicInt(0)
    private val totalPayloadSize: AtomicLong = AtomicLong(0)

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    var isCancelled: Boolean = false
        private set

    override fun getNotification(): Notification {
        return uploadNotification.getNotification()
    }

    override fun getNotificationId(): Int {
        return uploadNotification.getNotificationId()
    }

    override suspend fun run() {
        var done: Boolean

        isRunning.store(true)

        done = networkPacketList.isEmpty()

        try {
            while (!done && !isCancelled) {
                currentNetworkPacket = networkPacketList.removeAt(0).update {
                    put(SharePlugin.KEY_NUMBER_OF_FILES, totalNumFiles.load())
                    put(SharePlugin.KEY_TOTAL_PAYLOAD_SIZE, totalPayloadSize.load())
                }
                val packet = currentNetworkPacket ?: continue

                currentFileName = packet.getString("filename")
                currentFileNum++

                setProgress(prevProgressPercentage)

                // We set sendPayloadFromSameThread to true so this call blocks until the payload
                // has been received by the other end, so payloads are sent one by one.
                if (!device.sendPacket(packet, sendPacketStatusCallback)) {
                    throw RuntimeException("Sending packet failed")
                }

                done = networkPacketList.isEmpty()
            }

            if (isCancelled) {
                uploadNotification.cancel()
            } else {
                uploadNotification.setFinished(
                    context.resources.getQuantityString(
                        R.plurals.sent_files_title, currentFileNum, device.name, currentFileNum
                    )
                )
                uploadNotification.show()

                reportResult()
            }
        } catch (e: Exception) {
            val failedFiles: Int = (totalNumFiles.load() - currentFileNum + 1)
            uploadNotification.setFailed(
                context.resources
                    .getQuantityString(
                        R.plurals.send_files_fail_title, failedFiles, device.name,
                        failedFiles, totalNumFiles.load()
                    )
            )

            uploadNotification.show()
            reportError(e)
        } finally {
            isRunning.store(false)

            for (networkPacket in networkPacketList) {
                networkPacket.payload?.close()
            }
            networkPacketList.clear()
        }
    }

    private fun reportResult() {
        callback.onResult(id)
    }

    private fun reportError(error: Throwable) {
        callback.onError(id, error)
    }

    private fun setProgress(progress: Int) {
        uploadNotification.setProgress(
            progress, context.resources
                .getQuantityString(
                    R.plurals.outgoing_files_text,
                    totalNumFiles.load(),
                    currentFileName,
                    currentFileNum,
                    totalNumFiles.load()
                )
        )
        uploadNotification.show()
    }

    fun addNetworkPacket(networkPacket: NetworkPacket) {
        networkPacketList.add(networkPacket)
        totalNumFiles.fetchAndAdd(1)

        if (networkPacket.payloadSize >= 0) {
            totalPayloadSize.fetchAndAdd(networkPacket.payloadSize)
        }

        uploadNotification.setTitle(
            context.resources
                .getQuantityString(
                    R.plurals.outgoing_file_title,
                    totalNumFiles.load(),
                    totalNumFiles.load(),
                    device.name
                )
        )

        //Give SharePlugin some time to add more NetworkPackets
        if (isRunning.load() && !updatePacketPending.load()) {
            updatePacketPending.store(true)
            coroutineScope.launch { sendUpdatePacket() }
        }
    }

    /**
     * Use this to send metadata ahead of all the other [packets][.networkPacketList].
     */
    private suspend fun sendUpdatePacket() {
        val np = NetworkPacket(SharePlugin.PACKET_TYPE_SHARE_REQUEST_UPDATE).update {
            put("numberOfFiles", totalNumFiles.load())
            put("totalPayloadSize", totalPayloadSize.load())
        }
        updatePacketPending.store(false)

        device.sendPacket(np)
    }

    override fun cancel() {
        isCancelled = true
        coroutineScope.cancel()

        currentNetworkPacket?.cancel()
    }
}
