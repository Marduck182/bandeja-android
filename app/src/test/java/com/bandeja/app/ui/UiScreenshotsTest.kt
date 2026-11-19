package com.bandeja.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bandeja.app.BandejaApp
import com.bandeja.app.FakeMetadataFetcher
import com.bandeja.app.appContext
import com.bandeja.app.domain.model.Category
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.model.ThemeMode
import com.bandeja.app.domain.share.ShareInput
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.newContent
import com.bandeja.app.ui.editor.CategoryEditorScreen
import com.bandeja.app.ui.editor.CategoryEditorViewModel
import com.bandeja.app.ui.navigation.BandejaNavHost
import com.bandeja.app.ui.navigation.Routes
import com.bandeja.app.ui.onboarding.OnboardingScreen
import com.bandeja.app.ui.save.EditingContent
import com.bandeja.app.ui.save.InvalidContent
import com.bandeja.app.ui.save.SaveStep
import com.bandeja.app.ui.save.SaveUiState
import com.bandeja.app.ui.save.SavedContent
import com.bandeja.app.ui.save.SaveViewModel
import com.bandeja.app.ui.theme.BandejaTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the real screens, on the real navigation graph and a real database, and saves each one as a PNG in
 * `app/build/screenshots`. It is both a smoke test (every screen composes and navigates without crashing)
 * and the way the UI is reviewed when no device is at hand.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h820dp-xxhdpi")
class UiScreenshotsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val container get() = (appContext() as BandejaApp).container
    private lateinit var categories: List<Category>

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        categories = runBlocking { container.categoryRepository.observeAll().first() }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    // ---------------------------------------------------------------- data

    private fun category(name: String) = categories.first { it.name == name }

    private fun seed() = runBlocking {
        val repo = container.contentRepository
        fun add(title: String, url: String, source: String, type: ContentType, cat: String?, fav: Boolean = false, tags: List<String> = emptyList(), notes: String = "") =
            runBlocking {
                repo.add(newContent(url = url, title = title, source = source, type = type, categoryId = cat?.let { category(it).id }, favorite = fav, tags = tags, notes = notes))
            }
        add("Pasta carbonara auténtica en 20 minutos", "https://www.youtube.com/watch?v=pasta0000001", "youtube.com", ContentType.VIDEO, "Comidas", tags = listOf("pasta", "rápido"), notes = "Usar guanciale, no tocino. Huevos a temperatura ambiente.")
        add("Los 10 mejores tacos de la Ciudad de México", "https://www.eater.com/tacos-cdmx", "eater.com", ContentType.ARTICLE, "Comidas")
        add("Receta de pan de masa madre para principiantes", "https://www.youtube.com/watch?v=pan00000002", "youtube.com", ContentType.VIDEO, "Comidas")
        add("Jetpack Compose: la guía completa", "https://developer.android.com/compose", "developer.android.com", ContentType.ARTICLE, "Programación", tags = listOf("android", "ui"))
        add("Kotlin/kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines", "github.com", ContentType.CODE, "Programación", fav = true)
        add("Rutina de fuerza de 30 minutos sin equipo", "https://www.youtube.com/watch?v=fuerza000003", "youtube.com", ContentType.VIDEO, "Ejercicios", fav = true)
        add("Qué hacer en Oaxaca en 4 días", "https://www.instagram.com/p/oaxaca1234", "instagram.com", ContentType.SOCIAL, "Viajes")
        add("Idea: una app para organizar mis recetas", "https://www.reddit.com/r/SideProject/comments/1", "reddit.com", ContentType.SOCIAL, null)
    }

    // ---------------------------------------------------------------- helpers

    private fun snap(name: String) {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        // captureToImage waits for a frame the paused Robolectric looper never delivers, so the window is drawn directly.
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("screenshot $name is empty", bitmap.width > 0 && bitmap.height > 0)
    }

    private fun waitForText(text: String) =
        rule.waitUntil(timeoutMillis = 10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun themed(dark: Boolean = false, content: @Composable () -> Unit) = rule.setContent {
        BandejaTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { content() }
        }
    }

    private fun shell(dark: Boolean = false) = themed(dark) {
        BandejaNavHost(startDestination = Routes.HOME, pendingContentId = null, onPendingContentHandled = {}, onOnboardingFinished = {})
    }

    private fun tab(label: String): SemanticsNodeInteraction = rule.onNode(hasText(label) and hasClickAction())

    /** The sheet is drawn as a window by the real app; here it is framed by hand so it fits a screenshot. */
    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.BottomCenter) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0.dp), bottomEnd = androidx.compose.foundation.shape.CornerSize(0.dp)),
                modifier = Modifier.fillMaxWidth(),
            ) { Box(Modifier.padding(top = 16.dp)) { content() } }
        }
    }

    private fun saveViewModel(fetcher: FakeMetadataFetcher = FakeMetadataFetcher()) = container.let {
        SaveViewModel(it.saveContent, it.categoryRepository, it.settingsRepository, fetcher, it.appScope)
    }

    // ---------------------------------------------------------------- onboarding

    @Test
    fun onboarding() {
        themed { OnboardingScreen(onFinished = {}) }
        waitForText("Guarda lo que encuentres")
        snap("01-onboarding-1")
        rule.onNodeWithText("Siguiente").performClick()
        waitForText("Compartir → Bandeja")
        snap("02-onboarding-2")
        rule.onNodeWithText("Siguiente").performClick()
        waitForText("Tus categorías, tus reglas")
        snap("03-onboarding-3")
    }

    // ---------------------------------------------------------------- home

    @Test
    fun homeEmpty() {
        shell()
        waitForText("Tu bandeja está vacía")
        snap("10-home-empty-light")
    }

    @Test
    fun homePopulatedLight() {
        seed()
        shell()
        waitForText("Idea: una app para organizar mis recetas")
        snap("11-home-light")
    }

    @Test
    fun homePopulatedDark() {
        seed()
        shell(dark = true)
        waitForText("Idea: una app para organizar mis recetas")
        snap("12-home-dark")
    }

    @Config(sdk = [34], qualifiers = "w392dp-h820dp-xxhdpi", fontScale = 1.6f)
    @Test
    fun homeLargeFont() {
        seed()
        shell()
        waitForText("Mis categorías")
        snap("13-home-font-1.6")
    }

    // ---------------------------------------------------------------- navigation: category, detail

    @Test
    fun categoryAndDetail() {
        seed()
        shell()
        waitForText("Mis categorías")
        rule.onNodeWithText("Comidas").performClick()
        waitForText("Receta de pan de masa madre para principiantes")
        snap("20-category-light")

        rule.onNodeWithText("Pasta carbonara auténtica en 20 minutos").performClick()
        waitForText("Abrir original")
        snap("21-detail-light")
    }

    @Test
    fun detailDark() {
        seed()
        shell(dark = true)
        waitForText("Mis categorías")
        rule.onNodeWithText("Comidas").performClick()
        waitForText("Receta de pan de masa madre para principiantes")
        rule.onNodeWithText("Pasta carbonara auténtica en 20 minutos").performClick()
        waitForText("Abrir original")
        snap("22-detail-dark")
    }

    @Test
    fun categoryEmpty() {
        shell()
        waitForText("Mis categorías")
        rule.onNodeWithText("Viajes").performClick()
        waitForText("Aquí aún no hay nada")
        snap("23-category-empty")
    }

    // ---------------------------------------------------------------- tabs

    @Test
    fun searchStates() {
        seed()
        shell()
        waitForText("Mis categorías")
        tab("Buscar").performClick()
        waitForText("Encuentra lo que guardaste")
        snap("30-search-idle")

        rule.onNode(hasSetTextAction()).performTextInput("receta")
        waitForText("Receta de pan de masa madre para principiantes")
        snap("31-search-results")

        rule.onNode(hasSetTextAction()).performTextInput(" zzzz")
        waitForText("Sin resultados")
        snap("32-search-empty")
    }

    @Test
    fun favorites() {
        seed()
        shell()
        waitForText("Mis categorías")
        tab("Favoritos").performClick()
        waitForText("Rutina de fuerza de 30 minutos sin equipo")
        snap("40-favorites")
    }

    @Test
    fun favoritesEmpty() {
        shell()
        waitForText("Mis categorías")
        tab("Favoritos").performClick()
        waitForText("Aún no tienes favoritos")
        snap("41-favorites-empty")
    }

    @Test
    fun settings() {
        shell()
        waitForText("Mis categorías")
        tab("Ajustes").performClick()
        waitForText("Colores dinámicos")
        snap("50-settings")
    }

    // ---------------------------------------------------------------- category editor

    @Test
    fun categoryEditor() {
        val vm = CategoryEditorViewModel(null, container.categoryRepository)
        themed { CategoryEditorScreen(categoryId = null, onBack = {}, onDeleted = {}, viewModel = vm) }
        waitForText("Nombre")
        rule.onNode(hasSetTextAction()).performTextInput("Música")
        snap("60-category-editor")
    }

    @Test
    fun categoryEditorDark() {
        val vm = CategoryEditorViewModel(category("Ideas").id, container.categoryRepository)
        themed(dark = true) { CategoryEditorScreen(categoryId = category("Ideas").id, onBack = {}, onDeleted = {}, viewModel = vm) }
        waitForText("Editar categoría")
        snap("61-category-editor-dark")
    }

    // ---------------------------------------------------------------- save sheet (the main flow)

    @Test
    fun saveSheetFromShare() {
        val page = LinkMetadata("Pasta carbonara auténtica en 20 minutos", null, ContentType.VIDEO, null)
        val vm = saveViewModel(FakeMetadataFetcher(page))
        vm.startFromShare(ShareInput("https://youtu.be/dQw4w9WgXcQ?si=abc", null, null))
        themed {
            val state by vm.state.collectAsStateWithLifecycle()
            SheetFrame { EditingContent(state, vm, onOpenExisting = {}) }
        }
        waitForText("Pasta carbonara auténtica en 20 minutos")
        snap("70-save-sheet")
    }

    @Test
    fun saveSheetDark() {
        val page = LinkMetadata("Los 10 mejores tacos de la Ciudad de México", null, ContentType.ARTICLE, null)
        val vm = saveViewModel(FakeMetadataFetcher(page))
        vm.startFromShare(ShareInput("https://www.eater.com/tacos-cdmx", null, null))
        themed(dark = true) {
            val state by vm.state.collectAsStateWithLifecycle()
            SheetFrame { EditingContent(state, vm, onOpenExisting = {}) }
        }
        waitForText("Los 10 mejores tacos de la Ciudad de México")
        snap("71-save-sheet-dark")
    }

    @Test
    fun saveSheetLoadingPreview() {
        val vm = saveViewModel(FakeMetadataFetcher(null, kotlinx.coroutines.CompletableDeferred()))
        vm.startFromShare(ShareInput("https://slow.example.com/articulo", null, null))
        themed {
            val state by vm.state.collectAsStateWithLifecycle()
            SheetFrame { EditingContent(state, vm, onOpenExisting = {}) }
        }
        waitForText("Buscando vista previa…")
        snap("72-save-sheet-loading")
    }

    @Test
    fun saveSheetDuplicate() {
        seed()
        val vm = saveViewModel(FakeMetadataFetcher(LinkMetadata("Pasta carbonara auténtica en 20 minutos", null, ContentType.VIDEO, null)))
        vm.startFromShare(ShareInput("https://youtu.be/pasta0000001", null, null))
        themed {
            val state by vm.state.collectAsStateWithLifecycle()
            SheetFrame { EditingContent(state, vm, onOpenExisting = {}) }
        }
        waitForText("Ya está guardado")
        snap("73-save-sheet-duplicate")
    }

    @Test
    fun saveSheetManual() {
        val vm = saveViewModel()
        vm.startManual()
        vm.onUrlChange("esto no es un enlace")
        themed {
            val state by vm.state.collectAsStateWithLifecycle()
            SheetFrame { EditingContent(state, vm, onOpenExisting = {}) }
        }
        waitForText("El enlace no es válido.")
        snap("74-save-sheet-manual-error")
    }

    @Test
    fun saveSheetSavedAndError() {
        themed {
            SheetFrame { SavedContent(SaveUiState(step = SaveStep.SAVED, savedCategory = category("Comidas")), onDone = {}) }
        }
        waitForText("Guardado")
        snap("75-save-sheet-saved")
    }

    @Test
    fun saveSheetInvalidShare() {
        themed {
            SheetFrame { InvalidContent(InvalidUrlReason.NO_URL, onClose = {}) }
        }
        waitForText("No se pudo guardar")
        snap("76-save-sheet-invalid")
    }
}
