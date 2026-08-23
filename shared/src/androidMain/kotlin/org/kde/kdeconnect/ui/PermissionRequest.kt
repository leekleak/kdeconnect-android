package org.kde.kdeconnect.ui

import kotlinx.serialization.Serializable

@Serializable
data class PermissionRequest(
    val title: String,
    val description: String,
    val intentAction: String,
    val positiveButton: String,
)