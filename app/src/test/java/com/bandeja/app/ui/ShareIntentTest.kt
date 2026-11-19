package com.bandeja.app.ui

import android.content.Intent
import com.bandeja.app.domain.share.ShareParseResult
import com.bandeja.app.domain.share.SharedContentParser
import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.toShareInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Intent → ShareInput step: what other apps really send, including broken or hostile Intents. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShareIntentTest {

    private fun send(text: Any? = null, subject: Any? = null, title: Any? = null, action: String = Intent.ACTION_SEND) =
        Intent(action).setType("text/plain").apply {
            fun put(name: String, value: Any?) = when (value) {
                null -> this
                is CharSequence -> putExtra(name, value)
                is Int -> putExtra(name, value)
                else -> error("unsupported test value")
            }
            put(Intent.EXTRA_TEXT, text); put(Intent.EXTRA_SUBJECT, subject); put(Intent.EXTRA_TITLE, title)
        }

    private fun result(intent: Intent) = SharedContentParser.parse(intent.toShareInput())

    @Test
    fun `youtube style intent, only the url in the text`() {
        val parsed = result(send(text = "https://youtu.be/dQw4w9WgXcQ?si=abc")) as ShareParseResult.Parsed
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc", parsed.url)
    }

    @Test
    fun `chrome style intent, subject is the page title`() {
        val parsed = result(send(text = "https://example.com/article", subject = "Un buen artículo")) as ShareParseResult.Parsed
        assertEquals("Un buen artículo", parsed.titleHint)
    }

    @Test
    fun `x style intent, comment plus link`() {
        val parsed = result(send(text = "Esto está genial https://x.com/user/status/123")) as ShareParseResult.Parsed
        assertEquals("https://x.com/user/status/123", parsed.url)
        assertEquals("Esto está genial", parsed.titleHint)
    }

    @Test
    fun `intent title extra is read`() {
        val input = send(text = "https://example.com", title = "Titulo extra").toShareInput()
        assertEquals("Titulo extra", input.title)
    }

    @Test
    fun `intent without extras is empty`() {
        val reason = (result(send()) as ShareParseResult.Invalid).reason
        assertEquals(InvalidUrlReason.EMPTY, reason)
    }

    @Test
    fun `other actions are ignored`() {
        val input = send(text = "https://example.com", action = Intent.ACTION_VIEW).toShareInput()
        assertNull(input.text)
        assertEquals(InvalidUrlReason.EMPTY, (SharedContentParser.parse(input) as ShareParseResult.Invalid).reason)
    }

    @Test
    fun `an extra of the wrong type does not crash`() {
        val input = send(text = 42).toShareInput()
        assertNull(input.text)
    }

    @Test
    fun `huge shared text is capped before parsing`() {
        val input = send(text = "https://example.com/" + "a".repeat(50_000)).toShareInput()
        assertEquals(4_096, input.text!!.length)
        assertTrue(SharedContentParser.parse(input) is ShareParseResult.Invalid)
    }
}
