package br.com.saqz.groups.adapter.input.http

/**
 * Group versions travel as strong ETags (`"7"`). Proxies that recompress JSON
 * (Cloudflare) may weaken that to `W/"7"`. Optimistic concurrency still keys
 * off the integer, so both forms parse to the same version.
 */
internal object QuotedResourceVersion {
    private val PATTERN = Regex("""(?:W/)?"([1-9][0-9]*)"""")

    fun parseRequired(raw: String?): Long {
        if (raw == null) throw PreconditionRequiredException()
        return parse(raw)
    }

    fun parse(raw: String): Long =
        PATTERN.matchEntire(raw.trim())?.groupValues?.get(1)?.toLongOrNull()
            ?: throw InvalidGroupRequestException(
                mapOf("ifMatch" to listOf("must be a quoted positive version")),
            )
}
