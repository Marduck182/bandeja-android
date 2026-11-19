package com.bandeja.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.bandeja.app.data.local.DefaultCategories
import com.bandeja.app.ui.components.CategoryEmojis
import com.bandeja.app.ui.theme.BandejaDarkColors
import com.bandeja.app.ui.theme.BandejaLightColors
import com.bandeja.app.ui.theme.CategoryAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA (4.5:1 for text) on every pair of colors the design system pairs together. */
class ThemeContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(name: String, foreground: Color, background: Color, minimum: Double = 4.5) {
        val r = ratio(foreground, background)
        assertTrue("$name contrast is %.2f, needs >= $minimum".format(r), r >= minimum)
    }

    private fun scheme(name: String, c: ColorScheme) {
        check("$name onBackground/background", c.onBackground, c.background)
        check("$name onSurface/surface", c.onSurface, c.surface)
        check("$name onSurface/surfaceContainerLow", c.onSurface, c.surfaceContainerLow)
        check("$name onSurface/surfaceContainerHigh", c.onSurface, c.surfaceContainerHigh)
        check("$name onSurfaceVariant/surface", c.onSurfaceVariant, c.surface)
        check("$name onSurfaceVariant/surfaceContainerLow", c.onSurfaceVariant, c.surfaceContainerLow)
        check("$name onSurfaceVariant/surfaceContainerHigh", c.onSurfaceVariant, c.surfaceContainerHigh)
        check("$name onPrimary/primary", c.onPrimary, c.primary)
        check("$name onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer)
        check("$name primary/surface (links, active)", c.primary, c.surface)
        check("$name onSecondaryContainer/secondaryContainer", c.onSecondaryContainer, c.secondaryContainer)
        check("$name onTertiaryContainer/tertiaryContainer", c.onTertiaryContainer, c.tertiaryContainer)
        check("$name tertiary/surface (favorite star)", c.tertiary, c.surface, minimum = 3.0) // graphic, 3:1
        check("$name onError/error", c.onError, c.error)
        check("$name onErrorContainer/errorContainer", c.onErrorContainer, c.errorContainer)
        check("$name error/surface", c.error, c.surface)
        check("$name inverseOnSurface/inverseSurface", c.inverseOnSurface, c.inverseSurface)
        check("$name outline/surface (field borders)", c.outline, c.surface, minimum = 3.0)
    }

    @Test fun `light scheme meets AA`() = scheme("light", BandejaLightColors)

    @Test fun `dark scheme meets AA`() = scheme("dark", BandejaDarkColors)

    @Test
    fun `every category accent meets AA in both themes`() {
        CategoryAccent.entries.forEach { a ->
            check("${a.key} light", a.lightOn, a.lightContainer)
            check("${a.key} dark", a.darkOn, a.darkContainer)
        }
    }

    @Test
    fun `accent keys are unique and unknown keys fall back safely`() {
        assertEquals(CategoryAccent.entries.size, CategoryAccent.entries.map { it.key }.toSet().size)
        assertEquals(CategoryAccent.Slate, CategoryAccent.fromKey("from-a-future-version"))
        assertEquals(CategoryAccent.Coral, CategoryAccent.forIndex(CategoryAccent.entries.size)) // cycles
        assertEquals(CategoryAccent.Slate, CategoryAccent.forIndex(-1))
    }

    @Test
    fun `starter categories use real accents and pickable emoji`() {
        DefaultCategories.all.forEach {
            assertEquals(it.color, CategoryAccent.fromKey(it.color).key)
            assertTrue("${it.icon} must be in the picker so it shows as selected when editing", it.icon in CategoryEmojis)
        }
        assertEquals("emoji must be unique", CategoryEmojis.size, CategoryEmojis.toSet().size)
    }
}
