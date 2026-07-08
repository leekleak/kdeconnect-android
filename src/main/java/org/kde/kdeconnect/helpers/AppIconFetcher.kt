package org.kde.kdeconnect.helpers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options

class AppIconFetcher(
    private val appIcon: AppIcon,
    private val context: Context
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val drawable = try {
            context.packageManager.getApplicationIcon(appIcon.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            context.packageManager.defaultActivityIcon
        }

        return ImageFetchResult(
            image = drawable.asImage(),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AppIcon> {
        override fun create(data: AppIcon, options: Options, imageLoader: ImageLoader): Fetcher {
            return AppIconFetcher(data, context)
        }
    }
}

data class AppIcon(val packageName: String)
