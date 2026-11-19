package com.bandeja.app.domain

import com.bandeja.app.data.local.BandejaDatabase
import com.bandeja.app.data.repo.RoomContentRepository
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.domain.usecase.ContentDraft
import com.bandeja.app.domain.usecase.SaveContent
import com.bandeja.app.domain.usecase.SaveResult
import com.bandeja.app.await
import com.bandeja.app.newInMemoryDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SaveContentTest {
    private lateinit var db: BandejaDatabase
    private lateinit var contents: RoomContentRepository
    private lateinit var saveContent: SaveContent
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        db = newInMemoryDatabase()
        contents = RoomContentRepository(db)
        saveContent = SaveContent(contents, appScope)
    }

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    private fun draft(
        url: String = "https://www.example.com/post/?utm_source=x",
        title: String = "",
        notes: String = "",
        tags: List<String> = emptyList(),
    ) = ContentDraft(url, title, notes, categoryId = null, tags = tags, isFavorite = false)

    private suspend fun saved(result: SaveResult) = contents.observeItem((result as SaveResult.Saved).id).first()!!

    @Test
    fun `saves the original url, the normalized one, the source and the page metadata`() = runBlocking {
        val meta = LinkMetadata("Page title", "https://example.com/i.jpg", ContentType.ARTICLE, 123L)
        val item = saved(saveContent.save(draft(), meta))
        with(item.content) {
            assertEquals("https://www.example.com/post/?utm_source=x", url)
            assertEquals("https://example.com/post", normalizedUrl)
            assertEquals("example.com", source)
            assertEquals("Page title", title)
            assertEquals("https://example.com/i.jpg", thumbnailUrl)
            assertEquals(ContentType.ARTICLE, contentType)
            assertEquals(123L, publishedAt)
        }
    }

    @Test
    fun `the user's title wins over the page's, which wins over the domain`() = runBlocking {
        val meta = LinkMetadata(title = "Page title")
        assertEquals("Mine", saved(saveContent.save(draft(title = "Mine"), meta)).content.title)
        assertEquals("Page title", saved(saveContent.save(draft(title = "  "), meta)).content.title)
        assertEquals("example.com", saved(saveContent.save(draft(), null)).content.title)
    }

    @Test
    fun `works without any metadata, as when offline`() = runBlocking {
        val item = saved(saveContent.save(draft(url = "https://example.com/offline"), null))
        assertEquals("example.com", item.content.title)
        assertNull(item.content.thumbnailUrl)
        assertEquals(ContentType.LINK, item.content.contentType)
    }

    @Test
    fun `youtube gets its thumbnail and type without needing the network`() = runBlocking {
        val item = saved(saveContent.save(draft(url = "https://youtu.be/dQw4w9WgXcQ?si=abc"), null))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", item.content.thumbnailUrl)
        assertEquals(ContentType.VIDEO, item.content.contentType)
        assertEquals("a short youtu.be link is shown as youtube.com", "youtube.com", item.content.source)
    }

    @Test
    fun `text from the user is cleaned and bounded`() = runBlocking {
        val item = saved(
            saveContent.save(draft(title = "  A \n  title " + "x".repeat(500), notes = "n".repeat(9_999), tags = listOf("#Uno", "uno", " DOS ")), null),
        )
        assertEquals(200, item.content.title.length)
        assertEquals(5_000, item.content.description.length)
        assertEquals(listOf("dos", "uno"), item.tags)
    }

    @Test
    fun `invalid urls are refused and nothing is stored`() = runBlocking {
        assertEquals(SaveResult.Invalid(InvalidUrlReason.UNSUPPORTED_SCHEME), saveContent.save(draft(url = "javascript:alert(1)"), null))
        assertEquals(SaveResult.Invalid(InvalidUrlReason.MALFORMED), saveContent.save(draft(url = "https://exa mple.com"), null))
        assertEquals(SaveResult.Invalid(InvalidUrlReason.EMPTY), saveContent.save(draft(url = "  "), null))
        assertTrue(contents.observeRecent(10).first().isEmpty())
    }

    @Test
    fun `duplicates are found through the normalized url`() = runBlocking {
        val id = (saveContent.save(draft(url = "https://www.example.com/post/?utm_source=x"), null) as SaveResult.Saved).id
        assertEquals(id, saveContent.findDuplicate("http://example.com/post#top")!!.content.id)
        assertNull(saveContent.findDuplicate("https://example.com/other"))
        assertNull(saveContent.findDuplicate("not a url"))
    }

    @Test
    fun `saving does not wait for the preview, which is completed afterwards`() = runBlocking {
        val gate = CompletableDeferred<LinkMetadata?>()
        val pending = appScope.async { gate.await() }

        // The preview has not arrived: the item is stored right away with the domain as a placeholder title.
        val id = (saveContent.save(draft(url = "https://slow.example.com/a"), metadata = null, pending = pending) as SaveResult.Saved).id
        assertEquals("slow.example.com", contents.observeItem(id).first()!!.content.title)

        gate.complete(LinkMetadata("Arrived later", "https://slow.example.com/t.jpg", ContentType.ARTICLE, null))
        val updated = contents.observeItem(id).await { it?.content?.title == "Arrived later" }!!
        assertEquals("https://slow.example.com/t.jpg", updated.content.thumbnailUrl)
    }

    @Test
    fun `a title edited before the preview arrives is not replaced`() = runBlocking {
        val gate = CompletableDeferred<LinkMetadata?>()
        val pending = appScope.async { gate.await() }
        val id = (saveContent.save(draft(url = "https://slow.example.com/b", title = "Mine"), metadata = null, pending = pending) as SaveResult.Saved).id
        gate.complete(LinkMetadata(title = "Theirs", thumbnailUrl = "https://slow.example.com/t.jpg"))
        val item = contents.observeItem(id).await { it?.content?.thumbnailUrl != null }!!
        assertEquals("Mine", item.content.title)
    }
}
