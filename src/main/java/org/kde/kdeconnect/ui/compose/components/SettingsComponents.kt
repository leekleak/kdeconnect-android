package org.kde.kdeconnect.ui.compose.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.kde.kdeconnect_tp.R
import kotlin.math.roundToInt

@Composable
fun Preference(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    controls: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .card()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(
                start = if (icon != null) 8.dp else 16.dp,
                end = 16.dp,
            )
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = icon,
                    contentDescription = null,
                )
            }
        } else {
            Box(modifier = Modifier.size(0.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                )
            }
        }
        if (controls != null) {
            Box(
                modifier = Modifier.padding(start = 24.dp)
            ) {
                controls()
            }
        }
    }
}

@Composable
fun NavigatePreference(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    showControl: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    Preference(
        modifier = modifier,
        title = title,
        summary = summary,
        icon = icon,
        onClick = {
            onClick()
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
        enabled = enabled,
        controls =
            if (showControl) {
                {
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward_ios),
                        contentDescription = null,
                    )
                }
            } else {
                null
            }
    )
}

@Composable
fun NavigatePreferenceIcon(
    modifier: Modifier = Modifier,
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .padding(vertical = 4.dp)
            .card()
            .clickable(enabled = enabled, onClick = {
                onClick()
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            })
            .padding(18.dp)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = icon,
            contentDescription = contentDescription,
        )
    }
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter? = null,
    summary: String? = null,
    value: Boolean,
    enabled: Boolean = true,
    onValueChanged: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    fun onClick(state: Boolean) {
        val feedback = if (state) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
        haptic.performHapticFeedback(feedback)
        onValueChanged(state)
    }
    Preference(
        modifier = modifier,
        title = title,
        icon = icon,
        summary = summary,
        enabled = enabled,
        onClick = {
            onClick(!value)
        },
        controls = {
            Switch(
                enabled = enabled, checked = value, onCheckedChange = {
                    onClick(it)
                },
            )
        },
    )
}

@Composable
fun SliderPreference(
    modifier: Modifier = Modifier,
    modifierLabelText: Modifier = Modifier,
    title: String,
    icon: Painter? = null,
    value: Long,
    values: List<Pair<Long, String?>>,
    enabled: Boolean = true,
    onValueChanged: (Long) -> Unit
) {
    SliderComponent(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .card()
            .padding(start = 8.dp, end = 16.dp, bottom = 4.dp)
            .alpha(if (enabled) 1f else 0.38f),
        modifierLabelText = modifierLabelText,
        title = title,
        icon = icon,
        value = value,
        values = values,
        enabled = enabled,
        onValueChanged = onValueChanged
    )
}

@Composable
fun DialogPreference(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    enabled: Boolean = true,
    content: @Composable (ColumnScope.(MutableState<Boolean>) -> Unit) = {}
) {
    val expanded = remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Preference(
        modifier = modifier,
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = {
            expanded.value = true
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        },
    )
    if (expanded.value) {
        Dialog(onDismissRequest = { expanded.value = false }) {
            Column(modifier = Modifier
                .card()
                .background(colorScheme.surface)
                .padding(16.dp)
            ) {
                content(expanded)
            }
        }
    }
}

@Composable
fun DialogTextPreference(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    value: String,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    filterInput: (String) -> String = { it },
    onValueChanged: (String) -> Unit
) {
    var newValue by remember { mutableStateOf(value) }
    val font = remember { googleSans(weight = 600f) }
    DialogPreference(
        modifier = modifier,
        title = title,
        summary = summary ?: value,
        icon = icon,
        enabled = enabled
    ) { expanded ->
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            fontFamily = font
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            value = newValue,
            onValueChange = {
                newValue = filterInput(it)
            },
            singleLine = singleLine,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    onValueChanged(newValue)
                    expanded.value = false
                },
                enabled = newValue.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
            TextButton(onClick = { expanded.value = false }) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
fun <T> DialogItemSelectPreference(
    modifier: Modifier = Modifier,
    title: String,
    summary: String? = null,
    icon: Painter? = null,
    value: T,
    values: List<Pair<T, String>>,
    enabled: Boolean = true,
    onValueChanged: (T) -> Unit
) {
    val font = remember { googleSans(weight = 600f) }
    val currentValueLabel = values.find { it.first == value }?.second ?: value.toString()
    val haptic = LocalHapticFeedback.current
    DialogPreference(
        modifier = modifier,
        title = title,
        summary = summary ?: currentValueLabel,
        icon = icon,
        enabled = enabled
    ) { expanded ->
        Text(
             text = title,
             style = MaterialTheme.typography.headlineSmallEmphasized,
             fontFamily = font
        )
        Column(
             modifier = Modifier
                 .padding(vertical = 12.dp)
                 .fillMaxWidth()
                 .weight(1f, fill = false)
                 .verticalScroll(rememberScrollState()),
             verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { (itemValue, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            onValueChanged(itemValue)
                            expanded.value = false
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        }
                        .background(if (itemValue == value) colorScheme.primary else colorScheme.surfaceContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                     Text(
                         text = label,
                         style = MaterialTheme.typography.bodyLargeEmphasized,
                         color = if (itemValue == value) colorScheme.onPrimary else colorScheme.onSurface,
                         fontWeight = FontWeight.Bold
                     )
                }
             }
         }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
             TextButton(onClick = { expanded.value = false }) {
                 Text(stringResource(R.string.cancel))
             }
        }
    }
}

