package com.bandeja.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * When a site gives no thumbnail (Facebook and Instagram need a login) the tile is drawn in that site's own
 * color with a recognizable mark: a saved link then still says "this is from Instagram" at a glance.
 */
data class PlatformBrand(val color: Color, val mark: String)

fun platformBrand(source: String?): PlatformBrand? {
    val host = source?.lowercase() ?: return null
    fun isSite(vararg domains: String) = domains.any { host == it || host.endsWith(".$it") }
    return when {
        isSite("facebook.com", "fb.com", "fb.watch") -> PlatformBrand(Color(0xFF1877F2), "f")
        isSite("instagram.com") -> PlatformBrand(Color(0xFFC13584), "📷")
        isSite("tiktok.com") -> PlatformBrand(Color(0xFF111111), "♪")
        isSite("x.com", "twitter.com") -> PlatformBrand(Color(0xFF000000), "X")
        isSite("reddit.com") -> PlatformBrand(Color(0xFFFF4500), "r/")
        isSite("linkedin.com") -> PlatformBrand(Color(0xFF0A66C2), "in")
        isSite("threads.net") -> PlatformBrand(Color(0xFF000000), "@")
        isSite("pinterest.com") -> PlatformBrand(Color(0xFFE60023), "P")
        isSite("twitch.tv") -> PlatformBrand(Color(0xFF9146FF), "▶")
        isSite("youtube.com", "youtu.be") -> PlatformBrand(Color(0xFFFF0000), "▶")
        else -> null
    }
}
