package com.bandeja.app.domain

import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.url.ContentTypeDetector
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlNormalizer
import com.bandeja.app.domain.url.UrlValidator
import com.bandeja.app.domain.url.YouTubeLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    private fun reason(raw: String?) = (UrlValidator.validate(raw) as UrlCheck.Invalid).reason

    @Test
    fun `valid urls`() {
        listOf(
            "https://example.com",
            "http://example.com/a/b?x=1#frag",
            "https://sub.example.co.uk:8443/path",
            "https://[2001:db8::1]/x",
            "https://bücher.example/straße",
        ).forEach { assertTrue(it, UrlValidator.isValid(it)) }
    }

    @Test
    fun `whitespace inside the url is rejected`() {
        assertEquals(InvalidUrlReason.MALFORMED, reason("https://exa mple.com"))
        assertEquals(InvalidUrlReason.MALFORMED, reason("https://example.com/a b"))
    }

    @Test
    fun `invalid ports are rejected`() {
        assertEquals(InvalidUrlReason.MALFORMED, reason("https://example.com:99999"))
        assertEquals(InvalidUrlReason.MALFORMED, reason("https://example.com:0"))
        assertEquals(InvalidUrlReason.MALFORMED, reason("https://example.com:abc"))
    }

    @Test
    fun `empty null and long inputs`() {
        assertEquals(InvalidUrlReason.EMPTY, reason(null))
        assertEquals(InvalidUrlReason.EMPTY, reason("  "))
        assertEquals(InvalidUrlReason.TOO_LONG, reason("https://example.com/" + "a".repeat(2048)))
    }

    @Test
    fun `scheme is case insensitive but must be http or https`() {
        assertTrue(UrlValidator.isValid("HTTPS://EXAMPLE.COM"))
        assertFalse(UrlValidator.isValid("ftp://example.com"))
    }
}

class UrlNormalizerTest {
    private fun n(raw: String) = UrlNormalizer.normalize(raw)

    @Test
    fun `host is lowercased and www is dropped`() {
        assertEquals("https://example.com/Path", n("https://WWW.Example.COM/Path"))
    }

    @Test
    fun `mobile prefix is dropped but a real domain called m is kept`() {
        assertEquals("https://example.com/a", n("https://m.example.com/a"))
        assertEquals("https://m.com/a", n("https://m.com/a"))
    }

    @Test
    fun `scheme default ports fragment and trailing slash are normalized`() {
        assertEquals("https://example.com/x", n("http://example.com:80/x/#section"))
        assertEquals("https://example.com:8443/a", n("https://example.com:8443/a"))
        assertEquals(n("https://example.com"), n("https://example.com/"))
    }

    @Test
    fun `tracking parameters are removed and the rest is sorted`() {
        assertEquals(
            "https://example.com/p?a=1&b=2",
            n("https://example.com/p?utm_source=x&b=2&fbclid=abc&a=1&utm_medium=y&gclid=z"),
        )
    }

    @Test
    fun `meaningful query parameters are kept`() {
        assertEquals("https://example.com/search?q=kotlin", n("https://example.com/search?q=kotlin&utm_campaign=c"))
    }

    @Test
    fun `different spellings of one youtube video collapse into one`() {
        val expected = "https://youtube.com/watch?v=dQw4w9WgXcQ"
        listOf(
            "https://youtu.be/dQw4w9WgXcQ?si=abc&t=10",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1&feature=share",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "http://youtube.com/watch?feature=x&v=dQw4w9WgXcQ",
        ).forEach { assertEquals(it, expected, n(it)) }
    }

    @Test
    fun `youtube shorts keep their own identity`() {
        assertEquals("https://youtube.com/shorts/abcdEFG1234", n("https://www.youtube.com/shorts/abcdEFG1234?feature=share"))
    }

    @Test
    fun `social posts ignore share parameters`() {
        assertEquals("https://x.com/user/status/123", n("https://twitter.com/user/status/123?s=20&t=abc"))
        assertEquals("https://instagram.com/reel/ABC123", n("https://www.instagram.com/reel/ABC123/?igsh=xyz"))
        assertEquals("https://tiktok.com/@u/video/1", n("https://www.tiktok.com/@u/video/1?_t=8&_r=1"))
    }

    @Test
    fun `same page from two apps is a duplicate`() {
        assertEquals(
            n("https://www.example.com/article/?utm_source=twitter"),
            n("http://example.com/article#comments"),
        )
    }

    @Test
    fun `invalid urls normalize to null`() {
        assertNull(n("javascript:alert(1)"))
        assertNull(n("not a url"))
        assertNull(n(""))
    }

    @Test
    fun `youtube video id extraction`() {
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeLinks.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeLinks.videoId("https://www.youtube.com/@channel"))
        assertNull(YouTubeLinks.videoId("https://example.com/watch?v=dQw4w9WgXcQ"))
    }
}

class ContentTypeDetectorTest {
    private fun type(raw: String, og: String? = null): ContentType {
        val url = (UrlValidator.validate(raw) as UrlCheck.Valid).url
        return ContentTypeDetector.detect(url, og)
    }

    @Test
    fun `detects by site`() {
        assertEquals(ContentType.VIDEO, type("https://youtu.be/abc"))
        assertEquals(ContentType.VIDEO, type("https://www.tiktok.com/@u/video/1"))
        assertEquals(ContentType.VIDEO, type("https://www.instagram.com/reel/AB/"))
        assertEquals(ContentType.SOCIAL, type("https://www.instagram.com/p/AB/"))
        assertEquals(ContentType.SOCIAL, type("https://x.com/u/status/1"))
        assertEquals(ContentType.CODE, type("https://github.com/a/b"))
        assertEquals(ContentType.IMAGE, type("https://example.com/pic.PNG"))
        assertEquals(ContentType.ARTICLE, type("https://es.wikipedia.org/wiki/Kotlin"))
        assertEquals(ContentType.LINK, type("https://example.com/"))
    }

    @Test
    fun `page metadata refines unknown sites`() {
        assertEquals(ContentType.ARTICLE, type("https://blog.example.com/post", og = "article"))
        assertEquals(ContentType.VIDEO, type("https://media.example.com/x", og = "video.other"))
    }
}
