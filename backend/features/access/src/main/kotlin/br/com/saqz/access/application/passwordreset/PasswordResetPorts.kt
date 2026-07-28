package br.com.saqz.access.application.passwordreset

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Digest SHA-256 de código ou token. O número de quatro dígitos nunca é persistido,
 * e a comparação é em tempo constante — sair no primeiro byte diferente vazaria o
 * código pelo tempo de resposta.
 */
class ResetDigest private constructor(private val bytes: ByteArray) {
    fun toByteArray(): ByteArray = bytes.copyOf()

    fun matches(other: ResetDigest): Boolean = MessageDigest.isEqual(bytes, other.bytes)

    override fun toString(): String = "ResetDigest([REDACTED])"

    companion object {
        fun from(bytes: ByteArray): ResetDigest {
            require(bytes.size == 32) { "Reset digest must contain 32 bytes" }
            return ResetDigest(bytes.copyOf())
        }

        /** O e-mail entra no digest para que a mesma tabela arco-íris não sirva a duas contas. */
        fun ofCode(email: String, code: String): ResetDigest = sha256("$email:$code")

        fun ofToken(token: String): ResetDigest = sha256(token)

        private fun sha256(value: String): ResetDigest = from(
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)),
        )
    }
}

data class StoredResetCode(
    val email: String,
    val codeDigest: ResetDigest,
    val attempts: Int,
    val createdAt: Instant,
    val expiresAt: Instant,
)

data class IpRequestWindow(val startedAt: Instant, val count: Int)

sealed interface ReplaceCodeOutcome {
    data object Replaced : ReplaceCodeOutcome

    data class TooSoon(val previousCreatedAt: Instant) : ReplaceCodeOutcome
}

interface PasswordResetRepository {
    /** Conta o pedido e devolve a janela vigente do IP, reiniciando-a se começou antes de [windowFloor]. */
    fun recordIpRequest(ip: String, now: Instant, windowFloor: Instant): IpRequestWindow

    /** Substitui o código do e-mail, mas só se o vigente foi criado até [resendFloor]. */
    fun replaceCode(code: StoredResetCode, resendFloor: Instant): ReplaceCodeOutcome

    fun findByEmail(email: String): StoredResetCode?

    fun recordAttempt(email: String, attempts: Int)

    fun issueToken(email: String, tokenDigest: ResetDigest, expiresAt: Instant)

    /** Apaga a linha e devolve o e-mail: o token vale uma vez só, e o código morre com ele. */
    fun consumeToken(tokenDigest: ResetDigest, now: Instant): String?

    fun delete(email: String)
}

/** Contas do provedor de identidade. Quem implementa fala com o Firebase Admin SDK. */
interface PasswordAccounts {
    fun exists(email: String): Boolean

    /** `false` quando não há conta com este e-mail. */
    fun updatePassword(email: String, newPassword: String): Boolean
}

fun interface ResetCodeNotifier {
    fun send(recipient: String, code: String, validity: Duration)
}

interface ResetSecrets {
    fun code(): String

    fun token(): String
}

class SecureResetSecrets(private val random: SecureRandom = SecureRandom()) : ResetSecrets {
    override fun code(): String = random.nextInt(10_000).toString().padStart(4, '0')

    override fun token(): String = ENCODER.encodeToString(ByteArray(32).also(random::nextBytes))

    private companion object {
        val ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
