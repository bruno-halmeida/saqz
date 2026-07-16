package br.com.saqz.access.domain

@JvmInline
value class AccessName private constructor(
    val value: String,
) {
    companion object {
        fun from(raw: String): AccessName {
            val normalized = raw.trim()
            val length = normalized.codePointCount(0, normalized.length)
            require(length in 2..80) { "name must contain between 2 and 80 characters" }
            require(normalized.codePoints().noneMatch(Character::isISOControl)) {
                "name must not contain control characters"
            }
            return AccessName(normalized)
        }
    }
}
