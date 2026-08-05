/*
 * SPDX-FileCopyrightText: 2021 Art Pinch <leonardo90690@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */

package org.kde.kdeconnect.plugins.systemvolume

import android.media.AudioManager
import androidx.media.VolumeProviderCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.kde.kdeconnect.helpers.DEFAULT_MAX_VOLUME
import org.kde.kdeconnect.helpers.DEFAULT_VOLUME_STEP
import org.kde.kdeconnect.helpers.calculateNewVolume
import kotlin.math.ceil
import kotlin.math.floor

class SystemVolumeProvider : VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, DEFAULT_MAX_VOLUME, 0) {

    interface ProviderStateListener {
        fun onProviderStateChanged(systemVolumeProvider: SystemVolumeProvider, isActive: Boolean)
    }

    companion object {
        @JvmStatic
        var currentProvider: SystemVolumeProvider? = null
            private set

        @JvmStatic
        fun getInstance(): SystemVolumeProvider {
            val provider = currentProvider ?: SystemVolumeProvider()
            currentProvider = provider
            return provider
        }

        private fun scale(value: Int, maxValue: Int, maxScaled: Int): Int {
            val floatingResult = value * maxScaled / maxValue.toDouble()
            return if (maxScaled > maxValue) {
                ceil(floatingResult).toInt()
            } else {
                floor(floatingResult).toInt()
            }
        }
    }

    private val stateListeners: MutableList<ProviderStateListener> = mutableListOf()

    private var defaultSink: Sink? = null

    private var systemVolumePlugin: SystemVolumePlugin? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectionJob: Job? = null

    fun setPlugin(plugin: SystemVolumePlugin?) {
        if (plugin === systemVolumePlugin) return

        propagateState(false)
        defaultSink = null
        collectionJob?.cancel()
        systemVolumePlugin = plugin
        if (plugin != null) {
            collectionJob = scope.launch {
                plugin.sinks.collect { sinks ->
                    onSinksChanged(sinks)
                }
            }
        }
    }

    private fun onSinksChanged(sinks: List<Sink>) {
        val newDefaultSink = sinks.firstOrNull { it.isDefault }

        newDefaultSink?.also {
            updateLocalVolume(it)
        }

        if ((newDefaultSink == null) xor (defaultSink == null)) {
            val volumeAdjustSupported = isVolumeAdjustSupported(newDefaultSink)
            propagateState(volumeAdjustSupported)
        }
        defaultSink = newDefaultSink
    }

    override fun onAdjustVolume(direction: Int) {
        val step = when (direction) {
            AudioManager.ADJUST_RAISE -> DEFAULT_VOLUME_STEP
            AudioManager.ADJUST_LOWER -> -DEFAULT_VOLUME_STEP
            else -> return
        }
        val newVolume = calculateNewVolume(currentVolume, maxVolume, step)
        onSetVolumeTo(newVolume)
    }

    override fun onSetVolumeTo(volume: Int) {
        updateLocalAndRemoteVolume(defaultSink, volume)
    }

    private fun updateLocalAndRemoteVolume(sink: Sink?, volume: Int) {
        val systemVolumePlugin = systemVolumePlugin ?: return

        val shouldUpdateRemote = updateLocalVolume(volume)
        if (!shouldUpdateRemote || sink == null) return
        val remoteVolume = scaleFromLocal(volume, sink.maxVolume)
        systemVolumePlugin.sendVolume(sink.name, remoteVolume)
    }

    private fun updateLocalVolume(volume: Int): Boolean {
        if (currentVolume == volume) return false
        currentVolume = volume
        return true
    }

    private fun updateLocalVolume(sink: Sink) {
        val localVolume = scaleToLocal(sink.volume, sink.maxVolume)
        updateLocalVolume(localVolume)
    }

    private fun scaleToLocal(value: Int, maxValue: Int): Int {
        return scale(value, maxValue, maxVolume)
    }

    private fun scaleFromLocal(value: Int, maxScaled: Int): Int {
        return scale(value, maxVolume, maxScaled)
    }

    fun addStateListener(l: ProviderStateListener) {
        if (!stateListeners.contains(l)) {
            stateListeners.add(l)
            l.onProviderStateChanged(this, isVolumeAdjustSupported(defaultSink))
        }
    }

    fun removeStateListener(l: ProviderStateListener) {
        stateListeners.remove(l)
    }

    private fun propagateState(state: Boolean) {
        for (listener in stateListeners) {
            listener.onProviderStateChanged(this, state)
        }
    }

    private fun isVolumeAdjustSupported(sink: Sink?): Boolean {
        return sink != null
    }

    fun release() {
        collectionJob?.cancel()
        stateListeners.clear()
        currentProvider = null
    }
}
