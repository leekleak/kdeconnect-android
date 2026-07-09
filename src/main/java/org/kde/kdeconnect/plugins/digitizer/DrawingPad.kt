package org.kde.kdeconnect.plugins.digitizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingPad(
    modifier: Modifier = Modifier,
    fingerTouchEventsEnabled: Boolean = false,
    onToolEvent: (ToolEvent) -> Unit,
    onFingerTouchEvent: (Boolean) -> Unit,
    onSizeChanged: (width: Int, height: Int, xdpi: Float, ydpi: Float) -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainer
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        
        LaunchedEffect(width, height) {
            val metrics = android.content.res.Resources.getSystem().displayMetrics
            onSizeChanged(width, height, metrics.xdpi, metrics.ydpi)
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(fingerTouchEventsEnabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        
                        val tool = when (change.type) {
                            PointerType.Eraser -> ToolEvent.Tool.Rubber
                            else -> ToolEvent.Tool.Pen
                        }

                        val isFinger = change.type == PointerType.Touch
                        
                        val pressure = if (isFinger) {
                            if (fingerTouchEventsEnabled) 1.0 else 0.0
                        } else {
                            change.pressure.toDouble()
                        }

                        when (event.type) {
                            PointerEventType.Press -> {
                                onToolEvent(ToolEvent(
                                    active = true,
                                    touching = !isFinger,
                                    tool = tool,
                                    x = change.position.x.roundToInt(),
                                    y = change.position.y.roundToInt(),
                                    pressure = pressure
                                ))
                                if (isFinger) onFingerTouchEvent(true)
                            }
                            PointerEventType.Release -> {
                                onToolEvent(ToolEvent(
                                    active = !isFinger,
                                    touching = false,
                                ))
                                if (isFinger) onFingerTouchEvent(false)
                            }
                            PointerEventType.Move -> {
                                onToolEvent(ToolEvent(
                                    tool = tool,
                                    x = change.position.x.roundToInt(),
                                    y = change.position.y.roundToInt(),
                                    pressure = pressure
                                ))
                            }
                            PointerEventType.Enter -> {
                                onToolEvent(ToolEvent(
                                    active = true,
                                    x = change.position.x.roundToInt(),
                                    y = change.position.y.roundToInt()
                                ))
                            }
                            PointerEventType.Exit -> {
                                onToolEvent(ToolEvent(
                                    active = false
                                ))
                            }
                        }
                    }
                }
            }
        ) {
            drawRect(color = backgroundColor)

            val spacing = 5.0f / 25.4f * drawContext.density.density * 160f
            val offset = spacing / 2

            var x = offset
            while (x < size.width) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += spacing
            }

            var y = offset
            while (y < size.height) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += spacing
            }
        }
    }
}
