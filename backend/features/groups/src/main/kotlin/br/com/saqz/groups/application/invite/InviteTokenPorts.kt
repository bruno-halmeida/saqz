package br.com.saqz.groups.application.invite

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Código de convite em um dos dois formatos aceitos:
 * - curto: 8 caracteres de [SHORT_ALPHABET] (sem 0/O/1/I/L), mostrado como `44DF-8SA8`. Aceito em
 *   maiúscula ou minúscula, com ou sem hífen/espaço; [value] é sempre a forma canônica (`44DF8SA8`).
 * - longo (legado): 43 caracteres Base64URL, dos links já enviados antes do código curto.
 */
@JvmInline
value class InviteCode private constructor(val value: String) {
    val isShort: Boolean get() = value.length == SHORT_LENGTH

    /** Forma para mostrar a pessoas: `44DF-8SA8` para o curto; o longo segue como está. */
    val display: String get() = if (isShort) "${value.substring(0, 4)}-${value.substring(4)}" else value

    override fun toString(): String = "InviteCode([REDACTED])"

    companion object {
        const val SHORT_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
        const val SHORT_LENGTH = 8
        private val LONG_FORMAT = Regex("[A-Za-z0-9_-]{43}")
        private val SHORT_FORMAT = Regex("[2-9A-HJKMNP-Z]{8}")
        private val SHORT_SEPARATORS = Regex("[-\\s]")

        fun from(value: String): InviteCode {
            val text = value.trim()
            if (LONG_FORMAT.matches(text)) return InviteCode(text)
            val short = text.replace(SHORT_SEPARATORS, "").uppercase()
            require(SHORT_FORMAT.matches(short)) { "Invite code must be a short code or an unpadded 32-byte Base64URL value" }
            return InviteCode(short)
        }

        fun fromShort(value: String): InviteCode = from(value).also { require(it.isShort) { "Not a short invite code" } }
    }
}

class InviteTokenDigest private constructor(private val bytes: ByteArray) {
    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || other is InviteTokenDigest && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "InviteTokenDigest([REDACTED])"

    companion object {
        fun from(bytes: ByteArray): InviteTokenDigest {
            require(bytes.size == 32) { "Invite token digest must contain 32 bytes" }
            return InviteTokenDigest(bytes.copyOf())
        }

        fun sha256(code: InviteCode): InviteTokenDigest = from(
            MessageDigest.getInstance("SHA-256")
                .digest(code.value.toByteArray(StandardCharsets.US_ASCII)),
        )
    }
}

data class InviteToken(
    val code: InviteCode,
    val digest: InviteTokenDigest,
)

fun interface SecureTokenGenerator {
    fun generate(): InviteToken
}

fun interface InviteLinkFactory {
    fun create(code: InviteCode): URI
}
