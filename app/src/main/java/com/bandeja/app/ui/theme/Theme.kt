package com.bandeja.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bandeja.app.domain.model.ThemeMode

/** 4 dp grid. Every gap in the app is one of these. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal margin of every screen. */
    val screen = lg
}

val BandejaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** `true` when the dark scheme is active; components that need per-theme accents read this. */
val LocalIsDarkTheme = compositionLocalOf { false }

@Composable
fun BandejaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDark(themeMode)
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> BandejaDarkColors
        else -> BandejaLightColors
    }
    CompositionLocalProvider(LocalIsDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = colors,
            typography = BandejaTypography,
            shapes = BandejaShapes,
            content = content,
        )
    }
}

@Composable
@ReadOnlyComposable
fun CategoryAccent.container(): androidx.compose.ui.graphics.Color = container(LocalIsDarkTheme.current)

@Composable
@ReadOnlyComposable
fun CategoryAccent.onContainer(): androidx.compose.ui.graphics.Color = onContainer(LocalIsDarkTheme.current)
