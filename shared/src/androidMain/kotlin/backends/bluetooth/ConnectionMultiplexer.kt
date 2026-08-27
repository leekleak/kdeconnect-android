/*
 * SPDX-FileCopyrightText: 2019 Matthijs Tijink <matthijstijink@gmail.com>
 * SPDX-FileCopyrightText: 2024 Rob Emery <git@mintsoft.net>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.backends.bluetooth

import android.bluetooth.BluetoothSocket
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.Sink
import okio.Source
import okio.Timeout
import okio.buffer
import okio.sink
import okio.source
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.helpers.ThreadHelper.execute
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ConnectionMultiplexer(private val socket: BluetoothSocket) : Closeable {
    private class ChannelSource(val channel: Channel) : Source, Closeable {
        override fun read(sink: Buffer, byteCount: Long): Long {
            val b = ByteArray(byteCount.toInt())
            val remaining = channel.read(b, 0, byteCount.toInt())
            sink.write(b)
            return remaining.toLong()
        }

        override fun timeout(): Timeout {
            return Timeout.NONE
        }

        @Throws(IOException::class)
        override fun close() {
            channel.close()
        }
    }

    private class ChannelSink(val channel: Channel) : Sink, Closeable {
        @Throws(IOException::class)
        override fun close() {
            channel.close()
        }

        override fun write(source: Buffer, byteCount: Long) {
            channel.write(source.readByteArray(), 0, byteCount.toInt())
        }

        @Throws(IOException::class)
        override fun flush() {
            channel.flush()
        }

        override fun timeout(): Timeout = Timeout.NONE
    }

    private class Channel(val multiplexer: ConnectionMultiplexer, val id: UUID) : Closeable {
        val readBuffer: ByteBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        val lock = ReentrantLock()
        var lockCondition: Condition = lock.newCondition()

        var open = true
        var requestedReadAmount = 0 //Number of times we requested some bytes from the channel
        var freeWriteAmount = 0 //Number of times we can safely send bytes over the channel

        fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (true) {
                var makeRequest: Boolean
                lock.withLock {
                    if (readBuffer.position() >= len) {
                        readBuffer.flip()
                        readBuffer[b, off, len]
                        readBuffer.compact()

                        //TODO: non-blocking (opportunistic) read request
                        return len
                    } else if (readBuffer.position() > 0) {
                        val numberRead = readBuffer.position()
                        readBuffer.flip()
                        readBuffer[b, off, numberRead]
                        readBuffer.compact()

                        //TODO: non-blocking (opportunistic) read request
                        return numberRead
                    }
                    if (!open) return -1
                    makeRequest = requestedReadAmount < BUFFER_SIZE
                }
                if (makeRequest) {
                    multiplexer.readRequest(id)
                }
                lock.withLock {
                    if (!open) return -1
                    if (readBuffer.position() <= 0) {
                        try {
                            lockCondition.await()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        @Throws(IOException::class)
        override fun close() {
            flush()
            lock.withLock {
                if (!open) return
                open = false
                readBuffer.clear()
                lockCondition.signalAll()
            }
            multiplexer.closeChannel(id)
        }

        fun doClose() {
            lock.withLock {
                open = false
                lockCondition.signalAll()
            }
        }

        @Throws(IOException::class)
        fun write(data: ByteArray, off: Int, len: Int) {
            var offset = off
            var length = len
            while (length > 0) {
                lock.withLock {
                    while (true) {
                        if (!open) throw IOException("Connection closed!")
                        if (freeWriteAmount == 0) {
                            try {
                                lockCondition.await()
                            } catch (_: Exception) {
                            }
                        } else {
                            break
                        }
                    }
                }
                val numWritten = multiplexer.writeRequest(id, data, offset, length)
                offset += numWritten
                length -= numWritten
            }
        }

        @Throws(IOException::class)
        fun flush() {
            multiplexer.flush()
        }
    }

    private val channels: MutableMap<UUID, Channel> = HashMap()
    private val channelsLock = ReentrantLock()
    private var open = true
    private var receivedProtocolVersion = false

    private val underlyingSource: BufferedSource = socket.inputStream.source().buffer()
    private val underlyingSink: BufferedSink = socket.outputStream.sink().buffer()

    init {
        channels[DEFAULT_CHANNEL] = Channel(this, DEFAULT_CHANNEL)
        sendProtocolVersion()
        execute(ListenRunnable())
    }

    @Throws(IOException::class)
    private fun sendProtocolVersion() {
        val data = ByteArray(23)
        val message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        message.put(MESSAGE_PROTOCOL_VERSION)
        message.putShort(4.toShort())
        message.position(19)
        message.putShort(1.toShort())
        message.putShort(1.toShort())
        underlyingSink.write(data)
        underlyingSink.flush()
    }

    private fun handleException(e: Exception) {
        LoggerTagged.e(e) { "Handling exception" }
        channelsLock.withLock {
            open = false
            for (channel in channels.values) {
                channel.doClose()
            }
            channels.clear()
            if (socket.isConnected) {
                try {
                    socket.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun closeChannel(id: UUID) {
        channelsLock.withLock {
            if (channels.containsKey(id)) {
                channels.remove(id)
                val data = ByteArray(19)
                val message = ByteBuffer.wrap(data)
                message.order(ByteOrder.BIG_ENDIAN)
                message.put(MESSAGE_CLOSE_CHANNEL)
                message.putShort(0.toShort())
                message.putLong(id.mostSignificantBits)
                message.putLong(id.leastSignificantBits)
                try {
                    underlyingSink.write(data)
                    underlyingSink.flush()
                } catch (e: IOException) {
                    handleException(e)
                }
            }
        }
    }

    private fun readRequest(id: UUID) {
        channelsLock.withLock {
            val channel = channels[id] ?: return
            val data = ByteArray(21)
            channel.lock.withLock {
                if (!channel.open) return
                if (channel.readBuffer.position() + channel.requestedReadAmount >= BUFFER_SIZE) return
                val amount = BUFFER_SIZE - channel.readBuffer.position() - channel.requestedReadAmount
                val message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                message.put(MESSAGE_READ)
                message.putShort(2.toShort())
                message.putLong(id.mostSignificantBits)
                message.putLong(id.leastSignificantBits)
                message.putShort(amount.toShort())
                channel.requestedReadAmount += amount
                try {
                    underlyingSink.write(data)
                    underlyingSink.flush()
                } catch (e: IOException) {
                    handleException(e)
                } catch (e: NullPointerException) {
                    handleException(e)
                }
                channel.lockCondition.signalAll()
            }
        }
    }

    @Throws(IOException::class)
    private fun writeRequest(id: UUID, writeData: ByteArray, off: Int, writeLen: Int): Int {
        channelsLock.withLock {
            val channel = channels[id] ?: return 0
            val data = ByteArray(19 + BUFFER_SIZE)
            var length: Int
            channel.lock.withLock {
                if (!channel.open) return 0
                if (channel.freeWriteAmount == 0) return 0
                length = channel.freeWriteAmount
                if (writeLen < length) {
                    length = writeLen
                }
                val message = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                message.put(MESSAGE_WRITE)
                //Convert length to signed short
                val lengthShort: Short = if (length >= 0x10000) {
                    throw IOException("Invalid buffer size, too large!")
                } else if (length >= 0x8000) {
                    (-0x10000 + length).toShort()
                } else {
                    length.toShort()
                }
                message.putShort(lengthShort)
                message.putLong(id.mostSignificantBits)
                message.putLong(id.leastSignificantBits)
                message.put(writeData, off, length)
                channel.freeWriteAmount -= length
                channel.lockCondition.signalAll()
            }
            try {
                underlyingSink.write(data, 0, 19 + length)
                underlyingSink.flush()
            } catch (e: IOException) {
                handleException(e)
            }
            return length
        }
    }

    @Throws(IOException::class)
    private fun flush() {
        channelsLock.withLock {
            if (!open) return
            underlyingSink.flush()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        channelsLock.withLock {
            socket.close()
            for (channel in channels.values) {
                channel.doClose()
            }
            channels.clear()
        }
    }

    @Throws(IOException::class)
    fun newChannel(): UUID {
        val id = UUID.randomUUID()
        channelsLock.withLock {
            val data = ByteArray(19)
            val message = ByteBuffer.wrap(data)
            message.order(ByteOrder.BIG_ENDIAN)
            message.put(MESSAGE_OPEN_CHANNEL)
            message.putShort(0.toShort())
            message.putLong(id.mostSignificantBits)
            message.putLong(id.leastSignificantBits)
            try {
                underlyingSink.write(data)
                underlyingSink.flush()
            } catch (e: IOException) {
                handleException(e)
                throw e
            }
            channels.put(id, Channel(this, id))
        }
        return id
    }

    @get:Throws(IOException::class)
    val defaultSource: Source
        get() = getChannelSource(DEFAULT_CHANNEL)

    @get:Throws(IOException::class)
    val defaultSink: Sink
        get() = getChannelSink(DEFAULT_CHANNEL)

    @Throws(IOException::class)
    fun getChannelSource(id: UUID): Source {
        channelsLock.withLock {
            val channel = channels[id] ?: throw IOException("Invalid channel!")
            return ChannelSource(channel)
        }
    }

    @Throws(IOException::class)
    fun getChannelSink(id: UUID): Sink {
        channelsLock.withLock {
            val channel = channels[id] ?: throw IOException("Invalid channel!")
            return ChannelSink(channel)
        }
    }

    private inner class ListenRunnable : Runnable {

        @Throws(IOException::class)
        private fun readMessage() {
            val headerData = underlyingSource.readByteArray(19)
            val message = ByteBuffer.wrap(headerData).order(ByteOrder.BIG_ENDIAN)
            val type = message.get()
            var length = message.short.toInt()
            //signed short -> unsigned short (as int) conversion
            if (length < 0) length += 0x10000
            val channelIdMostSigBits = message.long
            val channelIdLeastSigBits = message.long
            val channelId = UUID(channelIdMostSigBits, channelIdLeastSigBits)
            if (!receivedProtocolVersion && type != MESSAGE_PROTOCOL_VERSION) {
                LoggerTagged.w { "Received invalid message type $type" }

                throw IOException("Did not receive protocol version message!")
            }
            when (type) {
                MESSAGE_OPEN_CHANNEL -> {
                    channelsLock.withLock {
                        channels.put(channelId, Channel(this@ConnectionMultiplexer, channelId))
                    }
                }
                MESSAGE_CLOSE_CHANNEL -> {
                    channelsLock.withLock {
                        val channel = channels[channelId] ?: return
                        channels.remove(channelId)
                        channel.doClose()
                    }
                }
                MESSAGE_READ -> {
                    if (length != 2) {
                        throw IOException("Message length is invalid for 'MESSAGE_READ'!")
                    }
                    val amountData = underlyingSource.readByteArray(2)
                    var amount = ByteBuffer.wrap(amountData).order(ByteOrder.BIG_ENDIAN).short.toInt()
                    //signed short -> unsigned short (as int) conversion
                    if (amount < 0) amount += 0x10000
                    channelsLock.withLock {
                        val channel = channels[channelId] ?: return
                        channel.lock.withLock {
                            channel.freeWriteAmount += amount
                            channel.lockCondition.signalAll()
                        }
                    }
                }
                MESSAGE_WRITE -> {
                    if (length > BUFFER_SIZE) {
                        throw IOException("Message length is bigger than read size!")
                    }
                    val writeData = underlyingSource.readByteArray(length.toLong())
                    channelsLock.withLock {
                        val channel = channels[channelId] ?: return
                        channel.lock.withLock {
                            if (channel.requestedReadAmount < length) {
                                throw IOException("No outstanding read requests of this length!")
                            }
                            channel.requestedReadAmount -= length
                            if (channel.readBuffer.position() + length > BUFFER_SIZE) {
                                throw IOException("Shouldn't be getting more data when the buffer is too full!")
                            }
                            channel.readBuffer.put(writeData, 0, length)
                            channel.lockCondition.signalAll()
                        }
                    }
                }
                MESSAGE_PROTOCOL_VERSION -> {
                    //Allow more than 4 bytes data, for future extensibility
                    if (length < 4) {
                        throw IOException("Message length is invalid for 'MESSAGE_PROTOCOL_VERSION'!")
                    }
                    val versionData = underlyingSource.readByteArray(length.toLong())

                    //Check remote endpoint protocol version
                    var minimumVersion = ByteBuffer.wrap(versionData, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                    //signed short -> unsigned short (as int) conversion
                    if (minimumVersion < 0) minimumVersion += 0x10000
                    var maximumVersion = ByteBuffer.wrap(versionData, 2, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                    //signed short -> unsigned short (as int) conversion
                    if (maximumVersion < 0) maximumVersion += 0x10000
                    if (minimumVersion > 1 || maximumVersion < 1) {
                        throw IOException("Unsupported protocol version $minimumVersion - $maximumVersion!")
                    }
                    //We now support receiving other messages
                    receivedProtocolVersion = true
                }
                else -> {
                    throw IOException("Invalid message type " + type.toInt())
                }
            }
        }

        override fun run() {
            while (true) {
                channelsLock.withLock {
                    if (!open) {
                        LoggerTagged.w { "connection not open, returning" }
                        return
                    }
                }
                try {
                    readMessage()
                } catch (e: IOException) {
                    LoggerTagged.w(e) { "run caught IOException" }
                    handleException(e)
                    return
                }
            }
        }
    }

    companion object {
        private val DEFAULT_CHANNEL = UUID.fromString("a0d0aaf4-1072-4d81-aa35-902a954b1266")
        private const val BUFFER_SIZE = 4096
        private const val MESSAGE_PROTOCOL_VERSION: Byte = 0 //Negotiate the protocol version
        private const val MESSAGE_OPEN_CHANNEL: Byte = 1 //Open a new channel
        private const val MESSAGE_CLOSE_CHANNEL: Byte = 2 //Close a channel
        private const val MESSAGE_READ: Byte = 3 //Request some bytes from a channel
        private const val MESSAGE_WRITE: Byte = 4 //Write some bytes to a channel
    }
}
