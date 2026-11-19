package com.bandeja.app.domain

import com.bandeja.app.domain.share.ShareInput
import com.bandeja.app.domain.share.ShareParseResult
import com.bandeja.app.domain.share.SharedContentParser
import com.bandeja.app.domain.url.InvalidUrlReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContentParserTest {

    private fun parse(text: String?, subject: String? = null, title: String? = null) =
        SharedContentParser.parse(ShareInput(text, subject, title))

    private fun parsed(result: ShareParseResult) = result as ShareParseResult.Parsed
    private fun invalid(result: ShareParseResult) = (result as ShareParseResult.Invalid).reason

    // --- URL only (Facebook, Instagram, TikTok…)

    @Test
    fun `plain url is accepted unchanged`() {
        val result = parsed(parse("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("https://youtu.be/dQw4w9WgXcQ", result.url)
        assertNull(result.titleHint)
    }

    @Test
    fun `surrounding whitespace and newlines are ignored`() {
        assertEquals("https://example.com/a", parsed(parse("  \n https://example.com/a \n")).url)
    }

    // --- Title + URL, comment + URL (Reddit, Chrome, X, WhatsApp)

    @Test
    fun `text around the url becomes the title hint`() {
        val result = parsed(parse("Check this out https://example.com/page"))
        assertEquals("https://example.com/page", result.url)
        assertEquals("Check this out", result.titleHint)
    }

    @Test
    fun `subject is preferred as title hint`() {
        val result = parsed(parse("https://www.reddit.com/r/kotlin/comments/1/x/", subject = "Why Kotlin is great"))
        assertEquals("Why Kotlin is great", result.titleHint)
    }

    @Test
    fun `explicit title extra is used when there is no subject`() {
        assertEquals("My title", parsed(parse("https://example.com", title = "My title")).titleHint)
    }

    @Test
    fun `a subject that is itself the url is not used as title`() {
        assertNull(parsed(parse(null, subject = "https://example.com/only-in-subject")).titleHint)
    }

    @Test
    fun `url found only in the subject is accepted`() {
        assertEquals("https://example.com/s", parsed(parse(null, subject = "https://example.com/s")).url)
    }

    @Test
    fun `comment and trailing mention keep the comment`() {
        val result = parsed(parse("Mira esto https://x.com/u/status/1 vía @user"))
        assertEquals("https://x.com/u/status/1", result.url)
        assertTrue(result.titleHint!!.startsWith("Mira esto"))
    }

    @Test
    fun `only the first url is used`() {
        assertEquals("https://one.com/a", parsed(parse("a https://one.com/a b https://two.com/b")).url)
    }

    // --- Messy punctuation

    @Test
    fun `trailing punctuation is stripped`() {
        assertEquals("https://example.com/a", parsed(parse("Mira https://example.com/a.")).url)
        assertEquals("https://example.com/a", parsed(parse("¿Viste https://example.com/a?")).url)
        assertEquals("https://example.com/a", parsed(parse("(ver https://example.com/a)")).url)
    }

    @Test
    fun `closing parenthesis that belongs to the url is kept`() {
        val url = "https://en.wikipedia.org/wiki/Kotlin_(programming_language)"
        assertEquals(url, parsed(parse("Lee $url")).url)
    }

    @Test
    fun `www without scheme becomes https`() {
        assertEquals("https://www.example.com/x", parsed(parse("www.example.com/x")).url)
    }

    @Test
    fun `non ascii path is accepted`() {
        val url = "https://es.wikipedia.org/wiki/Canción"
        assertEquals(url, parsed(parse(url)).url)
    }

    // --- Invalid, empty and dangerous content

    @Test
    fun `null and blank input are empty`() {
        assertEquals(InvalidUrlReason.EMPTY, invalid(parse(null)))
        assertEquals(InvalidUrlReason.EMPTY, invalid(parse("")))
        assertEquals(InvalidUrlReason.EMPTY, invalid(parse("   \n\t ")))
    }

    @Test
    fun `plain text without a url is rejected`() {
        assertEquals(InvalidUrlReason.NO_URL, invalid(parse("hola mundo, esto no es un enlace")))
        assertEquals(InvalidUrlReason.NO_URL, invalid(parse("Nota: comprar leche")))
    }

    @Test
    fun `dangerous or unsupported schemes are rejected`() {
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/secret.txt",
            "content://com.android.contacts/contacts",
            "intent://scan/#Intent;scheme=zxing;end",
            "data:text/html;base64,PHNjcmlwdD4=",
            "ftp://example.com/file",
            "mailto:someone@example.com",
        ).forEach { input ->
            assertEquals(input, InvalidUrlReason.UNSUPPORTED_SCHEME, invalid(parse(input)))
        }
    }

    @Test
    fun `credentials in the url are rejected`() {
        assertEquals(InvalidUrlReason.MALFORMED, invalid(parse("http://user:pass@evil.example.com/")))
    }

    @Test
    fun `host without a dot is rejected`() {
        assertEquals(InvalidUrlReason.MALFORMED, invalid(parse("http://localhost/admin")))
    }

    @Test
    fun `very long url is rejected`() {
        assertEquals(InvalidUrlReason.TOO_LONG, invalid(parse("https://example.com/" + "a".repeat(2100))))
    }

    @Test
    fun `huge title hint is capped`() {
        val result = parsed(parse("https://example.com", subject = "x".repeat(500)))
        assertEquals(200, result.titleHint!!.length)
    }
}
