package com.bandeja.app.data

import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.repo.RoomCategoryRepository
import com.bandeja.app.data.repo.RoomContentRepository
import com.bandeja.app.domain.model.CategoryFilter
import com.bandeja.app.domain.model.DatePeriod
import com.bandeja.app.domain.model.SearchFilters
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.newContent
import com.bandeja.app.newInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchRepositoryTest {
    private lateinit var db: BandejaDatabase
    private lateinit var contents: RoomContentRepository
    private lateinit var categories: RoomCategoryRepository
    private val day = 86_400_000L
    private var now = 400 * day
    private var food = 0L
    private var code = 0L

    @Before
    fun setUp() = runBlocking {
        db = newInMemoryDatabase()
        contents = RoomContentRepository(db) { now }
        categories = RoomCategoryRepository(db.categoryDao()) { now }
        food = (categories.save(null, "Comidas", "🍔", "coral") as CategoryOutcome.Saved).id
        code = (categories.save(null, "Programación", "💻", "indigo") as CategoryOutcome.Saved).id

        add("https://www.youtube.com/watch?v=pasta0000001", "Receta de pasta casera", "youtube.com", food, listOf("italiana", "rápido"), notes = "con albahaca fresca", daysAgo = 1)
        add("https://www.youtube.com/watch?v=pollo0000002", "Pollo al horno", "youtube.com", food, listOf("rápido"), favorite = true, daysAgo = 10)
        add("https://github.com/JetBrains/kotlin", "Kotlin repository", "github.com", code, listOf("kotlin", "oss"), daysAgo = 40)
        add("https://blog.example.com/100%-real_deal", "Descuento 100% real", "blog.example.com", null, emptyList(), daysAgo = 300)
        add("https://blog.example.com/old", "Archivo viejo", "blog.example.com", null, emptyList(), daysAgo = 380)
        Unit
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(
        url: String,
        title: String,
        source: String,
        category: Long?,
        tags: List<String>,
        notes: String = "",
        favorite: Boolean = false,
        daysAgo: Int,
    ) {
        now = 400 * day - daysAgo * day
        contents.add(newContent(url = url, title = title, source = source, categoryId = category, tags = tags, notes = notes, favorite = favorite))
        now = 400 * day
    }

    private fun search(filters: SearchFilters) = runBlocking { contents.search(filters).first().map { it.content.title } }
    private fun search(query: String) = search(SearchFilters(query = query))

    // --- by field

    @Test
    fun `finds by title`() = assertEquals(listOf("Receta de pasta casera"), search("pasta"))

    @Test
    fun `finds by url`() = assertEquals(listOf("Kotlin repository"), search("JetBrains"))

    @Test
    fun `finds by domain`() = assertEquals(listOf("Receta de pasta casera", "Pollo al horno"), search("youtube.com"))

    @Test
    fun `finds by notes`() = assertEquals(listOf("Receta de pasta casera"), search("albahaca"))

    @Test
    fun `finds by category name`() = assertEquals(listOf("Receta de pasta casera", "Pollo al horno"), search("comidas"))

    @Test
    fun `finds by tag`() {
        assertEquals(listOf("Receta de pasta casera"), search("italiana"))
        assertEquals(listOf("Receta de pasta casera", "Pollo al horno"), search("rápido"))
    }

    @Test
    fun `is case insensitive`() = assertEquals(listOf("Kotlin repository"), search("KOTLIN"))

    // --- combining words

    @Test
    fun `every word must match somewhere`() {
        assertEquals(listOf("Receta de pasta casera"), search("pasta italiana comidas"))
        assertEquals("one word matches nothing, so no result", emptyList<String>(), search("pasta kotlin"))
    }

    @Test
    fun `no match gives an empty list`() = assertTrue(search("zzzz").isEmpty())

    // --- wildcard safety

    @Test
    fun `percent and underscore typed by the user are literal`() {
        assertEquals(listOf("Descuento 100% real"), search("100%"))
        assertEquals(listOf("Descuento 100% real"), search("real_deal"))
        assertTrue("an underscore must not act as 'any character'", search("real-deal").isEmpty())
        assertTrue(search("%").size == 1)
    }

    @Test
    fun `hostile text is just text`() {
        assertTrue(search("'; DROP TABLE contents; --").isEmpty())
        assertEquals(5, runBlocking { contents.observeRecent(10).first().size })
    }

    // --- filters

    @Test
    fun `favorites filter`() =
        assertEquals(listOf("Pollo al horno"), search(SearchFilters(favoritesOnly = true)))

    @Test
    fun `category filter including uncategorized`() {
        assertEquals(listOf("Kotlin repository"), search(SearchFilters(category = CategoryFilter.Specific(code))))
        assertEquals(listOf("Descuento 100% real", "Archivo viejo"), search(SearchFilters(category = CategoryFilter.Uncategorized)))
    }

    @Test
    fun `date filter`() {
        assertEquals(listOf("Receta de pasta casera"), search(SearchFilters(period = DatePeriod.LAST_7_DAYS)))
        assertEquals(2, search(SearchFilters(period = DatePeriod.LAST_30_DAYS)).size)
        assertEquals("the 380-day-old item is outside the last year", 4, search(SearchFilters(period = DatePeriod.LAST_YEAR)).size)
    }

    @Test
    fun `source filter`() =
        assertEquals(listOf("Kotlin repository"), search(SearchFilters(source = "github.com")))

    @Test
    fun `filters combine with text`() {
        assertEquals(listOf("Pollo al horno"), search(SearchFilters(query = "rápido", favoritesOnly = true)))
        assertTrue(search(SearchFilters(query = "kotlin", favoritesOnly = true)).isEmpty())
    }

    @Test
    fun `with no text and no filters everything is returned newest first, and sources are distinct`() = runBlocking {
        assertEquals(
            listOf("Receta de pasta casera", "Pollo al horno", "Kotlin repository", "Descuento 100% real", "Archivo viejo"),
            search(SearchFilters()),
        )
        assertEquals(listOf("blog.example.com", "github.com", "youtube.com"), contents.observeSources().first())
    }

    // --- scale

    @Test
    fun `search stays fast with thousands of items`() = runBlocking {
        db.runInTransaction {
            val sqlite = db.openHelper.writableDatabase
            for (i in 1..5_000) {
                sqlite.execSQL(
                    "INSERT INTO contents (url, normalizedUrl, title, description, thumbnailUrl, source, contentType, categoryId, isFavorite, publishedAt, createdAt, updatedAt) " +
                        "VALUES (?, ?, ?, '', NULL, ?, 'LINK', NULL, 0, NULL, ?, ?)",
                    arrayOf<Any>("https://site$i.example.com/p/$i", "https://site$i.example.com/p/$i", "Article number $i about topic ${i % 50}", "site$i.example.com", i.toLong(), i.toLong()),
                )
            }
        }
        val started = System.nanoTime()
        val hits = contents.search(SearchFilters(query = "topic 7")).first()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("expected hits for 'topic 7'", hits.isNotEmpty())
        assertTrue("search over 5000 rows took ${elapsedMs}ms", elapsedMs < 2_000)
    }
}
