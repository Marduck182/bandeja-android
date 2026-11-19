package com.bandeja.app.data.remote

import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlNormalizer
import com.bandeja.app.domain.url.UrlValidator
import org.json.JSONObject
import java.net.URLEncoder

/**
 * oEmbed (oembed.com): the open, documented way for a site to describe a link it hosts. These providers answer
 * without any key or login, which is why their previews work where scraping the page does not (TikTok pages
 * are rendered by JavaScript). Facebook and Instagram are deliberately absent: Meta requires an app token for
 * oEmbed, and a token cannot safely live inside an APK.
 */
object OEmbed {
    private const val URL_PLACEHOLDER = "{url}"

    private val PROVIDERS = mapOf(
        "tiktok.com" to "https://www.tiktok.com/oembed?url=$URL_PLACEHOLDER",
        "vimeo.com" to "https://vimeo.com/api/oembed.json?url=$URL_PLACEHOLDER",
        "dailymotion.com" to "https://www.dailymotion.com/services/oembed?format=json&url=$URL_PLACEHOLDER",
        "open.spotify.com" to "https://open.spotify.com/oembed?url=$URL_PLACEHOLDER",
        "soundcloud.com" to "https://soundcloud.com/oembed?format=json&url=$URL_PLACEHOLDER",
    )

    /** The provider's oEmbed endpoint for [url], or `null` when the site is not a known provider. */
    fun endpointFor(url: String): String? {
        val parsed = (UrlValidator.validate(url) as? UrlCheck.Valid)?.url ?: return null
        val host = UrlNormalizer.sourceOf(parsed)
        // vm.tiktok.com (a short link) belongs to tiktok.com too.
        val template = PROVIDERS.entries.firstOrNull { (domain, _) -> host == domain || host.endsWith(".$domain") }?.value
        return template?.replace(URL_PLACEHOLDER, URLEncoder.encode(url, "UTF-8"))
    }

    /** Reads the standard fields (`title`, `thumbnail_url`, `type`). Anything missing or unsafe is left out. */
    fun parse(json: String): LinkMetadata? {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val title = obj.optString("title").replace(Regex("\\s+"), " ").trim().take(MAX_TITLE).ifEmpty { null }
        val thumbnail = obj.optString("thumbnail_url").takeIf { it.isNotBlank() }?.let(OpenGraphParser::safeImageUrl)
        val type = when (obj.optString("type")) {
            "video" -> ContentType.VIDEO
            "photo" -> ContentType.IMAGE
            else -> null
        }
        if (title == null && thumbnail == null) return null
        return LinkMetadata(title = title, thumbnailUrl = thumbnail, contentType = type)
    }

    /** Fields of [primary] win; [fallback] only fills what is missing. */
    fun merge(primary: LinkMetadata?, fallback: LinkMetadata?): LinkMetadata? {
        if (primary == null) return fallback
        if (fallback == null) return primary
        return LinkMetadata(
            title = primary.title ?: fallback.title,
            thumbnailUrl = primary.thumbnailUrl ?: fallback.thumbnailUrl,
            contentType = primary.contentType ?: fallback.contentType,
            publishedAt = primary.publishedAt ?: fallback.publishedAt,
        )
    }

    private const val MAX_TITLE = 200
}
