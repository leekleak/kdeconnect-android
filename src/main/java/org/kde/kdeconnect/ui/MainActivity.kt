/*
 * SPDX-FileCopyrightText: 2023 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
*/

package org.kde.kdeconnect.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation3.ui.NavDisplay
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.ArrayUtils
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.Device
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.extensions.setOnApplyWindowInsetsListenerCompat
import org.kde.kdeconnect.helpers.DeviceHelper
import org.kde.kdeconnect.plugins.presenter.PresenterPlugin
import org.kde.kdeconnect.plugins.share.ShareSettingsFragment
import org.kde.kdeconnect.ui.compose.KdeTheme
import org.kde.kdeconnect.ui.navigation.AboutKey
import org.kde.kdeconnect.ui.navigation.DeviceKey
import org.kde.kdeconnect.ui.navigation.KdeConnectKey
import org.kde.kdeconnect.ui.navigation.KdeConnectKeyConstants
import org.kde.kdeconnect.ui.navigation.Navigator
import org.kde.kdeconnect.ui.navigation.PairingKey
import org.kde.kdeconnect.ui.navigation.PluginIndividualSettingsKey
import org.kde.kdeconnect.ui.navigation.PluginSettingsKey
import org.kde.kdeconnect.ui.navigation.PresenterKey
import org.kde.kdeconnect.ui.navigation.SettingsKey
import org.kde.kdeconnect_tp.R
import org.kde.kdeconnect_tp.databinding.ActivityMainBinding
import org.koin.android.ext.android.inject
import org.koin.android.scope.AndroidScopeComponent
import org.koin.androidx.scope.activityRetainedScope
import org.koin.compose.koinInject
import org.koin.compose.navigation3.koinEntryProvider
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.scope.Scope

private const val MENU_ENTRY_ADD_DEVICE = 1 //0 means no-selection
private const val MENU_ENTRY_SETTINGS = 2
private const val MENU_ENTRY_ABOUT = 3
private const val MENU_ENTRY_DEVICE_FIRST_ID = 1000 //All subsequent ids are devices in the menu
private const val MENU_ENTRY_DEVICE_UNKNOWN = 9999 //It's still a device, but we don't know which one yet
private const val STORAGE_LOCATION_CONFIGURED = 2020
private const val STATE_SELECTED_MENU_ENTRY = "selected_entry" //Saved only in onSaveInstanceState
private const val STATE_SELECTED_DEVICE = "selected_device" //Saved persistently in preferences

class MainActivity : AppCompatActivity(), OnSharedPreferenceChangeListener, AndroidScopeComponent {
    override val scope: Scope by activityRetainedScope()

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val mNavigationView: NavigationView by lazy { binding.navigationDrawer }
    private var mDrawerLayout: DrawerLayout? = null

    private lateinit var mNavViewDeviceName: TextView

    private var mCurrentDevice: String? = null
    private var mCurrentMenuEntry = 0
    private val preferences: SharedPreferences by lazy { getSharedPreferences("stored_menu_selection", MODE_PRIVATE) }
    private val mMapMenuToDeviceId = HashMap<MenuItem, String>()

