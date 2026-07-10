package org.kde.kdeconnect.ui.compose.screen.settings.advanced.notifications

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import org.kde.kdeconnect.helpers.AppIcon
import org.kde.kdeconnect.plugins.notifications.AppDatabase
import org.kde.kdeconnect.ui.compose.components.AppSelector
import org.kde.kdeconnect.ui.compose.components.CategoryTitleTextSmall
import org.kde.kdeconnect.ui.compose.components.HazeScaffold
import org.kde.kdeconnect.ui.compose.components.Preference
import org.kde.kdeconnect.ui.compose.components.SearchField
import org.kde.kdeconnect.ui.compose.components.SwitchPreference
import org.kde.kdeconnect.ui.compose.components.card
import org.kde.kdeconnect_tp.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationSettings(
    viewModel: NotificationSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HazeScaffold(
        title = stringResource(R.string.notifications),
        backButton = true,
    ) {
        CategoryTitleTextSmall(stringResource(R.string.synchronization))
        SwitchPreference(
            title = stringResource(R.string.show_notification_if_screen_off),
            icon = painterResource(R.drawable.bedtime),
            value = uiState.screenOffNotification,
            onValueChanged = viewModel::setScreenOffNotification
        )
        NotificationBlacklistComponent(viewModel, uiState)

        CategoryTitleTextSmall(stringResource(R.string.media_controls))
        SwitchPreference(
            title = stringResource(R.string.mpris_notification_settings_title),
            summary = stringResource(R.string.mpris_notification_settings_summary),
            icon = painterResource(R.drawable.pinboard_unread),
            value = uiState.notificationEnabled,
            onValueChanged = viewModel::setNotificationEnabled
        )

        SwitchPreference(
            title = stringResource(R.string.mpris_keepwatching_settings_title),
            summary = stringResource(R.string.mpris_keepwatching_settings_summary),
            icon = painterResource(R.drawable.replay),
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
    val showPrivacyDialogForApp = uiState.enabledApps.find { it.packageName == showPrivacyDialogForAppPackageName }
        ?: uiState.disabledApps.find { it.packageName == showPrivacyDialogForAppPackageName }

    LaunchedEffect(textFieldState.text) {
        viewModel.setSearchQuery(textFieldState.text.toString())
    }

    Column(Modifier.card()) {
        Preference(
            title = stringResource(R.string.blacklisted_apps),
            icon = painterResource(R.drawable.block),
            onClick = {
                addApps = !addApps
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            },
            controls = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-16).dp)) {
                        uiState.enabledApps.take(3).forEach { app ->
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
                        painter = painterResource(R.drawable.arrow_forward_ios),
                        contentDescription = stringResource(R.string.expand)
                    )
                }
            }
        )

        AnimatedVisibility(visible = addApps) {
            Column {
                AppSelector(
                    apps = uiState.enabledApps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    onLongClick = { packageName ->
                        showPrivacyDialogForAppPackageName = packageName
                    }
                ) { packageName ->
                    viewModel.setAppEnabled(packageName, false)
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
                                painter = painterResource(R.drawable.arrow_upward),
                                contentDescription = null
                            )
                        }
                        FilledIconButton(onClick = {
                            viewModel.setAllEnabled(false)
                            haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_downward),
                                contentDescription = null
                            )
                        }
                    }
                }
                AppSelector(
                    apps = uiState.disabledApps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) { packageName ->
                    viewModel.setAppEnabled(packageName, true)
                }
                SearchField(textFieldState)
            }
        }
    }

    showPrivacyDialogForApp?.let { app ->
        AlertDialog(
            onDismissRequest = { showPrivacyDialogForAppPackageName = null },
            title = { Text(stringResource(R.string.privacy_options) + ": " + app.name) },
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
                        Text(stringResource(R.string.block_notification_contents))
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
                        Text(stringResource(R.string.block_notification_images))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialogForAppPackageName = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
