/*
 * SPDX-FileCopyrightText: 2018 Nicolas Fella <nicolas.fella@gmx.de>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/
package org.kde.kdeconnect.plugins.systemvolume

import org.json.JSONObject

data class Sink(
    val name: String,
    val description: String,
    val volume: Int,
    val maxVolume: Int,
    val isMuted: Boolean,
    val isDefault: Boolean
) {
    constructor(obj: JSONObject) : this(
        name = obj.getString("name"),
        description = obj.getString("description"),
        volume = obj.getInt("volume"),
        maxVolume = obj.getInt("maxVolume"),
        isMuted = obj.getBoolean("muted"),
        isDefault = obj.optBoolean("enabled", false)
    )
}
