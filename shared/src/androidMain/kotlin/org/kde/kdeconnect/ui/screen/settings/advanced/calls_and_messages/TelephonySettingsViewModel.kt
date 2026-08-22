package org.kde.kdeconnect.ui.screen.settings.advanced.calls_and_messages

import android.content.Context
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.datastore.TelephonySettingsDataStore

data class TelephonySettingsUiState(
    val blockedNumbers: Set<String> = emptySet(),
    val groupMessageAsMms: Boolean = true,
    val longTextAsMms: Boolean = false,
    val convertToMmsAfter: Int = 3,
    val ringtoneUri: String = "",
    val flashlightEnabled: Boolean = false
)

class TelephonySettingsViewModel(
    private val dataStore: TelephonySettingsDataStore
) : ViewModel() {

    val uiState: StateFlow<TelephonySettingsUiState> = combine(
        dataStore.blockedNumbers,
        dataStore.groupMessageAsMms,
        dataStore.longTextAsMms,
        dataStore.convertToMmsAfter,
        dataStore.ringtoneUri,
        dataStore.flashlightEnabled
    ) { params: Array<Any> ->
        val blockedNumbers = params[0] as Set<String>
        val groupMessageAsMms = params[1] as Boolean
        val longTextAsMms = params[2] as Boolean
        val convertToMmsAfter = params[3] as Int
        val ringtoneUriString = params[4] as String
        val flashlightEnabled = params[5] as Boolean

        val ringtoneUri = ringtoneUriString.ifEmpty { Settings.System.DEFAULT_RINGTONE_URI.toString() }

        TelephonySettingsUiState(
            blockedNumbers = blockedNumbers,
            groupMessageAsMms = groupMessageAsMms,
            longTextAsMms = longTextAsMms,
            convertToMmsAfter = convertToMmsAfter,
            ringtoneUri = ringtoneUri,
            flashlightEnabled = flashlightEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TelephonySettingsUiState()
    )

    fun getRingtoneTitle(context: Context): String {
        return try {
            RingtoneManager.getRingtone(context, uiState.value.ringtoneUri.toUri()).getTitle(context)
        } catch (_: Exception) {
            uiState.value.ringtoneUri
        }
    }
    fun blockNumber(number: String) {
        viewModelScope.launch {
            dataStore.updateBlockedNumbers(uiState.value.blockedNumbers.plus(number))
        }
    }

    fun unblockNumber(number: String) {
        viewModelScope.launch {
            dataStore.updateBlockedNumbers(uiState.value.blockedNumbers.minus(number))
        }
    }

    fun setGroupMessageAsMms(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setGroupMessageAsMms(enabled)
        }
    }

    fun setLongTextAsMms(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setLongTextAsMms(enabled)
        }
    }

    fun setConvertToMmsAfter(value: Int) {
        viewModelScope.launch {
            dataStore.setConvertToMmsAfter(value)
        }
    }

    fun setRingtone(uri: String) {
        viewModelScope.launch {
            dataStore.setRingtone(uri)
        }
    }

    fun setFlashlightEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setFlashlightEnabled(enabled)
        }
    }
}
