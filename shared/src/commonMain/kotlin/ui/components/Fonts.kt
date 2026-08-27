package org.kde.kdeconnect.ui.components

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import org.jetbrains.compose.resources.Font
import org.kde.kdeconnect.generated.resources.Res
import org.kde.kdeconnect.generated.resources.google_sans_flex

@OptIn(ExperimentalTextApi::class)
@Composable
fun googleSans(
    @FloatRange(100.0, 1000.0) weight: Float = 400f,
    @FloatRange(0.0, 100.0) grade: Float = 0f,
    @FloatRange(-10.0, 0.0) slant: Float = 0f,
    @FloatRange(25.0, 151.0) width: Float = 100f,
    @FloatRange(0.0, 100.0) roundness: Float = 0f
): FontFamily {
    val font = Font(
        Res.font.google_sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.Setting("wght", weight),
            FontVariation.Setting("GRAD", grade),
            FontVariation.Setting("slnt", slant),
            FontVariation.Setting("wdth", width),
            FontVariation.Setting("ROND", roundness)
        )
    )
    return remember(font) { FontFamily(font) }
}