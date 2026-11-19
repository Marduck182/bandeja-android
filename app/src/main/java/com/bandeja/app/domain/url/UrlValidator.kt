package com.bandeja.app.domain.url

enum class InvalidUrlReason { EMPTY, NO_URL, TOO_LONG, MALFORMED, UNSUPPORTED_SCHEME }

data class ParsedUrl(
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
)

sealed interface UrlCheck {
    data class Valid(val url: ParsedUrl) : UrlCheck
    data class Invalid(val reason: InvalidUrlReason) : UrlCheck
}

/**
 * Parses and validates links received from outside the app.
 * Hand-rolled instead of `java.net.URI`: real shared links contain characters URI rejects (spaces, `|`, `{}`).
 */
object UrlValidator {
    const val MAX_LENGTH = 2048

    private val URL_PATTERN = Regex("""^([A-Za-z][A-Za-z0-9+.\-]*)://([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""")
    private const val LABEL = """[\p{L}\p{N}_](?:[\p{L}\p{N}_\-]*[\p{L}\p{N}_])?"""

    // A dot is required: it rejects "http://foo" while allowing IDN and ordinary domains.
    private val HOST_PATTERN = Regex("""^$LABEL(?:\.$LABEL)+$""")
    private val IPV6_PATTERN = Regex("""^\[[0-9A-Fa-f:.]+]$""")

    fun validate(raw: String?): UrlCheck {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return UrlCheck.Invalid(InvalidUrlReason.EMPTY)
        if (text.length > MAX_LENGTH) return UrlCheck.Invalid(InvalidUrlReason.TOO_LONG)
        if (text.any { it.isWhitespace() || it.isISOControl() }) return UrlCheck.Invalid(InvalidUrlReason.MALFORMED)

        val match = URL_PATTERN.matchEntire(text)
            ?: return UrlCheck.Invalid(
                if (SCHEME_LIKE.containsMatchIn(text)) InvalidUrlReason.UNSUPPORTED_SCHEME else InvalidUrlReason.MALFORMED,
            )
        val scheme = match.groupValues[1].lowercase()
        if (scheme != "http" && scheme != "https") return UrlCheck.Invalid(InvalidUrlReason.UNSUPPORTED_SCHEME)

        val authority = match.groupValues[2]
        val at = authority.lastIndexOf('@')
        // user:password@host is a classic phishing trick and has no place in a saved link.
        if (at >= 0) return UrlCheck.Invalid(InvalidUrlReason.MALFORMED)

        val (host, port) = splitHostPort(authority) ?: return UrlCheck.Invalid(InvalidUrlReason.MALFORMED)
        val cleanHost = host.trimEnd('.').lowercase()
        if (!HOST_PATTERN.matches(cleanHost) && !IPV6_PATTERN.matches(cleanHost)) {
            return UrlCheck.Invalid(InvalidUrlReason.MALFORMED)
        }
        return UrlCheck.Valid(
            ParsedUrl(
                scheme = scheme,
                userInfo = null,
                host = cleanHost,
                port = port,
                path = match.groupValues[3],
                query = match.groups[4]?.value,
                fragment = match.groups[5]?.value,
            ),
        )
    }

    fun isValid(raw: String?): Boolean = validate(raw) is UrlCheck.Valid

    private val SCHEME_LIKE = Regex("""^[A-Za-z][A-Za-z0-9+.\-]*:""")

    private fun splitHostPort(authority: String): Pair<String, Int?>? {
        if (authority.isEmpty()) return null
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end < 0) return null
            val rest = authority.substring(end + 1)
            val port = when {
                rest.isEmpty() -> null
                rest.startsWith(":") -> parsePort(rest.substring(1)) ?: return null
                else -> return null
            }
            return authority.substring(0, end + 1) to port
        }
        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority to null
        val port = parsePort(authority.substring(colon + 1)) ?: return null
        return authority.substring(0, colon) to port
    }

    private fun parsePort(text: String): Int? =
        text.takeIf { it.isNotEmpty() && it.length <= 5 && it.all(Char::isDigit) }?.toInt()?.takeIf { it in 1..65535 }
}
