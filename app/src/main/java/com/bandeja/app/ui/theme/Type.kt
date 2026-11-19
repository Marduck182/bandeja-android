package com.bandeja.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bandeja.app.R

/** Plus Jakarta Sans, one variable font file bundled with the app (works offline, no Play Services). */
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val BandejaFontFamily = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = BandejaFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

/** display/headline 700 · title 600 · label 600 · body 400. Body text is never below 14 sp; 12 sp only for metadata. */
val BandejaTypography = Typography(
    displayLarge = style(48, 56, FontWeight.Bold, -0.02f),
    displayMedium = style(40, 48, FontWeight.Bold, -0.02f),
    displaySmall = style(32, 40, FontWeight.Bold, -0.015f),
    headlineLarge = style(30, 38, FontWeight.Bold, -0.015f),
    headlineMedium = style(26, 34, FontWeight.Bold, -0.01f),
    headlineSmall = style(22, 30, FontWeight.Bold, -0.005f),
    titleLarge = style(20, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, 0.005f),
    titleSmall = style(14, 20, FontWeight.SemiBold, 0.005f),
    bodyLarge = style(16, 24, FontWeight.Normal, 0.005f),
    bodyMedium = style(14, 20, FontWeight.Normal, 0.01f),
    bodySmall = style(12, 16, FontWeight.Normal, 0.015f),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.005f),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.02f),
    labelSmall = style(11, 16, FontWeight.Medium, 0.03f),
)