    private val closeDrawerCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            mDrawerLayout?.closeDrawer(mNavigationView)
        }
    }

    private val mNavigator: Navigator by inject()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val currentKey = mNavigator.backStack.lastOrNull()
        if (currentKey is PresenterKey) {
            val offScreenControlsSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
            if (!offScreenControlsSupported) {
                val keyCode = event.keyCode
                val action = event.action
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val volumeKeysEnabled = prefs.getBoolean(getString(R.string.pref_presenter_enable_volume_keys), true)

                if (volumeKeysEnabled) {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action == KeyEvent.ACTION_UP) {
                        KdeConnect.getInstance().getDevicePlugin(currentKey.deviceId, PresenterPlugin::class.java)?.sendPrevious()
                        return true
                    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && action == KeyEvent.ACTION_UP) {
                        KdeConnect.getInstance().getDevicePlugin(currentKey.deviceId, PresenterPlugin::class.java)?.sendNext()
                        return true
                    }
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DeviceHelper.initializeDeviceId(this)

        val root = binding.root
        setContentView(root)
        mDrawerLayout = root as? DrawerLayout

        // Replace the FrameLayout container with a ComposeView
        val container = binding.coordinatorLayout.findViewById<View>(R.id.container)
        val parent = container.parent as ViewGroup
        val index = parent.indexOfChild(container)
        parent.removeView(container)
        val composeView = ComposeView(this).apply {
            id = R.id.container
            layoutParams = container.layoutParams
        }
        parent.addView(composeView, index)

        // Pad the drawer header directly
        root.setOnApplyWindowInsetsListenerCompat { _, insets ->
            mNavigationView.getHeaderView(0)?.updatePadding(
                top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            )
            insets
        }

        val mDrawerHeader = mNavigationView.getHeaderView(0)
        mNavViewDeviceName = mDrawerHeader.findViewById(R.id.device_name)
        val mNavViewDeviceType = mDrawerHeader.findViewById<ImageView>(R.id.device_type)

        setSupportActionBar(binding.toolbarLayout.toolbar)
        mDrawerLayout?.let {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            val mDrawerToggle = DrawerToggle(it).apply { syncState() }
            it.addDrawerListener(mDrawerToggle)
            it.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START)
        } ?: {
            supportActionBar?.setDisplayShowHomeEnabled(false)
            supportActionBar?.setHomeButtonEnabled(false)
        }

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this)
        val deviceName = DeviceHelper.getDeviceName(this)
        mNavViewDeviceType?.setImageDrawable(DeviceHelper.deviceType.getIcon(this))
        mNavViewDeviceName.text = deviceName

        // Decide which menu entry should be selected at start
        var savedDevice: String?
        var savedMenuEntry: Int
        when {
            intent.hasExtra(FLAG_FORCE_OVERVIEW) -> {
                savedDevice = null
                savedMenuEntry = MENU_ENTRY_ADD_DEVICE
            }
            intent.hasExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID) -> {
                savedDevice = intent.getStringExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID)
                savedMenuEntry = MENU_ENTRY_DEVICE_UNKNOWN
                val pairStatus = intent.getStringExtra(PAIR_REQUEST_STATUS)
                if (pairStatus != null) {
                    savedDevice = onPairResultFromNotification(savedDevice, pairStatus)
                    if (savedDevice == null) {
                        savedMenuEntry = MENU_ENTRY_ADD_DEVICE
                    }
                }
                
                // Handle deep link to plugin settings
                if (intent.hasExtra(KdeConnectKeyConstants.EXTRA_PLUGIN_KEY)) {
                    val pluginKey = intent.getStringExtra(KdeConnectKeyConstants.EXTRA_PLUGIN_KEY)!!
                    intent.putExtra(KdeConnectKeyConstants.EXTRA_DEVICE_ID, savedDevice)
                    mNavigator.goTo(PluginSettingsKey(savedDevice!!))
                    mNavigator.goTo(PluginIndividualSettingsKey(savedDevice, pluginKey))
                }
            }
            savedInstanceState != null -> {
                savedDevice = savedInstanceState.getString(STATE_SELECTED_DEVICE)
                savedMenuEntry = savedInstanceState.getInt(STATE_SELECTED_MENU_ENTRY, MENU_ENTRY_ADD_DEVICE)
            }
            else -> {
                savedDevice = preferences.getString(STATE_SELECTED_DEVICE, null)
                savedMenuEntry = if (savedDevice != null) MENU_ENTRY_DEVICE_UNKNOWN else MENU_ENTRY_ADD_DEVICE
            }
        }
        mCurrentMenuEntry = savedMenuEntry
        mCurrentDevice = savedDevice
        mNavigationView.setCheckedItem(savedMenuEntry)

        composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MainActivityContent()
            }
        }

        mNavigationView.setNavigationItemSelectedListener { menuItem: MenuItem ->
            mCurrentMenuEntry = menuItem.itemId
            when (mCurrentMenuEntry) {
                MENU_ENTRY_ADD_DEVICE -> {
                    onDeviceSelected(null)
                }
                MENU_ENTRY_SETTINGS -> {
                    mNavigator.goTo(SettingsKey)
                }
                MENU_ENTRY_ABOUT -> {
                    mNavigator.goTo(AboutKey)
                }
                else -> {
                    val deviceId = mMapMenuToDeviceId[menuItem]
                    onDeviceSelected(deviceId)
                }
            }
            mDrawerLayout?.closeDrawer(mNavigationView)
            true
        }

        val missingPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionResult = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionResult != PackageManager.PERMISSION_GRANTED) {
                if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if(missingPermissions.isNotEmpty()){
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), RESULT_NOTIFICATIONS_ENABLED)
        }
    }

    fun openDrawer() {
        mDrawerLayout?.openDrawer(mNavigationView)
    }

    @OptIn(KoinExperimentalAPI::class)
    @Composable
    private fun MainActivityContent() {
        val entryProvider = koinEntryProvider<Any>()
        val navigator: Navigator = koinInject()
        KdeTheme(this) {
            NavDisplay(
                backStack = navigator.backStack,
                entryProvider = entryProvider,
                onBack = { navigator.goBack() },
                transitionSpec = {
                    if (navigator.backStack.size == 1) fadeIn(tween()) togetherWith fadeOut(tween())
                    else {
                        slideInHorizontally { it } togetherWith
                                slideOutHorizontally { -it / 2 } + scaleOut(targetScale = 0.7f) + fadeOut()
                    }
                },
                popTransitionSpec = {
                    slideInHorizontally { -it / 2 } + scaleIn(initialScale = 0.7f) + fadeIn(tween()) togetherWith
                            slideOutHorizontally { it }
                },
                predictivePopTransitionSpec = {
                    slideInHorizontally { -it/2 } + scaleIn(initialScale = 0.7f) + fadeIn(tween()) togetherWith
                            slideOutHorizontally { it }
                }
            )
        }

        val currentKey by remember {
            derivedStateOf {
                navigator.backStack.last()
            }
        }
        LaunchedEffect(currentKey) {
            updateActivityStateFromKey(currentKey)
        }
    }
    
    private fun updateActivityStateFromKey(key: KdeConnectKey?) {
        binding.toolbarLayout.root.visibility = View.GONE
        supportActionBar?.hide()
        when (key) {
            is PairingKey -> {
                mCurrentDevice = null
                mCurrentMenuEntry = MENU_ENTRY_ADD_DEVICE
                supportActionBar?.setTitle(R.string.pairing_title)
                supportActionBar?.subtitle = null
            }
            is DeviceKey -> {
                mCurrentDevice = key.deviceId
                mCurrentMenuEntry = deviceIdToMenuEntryId(key.deviceId)
                val device = KdeConnect.getInstance().getDevice(key.deviceId)
                supportActionBar?.title = device?.name
            }
            is SettingsKey -> {
                mCurrentDevice = null
                mCurrentMenuEntry = MENU_ENTRY_SETTINGS
                supportActionBar?.setTitle(R.string.settings)
                supportActionBar?.subtitle = null
            }
            is AboutKey -> {
                mCurrentDevice = null
                mCurrentMenuEntry = MENU_ENTRY_ABOUT
                supportActionBar?.setTitle(R.string.about)
                supportActionBar?.subtitle = null
            }
            is PresenterKey -> {
                mCurrentDevice = key.deviceId
                mCurrentMenuEntry = deviceIdToMenuEntryId(key.deviceId)
                supportActionBar?.setTitle(R.string.pref_plugin_presenter)
                supportActionBar?.subtitle = null
            }
            else -> {}
        }
        mNavigationView.setCheckedItem(mCurrentMenuEntry)
    }

    override fun onDestroy() {
        super.onDestroy()
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun onPairResultFromNotification(deviceId: String?, pairStatus: String): String? {
        assert(deviceId != null)
        if (pairStatus != PAIRING_PENDING) {
            val device = KdeConnect.getInstance().getDevice(deviceId)
            if (device == null) {
                return null
            }
            when (pairStatus) {
                PAIRING_ACCEPTED -> device.acceptPairing()
                PAIRING_REJECTED -> device.cancelPairing()
            }
        }
        return if (pairStatus == PAIRING_ACCEPTED || pairStatus == PAIRING_PENDING) deviceId else null
    }

    private fun deviceIdToMenuEntryId(deviceId: String?): Int {
        for ((key, value) in mMapMenuToDeviceId) {
            if (value == deviceId) {
                return key.itemId
            }
        }
        return MENU_ENTRY_DEVICE_UNKNOWN
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            mDrawerLayout?.openDrawer(mNavigationView)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun updateDeviceList() {
        val menu = mNavigationView.menu
        menu.clear()
        mMapMenuToDeviceId.clear()
        val devicesMenu = menu.addSubMenu(R.string.devices)
        var id = MENU_ENTRY_DEVICE_FIRST_ID
        val devices: Collection<Device> = KdeConnect.getInstance().devices.values
        for (device in devices) {
            if (device.isReachable && device.isPaired) {
                val item = devicesMenu.add(Menu.FIRST, id++, 1, device.name)
                item.icon = device.icon
                item.isCheckable = true
                mMapMenuToDeviceId[item] = device.deviceId
            }
        }
        val addDeviceItem = devicesMenu.add(Menu.FIRST, MENU_ENTRY_ADD_DEVICE, 1000, R.string.pair_new_device)
        addDeviceItem.setIcon(R.drawable.ic_action_content_add_circle_outline_32dp)
        addDeviceItem.isCheckable = true
        val settingsItem = menu.add(Menu.FIRST, MENU_ENTRY_SETTINGS, 1000, R.string.settings)
        settingsItem.setIcon(R.drawable.ic_settings_white_32dp)
        settingsItem.isCheckable = true
        val aboutItem = menu.add(Menu.FIRST, MENU_ENTRY_ABOUT, 1000, R.string.about)
        aboutItem.setIcon(R.drawable.ic_baseline_info_24)
        aboutItem.isCheckable = true

        if (mCurrentMenuEntry >= MENU_ENTRY_DEVICE_FIRST_ID) {
            mCurrentMenuEntry = deviceIdToMenuEntryId(mCurrentDevice)
        }
        mNavigationView.setCheckedItem(mCurrentMenuEntry)
    }

    override fun onStart() {
        super.onStart()
        BackgroundService.Start(applicationContext)
        KdeConnect.getInstance().addDeviceListChangedCallback(this::class.simpleName!!) { runOnUiThread { updateDeviceList() } }
        updateDeviceList()
        onBackPressedDispatcher.addCallback(closeDrawerCallback)
        if (mDrawerLayout == null) closeDrawerCallback.isEnabled = false
    }

    override fun onStop() {
        KdeConnect.getInstance().removeDeviceListChangedCallback(this::class.simpleName!!)
        closeDrawerCallback.remove()
        super.onStop()
    }

    @JvmOverloads
    fun onDeviceSelected(deviceId: String?, fromDeviceList: Boolean = false) {
        if (deviceId != null) {
            mNavigator.goTo(DeviceKey(deviceId, fromDeviceList))
        } else {
            mNavigator.goTo(PairingKey)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED_DEVICE, mCurrentDevice)
        outState.putInt(STATE_SELECTED_MENU_ENTRY, mCurrentMenuEntry)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when {
            requestCode == RESULT_NEEDS_RELOAD -> {
                CoroutineScope(Dispatchers.IO).launch {
                    KdeConnect.getInstance().devices.values.forEach(Device::reloadPluginsFromSettings)
                }
            }
            requestCode == STORAGE_LOCATION_CONFIGURED && resultCode == RESULT_OK && data != null -> {
                val uri = data.data
                ShareSettingsFragment.saveStorageLocationPreference(this, uri)
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun isPermissionGranted(permissions: Array<String>, grantResults: IntArray, permission : String) : Boolean {
        val index = ArrayUtils.indexOf(permissions, permission)
        return index != ArrayUtils.INDEX_NOT_FOUND && grantResults[index] == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsGranted = ArrayUtils.contains(grantResults, PackageManager.PERMISSION_GRANTED)
        if (permissionsGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isPermissionGranted(permissions, grantResults, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, STORAGE_LOCATION_CONFIGURED)
            }

            if (isPermissionGranted(permissions, grantResults, Manifest.permission.BLUETOOTH_CONNECT) &&
                isPermissionGranted(permissions, grantResults, Manifest.permission.BLUETOOTH_SCAN)) {
                PreferenceManager.getDefaultSharedPreferences(this).edit {
                    putBoolean(SettingsFragment.KEY_BLUETOOTH_ENABLED, true)
                }
                mNavigator.goTo(SettingsKey)
            }

            CoroutineScope(Dispatchers.IO).launch {
                KdeConnect.getInstance().devices.values.forEach(Device::reloadPluginsFromSettings)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (DeviceHelper.KEY_DEVICE_NAME_PREFERENCE == key) {
            mNavViewDeviceName.text = DeviceHelper.getDeviceName(this)
            BackgroundService.ForceRefreshConnections(this)
        }
    }

    private fun uncheckAllMenuItems(menu: Menu) {
        val size = menu.size
        for (i in 0 until size) {
            val item = menu[i]
            item.subMenu?.let { uncheckAllMenuItems(it) } ?: item.setChecked(false)
        }
    }

    companion object {
        const val EXTRA_DEVICE_ID = KdeConnectKeyConstants.EXTRA_DEVICE_ID
        const val PAIR_REQUEST_STATUS = "pair_req_status"
        const val PAIRING_ACCEPTED = "accepted"
        const val PAIRING_REJECTED = "rejected"
        const val PAIRING_PENDING = "pending"
        const val RESULT_NEEDS_RELOAD = RESULT_FIRST_USER
        const val RESULT_NOTIFICATIONS_ENABLED = RESULT_FIRST_USER+1
        const val FLAG_FORCE_OVERVIEW = "forceOverview"
    }

    private inner class DrawerToggle(drawerLayout: DrawerLayout) : ActionBarDrawerToggle(
        this,  /* host Activity */
        drawerLayout,  /* DrawerLayout object */
        R.string.open,  /* "open drawer" description */
        R.string.close /* "close drawer" description */
    ) {
        override fun onDrawerClosed(drawerView: View) {
            super.onDrawerClosed(drawerView)
            closeDrawerCallback.isEnabled = false
        }

        override fun onDrawerOpened(drawerView: View) {
            super.onDrawerOpened(drawerView)
            closeDrawerCallback.isEnabled = true
        }
    }

}
