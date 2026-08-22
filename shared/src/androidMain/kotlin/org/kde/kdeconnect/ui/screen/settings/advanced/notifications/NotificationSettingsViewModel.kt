package org.kde.kdeconnect.ui.screen.settings.advanced.notifications

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kde.kdeconnect.datastore.NotificationSettingsDataStore
import org.kde.kdeconnect.helpers.LoggerTagged
import org.kde.kdeconnect.plugins.notifications.AppDatabase
import org.koin.core.annotation.KoinViewModel

data class AppInfo(
    val packageName: String,
    val name: String,
    val blacklisted: Boolean,
    val blockContents: Boolean,
    val blockImages: Boolean
)

data class NotificationSettingsUiState(
    val screenOffNotification: Boolean = false,
    val searchQuery: String = "",
    val blacklistedApps: List<AppInfo> = emptyList(),
    val whitelistedApps: List<AppInfo> = emptyList(),
    val notificationEnabled: Boolean = true,
    val keepWatchingEnabled: Boolean = true
)

@KoinViewModel
class NotificationSettingsViewModel(
    application: Application,
    private val dataStore: NotificationSettingsDataStore,
    private val appDatabase: AppDatabase,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())

    val uiState: StateFlow<NotificationSettingsUiState> = combine(
        dataStore.screenOffNotification,
        dataStore.mprisNotificationEnabled,
        dataStore.mprisKeepWatchingEnabled,
        _searchQuery,
        _allApps
    ) { screenOff, mprisEnabled, keepWatching, query, apps ->

        val filtered = if (query.isEmpty()) apps else apps.filter { it.name.contains(query, ignoreCase = true) }
        val (blacklisted, whitelisted) = filtered.partition { it.blacklisted }
        NotificationSettingsUiState(
            screenOffNotification = screenOff,
            notificationEnabled = mprisEnabled,
            keepWatchingEnabled = keepWatching,
            searchQuery = query,
            blacklistedApps = blacklisted,
            whitelistedApps = whitelisted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationSettingsUiState()
    )

    init {
        loadApps(application)
    }

    private fun loadApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(0)
            val notificationApps = installedApps.filter { canPostNotifications(packageManager, it) }
            val allPackageNames = mutableSetOf<String>()

            val result = notificationApps.map { applicationInfo ->
                async {
                    allPackageNames.add(applicationInfo.packageName)
                    createAppInfo(packageManager, applicationInfo)
                }
            }.awaitAll()
            _allApps.value = result.sortedBy { it.name.lowercase() }

            // Work profiles
            val workResult = mutableListOf<AppInfo>() // Todo: Check if this should be kept as I'm unsure if normal apps actually have access to other users
            try {
                val currentUser = Process.myUserHandle()
                val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                val um = context.getSystemService(Context.USER_SERVICE) as UserManager
                for (userProfile in um.userProfiles) {
                    if (userProfile == currentUser) continue
                    for (app in launcher.getActivityList(null, userProfile)) {
                        val appInfo = app.applicationInfo
                        if (allPackageNames.contains(appInfo.packageName)) continue
                        if (canPostNotifications(packageManager, appInfo)) {
                            workResult.add(createAppInfo(packageManager, appInfo))
                            allPackageNames.add(appInfo.packageName)
                        }
                    }
                }
            } catch (e: Exception) {
                LoggerTagged.e(e) { "Failed to get apps from work profile" }
            }

            _allApps.value.union(workResult).sortedBy { it.name.lowercase() }
        }
    }

    private suspend fun createAppInfo(pm: PackageManager, info: ApplicationInfo): AppInfo {
        return AppInfo(
            packageName = info.packageName,
            name = info.loadLabel(pm).toString(),
            blacklisted = appDatabase.isBlacklisted(info.packageName),
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
            withContext(Dispatchers.IO) {
                appDatabase.setAllEnabled(enabled)
            }
            _allApps.update { it.map { app -> app.copy(blacklisted = enabled) } }
        }
    }

    fun setAppBlacklisted(packageName: String, blacklisted: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                appDatabase.setBlacklisted(packageName, blacklisted)
            }
            _allApps.update { apps -> apps.map { if (it.packageName == packageName) it.copy(blacklisted = blacklisted) else it } }
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
