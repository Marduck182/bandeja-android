package com.bandeja.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.bandeja.app.BandejaApp
import com.bandeja.app.appContext
import com.bandeja.app.domain.model.ThemeMode
import com.bandeja.app.newContent
import com.bandeja.app.ui.navigation.BandejaNavHost
import com.bandeja.app.ui.navigation.Routes
import com.bandeja.app.ui.theme.BandejaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Swipe a link to the left: a delete button appears; only pressing it deletes, and "Deshacer" brings the link back. */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h820dp-xxhdpi")
class SwipeDeleteUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val container get() = (appContext() as BandejaApp).container

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun shell() = rule.setContent {
        BandejaTheme(themeMode = ThemeMode.LIGHT) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                BandejaNavHost(startDestination = Routes.HOME, pendingContentId = null, onPendingContentHandled = {}, onOnboardingFinished = {})
            }
        }
    }

    private fun waitForText(text: String) =
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitUntilGone(text: String) =
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun snap(name: String) {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun seed(): Long = runBlocking {
        val food = container.categoryRepository.observeAll().first().first { it.name == "Comidas" }.id
        container.contentRepository.add(
            newContent(url = "https://example.com/uno", title = "Receta uno", categoryId = food, tags = listOf("pasta"), notes = "con albahaca", favorite = true),
        )
        container.contentRepository.add(newContent(url = "https://example.com/dos", title = "Receta dos", categoryId = food))
        container.contentRepository.observeRecent(10).first().first { it.content.title == "Receta uno" }.content.id
    }

    private fun openComidas() {
        shell()
        waitForText("Mis categorías")
        rule.onNodeWithText("Comidas").performClick()
        waitForText("Receta uno")
    }

    @Test
    fun `swiping a link left only reveals the delete button, pressing it deletes and offers undo`() {
        val id = seed()
        openComidas()

        rule.onNodeWithText("Receta uno").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodes(hasContentDescription("Eliminar")).fetchSemanticsNodes().isNotEmpty() }
        snap("90-swipe-revealed")
        // Nothing is deleted just by swiping.
        assertNotNull(runBlocking { container.contentRepository.observeItem(id).first() })
        rule.onNodeWithText("Receta uno").assertExistsCompat()

        rule.onNode(hasContentDescription("Eliminar")).performClick()
        waitForText("Enlace eliminado")
        waitUntilGone("Receta uno")
        snap("91-swipe-deleted-with-undo")

        assertNull(runBlocking { container.contentRepository.observeItem(id).first() })
        rule.onNodeWithText("Receta dos").assertExistsCompat()
    }

    @Test
    fun `undo puts the link back with its notes, tags, favorite and category`() {
        val id = seed()
        openComidas()

        rule.onNodeWithText("Receta uno").performTouchInput { swipeLeft() }
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodes(hasContentDescription("Eliminar")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasContentDescription("Eliminar")).performClick()
        waitForText("Enlace eliminado")
        rule.onNodeWithText("Deshacer").performClick()
        waitForText("Receta uno")

        val back = runBlocking { container.contentRepository.observeItem(id).first() }
        assertNotNull(back)
        assertEquals("Receta uno", back!!.content.title)
        assertEquals("con albahaca", back.content.description)
        assertEquals(listOf("pasta"), back.tags)
        assertEquals(true, back.content.isFavorite)
        assertEquals("Comidas", back.category?.name)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
