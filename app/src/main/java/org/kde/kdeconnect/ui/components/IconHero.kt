package org.kde.kdeconnect.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IconHero(
    modifier: Modifier = Modifier,
    backgroundSize: Dp,
    iconSize: Dp,
    @DrawableRes icon: Int
) {
    val backgroundColor = colorScheme.primary
    val backgroundShape = roundedShapes.random().toPath()
    val backgroundSizePx = backgroundSize.px
    val backgroundShapeTransformed = remember(backgroundSizePx) {
        val matrix = Matrix().apply { scale(backgroundSizePx, backgroundSizePx) }
        backgroundShape.copy().apply { transform(matrix) }
    }
    Box(
        modifier = modifier
            .size(backgroundSize)
            .drawBehind {
                drawPath(backgroundShapeTransformed, backgroundColor)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(iconSize),
            painter = painterResource(icon),
            contentDescription = null,
            tint = colorScheme.onPrimary
        )
    }
}