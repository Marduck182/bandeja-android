package com.bandeja.app.domain.url

/**
 * Turns different spellings of the same page into one string, so duplicates are detected
 * even when the link comes from another app (mobile site, tracking parameters, short links…).
 * Never used to *open* the link: the original URL is always what the user opens.
 */
object UrlNormalizer {

    /** Returns the normalized form, or `null` if [raw] is not a valid web URL. */
    fun normalize(raw: String): String? {
        val check = UrlValidator.validate(raw) as? UrlCheck.Valid ?: return null
        return normalize(check.url)
    }

    fun normalize(url: ParsedUrl): String {
        val host = canonicalHost(url.host)
        val path = url.path.ifEmpty { "/" }

        youTubeCanonical(host, path, url.query)?.let { return it }

        val keptQuery = if (host in HOSTS_WITHOUT_QUERY) emptyList() else cleanQuery(host, url.query)
        val normalizedPath = path.trimEnd('/').let { if (it.isEmpty()) "" else collapseSlashes(it) }
        val port = url.port?.takeUnless { it == 80 || it == 443 }

        return buildString {
            append("https://").append(host)
            if (port != null) append(':').append(port)
            append(normalizedPath)
            if (keptQuery.isNotEmpty()) append('?').append(keptQuery.joinToString("&"))
        }
    }

    /** Domain shown to the user: normalized host without `www.`/`m.`. */
    fun sourceOf(url: ParsedUrl): String = canonicalHost(url.host).let { DISPLAY_ALIASES[it] ?: it }

    internal fun canonicalHost(rawHost: String): String {
        var host = rawHost.lowercase().trimEnd('.')
        // Only strip a prefix when a real domain remains ("m.com" must stay "m.com").
        for (prefix in listOf("www.", "m.", "mobile.")) {
            if (host.startsWith(prefix) && host.removePrefix(prefix).contains('.')) {
                host = host.removePrefix(prefix)
                break
            }
        }
        return HOST_ALIASES[host] ?: host
    }

    private fun youTubeCanonical(host: String, path: String, query: String?): String? {
        val isShort = host == "youtu.be"
        if (!isShort && host != "youtube.com" && host != "youtube-nocookie.com") return null
        val id = YouTubeLinks.videoId(host, path, query) ?: return null
        val isShorts = path.startsWith("/shorts/")
        return if (isShorts) "https://youtube.com/shorts/$id" else "https://youtube.com/watch?v=$id"
    }

    private fun cleanQuery(host: String, query: String?): List<String> {
        if (query.isNullOrEmpty()) return emptyList()
        val hostSpecific = HOST_SPECIFIC_TRACKING[host].orEmpty()
        return query.split('&')
            .filter { it.isNotEmpty() }
            .filterNot { pair ->
                val key = pair.substringBefore('=').lowercase()
                key.startsWith("utm_") || key in TRACKING_KEYS || key in hostSpecific
            }
            .sortedWith(compareBy({ it.substringBefore('=') }, { it }))
    }

    private fun collapseSlashes(path: String): String = path.replace(Regex("/{2,}"), "/")

    private val TRACKING_KEYS = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "igshid", "igsh", "mc_cid", "mc_eid", "yclid", "twclid", "_ga", "ref_src",
    )

    private val HOST_SPECIFIC_TRACKING = mapOf(
        "youtube.com" to setOf("si", "feature", "pp"),
        "open.spotify.com" to setOf("si", "context"),
    )

    // The path identifies the post on these sites; every query parameter is share/tracking noise.
    private val HOSTS_WITHOUT_QUERY = setOf("x.com", "instagram.com", "tiktok.com", "reddit.com", "threads.net")

    // Short-link domains shown as the site they belong to (the normalized URL itself keeps the real host).
    private val DISPLAY_ALIASES = mapOf("youtu.be" to "youtube.com")

    private val HOST_ALIASES = mapOf("twitter.com" to "x.com", "old.reddit.com" to "reddit.com", "new.reddit.com" to "reddit.com")
}

object YouTubeLinks {
    private val ID_PATTERN = Regex("^[A-Za-z0-9_-]{6,20}$")

    fun videoId(rawUrl: String): String? {
        val parsed = (UrlValidator.validate(rawUrl) as? UrlCheck.Valid)?.url ?: return null
        val host = UrlNormalizer.canonicalHost(parsed.host)
        return videoId(host, parsed.path.ifEmpty { "/" }, parsed.query)
    }

    private val YOUTUBE_HOSTS = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")

    /** [host] must already be lowercase without `www.`/`m.`. Any other site returns `null`, whatever its path. */
    fun videoId(host: String, path: String, query: String?): String? {
        if (host !in YOUTUBE_HOSTS) return null
        val candidate = when {
            host == "youtu.be" -> path.trim('/').substringBefore('/')
            path == "/watch" -> query?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
            else -> listOf("/shorts/", "/embed/", "/live/", "/v/")
                .firstOrNull { path.startsWith(it) }
                ?.let { path.removePrefix(it).substringBefore('/') }
        }
        return candidate?.takeIf { ID_PATTERN.matches(it) }
    }

    fun thumbnailUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
