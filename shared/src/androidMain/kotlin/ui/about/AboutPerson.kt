package org.kde.kdeconnect.ui.about

import org.jetbrains.compose.resources.StringResource

data class AboutPerson(
    val name: String,
    val task: StringResource? = null,
    val emailAddress: String? = null,
    val webAddress: String? = null
)
