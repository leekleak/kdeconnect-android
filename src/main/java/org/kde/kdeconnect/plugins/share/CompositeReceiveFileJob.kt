/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.plugins.share

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.apache.commons.io.IOUtils
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.NetworkPacket
import org.kde.kdeconnect.async.BackgroundJob
import org.kde.kdeconnect.helpers.FilesHelper.findValidNonExistingFileName
import org.kde.kdeconnect.helpers.FilesHelper.getMimeTypeFromFile
import org.kde.kdeconnect.helpers.MediaStoreHelper.indexFile
import org.kde.kdeconnect_tp.R
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.milliseconds

/**
 * A type of [BackgroundJob] that reads Files from another device.
 * 
 * 
 * 
 * We receive the requests as [NetworkPacket]s.
 * 
 * 
 * 
 * Each packet should have a 'filename' property and a payload. If the payload is missing,
 * we'll just create an empty file. You can add new packets anytime via
 * [.addNetworkPacket].
 * 
 * 
 * 
 * The I/O-part of this file reading is handled by [.receiveFile].
 * 
 * 
 * @see CompositeUploadFileJob
 */
@OptIn(ExperimentalAtomicApi::class)
class CompositeReceiveFileJob(private val device: Device, private val context: Context, callBack: Callback<Void?>)
    : BackgroundJob<Device, Void?>(device, callBack) {
    private val receiveNotification: ReceiveNotification = ReceiveNotification(device, context, id)
    private var currentNetworkPacket: NetworkPacket? = null
    private var currentFileName: String? = null
    private var currentFileNum: Int = 0
    private var totalReceived: Long = 0
    private var lastProgressTimeMillis: Long = 0
    private var prevProgressPercentage: Long = 0


    private val networkPacketList: CopyOnWriteArrayList<NetworkPacket> = CopyOnWriteArrayList()

    private val totalNumFiles: AtomicInt = AtomicInt(0)

    private val totalPayloadSize: AtomicLong = AtomicLong(0)
    val isRunning: AtomicBoolean = AtomicBoolean(false)


    fun updateTotals(numberOfFiles: Int, payloadSize: Long) {
        totalNumFiles.store(numberOfFiles)
        totalPayloadSize.store(payloadSize)
        receiveNotification.setTitle(
            context.resources
                .getQuantityString(
                    R.plurals.incoming_file_title,
                    totalNumFiles.load(),
                    totalNumFiles.load(),
                    device.name
                )
        )
    }

    fun addNetworkPacket(networkPacket: NetworkPacket) {
        if (!networkPacketList.contains(networkPacket)) {
            networkPacketList.add(networkPacket)

            totalNumFiles.store(networkPacket.getInt(SharePlugin.KEY_NUMBER_OF_FILES, 1))
            totalPayloadSize.store(networkPacket.getLong(SharePlugin.KEY_TOTAL_PAYLOAD_SIZE))

                receiveNotification.setTitle(
                context.resources
                    .getQuantityString(
                        R.plurals.incoming_file_title,
                        totalNumFiles.load(),
                        totalNumFiles.load(),
                        device.name
                    )
            )
        }
    }

    override suspend fun run() {
        var done: Boolean = networkPacketList.isEmpty()
        var outputStream: OutputStream? = null

        try {
            var fileDocument: DocumentFile? = null

            isRunning.set(true)

            while (!done && !isCancelled) {
                val networkPacket = networkPacketList[0]
                currentNetworkPacket = networkPacket
                currentFileName = networkPacket.getString(
                    "filename",
                    System.currentTimeMillis().toString()
                )
                currentFileNum++

                setProgress(prevProgressPercentage.toInt())

                fileDocument = getDocumentFileFor(
                    currentFileName!!,
                    networkPacket.getBoolean("open", false)
                )

                if (networkPacket.hasPayload()) {
                    outputStream = BufferedOutputStream(
                        context.contentResolver.openOutputStream(fileDocument.uri)
                    )
                    val inputStream = networkPacket.payload?.inputStream ?: break

                    val received = receiveFile(inputStream, outputStream)

                    networkPacket.payload?.close()

                    withContext(Dispatchers.IO) { outputStream.close() }

                    outputStream = null

                    if (received != networkPacket.payloadSize) {
                        fileDocument.delete()

                        if (!isCancelled) {
                            throw RuntimeException("Failed to receive: " + currentFileName + " received:" + received + " bytes, expected: " + currentNetworkPacket!!.payloadSize + " bytes")
                        }
                    } else {
                        publishFile(fileDocument, received)
                    }
                } else {
                    //TODO: Only set progress to 100 if this is the only file/packet to send
                    setProgress(100)
                    publishFile(fileDocument, 0)
                }

                if (networkPacket.has("lastModified")) {
                    try {
                        val lastModified = networkPacket.getLong("lastModified")
                        withContext(Dispatchers.IO) {
                            Files.setLastModifiedTime(
                                Paths.get(fileDocument.uri.path),
                                FileTime.fromMillis(lastModified)
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("SharePlugin", "Can't set date on file")
                        e.printStackTrace()
                    }
                }

                networkPacketList.removeAt(0)
                val listIsEmpty: Boolean = networkPacketList.isEmpty()

                if (listIsEmpty && !isCancelled) {
                    delay(1000.milliseconds)

                    if (currentFileNum < totalNumFiles.load() && networkPacketList.isEmpty()) {
                        throw RuntimeException("Failed to receive " + (totalNumFiles.load() - currentFileNum + 1) + " files")
                    }
                }

                done = networkPacketList.isEmpty()
            }

            isRunning.set(false)

            if (isCancelled) {
                receiveNotification.cancel()
                return
            }

            val numFiles: Int = totalNumFiles.load()

            if (numFiles == 1 && currentNetworkPacket!!.getBoolean(
                    "open",
                    false
                ) && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            ) {
                receiveNotification.cancel()
                openFile(fileDocument!!)
            } else {
                //Update the notification and allow to open the file from it
                receiveNotification.setFinished(
                    context.resources.getQuantityString(
                        R.plurals.received_files_title, numFiles, device.name, numFiles
                    )
                )

                if (numFiles == 1 && fileDocument != null) {
                    receiveNotification.setURI(
                        fileDocument.uri,
                        fileDocument.type,
                        fileDocument.name
                    )
                }

                receiveNotification.show()
            }
            reportResult(null)
        } catch (_: ActivityNotFoundException) {
            receiveNotification.setFinished(context.getString(R.string.no_app_for_opening))
            receiveNotification.show()
        } catch (e: Exception) {
            isRunning.set(false)

            Log.e("Shareplugin", "Error receiving file", e)

            val failedFiles: Int = (totalNumFiles.load() - currentFileNum + 1)

            receiveNotification.setFailed(
                context.resources.getQuantityString(
                    R.plurals.received_files_fail_title,
                    failedFiles,
                    device.name,
                    failedFiles,
                    totalNumFiles.load()
                )
            )
            receiveNotification.show()
            reportError(e)
        } finally {
            closeAllInputStreams()
            networkPacketList.clear()
            try {
                IOUtils.close(outputStream)
            } catch (_: IOException) {
            }
        }
    }

    @Throws(RuntimeException::class)
    private fun getDocumentFileFor(filename: String, open: Boolean): DocumentFile {
        val destinationFolderDocument: DocumentFile

        // If the file should be opened immediately store it in the standard location to avoid the FileProvider trouble (See ReceiveNotification::setURI)
        if (open || !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("share_destination_custom", false)
        ) {
            val defaultPath =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath
            destinationFolderDocument = DocumentFile.fromFile(File(defaultPath))
        } else {
            destinationFolderDocument = getDestinationDirectory(context)
        }

        val filenameToUse = findValidNonExistingFileName(destinationFolderDocument, filename)

        val fileDocument: DocumentFile = destinationFolderDocument.createFile("*/*", filenameToUse) ?:
            throw RuntimeException(context.getString(R.string.cannot_create_file, filenameToUse))

        return fileDocument
    }

    private fun getDestinationDirectory(context: Context): DocumentFile {
        if (PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("share_destination_custom", false)
        ) {
            val path = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("share_destination_folder_uri", null)
            if (path != null) {
                val treeDocumentFile = DocumentFile.fromTreeUri(context, path.toUri())
                if (treeDocumentFile != null && treeDocumentFile.canWrite()) { //Checks for FLAG_DIR_SUPPORTS_CREATE on directories
                    return treeDocumentFile
                } else {
                    //Maybe permission was revoked
                    Log.w(
                        "SharePlugin",
                        "Share destination is not writable, falling back to default path."
                    )
                }
            }
        }
        val defaultDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        try {
            defaultDir.mkdirs()
        } catch (e: Exception) {
            Log.e("KDEConnect", "Exception", e)
        }
        return DocumentFile.fromFile(defaultDir)
    }

    @Throws(IOException::class)
    private fun receiveFile(input: InputStream, output: OutputStream): Long {
        val data = ByteArray(4096)
        var count: Int
        var received: Long = 0

        while ((input.read(data).also { count = it }) >= 0 && !isCancelled) {
            received += count.toLong()
            totalReceived += count.toLong()

            output.write(data, 0, count)

            val progressPercentage: Long = (totalReceived * 100 / totalPayloadSize.load())
            val curTimeMillis = System.currentTimeMillis()

            if (progressPercentage != prevProgressPercentage &&
                (progressPercentage == 100L || curTimeMillis - lastProgressTimeMillis >= 500)
            ) {
                prevProgressPercentage = progressPercentage
                lastProgressTimeMillis = curTimeMillis
                setProgress(progressPercentage.toInt())
            }
        }

        output.flush()

        return received
    }

    private fun closeAllInputStreams() {
        for (np in networkPacketList) {
            np.payload!!.close()
        }
    }

    private fun setProgress(progress: Int) {
        receiveNotification.setProgress(
            progress, context.resources
                .getQuantityString(
                    R.plurals.incoming_files_text,
                    totalNumFiles.load(),
                    currentFileName,
                    currentFileNum,
                    totalNumFiles.load()
                )
        )
        receiveNotification.show()
    }

    private fun publishFile(fileDocument: DocumentFile, size: Long) {
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("share_destination_custom", false)
        ) {
            Log.i("SharePlugin", "Adding to downloads")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues()
                contentValues.put(MediaStore.Downloads.TITLE, fileDocument.uri.lastPathSegment)
                contentValues.put(MediaStore.Downloads.DISPLAY_NAME, fileDocument.uri.lastPathSegment)
                contentValues.put(MediaStore.Downloads.MIME_TYPE, fileDocument.type)
                contentValues.put(MediaStore.Downloads.SIZE, size)

                contentValues.put(MediaStore.Downloads.RELATIVE_PATH, fileDocument.uri.path)

                val database = context.contentResolver
                database.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val manager = ContextCompat.getSystemService(context, DownloadManager::class.java)
                manager?.addCompletedDownload(
                    fileDocument.uri.lastPathSegment,
                    device.name,
                    true,
                    fileDocument.type,
                    fileDocument.uri.path,
                    size,
                    false
                )
            }
        } else {
            //Make sure it is added to the Android Gallery anyway
            Log.i("SharePlugin", "Adding to gallery")
            indexFile(context, fileDocument.uri)
        }
    }

    private fun openFile(fileDocument: DocumentFile) {
        val mimeType = getMimeTypeFromFile(fileDocument.name)
        val intent = Intent(Intent.ACTION_VIEW)

        val file = File(fileDocument.uri.path ?: return)
        val contentUri = FileProvider.getUriForFile(
            context,
            "org.kde.kdeconnect_tp.fileprovider",
            file
        )
        intent.setDataAndType(contentUri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        // Open files for KDE Itinerary explicitly because Android's activity resolution sucks
        if (fileDocument.name!!.endsWith(".itinerary")) {
            intent.setClassName("org.kde.itinerary", "org.kde.itinerary.Activity")
        }

        context.startActivity(intent)
    }
}
