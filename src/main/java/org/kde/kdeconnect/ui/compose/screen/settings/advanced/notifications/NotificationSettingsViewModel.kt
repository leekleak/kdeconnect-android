package org.kde.kdeconnect.ui.compose.screen.settings.advanced.notifications

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.plugins.notifications.AppDatabase
import org.koin.core.annotation.KoinViewModel

data class AppInfo(
    val packageName: String,
    val name: String,
    val isEnabled: Boolean,
    val blockContents: Boolean,
    val blockImages: Boolean
)

data class NotificationSettingsUiState(
    val screenOffNotification: Boolean = false,
    val searchQuery: String = "",
    val allEnabled: Boolean = true,
    val enabledApps: List<AppInfo> = emptyList(),
    val disabledApps: List<AppInfo> = emptyList(),
    val notificationEnabled: Boolean = true,
    val keepWatchingEnabled: Boolean = true
)

@KoinViewModel
class NotificationSettingsViewModel(
    application: Application,
    private val dataStore: NotificationSettingsDataStore,
) : AndroidViewModel(application) {
    private val appDatabase = AppDatabase.getInstance(application)

    private val _searchQuery = MutableStateFlow("")
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        dataStore.screenOffNotification,
        dataStore.mprisNotificationEnabled,
        dataStore.mprisKeepWatchingEnabled,
        dataStore.allNotificationsEnabled,
        _searchQuery,
        _allApps
    ) { params: Array<Any> ->
        val screenOff = params[0] as Boolean
        val mprisEnabled = params[1] as Boolean
        val keepWatching = params[2] as Boolean
        val allEnabled = params[3] as Boolean
        val query = params[4] as String
        val apps = params[5] as List<AppInfo>

        val filtered = if (query.isEmpty()) apps else apps.filter { it.name.contains(query, ignoreCase = true) }
        val (enabled, disabled) = filtered.partition { it.isEnabled }
        NotificationSettingsUiState(
            screenOffNotification = screenOff,
            notificationEnabled = mprisEnabled,
            keepWatchingEnabled = keepWatching,
            allEnabled = allEnabled,
            searchQuery = query,
            enabledApps = enabled,
            disabledApps = disabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationSettingsUiState()
    )

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                val packageManager = getApplication<Application>().packageManager
                val installedApps = packageManager.getInstalledApplications(0)
                val allPackageNames = mutableSetOf<String>()
                val result = mutableListOf<AppInfo>()

                for (appInfo in installedApps) {
                    if (canPostNotifications(packageManager, appInfo)) {
                        result.add(createAppInfo(packageManager, appInfo))
                        allPackageNames.add(appInfo.packageName)
                    }
                }

                // Work profiles
                try {
                    val context = getApplication<Application>()
                    val currentUser = Process.myUserHandle()
                    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val um = context.getSystemService(Context.USER_SERVICE) as UserManager
                    for (userProfile in um.userProfiles) {
                        if (userProfile == currentUser) continue
                        for (app in launcher.getActivityList(null, userProfile)) {
                            val appInfo = app.applicationInfo
                            if (allPackageNames.contains(appInfo.packageName)) continue
                            if (canPostNotifications(packageManager, appInfo)) {
                                result.add(createAppInfo(packageManager, appInfo))
                                allPackageNames.add(appInfo.packageName)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationFilterVM", "Failed to get apps from work profile", e)
                }

                result.sortedBy { it.name.lowercase() }
            }
            _allApps.value = apps
        }
    }

    private fun createAppInfo(pm: PackageManager, info: ApplicationInfo): AppInfo {
        return AppInfo(
            packageName = info.packageName,
            name = info.loadLabel(pm).toString(),
            isEnabled = appDatabase.isEnabled(info.packageName),
            blockContents = appDatabase.getPrivacy(info.packageName, AppDatabase.PrivacyOptions.BLOCK_CONTENTS),
            blockImages = appDatabase.getPrivacy(info.packageName, AppDatabase.PrivacyOptions.BLOCK_IMAGES)
        )
    }

    private fun canPostNotifications(pm: PackageManager, info: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || info.targetSdkVersion < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return try {
            val packageInfo = pm.getPackageInfo(info.packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.contains(Manifest.permission.POST_NOTIFICATIONS) ?: false
        } catch (_: Exception) {
            true
        }
    }

    fun setScreenOffNotification(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setScreenOffNotification(enabled)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAllEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setAllNotificationsEnabled(enabled)
            withContext(Dispatchers.IO) {
                appDatabase.allEnabled = enabled
            }
            _allApps.update { it.map { app -> app.copy(isEnabled = enabled) } }
        }
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appDatabase.setEnabled(packageName, enabled)
            }
            _allApps.update { apps -> apps.map { if (it.packageName == packageName) it.copy(isEnabled = enabled) else it } }
        }
    }

    fun setAppPrivacy(packageName: String, option: AppDatabase.PrivacyOptions, blocked: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appDatabase.setPrivacy(packageName, option, blocked)
            }
            _allApps.update { apps ->
                apps.map {
                    if (it.packageName == packageName) {
                        when (option) {
                            AppDatabase.PrivacyOptions.BLOCK_CONTENTS -> it.copy(blockContents = blocked)
                            AppDatabase.PrivacyOptions.BLOCK_IMAGES -> it.copy(blockImages = blocked)
                        }
                    } else it
                }
            }
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setMprisNotificationEnabled(enabled)
        }
    }

    fun setKeepWatchingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setMprisKeepWatchingEnabled(enabled)
        }
    }

    companion object {
        const val MPRIS_TIME_DEFAULT = 10000000
    }
}
