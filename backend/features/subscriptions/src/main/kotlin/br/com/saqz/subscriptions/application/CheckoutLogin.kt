package br.com.saqz.subscriptions.application

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

@JvmInline
value class CheckoutLoginCode private constructor(val value: String) {
    override fun toString(): String = "CheckoutLoginCode([REDACTED])"

    companion object {
        private val FORMAT = Regex("[A-Za-z0-9_-]{43}")

        fun from(value: String): CheckoutLoginCode? =
            value.takeIf(FORMAT::matches)?.let(::CheckoutLoginCode)
    }
}

class CheckoutLoginDigest private constructor(private val bytes: ByteArray) {
    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || other is CheckoutLoginDigest && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "CheckoutLoginDigest([REDACTED])"

    companion object {
        fun from(bytes: ByteArray): CheckoutLoginDigest {
            require(bytes.size == 32) { "Checkout login digest must contain 32 bytes" }
            return CheckoutLoginDigest(bytes.copyOf())
        }

        fun sha256(code: CheckoutLoginCode): CheckoutLoginDigest = from(
            MessageDigest.getInstance("SHA-256")
                .digest(code.value.toByteArray(StandardCharsets.US_ASCII)),
        )
    }
}

data class CheckoutLoginSecret(
    val code: CheckoutLoginCode,
    val digest: CheckoutLoginDigest,
) {
    override fun toString(): String = "CheckoutLoginSecret([REDACTED])"
}

fun interface CheckoutLoginSecrets {
    fun next(): CheckoutLoginSecret
}

class SecureCheckoutLoginSecrets(
    private val nextBytes: (ByteArray) -> Unit = SecureRandom()::nextBytes,
) : CheckoutLoginSecrets {
    override fun next(): CheckoutLoginSecret {
        val entropy = ByteArray(TOKEN_BYTES)
        nextBytes(entropy)
        val code = checkNotNull(CheckoutLoginCode.from(ENCODER.encodeToString(entropy)))
        return CheckoutLoginSecret(code, CheckoutLoginDigest.sha256(code))
    }

    private companion object {
        const val TOKEN_BYTES = 32
        val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

data class OpenCheckoutLogin(
    val id: UUID,
    val ownerUserId: UUID,
)

/**
 * One-time login for the purchase e-mail. The mailbox never sees a Firebase custom
 * token — only a high-entropy code whose digest lives here.
 */
interface CheckoutLoginTokens {
    fun issue(ownerUserId: UUID, now: Instant): String

    fun findOpen(rawToken: String, now: Instant): OpenCheckoutLogin?

    fun consume(id: UUID, consumedAt: Instant): Boolean
}

fun interface CheckoutIdentitySessions {
    fun customTokenFor(ownerUserId: UUID): String?
}

class CheckoutIdentityUnavailable(cause: Throwable? = null) : RuntimeException(cause)

class CheckoutLoginLinkFactory(
    private val tokens: CheckoutLoginTokens,
    purchaseUrl: String,
) {
    private val purchaseUrl = purchaseUrl.also {
        require('?' !in it && '#' !in it) { "Purchase URL must not include a query or fragment" }
    }

    fun issue(ownerUserId: UUID, now: Instant): String =
        "$purchaseUrl?t=${tokens.issue(ownerUserId, now)}"
}

sealed interface RedeemCheckoutLoginResult {
    data class Success(val customToken: String) : RedeemCheckoutLoginResult

    data object Invalid : RedeemCheckoutLoginResult
}

class RedeemCheckoutLogin(
    private val tokens: CheckoutLoginTokens,
    private val sessions: CheckoutIdentitySessions,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun execute(rawToken: String): RedeemCheckoutLoginResult {
        val now = clock.instant()
        val open = tokens.findOpen(rawToken, now) ?: return RedeemCheckoutLoginResult.Invalid
        val customToken = sessions.customTokenFor(open.ownerUserId) ?: run {
            tokens.consume(open.id, now)
            return RedeemCheckoutLoginResult.Invalid
        }
        tokens.consume(open.id, now)
        return RedeemCheckoutLoginResult.Success(customToken)
    }

    companion object {
        val TOKEN_TTL: Duration = Duration.ofHours(24)
    }
}
