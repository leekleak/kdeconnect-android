package org.kde.kdeconnect.ui.compose.components

import androidx.annotation.FloatRange
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import org.kde.kdeconnect_tp.R

@OptIn(ExperimentalTextApi::class)
fun googleSans(
    @FloatRange(100.0, 1000.0) weight: Float = 400f,
    @FloatRange(0.0, 100.0) grade: Float = 0f,
    @FloatRange(-10.0, 0.0) slant: Float = 0f,
    @FloatRange(25.0, 151.0) width: Float = 100f,
    @FloatRange(0.0, 100.0) roundness: Float = 0f
): FontFamily {
    return FontFamily(
            Font(
                R.font.google_sans_flex,
                variationSettings = FontVariation.Settings(
                    FontVariation.Setting("wght", weight),
                    FontVariation.Setting("GRAD", grade),
                    FontVariation.Setting("slnt", slant),
                    FontVariation.Setting("wdth", width),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
        )
}