package com.bandeja.app.domain

import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.share.ShareInput
import com.bandeja.app.domain.share.ShareParseResult
import com.bandeja.app.domain.share.SharedContentParser
import com.bandeja.app.domain.url.ContentTypeDetector
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlNormalizer
import com.bandeja.app.domain.url.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * What each platform's "share" button puts in the Intent for a short video, and what the app makes of it.
 * The shapes follow what the apps share; the oEmbed/page answers of each site were probed separately.
 */
class ShortVideoLinksTest {

    private fun shared(text: String): String =
        (SharedContentParser.parse(ShareInput(text, null, null)) as ShareParseResult.Parsed).url

    private fun type(url: String): ContentType =
        ContentTypeDetector.detect((UrlValidator.validate(url) as UrlCheck.Valid).url)

    private fun source(url: String): String = UrlNormalizer.sourceOf((UrlValidator.validate(url) as UrlCheck.Valid).url)

    // --- YouTube Shorts

    @Test
    fun `youtube short with the si tracking parameter`() {
        val url = shared("https://youtube.com/shorts/8xkEICotpOM?si=AbCdEfGhIj")
        assertEquals(ContentType.VIDEO, type(url))
        assertEquals("youtube.com", source(url))
        assertEquals("https://youtube.com/shorts/8xkEICotpOM", UrlNormalizer.normalize(url))
    }

    @Test
    fun `the same short from the web, from the app and with a feature parameter is one item`() {
        val expected = UrlNormalizer.normalize("https://www.youtube.com/shorts/8xkEICotpOM")
        listOf(
            "https://youtube.com/shorts/8xkEICotpOM?si=zzz",
            "https://m.youtube.com/shorts/8xkEICotpOM",
            "https://www.youtube.com/shorts/8xkEICotpOM?feature=share",
        ).forEach { assertEquals(it, expected, UrlNormalizer.normalize(it)) }
    }

    // --- TikTok

    @Test
    fun `tiktok share text with a comment and a short link`() {
        val url = shared("Mira este video de @pedro 🔥 https://vm.tiktok.com/ZMabc123/")
        assertEquals("https://vm.tiktok.com/ZMabc123/", url)
        assertEquals(ContentType.VIDEO, type(url))
        assertEquals("a short link is shown as the site it belongs to", "tiktok.com", source(url))
    }

    @Test
    fun `tiktok full link ignores share parameters`() {
        val a = UrlNormalizer.normalize("https://www.tiktok.com/@pedroaraujostudio/video/7350285347961507078?_r=1&_t=8abc&is_from_webapp=1")
        val b = UrlNormalizer.normalize("https://m.tiktok.com/@pedroaraujostudio/video/7350285347961507078/")
        assertEquals("https://tiktok.com/@pedroaraujostudio/video/7350285347961507078", a)
        assertEquals(a, b)
    }

    // --- Instagram Reels

    @Test
    fun `instagram reel link with igsh`() {
        val url = shared("https://www.instagram.com/reel/CxYz123abcd/?igsh=MWxxYWJjZGVm")
        assertEquals(ContentType.VIDEO, type(url))
        assertEquals("https://instagram.com/reel/CxYz123abcd", UrlNormalizer.normalize(url))
    }

    @Test
    fun `reel and reels paths are videos but a normal post is not`() {
        assertEquals(ContentType.VIDEO, type("https://www.instagram.com/reels/CxYz123abcd/"))
        assertEquals(ContentType.SOCIAL, type("https://www.instagram.com/p/CxYz123abcd/"))
    }

    // --- Facebook Reels

    @Test
    fun `facebook reel with mibextid, s and fs is the same item as the clean link`() {
        val clean = UrlNormalizer.normalize("https://www.facebook.com/reel/1234567890123456")
        val shared = UrlNormalizer.normalize("https://www.facebook.com/reel/1234567890123456?mibextid=rS40aB7S9Ucbxw6v&s=yWDuG2&fs=e")
        val mobile = UrlNormalizer.normalize("https://m.facebook.com/reel/1234567890123456/?fbclid=AbC")
        assertEquals("https://facebook.com/reel/1234567890123456", clean)
        assertEquals(clean, shared)
        assertEquals(clean, mobile)
        assertEquals(ContentType.VIDEO, type("https://www.facebook.com/reel/1234567890123456?mibextid=x"))
    }

    @Test
    fun `facebook links whose id lives in the query keep it`() {
        val a = UrlNormalizer.normalize("https://www.facebook.com/watch/?v=111&mibextid=x")
        val b = UrlNormalizer.normalize("https://www.facebook.com/watch/?v=222&mibextid=x")
        assertEquals("https://facebook.com/watch?v=111", a)
        assertNotEquals(a, b)
        assertNotEquals(
            UrlNormalizer.normalize("https://www.facebook.com/permalink.php?story_fbid=1&id=9"),
            UrlNormalizer.normalize("https://www.facebook.com/permalink.php?story_fbid=2&id=9"),
        )
    }

    @Test
    fun `facebook share links and fb watch short links are accepted as videos or links`() {
        assertEquals(ContentType.VIDEO, type(shared("https://fb.watch/abc123XYZ/")))
        assertEquals("https://facebook.com/share/r/1AbCdEf", UrlNormalizer.normalize("https://www.facebook.com/share/r/1AbCdEf/?mibextid=wwXIfr"))
    }
}
