package org.kde.kdeconnect.plugins.mpris

import android.annotation.SuppressLint
import androidx.lifecycle.coroutineScope
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MprisMediaSessionService : MediaSessionService(), KoinComponent {
    private val mprisMediaSession: MprisMediaSession by inject()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mprisMediaSession.mediaSession

    @SuppressLint("UnsafeOptInUsageError")
    override fun onCreate() {
        super.onCreate()

        lifecycle.coroutineScope.launch {
            mprisMediaSession.sessionFlow.collect { session ->
                sessions.forEach { removeSession(it) }
                session?.let { addSession(it) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
