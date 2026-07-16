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
import androidx.annotation.GuardedBy
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
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
class CompositeReceiveFileJob(private val device: Device, callBack: Callback<Void?>) : BackgroundJob<Device, Void?>(device, callBack) {
    private val receiveNotification: ReceiveNotification = ReceiveNotification(device, id)
    private var currentNetworkPacket: NetworkPacket? = null
    private var currentFileName: String? = null
    private var currentFileNum: Int = 0
    private var totalReceived: Long
    private var lastProgressTimeMillis: Long
    private var prevProgressPercentage: Long

    private val lock: Any = Any() //Use to protect concurrent access to the variables below

    @GuardedBy("lock")
    private val networkPacketList: MutableList<NetworkPacket> = ArrayList()

    @GuardedBy("lock")
    private var totalNumFiles: Int

    @GuardedBy("lock")
    private var totalPayloadSize: Long
    var isRunning: Boolean = false
        private set

    init {
        totalNumFiles = 0
        totalPayloadSize = 0
        totalReceived = 0
        lastProgressTimeMillis = 0
        prevProgressPercentage = 0
    }

    fun updateTotals(numberOfFiles: Int, totalPayloadSize: Long) {
        synchronized(lock) {
            this.totalNumFiles = numberOfFiles
            this.totalPayloadSize = totalPayloadSize
            receiveNotification.setTitle(
                this.device.context.resources
                    .getQuantityString(
                        R.plurals.incoming_file_title,
                        totalNumFiles,
                        totalNumFiles,
                        this.device.name
                    )
            )
        }
    }

    fun addNetworkPacket(networkPacket: NetworkPacket) {
        synchronized(lock) {
            if (!networkPacketList.contains(networkPacket)) {
                networkPacketList.add(networkPacket)

                totalNumFiles = networkPacket.getInt(SharePlugin.KEY_NUMBER_OF_FILES, 1)
                totalPayloadSize = networkPacket.getLong(SharePlugin.KEY_TOTAL_PAYLOAD_SIZE)

                receiveNotification.setTitle(
                    this.device.context.resources
                        .getQuantityString(
                            R.plurals.incoming_file_title,
                            totalNumFiles,
                            totalNumFiles,
                            this.device.name
                        )
                )
            }
        }
    }

