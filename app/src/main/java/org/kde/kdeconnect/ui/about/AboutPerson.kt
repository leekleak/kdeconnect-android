package org.kde.kdeconnect.ui.about

data class AboutPerson(
    val name: String,
    val task: Int? = null,
    val emailAddress: String? = null,
    val webAddress: String? = null
)
