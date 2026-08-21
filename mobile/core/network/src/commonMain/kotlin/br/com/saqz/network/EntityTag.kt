package br.com.saqz.network

import io.ktor.http.HttpHeaders

/**
 * Returns the strong form of an entity tag. Cloudflare weakens `"7"` to `W/"7"`
 * when it recompresses a JSON body; HttpURLConnection may also drop the quotes.
 * The API's If-Match parser only accepts a quoted positive version.
 */
fun String.toStrongEntityTag(): String {
    val trimmed = trim()
    if (trimmed == "*") return trimmed
    val opaque = if (trimmed.startsWith("W/")) trimmed.substring(2).trim() else trimmed
    if (opaque.isEmpty()) return trimmed
    return if (opaque.startsWith("\"")) opaque else "\"$opaque\""
}

internal fun isEntityTagHeader(name: String): Boolean =
    name.equals(HttpHeaders.ETag, ignoreCase = true) ||
        name.equals(HttpHeaders.IfMatch, ignoreCase = true) ||
        name.equals(HttpHeaders.IfNoneMatch, ignoreCase = true)
