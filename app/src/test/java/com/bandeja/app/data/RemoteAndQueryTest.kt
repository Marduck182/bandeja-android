package com.bandeja.app.data

import com.bandeja.app.data.local.SearchQueryBuilder
import com.bandeja.app.data.remote.HostGuard
import com.bandeja.app.data.remote.OpenGraphParser
import com.bandeja.app.domain.model.CategoryFilter
import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.DatePeriod
import com.bandeja.app.domain.model.SearchFilters
import com.bandeja.app.domain.usecase.TextRules
import androidx.sqlite.db.SimpleSQLiteQuery
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenGraphParserTest {
    private val page = "https://example.com/post/1"

    @Test
    fun `reads title image type and date from open graph`() {
        val html = """
            <html><head>
              <title>Fallback title</title>
              <meta property="og:title" content="  Real   title ">
              <meta property="og:image" content="/img/cover.jpg">
              <meta property="og:type" content="article">
              <meta property="article:published_time" content="2024-05-01T10:00:00Z">
            </head></html>
        """.trimIndent()
        val meta = OpenGraphParser.parse(html, page)!!
        assertEquals("Real title", meta.title)
        assertEquals("https://example.com/img/cover.jpg", meta.thumbnailUrl)
        assertEquals(ContentType.ARTICLE, meta.contentType)
        assertEquals(1714557600000L, meta.publishedAt)
    }

    @Test
    fun `falls back to twitter tags and then to the title element`() {
        val twitter = """<meta name="twitter:title" content="From twitter"><meta name="twitter:image" content="http://cdn.example.com/a.png">"""
        val meta = OpenGraphParser.parse("<html><head>$twitter</head></html>", page)!!
        assertEquals("From twitter", meta.title)
        assertEquals("https://cdn.example.com/a.png", meta.thumbnailUrl) // http image upgraded to https

        assertEquals("Only title", OpenGraphParser.parse("<html><head><title>Only title</title></head></html>", page)!!.title)
    }

    @Test
    fun `unsafe image urls are discarded`() {
        val html = """<html><head><title>T</title><meta property="og:image" content="data:image/png;base64,AAAA"></head></html>"""
        assertNull(OpenGraphParser.parse(html, page)!!.thumbnailUrl)
        val js = """<html><head><title>T</title><meta property="og:image" content="javascript:alert(1)"></head></html>"""
        assertNull(OpenGraphParser.parse(js, page)!!.thumbnailUrl)
    }

    @Test
    fun `page without usable metadata gives null`() {
        assertNull(OpenGraphParser.parse("<html><body>hello</body></html>", page))
    }

    @Test
    fun `huge titles are capped`() {
        val html = "<html><head><title>${"x".repeat(1000)}</title></head></html>"
        assertEquals(200, OpenGraphParser.parse(html, page)!!.title!!.length)
    }

    @Test
    fun `malformed date is ignored`() {
        val html = """<html><head><title>T</title><meta property="article:published_time" content="ayer"></head></html>"""
        assertNull(OpenGraphParser.parse(html, page)!!.publishedAt)
    }
}

class HostGuardTest {
    private fun ip(literal: String) = InetAddress.getByName(literal)

    @Test
    fun `private loopback and link local addresses are blocked`() {
        listOf(
            "127.0.0.1", "10.0.0.5", "192.168.1.1", "172.16.0.1", "172.31.255.255", "169.254.169.254",
            "100.64.0.1", "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fc00::1", "fd12:3456::1",
        ).forEach { assertFalse(it, HostGuard.isPublic(ip(it))) }
    }

    @Test
    fun `public addresses are allowed`() {
        listOf("8.8.8.8", "1.1.1.1", "93.184.216.34", "2001:4860:4860::8888").forEach {
            assertTrue(it, HostGuard.isPublic(ip(it)))
        }
    }

    @Test
    fun `a name that does not resolve is not public`() {
        assertFalse(HostGuard.resolvesToPublicOnly("this-host-does-not-exist.invalid"))
    }
}

class SearchQueryBuilderTest {
    @Test
    fun `terms are split trimmed and capped`() {
        assertEquals(listOf("pollo", "receta"), SearchQueryBuilder.terms("  pollo   receta "))
        assertTrue(SearchQueryBuilder.terms("").isEmpty())
        assertEquals(SearchQueryBuilder.MAX_TERMS, SearchQueryBuilder.terms("a b c d e f g h i j k").size)
    }

    @Test
    fun `like wildcards typed by the user are escaped`() {
        assertEquals("100\\%", SearchQueryBuilder.escapeLike("100%"))
        assertEquals("a\\_b", SearchQueryBuilder.escapeLike("a_b"))
        assertEquals("c:\\\\x", SearchQueryBuilder.escapeLike("c:\\x"))
    }

    @Test
    fun `user text only travels as bound arguments`() {
        val hostile = "'; DROP TABLE contents; --"
        val query = SearchQueryBuilder.build(SearchFilters(query = hostile), now = 0) as SimpleSQLiteQuery
        assertFalse(query.sql.contains("DROP"))
        assertFalse(query.sql.contains("--"))
        // 4 terms ("';", "DROP", ...) x 6 columns each
        assertEquals(SearchQueryBuilder.terms(hostile).size * 6, query.argCount)
    }

    @Test
    fun `every filter adds its clause`() {
        val all = SearchFilters(
            query = "x",
            category = CategoryFilter.Specific(3),
            favoritesOnly = true,
            period = DatePeriod.LAST_7_DAYS,
            source = "youtube.com",
        )
        val sql = (SearchQueryBuilder.build(all, now = 1_000_000_000L) as SimpleSQLiteQuery).sql
        assertTrue(sql.contains("c.categoryId = ?"))
        assertTrue(sql.contains("c.isFavorite = 1"))
        assertTrue(sql.contains("c.createdAt >= ?"))
        assertTrue(sql.contains("c.source = ?"))
        assertTrue((SearchQueryBuilder.build(SearchFilters(category = CategoryFilter.Uncategorized), 0) as SimpleSQLiteQuery)
            .sql.contains("c.categoryId IS NULL"))
    }

    @Test
    fun `no query and no filters has no extra clauses`() {
        val sql = (SearchQueryBuilder.build(SearchFilters(), 0) as SimpleSQLiteQuery).sql
        assertFalse(sql.contains("LIKE"))
        assertNotNull(sql)
    }
}

class TextRulesTest {
    @Test
    fun `tags are cleaned deduplicated and capped`() {
        assertEquals(listOf("receta", "fácil rápido"), TextRules.tags(listOf("#Receta", " receta ", "Fácil   Rápido", "", "  ")))
        assertEquals(TextRules.MAX_TAGS, TextRules.tags((1..50).map { "tag$it" }).size)
        assertEquals(TextRules.MAX_TAG, TextRules.tags(listOf("x".repeat(100))).single().length)
    }

    @Test
    fun `title and notes are bounded`() {
        assertEquals("a b c", TextRules.title("  a \n b\t c  "))
        assertEquals(TextRules.MAX_TITLE, TextRules.title("x".repeat(999)).length)
        assertEquals(TextRules.MAX_NOTES, TextRules.notes("y".repeat(99_999)).length)
    }
}
