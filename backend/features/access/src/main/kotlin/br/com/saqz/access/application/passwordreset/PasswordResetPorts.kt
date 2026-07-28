package br.com.saqz.access.application.passwordreset

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Digest de código ou token. O número de quatro dígitos nunca é persistido, e a
 * comparação é em tempo constante — sair no primeiro byte diferente vazaria o código
 * pelo tempo de resposta.
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
    }
}

/**
 * HMAC-SHA256 com chave de servidor. Hash puro não serve aqui: quatro dígitos são dez
 * mil possibilidades, e quem tivesse leitura do banco inverteria um SHA-256 em
 * milissegundos. Com a chave fora do banco, o dump sozinho não vale nada.
 *
 * O e-mail continua dentro da mensagem para que a mesma tabela não sirva a duas contas.
 */
class ResetSecretHasher(secret: String) {
    init {
        require(secret.length >= MIN_SECRET_LENGTH) {
            "Password reset secret must have at least $MIN_SECRET_LENGTH characters"
        }
    }

    private val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM)

    fun ofCode(email: String, code: String): ResetDigest = mac("codigo:$email:$code")

    fun ofToken(token: String): ResetDigest = mac("token:$token")

    private fun mac(message: String): ResetDigest = ResetDigest.from(
        Mac.getInstance(ALGORITHM)
            .apply { init(key) }
            .doFinal(message.toByteArray(StandardCharsets.UTF_8)),
    )

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val MIN_SECRET_LENGTH = 32
    }
}

data class NewResetCode(
    val email: String,
    val codeDigest: ResetDigest,
    val createdAt: Instant,
    val expiresAt: Instant,
)

data class RateLimitWindow(val startedAt: Instant, val count: Int)

sealed interface ReplaceCodeOutcome {
    data object Replaced : ReplaceCodeOutcome

    data class TooSoon(val previousCreatedAt: Instant) : ReplaceCodeOutcome
}

sealed interface AttemptOutcome {
    /** A tentativa foi contada; [attempts] é o valor resultante, não o lido antes. */
    data class Consumed(val codeDigest: ResetDigest, val attempts: Int) : AttemptOutcome

    /** O código existe mas já gastou o teto de tentativas. */
    data object Exhausted : AttemptOutcome
}

interface PasswordResetRepository {
    /** Conta o acesso e devolve a janela vigente do balde, reiniciando-a se começou antes de [windowFloor]. */
    fun recordRateLimit(bucket: String, now: Instant, windowFloor: Instant): RateLimitWindow

    /** Substitui o código do e-mail, mas só se o vigente foi criado até [resendFloor]. */
    fun replaceCode(code: NewResetCode, resendFloor: Instant): ReplaceCodeOutcome

    /**
     * Incrementa a tentativa e devolve o estado resultante num **único statement**.
     *
     * Ler o contador, comparar o digest e só então gravar deixaria mil palpites
     * simultâneos enxergarem zero e gravarem um — o teto simplesmente não existiria
     * sob concorrência. Aqui cada chamada concorrente recebe o seu próprio valor.
     *
     * `null` quando não há código verificável: inexistente, expirado, ou já trocado
     * por um token.
     */
    fun consumeAttempt(email: String, now: Instant, ceiling: Int): AttemptOutcome?

    /**
     * Troca o código pelo token no mesmo UPDATE, condicionado a ainda haver código.
     * `false` quando outra requisição chegou primeiro.
     */
    fun issueToken(email: String, tokenDigest: ResetDigest, expiresAt: Instant): Boolean

    /** Apaga a linha do código estourado, sem tocar em linha que já carrega token. */
    fun retireCode(email: String)

    /** Apaga a linha e devolve o e-mail: o token vale uma vez só. */
    fun consumeToken(tokenDigest: ResetDigest, now: Instant): String?
}

/** Sinaliza que o provedor de identidade não respondeu — distinto de conta inexistente. */
class PasswordAccountsUnavailable(cause: Throwable? = null) : RuntimeException(cause)

/** Contas do provedor de identidade. Quem implementa fala com o Firebase Admin SDK. */
interface PasswordAccounts {
    /** @throws PasswordAccountsUnavailable quando o provedor falha por qualquer outro motivo. */
    fun exists(email: String): Boolean

    /** `false` somente quando não há conta com este e-mail. */
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
