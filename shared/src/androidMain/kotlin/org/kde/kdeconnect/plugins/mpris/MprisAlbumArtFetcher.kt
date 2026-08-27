package org.kde.kdeconnect.plugins.mpris

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.net.ConnectivityManagerCompat
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Source
import okio.buffer
import okio.source
import org.kde.kdeconnect.DeviceManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

class MprisAlbumArtFetcher(
    private val data: MprisAlbumArt,
    private val options: Options,
    private val deviceManager: DeviceManager
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val plugin = deviceManager.getDevicePlugin(data.deviceId, MprisPlugin::class.java)
            ?: return null

        val url = data.url.toUri()

        val source = when (url.scheme) {
            in listOf("kdeconnect", "file") -> {
                plugin.fetchAlbumArt(data.url, data.playerName)?.source
            }
            in listOf("http", "https") -> {
                if (isMetered(options.context)) return null
                openHttp(data.url)
            }
            else -> {
                null
            }
        }

        return source?.let {
            SourceFetchResult(
                source = ImageSource(it.buffer(), options.fileSystem),
                mimeType = null,
                dataSource = DataSource.NETWORK
            )
        }
    }

    private fun openHttp(url: String): Source? {
        var currentUrl = URL(url)
        var connection: HttpURLConnection
        loop@ for (i in 0..4) {
            connection = currentUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.instanceFollowRedirects = false
            when (connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP -> {
                    var location = connection.getHeaderField("Location")
                    location = URLDecoder.decode(location, "UTF-8")
                    currentUrl = URL(currentUrl, location)
                    if (currentUrl.protocol !in arrayOf("http", "https")) {
                        return null
                    }
                    connection.disconnect()
                    continue@loop
                }
            }
            return connection.inputStream.source()
        }
        return null
    }

    private fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return ConnectivityManagerCompat.isActiveNetworkMetered(cm)
    }

    class Factory(private val deviceManager: DeviceManager) : Fetcher.Factory<MprisAlbumArt> {
        override fun create(data: MprisAlbumArt, options: Options, imageLoader: ImageLoader): Fetcher {
            return MprisAlbumArtFetcher(data, options, deviceManager)
        }
    }

    class Keyer : coil3.key.Keyer<MprisAlbumArt> {
        override fun key(data: MprisAlbumArt, options: Options): String {
            return "${data.deviceId}:${data.url}"
        }
    }
}

data class MprisAlbumArt(val deviceId: String, val playerName: String, val url: String)
