package com.bandeja.app.domain.url

import com.bandeja.app.domain.model.ContentType

/** Best-effort guess of what a link is, from its address; the page's own `og:type` refines it when available. */
object ContentTypeDetector {

    fun detect(url: ParsedUrl, ogType: String? = null): ContentType {
        // The real host, not the name shown on screen: fb.watch is displayed as facebook.com but is always a video.
        val host = UrlNormalizer.canonicalHost(url.host)
        val path = url.path.lowercase()

        if (IMAGE_EXTENSIONS.any { path.endsWith(it) }) return ContentType.IMAGE
        // Subdomains count too: vm.tiktok.com and vt.tiktok.com are TikTok's short links.
        if (VIDEO_HOSTS.any { host == it || host.endsWith(".$it") }) return ContentType.VIDEO
        if (host == "instagram.com" && (path.startsWith("/reel") )) return ContentType.VIDEO
        if (host == "facebook.com" && VIDEO_FACEBOOK_PATHS.any { path.contains(it) }) return ContentType.VIDEO
        if (host in SOCIAL_HOSTS) return ContentType.SOCIAL
        if (host in CODE_HOSTS) return ContentType.CODE
        val og = ogType?.lowercase().orEmpty()
        if (og.startsWith("video")) return ContentType.VIDEO
        if (og == "article" || host in ARTICLE_HOSTS || host.endsWith(".substack.com") || host.endsWith(".wikipedia.org")) {
            return ContentType.ARTICLE
        }
        return ContentType.LINK
    }

    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif")
    private val VIDEO_HOSTS = setOf(
        "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "twitch.tv", "dailymotion.com", "fb.watch",
    )
    private val VIDEO_FACEBOOK_PATHS = listOf("/watch", "/reel", "/videos")
    private val SOCIAL_HOSTS = setOf(
        "instagram.com", "facebook.com", "fb.com", "x.com", "reddit.com", "threads.net", "linkedin.com", "pinterest.com",
    )
    private val CODE_HOSTS = setOf("github.com", "gitlab.com", "bitbucket.org", "codeberg.org", "stackoverflow.com")
    private val ARTICLE_HOSTS = setOf("medium.com", "substack.com", "dev.to", "wikipedia.org")
}
