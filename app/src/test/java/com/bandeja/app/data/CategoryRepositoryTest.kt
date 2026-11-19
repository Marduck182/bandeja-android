package com.bandeja.app.data

import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.repo.RoomCategoryRepository
import com.bandeja.app.data.repo.RoomContentRepository
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.repository.CategoryRepository
import com.bandeja.app.newContent
import com.bandeja.app.newInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryRepositoryTest {
    private lateinit var db: BandejaDatabase
    private lateinit var categories: RoomCategoryRepository
    private lateinit var contents: RoomContentRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        categories = RoomCategoryRepository(db.categoryDao()) { clock }
        contents = RoomContentRepository(db) { clock }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun create(name: String, icon: String = "🍔", color: String = "coral"): Long =
        (categories.save(null, name, icon, color) as CategoryOutcome.Saved).id

    // --- create

    @Test
    fun `create stores every field and keeps creation order`() = runBlocking {
        val a = create("Comidas", "🍔", "coral")
        val b = create("Viajes", "✈️", "sky")
        val all = categories.observeAll().first()
        assertEquals(listOf("Comidas", "Viajes"), all.map { it.name })
        assertEquals(listOf(a, b), all.map { it.id })
        assertEquals("🍔", all[0].icon)
        assertEquals("sky", all[1].color)
        assertEquals(1_000L, all[0].createdAt)
    }

    @Test
    fun `name is trimmed and inner whitespace collapsed`() = runBlocking {
        create("  Mis   recetas  ")
        assertEquals("Mis recetas", categories.observeAll().first().single().name)
    }

    @Test
    fun `invalid names are rejected`() = runBlocking {
        assertEquals(CategoryOutcome.NameBlank, categories.save(null, "   ", "🍔", "coral"))
        assertEquals(CategoryOutcome.NameTooLong, categories.save(null, "x".repeat(CategoryRepository.MAX_NAME_LENGTH + 1), "🍔", "coral"))
        assertTrue(categories.observeAll().first().isEmpty())
    }

    @Test
    fun `names are unique ignoring case and accents case`() = runBlocking {
        create("Música")
        assertEquals(CategoryOutcome.NameTaken, categories.save(null, "música", "🎵", "pink"))
        assertEquals(CategoryOutcome.NameTaken, categories.save(null, "MÚSICA", "🎵", "pink"))
        create("Comidas")
        assertEquals(CategoryOutcome.NameTaken, categories.save(null, "comidas", "🍔", "coral"))
    }

    // --- edit

    @Test
    fun `edit changes name icon color and updatedAt`() = runBlocking {
        val id = create("Comidas")
        clock = 5_000L
        assertEquals(CategoryOutcome.Saved(id), categories.save(id, "Cocina", "🍳", "amber"))
        val edited = categories.observe(id).first()!!
        assertEquals("Cocina", edited.name)
        assertEquals("🍳", edited.icon)
        assertEquals("amber", edited.color)
        assertEquals(1_000L, edited.createdAt)
        assertEquals(5_000L, edited.updatedAt)
    }

    @Test
    fun `a category can keep its own name when edited but not take another's`() = runBlocking {
        val a = create("Comidas")
        create("Viajes")
        assertEquals(CategoryOutcome.Saved(a), categories.save(a, "Comidas", "🍕", "lime")) // same name, new icon
        assertEquals(CategoryOutcome.NameTaken, categories.save(a, "Viajes", "🍕", "lime"))
    }

    // --- delete and content

    @Test
    fun `deleting a category keeps its content as uncategorized`() = runBlocking {
        val food = create("Comidas")
        val id = contents.add(newContent(categoryId = food, tags = listOf("receta")))

        categories.delete(food)

        assertTrue(categories.observeAll().first().isEmpty())
        val item = contents.observeItem(id).first()
        assertNotNull("content must survive its category", item)
        assertNull(item!!.content.categoryId)
        assertNull(item.category)
        assertEquals(listOf("receta"), item.tags)
        assertEquals(1, contents.observeUncategorizedCount().first())
    }

    @Test
    fun `counts per category are accurate`() = runBlocking {
        val food = create("Comidas")
        val trips = create("Viajes")
        create("Ideas")
        contents.add(newContent(url = "https://a.com/1", categoryId = food))
        contents.add(newContent(url = "https://a.com/2", categoryId = food))
        contents.add(newContent(url = "https://a.com/3", categoryId = trips))
        contents.add(newContent(url = "https://a.com/4", categoryId = null))

        val counts = categories.observeAllWithCounts().first().associate { it.category.name to it.contentCount }
        assertEquals(mapOf("Comidas" to 2, "Viajes" to 1, "Ideas" to 0), counts)
        assertEquals(1, contents.observeUncategorizedCount().first())
    }

    @Test
    fun `assign and move content between categories`() = runBlocking {
        val food = create("Comidas")
        val trips = create("Viajes")
        val id = contents.add(newContent(categoryId = food))
        assertEquals(food, contents.observeItem(id).first()!!.content.categoryId)

        contents.moveToCategory(id, trips)
        assertEquals(trips, contents.observeItem(id).first()!!.content.categoryId)
        assertEquals(1, contents.observeByCategory(trips).first().size)
        assertTrue(contents.observeByCategory(food).first().isEmpty())

        contents.moveToCategory(id, null)
        assertEquals(listOf(id), contents.observeByCategory(null).first().map { it.content.id })
    }

    @Test
    fun `assigning a category that does not exist falls back to uncategorized instead of failing`() = runBlocking {
        val id = contents.add(newContent(categoryId = 999))
        assertNull(contents.observeItem(id).first()!!.content.categoryId)
        contents.moveToCategory(id, 12345)
        assertNull(contents.observeItem(id).first()!!.content.categoryId)
    }
}
