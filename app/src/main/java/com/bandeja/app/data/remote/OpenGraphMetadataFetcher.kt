package com.bandeja.app.data.remote

import com.bandeja.app.domain.model.ContentType
import com.bandeja.app.domain.model.LinkMetadata
import com.bandeja.app.domain.repository.MetadataFetcher
import com.bandeja.app.domain.url.UrlCheck
import com.bandeja.app.domain.url.UrlValidator
import com.bandeja.app.domain.url.YouTubeLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

/**
 * Fetches a page's preview. Hard limits: https only, public hosts only (re-checked on every redirect),
 * at most 8 s in total (a real deadline, checked between reads and enforced by disconnecting on cancellation),
 * 512 KB read, HTML only, no cookies. Any failure is `null`.
 */
class OpenGraphMetadataFetcher : MetadataFetcher {

    override suspend fun fetch(url: String): LinkMetadata? = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
        val deadline = System.nanoTime() + TOTAL_TIMEOUT_MS * NANOS_PER_MS
        val active = AtomicReference<HttpURLConnection?>()
        coroutineScope {
            val worker = async(Dispatchers.IO) { runCatching { load(url, deadline, active) }.getOrNull() }
            try {
                worker.await()
            } catch (e: CancellationException) {
                // Blocking socket reads do not react to cancellation; closing the connection makes them fail now.
                active.get()?.disconnect()
                throw e
            }
        }
    }

    private fun load(url: String, deadline: Long, active: AtomicReference<HttpURLConnection?>): LinkMetadata? {
        if (!UrlValidator.isValid(url)) return null
        if (YouTubeLinks.videoId(url) != null) youTubeOEmbed(url, deadline, active)?.let { return it }
        val html = download(url, deadline, active) ?: return null
        return OpenGraphParser.parse(html.body, html.finalUrl)
    }

    /** YouTube's pages need consent/JS; its oEmbed endpoint is the stable, key-less way to get the title. */
    private fun youTubeOEmbed(url: String, deadline: Long, active: AtomicReference<HttpURLConnection?>): LinkMetadata? {
        val endpoint = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(url, "UTF-8")
        val body = download(endpoint, deadline, active, expectHtml = false)?.body ?: return null
        val json = JSONObject(body)
        val id = YouTubeLinks.videoId(url)
        return LinkMetadata(
            title = json.optString("title").trim().take(200).ifEmpty { null },
            thumbnailUrl = id?.let(YouTubeLinks::thumbnailUrl) ?: json.optString("thumbnail_url").ifEmpty { null },
            contentType = ContentType.VIDEO,
        )
    }

    private class Page(val body: String, val finalUrl: String)

    private fun remainingMs(deadline: Long): Long = (deadline - System.nanoTime()) / NANOS_PER_MS

    private fun download(
        startUrl: String,
        deadline: Long,
        active: AtomicReference<HttpURLConnection?>,
        expectHtml: Boolean = true,
    ): Page? {
        var current = toHttps(startUrl) ?: return null
        repeat(MAX_REDIRECTS + 1) {
            val budget = remainingMs(deadline)
            if (budget <= 0) return null
            val check = UrlValidator.validate(current) as? UrlCheck.Valid ?: return null
            if (!HostGuard.resolvesToPublicOnly(check.url.host)) return null

            // Neither connecting nor a single read may outlast what is left of the overall budget.
            val timeout = minOf(CONNECT_TIMEOUT_MS.toLong(), budget).toInt()
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeout
                readTimeout = timeout
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", if (expectHtml) "text/html,application/xhtml+xml" else "application/json")
                setRequestProperty("Accept-Language", "es,en;q=0.8")
            }
            active.set(connection)
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return null
                    current = toHttps(URL(URL(current), location).toString()) ?: return null
                    return@repeat
                }
                if (code !in 200..299) return null
                val type = connection.contentType.orEmpty().lowercase()
                if (expectHtml && !type.contains("html")) return null
                val charset = Regex("charset=([\\w-]+)").find(type)?.groupValues?.get(1) ?: "UTF-8"
                return Page(readLimited(connection, deadline).toString(charset), current)
            } finally {
                connection.disconnect()
                active.compareAndSet(connection, null)
            }
        }
        return null
    }

    /** Stops at [MAX_BYTES] or at the deadline, so a server dripping one byte at a time cannot hold the thread. */
    private fun readLimited(connection: HttpURLConnection, deadline: Long): ByteArrayOutputStream {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        connection.inputStream.use { input ->
            while (out.size() < MAX_BYTES && remainingMs(deadline) > 0) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, minOf(n, MAX_BYTES - out.size()))
            }
        }
        return out
    }

    private fun toHttps(url: String): String? = when {
        url.startsWith("https://", ignoreCase = true) -> url
        url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring(7)
        else -> null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val TOTAL_TIMEOUT_MS = 8_000L
        const val NANOS_PER_MS = 1_000_000L
        const val MAX_BYTES = 512 * 1024
        const val MAX_REDIRECTS = 3
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }
}
