package org.kde.kdeconnect.ui

import androidx.annotation.StringRes
import kotlinx.serialization.Serializable

@Serializable
data class PermissionRequest(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val intentAction: String,
    @StringRes val positiveButton: Int,
)