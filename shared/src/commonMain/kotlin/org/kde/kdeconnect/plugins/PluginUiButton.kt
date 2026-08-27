package org.kde.kdeconnect.plugins

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.kde.kdeconnect.ui.navigation.Navigator

enum class ButtonCategory {
    SEND,
    CONTROL
}

data class PluginUiButton(
    val pluginKey: String,
    val name: StringResource,
    val nameFull: StringResource = name,
    val iconRes: DrawableResource,
    val category: ButtonCategory,
    val onClick: suspend (navigator: Navigator) -> Unit,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluginUiButton) return false

        if (pluginKey != other.pluginKey) return false
        if (name != other.name) return false
        if (nameFull != other.nameFull) return false
        if (iconRes != other.iconRes) return false
        if (category != other.category) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pluginKey.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + nameFull.hashCode()
        result = 31 * result + iconRes.hashCode()
        result = 31 * result + category.hashCode()
        return result
    }
}