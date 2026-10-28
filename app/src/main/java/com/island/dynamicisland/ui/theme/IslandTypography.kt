package com.island.dynamicisland.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.R

/** Bundled Inter weights; every island surface uses the same family, including clocks. */
internal val IslandFontFamily = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun TextStyle.islandStyle() = copy(
    fontFamily = IslandFontFamily,
    fontFeatureSettings = "tnum",
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

internal val IslandTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.islandStyle(),
        displayMedium = base.displayMedium.islandStyle(),
        displaySmall = base.displaySmall.islandStyle(),
        headlineLarge = base.headlineLarge.islandStyle(),
        headlineMedium = base.headlineMedium.islandStyle(),
        headlineSmall = base.headlineSmall.islandStyle(),
        titleLarge = base.titleLarge.islandStyle(),
        titleMedium = base.titleMedium.islandStyle(),
        titleSmall = base.titleSmall.islandStyle(),
        bodyLarge = base.bodyLarge.islandStyle(),
        bodyMedium = base.bodyMedium.islandStyle(),
        bodySmall = base.bodySmall.islandStyle(),
        labelLarge = base.labelLarge.islandStyle(),
        labelMedium = base.labelMedium.islandStyle(),
        labelSmall = base.labelSmall.islandStyle(),
    )
}
