package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.sharedkernel.RequestIdentity
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

class AppOnboardingCode private constructor(val value: String) {
    override fun toString(): String = "AppOnboardingCode([REDACTED])"

    companion object {
        private val FORMAT = Regex("[A-Za-z0-9_-]{43}")

        fun from(value: String): AppOnboardingCode? =
            value.takeIf(FORMAT::matches)?.let(::AppOnboardingCode)
    }
}

class AppOnboardingDigest private constructor(private val bytes: ByteArray) {
    fun toByteArray(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?): Boolean =
        this === other || other is AppOnboardingDigest && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "AppOnboardingDigest([REDACTED])"

    companion object {
        fun from(bytes: ByteArray): AppOnboardingDigest {
            require(bytes.size == 32) { "App onboarding digest must contain 32 bytes" }
            return AppOnboardingDigest(bytes.copyOf())
        }

        fun sha256(code: AppOnboardingCode): AppOnboardingDigest = from(
            MessageDigest.getInstance("SHA-256")
                .digest(code.value.toByteArray(StandardCharsets.US_ASCII)),
        )
    }
}

data class AppOnboardingSecret(
    val code: AppOnboardingCode,
    val digest: AppOnboardingDigest,
) {
    override fun toString(): String = "AppOnboardingSecret([REDACTED])"
}

fun interface AppOnboardingSecrets {
    fun next(): AppOnboardingSecret
}

class SecureAppOnboardingSecrets(
    private val nextBytes: (ByteArray) -> Unit = SecureRandom()::nextBytes,
) : AppOnboardingSecrets {
    override fun next(): AppOnboardingSecret {
        val entropy = ByteArray(32)
        nextBytes(entropy)
        val code = checkNotNull(AppOnboardingCode.from(ENCODER.encodeToString(entropy)))
        return AppOnboardingSecret(code, AppOnboardingDigest.sha256(code))
    }

    private companion object {
        val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

data class AppOnboardingIssuedCode(
    val ownerUserId: UUID,
    val code: AppOnboardingCode,
    val expiresAt: Instant,
) {
    override fun toString(): String = "AppOnboardingIssuedCode(ownerUserId=$ownerUserId, code=[REDACTED], expiresAt=$expiresAt)"
}

data class AppOnboardingOwner(
    val ownerUserId: UUID,
    val firebaseSubject: String,
    val displayName: AccessName,
    val onboardingCompleted: Boolean = false,
)

interface AppOnboardingTokenStore {
    fun issue(subject: String, now: Instant): AppOnboardingIssuedCode?

    fun consumeOpen(code: AppOnboardingCode, now: Instant): AppOnboardingOwner?

    fun onboardingCompleted(ownerUserId: UUID): Boolean

    fun completeOnboarding(ownerUserId: UUID): Boolean
}

class IssueAppOnboardingLink(
    private val tokens: AppOnboardingTokenStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun execute(identity: RequestIdentity): AppOnboardingIssuedCode? =
        tokens.issue(identity.subject, clock.instant())

    companion object {
        val TOKEN_TTL: Duration = Duration.ofMinutes(10)
    }
}
