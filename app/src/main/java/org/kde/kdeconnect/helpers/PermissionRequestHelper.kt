package org.kde.kdeconnect.helpers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PermissionRequestHelper {
    private val _shownExplanations = MutableStateFlow<Set<String>>(emptySet())
    val shownExplanations: StateFlow<Set<String>> = _shownExplanations.asStateFlow()

    fun isExplanationShown(pluginKey: String): Boolean {
        return _shownExplanations.value.contains(pluginKey)
    }

    fun markExplanationShown(pluginKey: String) {
        _shownExplanations.update { it + pluginKey }
    }

    fun markExplanationDismissed(pluginKey: String) {
        _shownExplanations.update { it - pluginKey }
    }
}
