package com.bandeja.app.data.remote

import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.url.ContentTypeDetector
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlValidator
import org.jsoup.Jsoup
import java.time.Instant
import java.time.OffsetDateTime

/** Extracts title, thumbnail, type and date from a page's HTML. Pure: no network, tested with fixtures. */
object OpenGraphParser {

    fun parse(html: String, pageUrl: String): LinkMetadata? {
        val doc = Jsoup.parse(html, pageUrl)
        fun meta(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            doc.select("meta[property=$key], meta[name=$key]").firstOrNull()?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }
        }

        val title = (meta("og:title", "twitter:title") ?: doc.title().trim()).clean()
        // absUrl resolves a relative image path against the page address.
        val image = listOf("og:image", "og:image:url", "twitter:image", "twitter:image:src")
            .firstNotNullOfOrNull { key ->
                doc.select("meta[property=$key], meta[name=$key]").firstOrNull { it.attr("content").isNotBlank() }
            }
            ?.absUrl("content")?.takeIf { it.isNotEmpty() }
            ?.let(::safeImageUrl)
        val ogType = meta("og:type")
        val parsed = (UrlValidator.validate(pageUrl) as? UrlCheck.Valid)?.url
        val type = parsed?.let { ContentTypeDetector.detect(it, ogType) }
        val published = meta("article:published_time", "og:article:published_time", "datePublished", "date")
            ?.let(::parseDate)

        if (title.isNullOrEmpty() && image == null) return null
        return LinkMetadata(title = title?.takeIf { it.isNotEmpty() }, thumbnailUrl = image, contentType = type, publishedAt = published)
    }

    /** Only web images; relative or `data:` values are discarded. Plain http is upgraded because cleartext is off. */
    private fun safeImageUrl(url: String): String? = when {
        url.startsWith("https://", ignoreCase = true) -> url
        url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
        url.startsWith("//") -> "https:$url"
        else -> null
    }?.takeIf { UrlValidator.isValid(it) }

    private fun String?.clean(): String? = this?.replace(Regex("\\s+"), " ")?.trim()?.take(MAX_TITLE)

    private fun parseDate(text: String): Long? =
        runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(text).toEpochMilli() }
            .getOrNull()

    private const val MAX_TITLE = 200
}
