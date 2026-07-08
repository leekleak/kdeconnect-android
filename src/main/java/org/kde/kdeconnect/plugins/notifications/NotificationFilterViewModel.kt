package org.kde.kdeconnect.plugins.notifications

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserManager
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kde.kdeconnect_tp.R
import org.koin.compose.koinInject
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.koin.java.KoinJavaComponent.inject

data class AppInfo(
    val packageName: String,
    val name: String,
    val isEnabled: Boolean,
    val blockContents: Boolean,
    val blockImages: Boolean
)

data class NotificationFilterUiState(
    val screenOffNotification: Boolean = false,
    val searchQuery: String = "",
    val allEnabled: Boolean = true,
    val isLoading: Boolean = true,
    val enabledApps: List<AppInfo> = emptyList(),
    val disabledApps: List<AppInfo> = emptyList()
)

@KoinViewModel
class NotificationFilterViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val appDatabase = AppDatabase.getInstance(application)
    private val prefs: SharedPreferences = application.getSharedPreferences(NotificationsPlugin.PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(NotificationFilterUiState())
    val uiState: StateFlow<NotificationFilterUiState> = _uiState.asStateFlow()

    private var allApps: List<AppInfo> = emptyList()

    init {
        loadSettings()
        loadApps()
    }

    private fun loadSettings() {
        _uiState.update { it.copy(
            screenOffNotification = prefs.getBoolean(NotificationsPlugin.PREF_NOTIFICATION_SCREEN_OFF, false),
            allEnabled = appDatabase.allEnabled
        ) }
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            allApps = withContext(Dispatchers.IO) {
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
            filterApps()
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
        } catch (e: Exception) {
            true
        }
    }

    fun setScreenOffNotification(enabled: Boolean) {
        prefs.edit { putBoolean(NotificationsPlugin.PREF_NOTIFICATION_SCREEN_OFF, enabled) }
        _uiState.update { it.copy(screenOffNotification = enabled) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterApps()
    }

    private fun filterApps() {
        val query = _uiState.value.searchQuery.lowercase().trim()
        val enabled = allApps.filter { it.isEnabled }
        val disabled = allApps.filter { !it.isEnabled }.let { list ->
            if (query.isEmpty()) {
                list
            } else {
                list.filter { it.name.lowercase().contains(query) }
            }
        }
        _uiState.update { it.copy(
            enabledApps = enabled,
            disabledApps = disabled,
            isLoading = false
        ) }
    }

    fun setAllEnabled(enabled: Boolean) {
        appDatabase.allEnabled = enabled
        allApps = allApps.map { it.copy(isEnabled = enabled) }
        _uiState.update { it.copy(allEnabled = enabled) }
        filterApps()
    }

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        appDatabase.setEnabled(packageName, enabled)
        allApps = allApps.map { if (it.packageName == packageName) it.copy(isEnabled = enabled) else it }
        filterApps()
    }

    fun setAppPrivacy(packageName: String, option: AppDatabase.PrivacyOptions, blocked: Boolean) {
        appDatabase.setPrivacy(packageName, option, blocked)
        allApps = allApps.map { 
            if (it.packageName == packageName) {
                when (option) {
                    AppDatabase.PrivacyOptions.BLOCK_CONTENTS -> it.copy(blockContents = blocked)
                    AppDatabase.PrivacyOptions.BLOCK_IMAGES -> it.copy(blockImages = blocked)
                }
            } else it 
        }
        filterApps()
    }
}
