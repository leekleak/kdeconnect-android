@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.kde.kdeconnect.ui.compose.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp


inline val Dp.px: Float
    @Composable get() = with(LocalDensity.current) { this@px.toPx() }

val roundedShapes = listOf(VerySunny, Sunny, Cookie6Sided, Cookie7Sided,
    Cookie9Sided, Cookie12Sided, Clover8Leaf, Burst, SoftBurst, Flower)

val angledShapes = listOf(Square, Slanted, Arch, Cookie4Sided, Clover4Leaf, PixelCircle)