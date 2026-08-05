package org.kde.kdeconnect.plugins.mpris

import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import org.kde.kdeconnect.DeviceManager

class MprisAlbumArtFetcher(
    private val data: MprisAlbumArt,
    private val deviceManager: DeviceManager
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val plugin = deviceManager.getDevicePlugin(data.deviceId, MprisPlugin::class.java) 
            ?: throw Exception("MprisPlugin not found for device ${data.deviceId}")
        
        val bitmap = plugin.players.mapNotNull {
            AlbumArtCache.getAlbumArt(data.url, plugin, data.playerName)
        }.first()

        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val deviceManager: DeviceManager) : Fetcher.Factory<MprisAlbumArt> {
        override fun create(data: MprisAlbumArt, options: Options, imageLoader: ImageLoader): Fetcher {
            return MprisAlbumArtFetcher(data, deviceManager)
        }
    }
}

data class MprisAlbumArt(val deviceId: String, val playerName: String, val url: String)