    override fun run() {
        var done: Boolean
        var outputStream: OutputStream? = null

        synchronized(lock) {
            done = networkPacketList.isEmpty()
        }

        try {
            var fileDocument: DocumentFile? = null

            isRunning = true

            while (!done && !isCancelled) {
                synchronized(lock) {
                    currentNetworkPacket = networkPacketList[0]
                }
                currentFileName = currentNetworkPacket!!.getString(
                    "filename",
                    System.currentTimeMillis().toString()
                )
                currentFileNum++

                setProgress(prevProgressPercentage.toInt())

                fileDocument = getDocumentFileFor(
                    currentFileName!!,
                    currentNetworkPacket!!.getBoolean("open", false)
                )

                if (currentNetworkPacket!!.hasPayload()) {
                    outputStream = BufferedOutputStream(
                        this.device.context.contentResolver
                            .openOutputStream(fileDocument.uri)
                    )
                    val inputStream = currentNetworkPacket!!.payload!!.inputStream

                    val received = receiveFile(inputStream!!, outputStream)

                    currentNetworkPacket!!.payload!!.close()

                    try {
                        outputStream.close()
                    } catch (_: IOException) {
                    }
                    outputStream = null

                    if (received != currentNetworkPacket!!.payloadSize) {
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

                if (currentNetworkPacket!!.has("lastModified")) {
                    try {
                        val lastModified = currentNetworkPacket!!.getLong("lastModified")
                        Files.setLastModifiedTime(
                            Paths.get(fileDocument.uri.path),
                            FileTime.fromMillis(lastModified)
                        )
                    } catch (e: Exception) {
                        Log.e("SharePlugin", "Can't set date on file")
                        e.printStackTrace()
                    }
                }

                val listIsEmpty: Boolean

                synchronized(lock) {
                    networkPacketList.removeAt(0)
                    listIsEmpty = networkPacketList.isEmpty()
                }

                if (listIsEmpty && !isCancelled) {
                    try {
                        Thread.sleep(1000)
                    } catch (_: InterruptedException) {
                    }

                    synchronized(lock) {
                        if (currentFileNum < totalNumFiles && networkPacketList.isEmpty()) {
                            throw RuntimeException("Failed to receive " + (totalNumFiles - currentFileNum + 1) + " files")
                        }
                    }
                }

                synchronized(lock) {
                    done = networkPacketList.isEmpty()
                }
            }

            isRunning = false

            if (isCancelled) {
                receiveNotification.cancel()
                return
            }

            val numFiles: Int
            synchronized(lock) {
                numFiles = totalNumFiles
            }

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
                    this.device.context.resources.getQuantityString(
                        R.plurals.received_files_title, numFiles, this.device.name, numFiles
                    )
                )

                if (totalNumFiles == 1 && fileDocument != null) {
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
            receiveNotification.setFinished(this.device.context.getString(R.string.no_app_for_opening))
            receiveNotification.show()
        } catch (e: Exception) {
            isRunning = false

            Log.e("Shareplugin", "Error receiving file", e)

            val failedFiles: Int
            synchronized(lock) {
                failedFiles = (totalNumFiles - currentFileNum + 1)
            }

            receiveNotification.setFailed(
                this.device.context.resources.getQuantityString(
                    R.plurals.received_files_fail_title,
                    failedFiles,
                    this.device.name,
                    failedFiles,
                    totalNumFiles
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
        if (open || !PreferenceManager.getDefaultSharedPreferences(this.device.context)
                .getBoolean("share_destination_custom", false)
        ) {
            val defaultPath =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath
            destinationFolderDocument = DocumentFile.fromFile(File(defaultPath))
        } else {
            destinationFolderDocument = getDestinationDirectory(this.device.context)
        }

        val filenameToUse = findValidNonExistingFileName(destinationFolderDocument, filename)

        val fileDocument: DocumentFile = destinationFolderDocument.createFile("*/*", filenameToUse) ?:
            throw RuntimeException(device.context.getString(R.string.cannot_create_file, filenameToUse))

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

            val progressPercentage: Long
            synchronized(lock) {
                progressPercentage = (totalReceived * 100 / totalPayloadSize)
            }
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
        synchronized(lock) {
            receiveNotification.setProgress(
                progress, this.device.context.resources
                    .getQuantityString(
                        R.plurals.incoming_files_text,
                        totalNumFiles,
                        currentFileName,
                        currentFileNum,
                        totalNumFiles
                    )
            )
        }
        receiveNotification.show()
    }

    private fun publishFile(fileDocument: DocumentFile, size: Long) {
        if (!PreferenceManager.getDefaultSharedPreferences(this.device.context)
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

                val database = this.device.context.contentResolver
                database.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                val manager = ContextCompat.getSystemService<DownloadManager?>(
                    this.device.context,
                    DownloadManager::class.java
                )
                manager?.addCompletedDownload(
                    fileDocument.uri.lastPathSegment,
                    this.device.name,
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
            indexFile(this.device.context, fileDocument.uri)
        }
    }

    private fun openFile(fileDocument: DocumentFile) {
        val mimeType = getMimeTypeFromFile(fileDocument.name)
        val intent = Intent(Intent.ACTION_VIEW)

        val file = File(fileDocument.uri.path ?: return)
        val contentUri = FileProvider.getUriForFile(
            this.device.context,
            "org.kde.kdeconnect_tp.fileprovider",
            file
        )
        intent.setDataAndType(contentUri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

        // Open files for KDE Itinerary explicitly because Android's activity resolution sucks
        if (fileDocument.name!!.endsWith(".itinerary")) {
            intent.setClassName("org.kde.itinerary", "org.kde.itinerary.Activity")
        }

        this.device.context.startActivity(intent)
    }
}
