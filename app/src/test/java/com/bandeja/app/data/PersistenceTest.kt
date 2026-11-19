package com.bandeja.app.data

import androidx.room.Room
import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.local.DefaultCategories
import com.bandeja.app.data.repo.RoomCategoryRepository
import com.bandeja.app.data.repo.RoomContentRepository
import com.bandeja.app.domain.repository.CategoryOutcome
import com.bandeja.app.appContext
import com.bandeja.app.newContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Data must survive the app being closed: write, close the database, reopen the same file, read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersistenceTest {
    private val name = "persistence-test.db"

    @After
    fun cleanUp() {
        appContext().deleteDatabase(name)
        appContext().deleteDatabase(BandejaDatabase.NAME)
    }

    private fun open() = Room.databaseBuilder(appContext(), BandejaDatabase::class.java, name).allowMainThreadQueries().build()

    @Test
    fun `categories content tags and favorites are still there after reopening the database`() = runBlocking {
        val first = open()
        val categoryId = (RoomCategoryRepository(first.categoryDao()).save(null, "Comidas", "🍔", "coral") as CategoryOutcome.Saved).id
        val contentId = RoomContentRepository(first).add(
            newContent(
                url = "https://example.com/receta",
                title = "Receta",
                notes = "nota",
                categoryId = categoryId,
                tags = listOf("pasta"),
                favorite = true,
            ),
        )
        first.close()

        val second = open()
        val item = RoomContentRepository(second).observeItem(contentId).first()!!
        assertEquals("Receta", item.content.title)
        assertEquals("nota", item.content.description)
        assertTrue(item.content.isFavorite)
        assertEquals("Comidas", item.category!!.name)
        assertEquals(listOf("pasta"), item.tags)
        assertEquals(1, RoomCategoryRepository(second.categoryDao()).observeAll().first().size)
        second.close()
    }

    @Test
    fun `a brand new database starts with the example categories, owned by the user from then on`() = runBlocking {
        val db = BandejaDatabase.create(appContext())
        val repo = RoomCategoryRepository(db.categoryDao())
        val seeded = repo.observeAll().first()
        assertEquals(DefaultCategories.all.map { it.name }, seeded.map { it.name })
        assertEquals(DefaultCategories.all.map { it.icon }, seeded.map { it.icon })

        // The user is in full control: rename, delete, add.
        repo.save(seeded.first().id, "Cocina", "🍳", "amber")
        repo.delete(seeded.last().id)
        repo.save(null, "Mi categoría", "📌", "slate")
        val names = repo.observeAll().first().map { it.name }
        assertEquals(DefaultCategories.all.size, names.size)
        assertTrue("Cocina" in names && "Mi categoría" in names && DefaultCategories.all.last().name !in names)
        db.close()
    }
}
