package org.kde.kdeconnect.ui.compose.screen.settings.advanced.calls_and_messages

import android.app.Application
import android.media.RingtoneManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.kde.kdeconnect.settings.TelephonySettingsDataStore

data class TelephonySettingsUiState(
    val blockedNumbers: Set<String> = emptySet(),
    val groupMessageAsMms: Boolean = true,
    val longTextAsMms: Boolean = false,
    val convertToMmsAfter: Int = 3,
    val ringtoneUri: String = "",
    val ringtoneTitle: String = "",
    val flashlightEnabled: Boolean = false
)

class TelephonySettingsViewModel(
    application: Application,
    private val dataStore: TelephonySettingsDataStore
) : AndroidViewModel(application) {

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
        val ringtoneTitle = try {
            RingtoneManager.getRingtone(getApplication(), ringtoneUri.toUri()).getTitle(getApplication())
        } catch (_: Exception) {
            ringtoneUri
        }
        TelephonySettingsUiState(
            blockedNumbers = blockedNumbers,
            groupMessageAsMms = groupMessageAsMms,
            longTextAsMms = longTextAsMms,
            convertToMmsAfter = convertToMmsAfter,
            ringtoneUri = ringtoneUri,
            ringtoneTitle = ringtoneTitle,
            flashlightEnabled = flashlightEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TelephonySettingsUiState()
    )

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