@Composable
fun SliderComponent(
    modifier: Modifier,
    modifierLabelText: Modifier = Modifier,
    title: String,
    icon: Painter? = null,
    value: Long,
    values: List<Pair<Long, String?>>,
    enabled: Boolean = true,
    onValueChanged: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val fontFamilyBold = remember { googleSans(weight = 800f) }
    val currentIndex = remember(value, values) {
        values.indexOfFirst { it.first == value }.coerceAtLeast(0)
    }

    Column(modifier = modifier.alpha(if (enabled) 1f else 0.38f)) {
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let { Icon(
                modifier = Modifier.width(48.dp),
                painter = it,
                contentDescription = null,
            ) }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val interactionSource = remember { MutableInteractionSource() }
            Slider(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                value = currentIndex.toFloat(),
                onValueChange = {
                    val newIndex = it.roundToInt()
                    if (newIndex != currentIndex && newIndex in values.indices) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        onValueChanged(values[newIndex].first)
                    }
                },
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        thumbSize = DpSize(4.dp, 28.dp)
                    )
                },
                interactionSource = interactionSource,
                enabled = enabled,
                valueRange = 0f..((values.size - 1).coerceAtLeast(0).toFloat()),
                steps = (values.size - 2).coerceAtLeast(0)
            )
            val valueLabel = remember(currentIndex, values) {
                val pair = values.getOrNull(currentIndex)
                pair?.second ?: pair?.first?.toString() ?: ""
            }
            AnimatedContent(
                targetState = valueLabel,
                transitionSpec = {
                    (slideInVertically{ -it / 2 } + fadeIn()).togetherWith(
                        (slideOutVertically { it / 2 }) + fadeOut()
                    )
                }
            ) {
                Text(
                    modifier = modifierLabelText.padding(start = 16.dp),
                    text = it,
                    fontFamily = fontFamilyBold,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun IconPreference(
    title: String,
    painter: Painter,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .card()
            .clickable(enabled = enabled) { onClick.invoke() }
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Icon(
            modifier = Modifier.align(Alignment.Center),
            painter = painter,
            contentDescription = title
        )
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    icon: Painter,
    onClick: () -> Unit,
) {
    InfoCard(
        title = title,
        description = description,
        icon = icon,
        buttonIcon = painterResource(R.drawable.grant),
        buttonDescription = stringResource(R.string.grant),
        onClick = onClick,
    )
}

@Composable
fun InfoCard(
    title: String,
    description: String,
    icon: Painter,
    backgroundColor: Color = colorScheme.surfaceContainer,
    buttonIcon: Painter? = null,
    buttonDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row (
        modifier = Modifier
            .height(IntrinsicSize.Max)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .card()
                .background(backgroundColor)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, null)
                Text(modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, text = title)
            }
            Text(modifier = Modifier.fillMaxWidth(), text = description)
        }
        if (onClick != null && buttonIcon != null) {
            FilledIconButton(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(56.dp),
                shape = MaterialTheme.shapes.large,
                onClick = onClick,
            ) {
                Icon(
                    painter = buttonIcon,
                    contentDescription = buttonDescription,
                )
            }
        }
    }
}

@Composable
fun SettingsSearchBar(
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    placeholder: String = "",
    actionButton: @Composable (() -> Unit)
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .card()
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .onFocusChanged { isFocused = it.isFocused },
            textStyle = TextStyle(
                color = colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            state = state,
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colorScheme.onSurface),
            decorator = { innerTextField ->
                Box {
                    if (state.text.isEmpty() && !isFocused) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
        actionButton()
    }
}

@Composable
fun NotificationTogglePreference(
    modifier: Modifier = Modifier,
    title: String,
    icon: Painter,
    value: Boolean,
    onValueChanged: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val backgroundColor by animateColorAsState(if (value) colorScheme.primaryContainer else Color.Transparent)

    fun onClick(state: Boolean) {
        val feedback = if (state) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
        haptic.performHapticFeedback(feedback)
        onValueChanged(state)
    }
    Box(
        modifier = modifier
            .height(108.dp)
            .clip(MaterialTheme.shapes.large)
            .background(backgroundColor)
            .border(1.5.dp, colorScheme.primary, MaterialTheme.shapes.large)
            .clickable(onClick = { onClick(!value) }),
    ) {
        AnimatedVisibility(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopEnd),
            visible = value,
            enter = fadeIn(tween()) + scaleIn(),
            exit = fadeOut(tween()) + scaleOut()
        ) {
            Icon(
                painter = painterResource(R.drawable.check_circle),
                contentDescription = stringResource(R.string.enabled)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = icon,
                contentDescription = null,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
@Preview
fun NotificationTogglePreferencePreview() {
    NotificationTogglePreference(
        modifier = Modifier.width(150.dp),
        title = "Send",
        icon = painterResource(R.drawable.ic_arrow_upward_black_24dp),
        value = true
    ) { }
}
