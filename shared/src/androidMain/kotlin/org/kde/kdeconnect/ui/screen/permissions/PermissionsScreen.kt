package org.kde.kdeconnect.ui.screen.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.BackgroundService
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.draw_over_other_apps
import org.kde.kdeconnect.generated.resources.ensures_the_app_can_run_in_the_background
import org.kde.kdeconnect.generated.resources.ensures_the_app_is_able_to_process_incoming_commands
import org.kde.kdeconnect.generated.resources.flip_to_front
import org.kde.kdeconnect.generated.resources.grant
import org.kde.kdeconnect.generated.resources.ic_kde_24dp
import org.kde.kdeconnect.generated.resources.local_network_access
import org.kde.kdeconnect.generated.resources.location_permission_needed_title
import org.kde.kdeconnect.generated.resources.notifications
import org.kde.kdeconnect.generated.resources.required_to_find_other_devices
import org.kde.kdeconnect.generated.resources.wifi
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.IconPreference
import org.kde.kdeconnect.ui.components.Preference
import org.kde.kdeconnect.ui.components.googleSans
import org.kde.kdeconnect.ui.navigation.HomeKey
import org.kde.kdeconnect.ui.navigation.Navigator

@Composable
fun PermissionsScreen(
    navigator: Navigator,
) {
    val context = LocalContext.current

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var hasNetworkPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
                hasOverlayPermission = Settings.canDrawOverlays(context)
                hasNetworkPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                    context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
                } else true

                if (hasNotificationPermission && hasOverlayPermission && hasNetworkPermission) {
                    BackgroundService.start(context)
                    navigator.setTo(HomeKey)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    val networkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNetworkPermission = isGranted
    }

    val font = googleSans(weight = 600f, roundness = 70f)

    HazeScaffold(backAction = BackAction.None) {
        Icon(
            modifier = Modifier
                .padding(top = 56.dp)
                .size(112.dp)
                .align(Alignment.CenterHorizontally),
            painter = painterResource(Res.drawable.ic_kde_24dp),
            contentDescription = null,
            tint = colorScheme.onSurface,
        )
        Text(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.CenterHorizontally),
            text = stringResource(Res.string.location_permission_needed_title),
            style = typography.headlineMedium,
            color = colorScheme.onSurface,
            fontFamily = font,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = stringResource(Res.string.notifications),
                description = stringResource(Res.string.ensures_the_app_can_run_in_the_background),
                icon = Res.drawable.notifications,
                granted = hasNotificationPermission,
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }

        PermissionCard(
            title = stringResource(Res.string.draw_over_other_apps),
            description = stringResource(Res.string.ensures_the_app_is_able_to_process_incoming_commands),
            icon = Res.drawable.flip_to_front,
            granted = hasOverlayPermission,
            onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                )
                context.startActivity(intent)
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            PermissionCard(
                title = stringResource(Res.string.local_network_access),
                description = stringResource(Res.string.required_to_find_other_devices),
                icon = Res.drawable.wifi,
                granted = hasNetworkPermission,
                onClick = { networkLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: DrawableResource,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row (
        modifier = Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Preference(
            modifier = Modifier.weight(1f),
            title = title,
            summary = description,
            icon = painterResource(icon),
        )
        IconPreference(
            title = stringResource(Res.string.grant),
            painter = painterResource(Res.drawable.grant),
            enabled = !granted,
            important = true,
            onClick = onClick
        )
    }
}
