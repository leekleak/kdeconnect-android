package org.kde.kdeconnect.ui

import android.net.Uri

interface ShareHandler {
    var shareGetResultCallback: ((List<Uri>) -> Unit)?
    fun launchSharePicker(mimeType: String)
}
