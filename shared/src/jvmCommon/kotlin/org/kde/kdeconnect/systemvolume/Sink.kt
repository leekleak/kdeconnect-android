/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.systemvolume

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

data class Sink(
    val name: String,
    val description: String,
    val volume: Int,
    val maxVolume: Int,
    val isMuted: Boolean,
    val isDefault: Boolean
) {
    constructor(obj: JsonObject) : this(
        name = obj["name"]?.jsonPrimitive?.content ?: "",
        description = obj["description"]?.jsonPrimitive?.content ?: "",
        volume = obj["volume"]?.jsonPrimitive?.int ?: 0,
        maxVolume = obj["maxVolume"]?.jsonPrimitive?.int ?: 100,
        isMuted = obj["muted"]?.jsonPrimitive?.boolean ?: false,
        isDefault = obj["enabled"]?.jsonPrimitive?.boolean ?: false,
    )
}
