package br.com.saqz.access.application.invite

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@JvmInline
value class InviteCode private constructor(val value: String) {
    override fun toString(): String = "InviteCode([REDACTED])"

    companion object {
        private val FORMAT = Regex("[A-Za-z0-9_-]{43}")

        fun from(value: String): InviteCode {
            require(FORMAT.matches(value)) { "Invite code must be an unpadded 32-byte Base64URL value" }
            return InviteCode(value)
        }
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
