@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.kde.kdeconnect.ui.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes.Companion.Arch
import androidx.compose.material3.MaterialShapes.Companion.Burst
import androidx.compose.material3.MaterialShapes.Companion.Clover4Leaf
import androidx.compose.material3.MaterialShapes.Companion.Clover8Leaf
import androidx.compose.material3.MaterialShapes.Companion.Cookie12Sided
import androidx.compose.material3.MaterialShapes.Companion.Cookie4Sided
import androidx.compose.material3.MaterialShapes.Companion.Cookie6Sided
import androidx.compose.material3.MaterialShapes.Companion.Cookie7Sided
import androidx.compose.material3.MaterialShapes.Companion.Cookie9Sided
import androidx.compose.material3.MaterialShapes.Companion.Flower
import androidx.compose.material3.MaterialShapes.Companion.PixelCircle
import androidx.compose.material3.MaterialShapes.Companion.Slanted
import androidx.compose.material3.MaterialShapes.Companion.SoftBurst
import androidx.compose.material3.MaterialShapes.Companion.Square
import androidx.compose.material3.MaterialShapes.Companion.Sunny
import androidx.compose.material3.MaterialShapes.Companion.VerySunny
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kde.kdeconnect_tp.R
import kotlin.math.PI
import kotlin.math.roundToInt


inline val Dp.px: Float
    @Composable get() = with(LocalDensity.current) { this@px.toPx() }

val roundedShapes = listOf(VerySunny, Sunny, Cookie6Sided, Cookie7Sided,
    Cookie9Sided, Cookie12Sided, Clover8Leaf, Burst, SoftBurst, Flower)

val angledShapes = listOf(Square, Slanted, Arch, Cookie4Sided, Clover4Leaf, PixelCircle)

fun DrawScope.smartDashBorder(
    cornerRadius: Float,
    dashLength: Float,
    width: Float,
    outlineColor: Color
) {
    val radius = cornerRadius.coerceAtMost(minOf(size.width, size.height) / 2f)

    val perimeter = 2 * (size.width + size.height) - (8 - 2 * PI.toFloat()) * radius

    val targetCycle = dashLength * 2f
    val cycleCount = (perimeter / targetCycle).roundToInt().coerceAtLeast(1)
    val actualCycle = perimeter / cycleCount
    val dash = actualCycle / 2f
    val gap = actualCycle / 2f

    val stroke = Stroke(
        width = width,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), 0f)
    )

    drawRoundRect(
        color = outlineColor,
        style = stroke,
        cornerRadius = CornerRadius(radius),
    )
}

@Composable
fun BoxScope.PairingExplanations(wifiAvailable: Boolean, trustedNetwork: Boolean) {
    if (!wifiAvailable) {
        BigPlaceholder(
            modifier = Modifier.align(Alignment.Center),
            painter = painterResource(R.drawable.wifi_off),
            text = stringResource(R.string.on_non_trusted_message),
        )
    } else if (!trustedNetwork) {
        BigPlaceholder(
            modifier = Modifier.align(Alignment.Center),
            painter = painterResource(R.drawable.verified_off),
            text = stringResource(R.string.on_non_trusted_message),
        )
    } else {
        BigPlaceholder(
            modifier = Modifier.align(Alignment.Center),
            painter = painterResource(R.drawable.sentiment_sad),
            text = stringResource(R.string.device_list_empty)
        )
    }
}

@Composable
fun BigPlaceholder(modifier: Modifier = Modifier, painter: Painter, text: String) {
    val font = remember { googleSans(weight = 600f, roundness = 100f) }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            modifier = Modifier.size(256.dp),
            painter = painter,
            contentDescription = null
        )
        Text(
            text = text,
            fontSize = 22.sp,
            fontFamily = font,
            textAlign = TextAlign.Center
        )
    }
}