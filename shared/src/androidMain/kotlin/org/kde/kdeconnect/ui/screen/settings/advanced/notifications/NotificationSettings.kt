package org.kde.kdeconnect.ui.screen.settings.advanced.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.arrow_downward
import org.kde.kdeconnect.generated.resources.arrow_forward_ios
import org.kde.kdeconnect.generated.resources.arrow_upward
import org.kde.kdeconnect.generated.resources.bedtime
import org.kde.kdeconnect.generated.resources.blacklisted_apps
import org.kde.kdeconnect.generated.resources.block
import org.kde.kdeconnect.generated.resources.block_notification_contents
import org.kde.kdeconnect.generated.resources.block_notification_images
import org.kde.kdeconnect.generated.resources.expand
import org.kde.kdeconnect.generated.resources.media_controls
import org.kde.kdeconnect.generated.resources.mpris_keepwatching_settings_summary
import org.kde.kdeconnect.generated.resources.mpris_keepwatching_settings_title
import org.kde.kdeconnect.generated.resources.mpris_notification_settings_summary
import org.kde.kdeconnect.generated.resources.mpris_notification_settings_title
import org.kde.kdeconnect.generated.resources.notifications
import org.kde.kdeconnect.generated.resources.ok
import org.kde.kdeconnect.generated.resources.pinboard_unread
import org.kde.kdeconnect.generated.resources.privacy_options
import org.kde.kdeconnect.generated.resources.replay
import org.kde.kdeconnect.generated.resources.show_notification_if_screen_off
import org.kde.kdeconnect.generated.resources.synchronization
import org.kde.kdeconnect.helpers.AppIcon
import org.kde.kdeconnect.plugins.notifications.AppDatabase
import org.kde.kdeconnect.ui.components.AppSelector
import org.kde.kdeconnect.ui.components.BackAction
import org.kde.kdeconnect.ui.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.components.HazeScaffold
import org.kde.kdeconnect.ui.components.Preference
import org.kde.kdeconnect.ui.components.SearchField
import org.kde.kdeconnect.ui.components.SwitchPreference
import org.kde.kdeconnect.ui.components.card
import org.kde.kdeconnect.ui.navigation.Navigator
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationSettings(
    viewModel: NotificationSettingsViewModel = koinViewModel(),
    navigator: Navigator,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(Res.string.notifications),
        backAction = BackAction.Normal(navigator),
    ) {
        CategoryTitleTextSmall(stringResource(Res.string.synchronization))
        SwitchPreference(
            title = stringResource(Res.string.show_notification_if_screen_off),
            icon = painterResource(Res.drawable.bedtime),
            value = uiState.screenOffNotification,
            onValueChanged = viewModel::setScreenOffNotification
        )
        NotificationBlacklistComponent(viewModel, uiState)

        CategoryTitleTextSmall(stringResource(Res.string.media_controls))
        SwitchPreference(
            title = stringResource(Res.string.mpris_notification_settings_title),
            summary = stringResource(Res.string.mpris_notification_settings_summary),
            icon = painterResource(Res.drawable.pinboard_unread),
            value = uiState.notificationEnabled,
            onValueChanged = viewModel::setNotificationEnabled
        )

        SwitchPreference(
            title = stringResource(Res.string.mpris_keepwatching_settings_title),
            summary = stringResource(Res.string.mpris_keepwatching_settings_summary),
            icon = painterResource(Res.drawable.replay),
            value = uiState.keepWatchingEnabled,
            onValueChanged = viewModel::setKeepWatchingEnabled
        )
    }
}

@Composable
private fun NotificationBlacklistComponent(
    viewModel: NotificationSettingsViewModel,
    uiState: NotificationSettingsUiState,
) {
    var addApps by remember { mutableStateOf(value = false) }
    val textFieldState = rememberTextFieldState(uiState.searchQuery)
    val haptic = LocalHapticFeedback.current
    var showPrivacyDialogForAppPackageName by remember { mutableStateOf<String?>(null) }
    val showPrivacyDialogForApp = uiState.blacklistedApps.find { it.packageName == showPrivacyDialogForAppPackageName }
        ?: uiState.whitelistedApps.find { it.packageName == showPrivacyDialogForAppPackageName }

    LaunchedEffect(textFieldState.text) {
        viewModel.setSearchQuery(textFieldState.text.toString())
    }

    Column(Modifier.card()) {
        Preference(
            title = stringResource(Res.string.blacklisted_apps),
            icon = painterResource(Res.drawable.block),
            onClick = {
                addApps = !addApps
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
            controls = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                        uiState.blacklistedApps.take(3).forEach { app ->
                            AnimatedVisibility(
                                visible = !addApps,
                                enter = fadeIn(tween()) + scaleIn(),
                                exit = fadeOut(tween()) + scaleOut()
                            ) {
                                Image(
                                    modifier = Modifier.size(36.dp),
                                    painter = rememberAsyncImagePainter(AppIcon(app.packageName)),
                                    contentDescription = app.name,
                                )
                            }
                        }
                    }
                    val rotateDegrees by animateFloatAsState(if (addApps) 90f else 0f)
                    Icon(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .rotate(rotateDegrees),
                        painter = painterResource(Res.drawable.arrow_forward_ios),
                        contentDescription = stringResource(Res.string.expand)
                    )
                }
            }
        )

        AnimatedVisibility(visible = addApps) {
            Column {
                AppSelector(
                    apps = uiState.blacklistedApps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    onLongClick = { packageName ->
                        showPrivacyDialogForAppPackageName = packageName
                    }
                ) { packageName ->
                    viewModel.setAppBlacklisted(packageName, false)
                }
                Box(
                    Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                ) {
                    HorizontalDivider(Modifier.align(Alignment.Center))
                    Row(Modifier.align(Alignment.Center)) {
                        FilledIconButton(onClick = {
                            viewModel.setAllEnabled(true)
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.arrow_upward),
                                contentDescription = null
                            )
                        }
                        FilledIconButton(onClick = {
                            viewModel.setAllEnabled(false)
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        }) {
                            Icon(
                                painter = painterResource(Res.drawable.arrow_downward),
                                contentDescription = null
                            )
                        }
                    }
                }
                AppSelector(
                    apps = uiState.whitelistedApps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) { packageName ->
                    viewModel.setAppBlacklisted(packageName, true)
                }
                SearchField(textFieldState)
            }
        }
    }

    showPrivacyDialogForApp?.let { app ->
        AlertDialog(
            onDismissRequest = { showPrivacyDialogForAppPackageName = null },
            title = { Text(stringResource(Res.string.privacy_options) + ": " + app.name) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = app.blockContents,
                            onCheckedChange = {
                                viewModel.setAppPrivacy(
                                    app.packageName,
                                    AppDatabase.PrivacyOptions.BLOCK_CONTENTS,
                                    it
                                )
                            }
                        )
                        Text(stringResource(Res.string.block_notification_contents))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = app.blockImages,
                            onCheckedChange = {
                                viewModel.setAppPrivacy(
                                    app.packageName,
                                    AppDatabase.PrivacyOptions.BLOCK_IMAGES,
                                    it
                                )
                            }
                        )
                        Text(stringResource(Res.string.block_notification_images))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialogForAppPackageName = null }) {
                    Text(stringResource(Res.string.ok))
                }
            }
        )
    }
}
