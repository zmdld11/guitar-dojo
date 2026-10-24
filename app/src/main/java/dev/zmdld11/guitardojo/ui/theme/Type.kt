package dev.zmdld11.guitardojo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val default = Typography()

internal val GuitarDojoTypography =
    Typography(
        displaySmall =
            default.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Default,
            ),
        headlineMedium = default.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = default.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = default.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = default.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = default.labelLarge.copy(letterSpacing = 0.4.sp),
    )
