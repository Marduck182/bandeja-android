package com.bandeja.app.domain.share

import com.bandeja.app.domain.url.InvalidUrlReason
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlValidator

/** Raw values from an `ACTION_SEND` intent, with no interpretation. */
data class ShareInput(
    val text: String?,
    val subject: String?,
    val title: String?,
)

sealed interface ShareParseResult {
    data class Parsed(val url: String, val titleHint: String?) : ShareParseResult
    data class Invalid(val reason: InvalidUrlReason) : ShareParseResult
}

/**
 * Apps share very different things: only the URL (Facebook, Instagram), "title + URL" (Reddit, Chrome),
 * "comment + URL" (X, WhatsApp) or URL in the subject. This converts all of them to one internal shape.
 * Pure Kotlin: it never touches the Intent, so it is tested on the JVM.
 */
object SharedContentParser {
    private const val MAX_TITLE_HINT = 200

    private val WEB_URL = Regex("""(?i)(?:https?://|www\.)[^\s<>"'`]+""")
    // "scheme://x" for any scheme, or the schemeless dangerous/system ones ("javascript:alert(1)", "mailto:a@b.c").
    private val UNSUPPORTED_SCHEME = Regex(
        """(?i)[a-z][a-z0-9+.\-]{0,31}://\S+|(?:javascript|vbscript|data|mailto|tel|sms|about|blob):\S+""",
    )
    private const val TRAILING_PUNCTUATION = ".,;:!?'\"»”’>]}"

    fun parse(input: ShareInput): ShareParseResult {
        val candidates = listOfNotNull(input.text, input.subject, input.title).filter { it.isNotBlank() }
        if (candidates.isEmpty()) return ShareParseResult.Invalid(InvalidUrlReason.EMPTY)

        for (candidate in candidates) {
            val match = WEB_URL.find(candidate) ?: continue
            val url = cleanUrl(match.value)
            return when (val check = UrlValidator.validate(url)) {
                is UrlCheck.Valid -> ShareParseResult.Parsed(url, titleHint(input, candidate, match.value, url))
                is UrlCheck.Invalid -> ShareParseResult.Invalid(check.reason)
            }
        }

        // No web URL anywhere: tell "something else was shared" (javascript:, file:, intent:…) from plain text.
        val unsupported = candidates.any { UNSUPPORTED_SCHEME.containsMatchIn(it) }
        return ShareParseResult.Invalid(if (unsupported) InvalidUrlReason.UNSUPPORTED_SCHEME else InvalidUrlReason.NO_URL)
    }

    private fun cleanUrl(found: String): String {
        var url = found
        while (url.isNotEmpty()) {
            val last = url.last()
            val strip = when {
                last in TRAILING_PUNCTUATION -> true
                // Keep ")" when the URL itself contains "(": Wikipedia links end like that.
                last == ')' -> !url.contains('(')
                else -> false
            }
            if (!strip) break
            url = url.dropLast(1)
        }
        return if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
    }

    private fun titleHint(input: ShareInput, from: String, rawMatch: String, url: String): String? {
        val explicit = listOfNotNull(input.title, input.subject)
            .map(::tidy)
            .firstOrNull { it.isNotEmpty() && !it.contains(rawMatch) && !WEB_URL.containsMatchIn(it) }
        if (explicit != null) return explicit.take(MAX_TITLE_HINT)

        // "Comment + URL": whatever surrounds the URL is a useful title.
        val remainder = tidy(from.replace(rawMatch, " ")).trim('-', '–', '—', ':', '|', ' ')
        return remainder.takeIf { it.isNotEmpty() && it != url && !WEB_URL.containsMatchIn(it) }?.take(MAX_TITLE_HINT)
    }

    private fun tidy(text: String): String = text.replace(Regex("\\s+"), " ").trim().trim('"', '“', '”')
}
