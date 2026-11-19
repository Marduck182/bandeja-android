package com.bandeja.app.ui

import com.bandeja.app.FakeMetadataFetcher
import com.bandeja.app.FakeSettingsRepository
import com.bandeja.app.await
import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.repo.RoomCategoryRepository
import com.bandeja.app.data.repo.RoomContentRepository
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.share.ShareInput
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.domain.usecase.SaveContent
import com.bandeja.app.newContent
import com.bandeja.app.newInMemoryDatabase
import com.bandeja.app.ui.save.SaveStep
import com.bandeja.app.ui.save.SaveViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The main flow of the product, end to end below the UI:
 * Share → parse → preview → duplicate check → pick category → Save → stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveViewModelTest {
    private lateinit var db: BandejaDatabase
    private lateinit var contents: RoomContentRepository
    private lateinit var categories: RoomCategoryRepository
    private lateinit var settings: FakeSettingsRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var food = 0L
    private var code = 0L

    @Before
    fun setUp() = runBlocking {
        // viewModelScope runs on Main; without a looper-driven main thread it is replaced by an unconfined dispatcher.
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = newInMemoryDatabase()
        contents = RoomContentRepository(db)
        categories = RoomCategoryRepository(db.categoryDao())
        settings = FakeSettingsRepository()
        food = (categories.save(null, "Comidas", "🍔", "coral") as CategoryOutcome.Saved).id
        code = (categories.save(null, "Programación", "💻", "indigo") as CategoryOutcome.Saved).id
        Unit
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(fetcher: FakeMetadataFetcher = FakeMetadataFetcher()) =
        SaveViewModel(SaveContent(contents, appScope), categories, settings, fetcher, appScope)

    private fun share(text: String?, subject: String? = null) = ShareInput(text, subject, null)

    private val page = LinkMetadata("Receta de pasta | Cocina fácil", "https://img.example.com/pasta.jpg", ContentType.ARTICLE, null)

    // --- the happy path

    @Test
    fun `share a link, pick a category, save, and find it again`() = runBlocking {
        val vm = viewModel(FakeMetadataFetcher(page))
        vm.startFromShare(share("Mira esta receta https://www.example.com/pasta?utm_source=wa&fbclid=1", subject = null))

        val ready = vm.state.await { it.canSave && !it.isLoadingMetadata && it.categories.size == 2 }
        assertEquals(SaveStep.EDITING, ready.step)
        assertFalse("shared content is not editable as a url", ready.urlEditable)
        assertEquals("example.com", ready.source)
        assertEquals("Receta de pasta | Cocina fácil", ready.title) // the page's title replaced the hint
        assertEquals("https://img.example.com/pasta.jpg", ready.metadata!!.thumbnailUrl)
        assertTrue(ready.canSave)

        vm.onCategorySelected(food)
        vm.onNotesChange("para el domingo")
        vm.onTagsChange(listOf("Pasta", "domingo"))
        vm.onFavoriteChange(true)
        vm.save()

        val done = vm.state.await { it.step == SaveStep.SAVED }
        assertEquals("Comidas", done.savedCategory!!.name)

        val saved = contents.observeByCategory(food).first().single()
        assertEquals("https://example.com/pasta", saved.content.normalizedUrl)
        assertEquals("Receta de pasta | Cocina fácil", saved.content.title)
        assertEquals("https://img.example.com/pasta.jpg", saved.content.thumbnailUrl)
        assertEquals("para el domingo", saved.content.description)
        assertTrue(saved.content.isFavorite)
        assertEquals(listOf("domingo", "pasta"), saved.tags)
        assertEquals("the last used category is remembered for next time", food, settings.current.lastCategoryId)
    }

    @Test
    fun `the category used last time is preselected so saving is a single tap`() = runBlocking {
        settings.setLastCategoryId(code)
        val vm = viewModel()
        vm.startFromShare(share("https://github.com/JetBrains/kotlin"))
        val ready = vm.state.await { it.canSave && it.categories.isNotEmpty() }
        assertEquals(code, ready.categoryId)

        vm.save()
        vm.state.await { it.step == SaveStep.SAVED }
        assertEquals(1, contents.observeByCategory(code).first().size)
    }

    @Test
    fun `a category the user chose is not overridden by the preselection`() = runBlocking {
        settings.setLastCategoryId(code)
        val vm = viewModel()
        vm.onCategorySelected(food)
        vm.startFromShare(share("https://example.com/x"))
        vm.state.await { it.categories.isNotEmpty() }
        assertEquals(food, vm.state.value.categoryId)
    }

    // --- shared content that cannot be saved

    @Test
    fun `invalid shares end in an error state and store nothing`() = runBlocking {
        val cases = mapOf(
            null to InvalidUrlReason.EMPTY,
            "   " to InvalidUrlReason.EMPTY,
            "solo texto, sin enlace" to InvalidUrlReason.NO_URL,
            "javascript:alert(1)" to InvalidUrlReason.UNSUPPORTED_SCHEME,
            "file:///sdcard/x" to InvalidUrlReason.UNSUPPORTED_SCHEME,
            "http://user:pw@host.example.com" to InvalidUrlReason.MALFORMED,
        )
        cases.forEach { (text, reason) ->
            val vm = viewModel()
            vm.startFromShare(share(text))
            val state = vm.state.value
            assertEquals(text, SaveStep.INVALID, state.step)
            assertEquals(text, reason, state.invalidReason)
            assertFalse(state.canSave)
            vm.save() // must be a no-op
        }
        assertTrue(contents.observeRecent(10).first().isEmpty())
    }

    @Test
    fun `a share is processed once even if the screen is recreated`() = runBlocking {
        val fetcher = FakeMetadataFetcher(page)
        val vm = viewModel(fetcher)
        vm.startFromShare(share("https://example.com/a"))
        vm.startFromShare(share("https://example.com/b")) // e.g. rotation re-delivering the intent
        vm.state.await { it.isUrlValid && !it.isLoadingMetadata }
        assertEquals(listOf("https://example.com/a"), fetcher.requested)
    }

    // --- duplicates

    @Test
    fun `saving a link that already exists warns first and lets the user decide`() = runBlocking {
        val existing = contents.add(newContent(url = "https://example.com/post", title = "Ya guardado", categoryId = food))

        val vm = viewModel(FakeMetadataFetcher(page))
        vm.startFromShare(share("https://www.example.com/post/?utm_source=x#c"))

        val warned = vm.state.await { it.duplicate != null }
        assertEquals(existing, warned.duplicate!!.content.id)
        assertEquals("Comidas", warned.duplicate!!.category!!.name)
        assertFalse("saving is blocked until the user chooses", warned.canSave)
        vm.save()
        assertEquals(1, contents.observeRecent(10).first().size)

        vm.allowDuplicate()
        assertTrue(vm.state.value.canSave)
        vm.save()
        vm.state.await { it.step == SaveStep.SAVED }
        assertEquals(2, contents.observeRecent(10).first().size)
    }

    @Test
    fun `a new link shows no duplicate warning`() = runBlocking {
        contents.add(newContent(url = "https://example.com/post"))
        val vm = viewModel()
        vm.startFromShare(share("https://example.com/another"))
        vm.state.await { it.canSave && !it.isLoadingMetadata }
        assertNull(vm.state.value.duplicate)
        assertTrue(vm.state.value.canSave)
    }

    // --- network behaviour

    @Test
    fun `offline the link is still saved, with the domain as title`() = runBlocking {
        val vm = viewModel(FakeMetadataFetcher(result = null))
        vm.startFromShare(share("https://example.com/offline"))
        val ready = vm.state.await { it.canSave && !it.isLoadingMetadata }
        assertNull(ready.metadata)
        vm.save()
        vm.state.await { it.step == SaveStep.SAVED }
        assertEquals("example.com", contents.observeRecent(1).first().single().content.title)
    }

    @Test
    fun `saving while the preview is still loading does not wait, and the preview is applied later`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fetcher = FakeMetadataFetcher(page, gate)
        val vm = viewModel(fetcher)
        vm.startFromShare(share("https://slow.example.com/post"))
        val loading = vm.state.await { it.canSave }
        assertTrue(loading.isLoadingMetadata)
        assertTrue("the user can save without waiting", loading.canSave)

        vm.save()
        vm.state.await { it.step == SaveStep.SAVED }
        assertEquals("slow.example.com", contents.observeRecent(1).first().single().content.title)

        fetcher.release() // the sheet is long gone, but the app scope finishes the job
        val enriched = contents.observeRecent(1).await { it.single().content.title == page.title }.single()
        assertEquals("https://img.example.com/pasta.jpg", enriched.content.thumbnailUrl)
    }

    @Test
    fun `the user's own title is never replaced by the page's`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val vm = viewModel(FakeMetadataFetcher(page, gate))
        vm.startFromShare(share("https://example.com/t"))
        vm.state.await { it.isUrlValid }
        vm.onTitleChange("Mi título")
        gate.complete(Unit)
        vm.state.await { !it.isLoadingMetadata }
        assertEquals("Mi título", vm.state.value.title)
    }

    // --- manual save (pasted link)

    @Test
    fun `a pasted link is validated and only then can be saved`() = runBlocking {
        val vm = viewModel(FakeMetadataFetcher(page))
        vm.startManual()
        assertTrue(vm.state.value.urlEditable)
        assertFalse(vm.state.value.canSave)

        vm.onUrlChange("esto no es un enlace")
        val bad = vm.state.await { it.urlError != null }
        assertEquals(InvalidUrlReason.MALFORMED, bad.urlError)
        assertFalse(bad.canSave)

        vm.onUrlChange("https://example.com/manual")
        val good = vm.state.await { it.canSave && !it.isLoadingMetadata }
        assertTrue(good.canSave)

        vm.save()
        vm.state.await { it.step == SaveStep.SAVED }
        assertEquals("https://example.com/manual", contents.observeRecent(1).first().single().content.url)
    }

    @Test
    fun `changing the link discards the previous preview`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fetcher = FakeMetadataFetcher(page, gate)
        val vm = viewModel(fetcher)
        vm.startManual()
        vm.onUrlChange("https://first.example.com/a")
        vm.state.await { it.isUrlValid && it.isLoadingMetadata }
        vm.onUrlChange("") // user clears the field
        val cleared = vm.state.await { !it.isUrlValid }
        assertNull(cleared.metadata)
        assertFalse(cleared.isLoadingMetadata)
        gate.complete(Unit)
        // the late answer of the first link must not appear
        assertNull(vm.state.value.metadata)
    }

    @Test
    fun `startManual resets a previously used form`() = runBlocking {
        val vm = viewModel()
        vm.startManual()
        vm.onUrlChange("https://example.com/one")
        vm.state.await { it.isUrlValid }
        vm.onNotesChange("x")
        vm.startManual()
        val fresh = vm.state.value
        assertEquals("", fresh.url)
        assertEquals("", fresh.notes)
        assertFalse(fresh.isUrlValid)
        assertEquals(SaveStep.EDITING, fresh.step)
    }

    // --- categories inside the sheet

    @Test
    fun `a category created from the sheet is selected`() = runBlocking {
        val vm = viewModel()
        vm.startFromShare(share("https://example.com/x"))
        vm.state.await { it.categories.size == 2 }

        val outcome = vm.createCategory("Viajes", "✈️") as CategoryOutcome.Saved
        val state = vm.state.await { it.categories.size == 3 && it.categoryId == outcome.id }
        assertEquals("Viajes", state.categories.first { it.id == outcome.id }.name)

        assertEquals(CategoryOutcome.NameTaken, vm.createCategory("viajes", "✈️"))
        assertEquals(CategoryOutcome.NameBlank, vm.createCategory("  ", "✈️"))
    }

    @Test
    fun `if the selected category is deleted meanwhile the selection is cleared`() = runBlocking {
        val vm = viewModel()
        vm.startFromShare(share("https://example.com/x"))
        vm.state.await { it.categories.size == 2 }
        vm.onCategorySelected(code)

        categories.delete(code)

        val state = vm.state.await { it.categories.size == 1 }
        assertNull(state.categoryId)
        assertTrue(state.canSave)
    }
}
