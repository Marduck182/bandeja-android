package com.bandeja.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.bandeja.app.BandejaApp
import com.bandeja.app.appContext
import com.bandeja.app.domain.model.ThemeMode
import com.bandeja.app.newContent
import com.bandeja.app.ui.navigation.BandejaNavHost
import com.bandeja.app.ui.navigation.Routes
import com.bandeja.app.ui.theme.BandejaTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Deleting a category through the real screens: every way to reach it, and what happens to its content. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h820dp-xxhdpi")
class CategoryDeletionUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val container get() = (appContext() as BandejaApp).container

    private fun shell() = rule.setContent {
        BandejaTheme(themeMode = ThemeMode.LIGHT) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                BandejaNavHost(startDestination = Routes.HOME, pendingContentId = null, onPendingContentHandled = {}, onOnboardingFinished = {})
            }
        }
    }

    private fun waitForText(text: String) {
        try {
            rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            snap("fail-" + text.filter { it.isLetterOrDigit() })
            println("WAITING FOR '$text' FAILED. Tree: " + rule.onRoot().printToString(maxDepth = 40).lines().filter { "Text =" in it || "ContentDescription" in it }.joinToString(" | ").replace("  ", " "))
            throw e
        }
    }

    private fun waitUntilGone(text: String) =
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun snap(name: String) {
        rule.waitForIdle()
        shadowOf().idle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shadowOf() = org.robolectric.Shadows.shadowOf(Looper.getMainLooper())

    private fun categories() = runBlocking { container.categoryRepository.observeAll().first() }
    private fun idOf(name: String) = categories().first { it.name == name }.id

    private fun seedInto(name: String) = runBlocking {
        container.contentRepository.add(newContent(url = "https://example.com/$name", title = "Guardado en $name", categoryId = idOf(name)))
    }

    @Test
    fun `long press on Home offers delete, confirms, and keeps the content`() {
        val itemId = seedInto("Comidas")
        shell()
        waitForText("Comidas")

        rule.onNodeWithText("Comidas").performTouchInput { longClick() }
        waitForText("Eliminar")
        snap("80-category-long-press-menu")

        rule.onNodeWithText("Eliminar").performClick()
        waitForText("¿Eliminar «Comidas»?")
        snap("81-category-delete-confirm")
        rule.onNodeWithText("Sus 1 elemento".takeIf { false } ?: "Su elemento no se borra: pasará a «Sin categoría».").assertExistsCompat()

        rule.onNode(hasText("Eliminar") and androidx.compose.ui.test.hasClickAction()).performClick()
        waitUntilGone("¿Eliminar «Comidas»?")

        assertNull(categories().firstOrNull { it.name == "Comidas" })
        val item = runBlocking { container.contentRepository.observeItem(itemId).first() }
        assertNotNull("the content must survive", item)
        assertNull(item!!.content.categoryId)
        assertEquals(6, categories().size)
        // Home now lists it under "Sin categoría".
        waitForText("Sin categoría")
        snap("82-home-after-delete")
    }

    @Test
    fun `cancelling the confirmation deletes nothing`() {
        shell()
        waitForText("Viajes")
        rule.onNodeWithText("Viajes").performTouchInput { longClick() }
        waitForText("Eliminar")
        rule.onNodeWithText("Eliminar").performClick()
        waitForText("¿Eliminar «Viajes»?")
        rule.onNodeWithText("Cancelar").performClick()
        waitUntilGone("¿Eliminar «Viajes»?")
        assertNotNull(categories().firstOrNull { it.name == "Viajes" })
    }

    @Test
    fun `delete button inside the category editor`() {
        shell()
        waitForText("Ideas")
        rule.onNodeWithText("Ideas").performTouchInput { longClick() }
        waitForText("Editar")
        rule.onNodeWithText("Editar").performClick()
        waitForText("Eliminar categoría")
        snap("83-editor-with-delete")

        // The button at the end of the form (the top bar icon is covered by the next test).
        rule.onNodeWithText("Eliminar categoría").performScrollTo().performClick()
        waitForText("¿Eliminar «Ideas»?")
        rule.onNode(hasText("Eliminar") and androidx.compose.ui.test.hasClickAction()).performClick()

        waitForText("Mis categorías") // back on Home
        assertNull(categories().firstOrNull { it.name == "Ideas" })
    }

    @Test
    fun `trash icon in the editor top bar`() {
        shell()
        waitForText("Aprendizaje")
        rule.onNodeWithText("Aprendizaje").performTouchInput { longClick() }
        waitForText("Editar")
        rule.onNodeWithText("Editar").performClick()
        waitForText("Eliminar categoría")
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Eliminar categoría")).performClick()
        waitForText("¿Eliminar «Aprendizaje»?")
        rule.onNode(hasText("Eliminar") and androidx.compose.ui.test.hasClickAction()).performClick()
        waitForText("Mis categorías")
        assertNull(categories().firstOrNull { it.name == "Aprendizaje" })
    }

    @Test
    fun `the old menu inside a category still works`() {
        shell()
        waitForText("Videojuegos")
        rule.onNodeWithText("Videojuegos").performClick()
        waitForText("Aquí aún no hay nada")
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Más opciones")).performClick()
        waitForText("Eliminar")
        rule.onNodeWithText("Eliminar").performClick()
        waitForText("¿Eliminar «Videojuegos»?")
        rule.onNode(hasText("Eliminar") and androidx.compose.ui.test.hasClickAction()).performClick()
        waitForText("Mis categorías")
        assertNull(categories().firstOrNull { it.name == "Videojuegos" })
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
