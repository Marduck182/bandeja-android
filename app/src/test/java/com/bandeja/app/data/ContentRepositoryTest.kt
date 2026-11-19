package com.bandeja.app.data

import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.repo.RoomCategoryRepository
import com.bandeja.app.data.repo.RoomContentRepository
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.domain.url.UrlNormalizer
import com.bandeja.app.newContent
import com.bandeja.app.newInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class ContentRepositoryTest {
    private lateinit var db: BandejaDatabase
    private lateinit var contents: RoomContentRepository
    private lateinit var categories: RoomCategoryRepository
    private var clock = 1_000L

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        contents = RoomContentRepository(db) { clock }
        categories = RoomCategoryRepository(db.categoryDao()) { clock }
    }

    @After
    fun tearDown() = db.close()

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    // --- save and read

    @Test
    fun `saved content can be read back with every field`() = runBlocking {
        val cat = (categories.save(null, "Comidas", "🍔", "coral") as CategoryOutcome.Saved).id
        val id = contents.add(
            newContent(
                url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&utm_source=x",
                title = "Receta de pasta",
                notes = "con albahaca",
                categoryId = cat,
                tags = listOf("Pasta", "rápido"),
                favorite = true,
                source = "youtube.com",
                type = ContentType.VIDEO,
                thumbnail = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            ),
        )
        val item = contents.observeItem(id).first()!!
        with(item.content) {
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ&utm_source=x", url)
            assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", normalizedUrl)
            assertEquals("Receta de pasta", title)
            assertEquals("con albahaca", description)
            assertEquals("youtube.com", source)
            assertEquals(ContentType.VIDEO, contentType)
            assertTrue(isFavorite)
            assertEquals(1_000L, createdAt)
            assertEquals(1_000L, updatedAt)
        }
        assertEquals("Comidas", item.category!!.name)
        assertEquals(listOf("pasta", "rápido"), item.tags)
    }

    @Test
    fun `recent items come newest first and respect the limit`() = runBlocking {
        for (i in 1..5) {
            clock = i * 1_000L
            contents.add(newContent(url = "https://a.com/$i", title = "T$i"))
        }
        assertEquals(listOf("T5", "T4", "T3"), contents.observeRecent(3).first().map { it.content.title })
    }

    // --- edit

    @Test
    fun `update changes title notes category and tags and cleans unused tags`() = runBlocking {
        val cat = (categories.save(null, "Ideas", "💡", "pink") as CategoryOutcome.Saved).id
        val id = contents.add(newContent(title = "Old", tags = listOf("a", "b")))
        assertEquals(2, count("tags"))

        clock = 9_000L
        contents.update(id, "New title", "some notes", cat, listOf("b", "c"))

        val item = contents.observeItem(id).first()!!
        assertEquals("New title", item.content.title)
        assertEquals("some notes", item.content.description)
        assertEquals(cat, item.content.categoryId)
        assertEquals(listOf("b", "c"), item.tags)
        assertEquals(1_000L, item.content.createdAt)
        assertEquals(9_000L, item.content.updatedAt)
        assertEquals("tag 'a' is no longer used by anyone and must be removed", 2, count("tags"))
    }

    @Test
    fun `blank title on edit falls back to the source`() = runBlocking {
        val id = contents.add(newContent(title = "Old", source = "example.com"))
        contents.update(id, "   ", "", null, emptyList())
        assertEquals("example.com", contents.observeItem(id).first()!!.content.title)
    }

    @Test
    fun `editing something that no longer exists does nothing`() = runBlocking {
        contents.update(4242, "x", "y", null, listOf("t"))
        assertEquals(0, count("contents"))
        assertEquals(0, count("tags"))
    }

    // --- favorites

    @Test
    fun `favorite can be toggled and listed`() = runBlocking {
        val a = contents.add(newContent(url = "https://a.com/1"))
        val b = contents.add(newContent(url = "https://a.com/2"))
        assertTrue(contents.observeFavorites().first().isEmpty())

        contents.setFavorite(a, true)
        contents.setFavorite(b, true)
        assertEquals(setOf(a, b), contents.observeFavorites().first().map { it.content.id }.toSet())

        contents.setFavorite(a, false)
        assertEquals(listOf(b), contents.observeFavorites().first().map { it.content.id })
        assertFalse(contents.observeItem(a).first()!!.content.isFavorite)
    }

    // --- delete

    @Test
    fun `delete removes the item its tag links and orphan tags`() = runBlocking {
        val id = contents.add(newContent(tags = listOf("x", "y")))
        val other = contents.add(newContent(url = "https://a.com/2", tags = listOf("y")))

        contents.delete(id)

        assertNull(contents.observeItem(id).first())
        assertNotNull(contents.observeItem(other).first())
        assertEquals(1, count("contents"))
        assertEquals("only the shared tag 'y' remains", 1, count("tags"))
        assertEquals(1, count("content_tags"))
    }

    // --- undo

    @Test
    fun `restore puts a deleted item back with the same id, dates, tags and category`() = runBlocking {
        val cat = (categories.save(null, "Comidas", "🍔", "coral") as CategoryOutcome.Saved).id
        clock = 2_000L
        val id = contents.add(newContent(title = "Receta", notes = "nota", categoryId = cat, tags = listOf("a", "b"), favorite = true))
        val before = contents.observeItem(id).first()!!

        contents.delete(id)
        assertNull(contents.observeItem(id).first())
        clock = 9_000L
        contents.restore(before)

        val after = contents.observeItem(id).first()!!
        assertEquals(before.content, after.content)
        assertEquals(2_000L, after.content.createdAt)
        assertEquals(listOf("a", "b"), after.tags)
        assertEquals("Comidas", after.category!!.name)
        assertEquals(2, count("tags"))
    }

    @Test
    fun `restore after its category was deleted leaves the item uncategorized instead of failing`() = runBlocking {
        val cat = (categories.save(null, "Viajes", "✈️", "sky") as CategoryOutcome.Saved).id
        val id = contents.add(newContent(categoryId = cat))
        val before = contents.observeItem(id).first()!!
        contents.delete(id)
        categories.delete(cat)

        contents.restore(before)

        assertNull(contents.observeItem(id).first()!!.content.categoryId)
    }

    // --- duplicates

    @Test
    fun `find by normalized url returns the newest and duplicates are allowed`() = runBlocking {
        clock = 1_000L
        val first = contents.add(newContent(url = "https://www.example.com/post?utm_source=a", title = "First"))
        clock = 2_000L
        val second = contents.add(newContent(url = "http://example.com/post", title = "Second"))

        assertEquals(2, count("contents"))
        val found = contents.findByNormalizedUrl(UrlNormalizer.normalize("https://example.com/post/#x")!!)!!
        assertEquals(second, found.content.id)
        assertTrue(first != second)
        assertNull(contents.findByNormalizedUrl("https://example.com/other"))
    }

    // --- enrichment after save

    @Test
    fun `missing metadata fills placeholders but never overwrites the user's data`() = runBlocking {
        val bare = contents.add(newContent(url = "https://a.com/1", title = "a.com", source = "a.com"))
        contents.fillMissingMetadata(
            bare,
            LinkMetadata(title = "Real title", thumbnailUrl = "https://a.com/i.jpg", contentType = ContentType.ARTICLE, publishedAt = 77L),
        )
        with(contents.observeItem(bare).first()!!.content) {
            assertEquals("Real title", title)
            assertEquals("https://a.com/i.jpg", thumbnailUrl)
            assertEquals(ContentType.ARTICLE, contentType)
            assertEquals(77L, publishedAt)
        }

        val custom = contents.add(
            newContent(url = "https://b.com/1", title = "My own title", source = "b.com", thumbnail = "https://b.com/mine.jpg", type = ContentType.VIDEO),
        )
        contents.fillMissingMetadata(custom, LinkMetadata("Other", "https://b.com/other.jpg", ContentType.ARTICLE, 5L))
        with(contents.observeItem(custom).first()!!.content) {
            assertEquals("My own title", title)
            assertEquals("https://b.com/mine.jpg", thumbnailUrl)
            assertEquals(ContentType.VIDEO, contentType)
        }
    }

    @Test
    fun `enriching an item that was deleted meanwhile is harmless`() = runBlocking {
        val id = contents.add(newContent())
        contents.delete(id)
        contents.fillMissingMetadata(id, LinkMetadata(title = "x"))
        assertEquals(0, count("contents"))
    }
}
