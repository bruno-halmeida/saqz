package br.com.saqz.groups.application.attendance.share

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@JvmInline
value class AttendanceLinkCode private constructor(val value: String) {
    override fun toString(): String = "AttendanceLinkCode([REDACTED])"

    companion object {
        private val FORMAT = Regex("[A-Za-z0-9_-]{43}")

        fun from(value: String): AttendanceLinkCode {
            require(FORMAT.matches(value)) {
                "Attendance link code must be an unpadded 32-byte Base64URL value"
            }
            return AttendanceLinkCode(value)
        }
    }
}

class AttendanceLinkTokenDigest private constructor(private val bytes: ByteArray) {
    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || other is AttendanceLinkTokenDigest && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "AttendanceLinkTokenDigest([REDACTED])"

    companion object {
        fun from(bytes: ByteArray): AttendanceLinkTokenDigest {
            require(bytes.size == 32) { "Attendance link token digest must contain 32 bytes" }
            return AttendanceLinkTokenDigest(bytes.copyOf())
        }

        fun sha256(code: AttendanceLinkCode): AttendanceLinkTokenDigest = from(
            MessageDigest.getInstance("SHA-256")
                .digest(code.value.toByteArray(StandardCharsets.US_ASCII)),
        )
    }
}

data class AttendanceLinkToken(
    val code: AttendanceLinkCode,
    val digest: AttendanceLinkTokenDigest,
)

fun interface AttendanceLinkTokenGenerator {
    fun generate(): AttendanceLinkToken
}

fun interface AttendanceLinkFactory {
    fun create(code: AttendanceLinkCode): URI
}
