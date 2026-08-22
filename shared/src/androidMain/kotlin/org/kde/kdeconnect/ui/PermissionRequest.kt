package org.kde.kdeconnect.ui

import org.jetbrains.compose.resources.StringResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

@Serializable
data class PermissionRequest(
    @Contextual val title: StringResource,
    @Contextual val description: StringResource,
    val intentAction: String,
    @Contextual val positiveButton: StringResource,
)