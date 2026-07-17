/*
 * SPDX-FileCopyrightText: 2019 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.BackgroundJob
import org.kde.kdeconnect_tp.R
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A type of [BackgroundJob] that sends Files to another device.
 * 
 * 
 * 
 * We represent the individual upload requests as [NetworkPacket]s.
 * 
 * 
 * 
 * Each packet should have a 'filename' property and a payload. If the payload is
 * missing, we'll just send an empty file. You can add new packets anytime via
 * [.addNetworkPacket].
 * 
 * 
 * 
 * The I/O-part of this file sending is handled by
 * [Device.sendPacketBlocking].
 * 
 * 
 * @see CompositeReceiveFileJob
 * 
 * @see SendPacketStatusCallback
 */
@OptIn(ExperimentalAtomicApi::class)
class CompositeUploadFileJob(private val device: Device, private val context: Context, callback: Callback<Void?>) :
    BackgroundJob<Device, Void?>(device, callback) {
    private val isRunning: AtomicBoolean = AtomicBoolean(false)
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var currentFileName: String? = ""
    private var currentFileNum = 0
    private val updatePacketPending: AtomicBoolean = AtomicBoolean(false)
    private var totalSend: Long = 0
    private var prevProgressPercentage = 0
    private val uploadNotification: UploadNotification = UploadNotification(device, context, id)

    private val networkPacketList: CopyOnWriteArrayList<NetworkPacket> = CopyOnWriteArrayList()
    private var currentNetworkPacket: NetworkPacket? = null
    private val sendPacketStatusCallback: Device.SendPacketStatusCallback by lazy { SendPacketStatusCallback() }

    private val totalNumFiles: AtomicInt = AtomicInt(0)
    private val totalPayloadSize: AtomicLong = AtomicLong(0)

    override suspend fun run() {
        var done: Boolean

        isRunning.store(true)

        done = networkPacketList.isEmpty()

        try {
            while (!done && !isCancelled) {
                currentNetworkPacket = networkPacketList.removeAt(0)
                val packet = currentNetworkPacket ?: continue

                currentFileName = packet.getString("filename")
                currentFileNum++

                setProgress(prevProgressPercentage)

                addTotalsToNetworkPacket(packet)

                // We set sendPayloadFromSameThread to true so this call blocks until the payload
                // has been received by the other end, so payloads are sent one by one.
                if (!device.sendPacketBlocking(
                        packet,
                        sendPacketStatusCallback,
                        true
                    )
                ) {
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

                reportResult(null)
            }
        } catch (e: RuntimeException) {
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

    private fun addTotalsToNetworkPacket(networkPacket: NetworkPacket) {
        networkPacket[SharePlugin.KEY_NUMBER_OF_FILES] = totalNumFiles.load()
        networkPacket[SharePlugin.KEY_TOTAL_PAYLOAD_SIZE] = totalPayloadSize.load()
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
                handler.post { sendUpdatePacket() }
            }
    }

    /**
     * Use this to send metadata ahead of all the other [packets][.networkPacketList].
     */
    private fun sendUpdatePacket() {
        val np = NetworkPacket(SharePlugin.PACKET_TYPE_SHARE_REQUEST_UPDATE)

        np["numberOfFiles"] = totalNumFiles.load()
        np["totalPayloadSize"] = totalPayloadSize.load()
        updatePacketPending.store(false)

        device.sendPacket(np)
    }

    override fun cancel() {
        super.cancel()

        currentNetworkPacket?.cancel()
    }

    inner class SendPacketStatusCallback : Device.SendPacketStatusCallback() {
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
}
