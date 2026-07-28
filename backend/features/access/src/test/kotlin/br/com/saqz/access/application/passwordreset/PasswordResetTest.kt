package br.com.saqz.access.application.passwordreset

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O teto sob concorrência não se prova aqui: um repositório de mentira sempre é
 * atômico. Quem prova é o `JdbcPasswordResetRepositoryIntegrationTest`, com threads
 * de verdade contra o Postgres.
 */
class PasswordResetTest {
    private val clock = MovableClock(Instant.parse("2026-07-28T12:00:00Z"))
    private val repository = InMemoryPasswordResetRepository()
    private val accounts = FakePasswordAccounts(mutableMapOf("atleta@saqz.test" to "senha-antiga"))
    private val notifier = RecordingNotifier()
    private val secrets = FixedSecrets()
    private val hasher = ResetSecretHasher("segredo-de-teste-com-trinta-e-dois")
    private val useCase = PasswordReset(repository, accounts, notifier, secrets, hasher, clock)

    @Test
    fun `envia o codigo para quem tem conta`() {
        assertEquals(RequestCodeResult.Accepted, useCase.request("atleta@saqz.test", "10.0.0.1"))

        assertEquals(listOf(Triple("atleta@saqz.test", "1234", Duration.ofMinutes(10))), notifier.sent)
    }

    @Test
    fun `aceita e-mail sem conta sem enviar nada`() {
        assertEquals(RequestCodeResult.Accepted, useCase.request("ninguem@saqz.test", "10.0.0.1"))

        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun `normaliza o e-mail antes de guardar e enviar`() {
        useCase.request("  Atleta@SAQZ.test ", "10.0.0.1")

        assertEquals("atleta@saqz.test", notifier.sent.single().first)
        assertEquals("atleta@saqz.test", repository.codes.keys.single())
    }

    @Test
    fun `guarda o digest e nunca o numero`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        val stored = repository.codes.getValue("atleta@saqz.test")
        assertTrue(stored.codeDigest!!.matches(hasher.ofCode("atleta@saqz.test", "1234")))
        assertEquals("ResetDigest([REDACTED])", stored.codeDigest.toString())
        assertNotEquals(
            hasher.ofCode("outro@saqz.test", "1234").toByteArray().toList(),
            stored.codeDigest.toByteArray().toList(),
        )
    }

    @Test
    fun `o digest depende do segredo do servidor, nao so do conteudo do banco`() {
        val outro = ResetSecretHasher("outro-segredo-com-trinta-e-dois-c")

        assertNotEquals(
            hasher.ofCode("atleta@saqz.test", "1234").toByteArray().toList(),
            outro.ofCode("atleta@saqz.test", "1234").toByteArray().toList(),
        )
        assertNotEquals(
            hasher.ofToken("token-secreto").toByteArray().toList(),
            outro.ofToken("token-secreto").toByteArray().toList(),
        )
    }

    @Test
    fun `segredo curto demais nao monta o hasher`() {
        assertFailsWith<IllegalArgumentException> { ResetSecretHasher("curto") }
    }

    @Test
    fun `e-mail malformado sai como aceito sem guardar nem enviar`() {
        listOf("", "sem-arroba", "a@b", "duplo@@saqz.test", "com espaco@saqz.test").forEach {
            assertEquals(RequestCodeResult.Accepted, useCase.request(it, "10.0.0.1"))
        }

        assertTrue(repository.codes.isEmpty())
        assertTrue(notifier.sent.isEmpty())
    }

    @Test
    fun `falha de entrega responde igual a e-mail inexistente`() {
        notifier.failure = IllegalStateException("SMTP fora do ar")

        val existente = useCase.request("atleta@saqz.test", "10.0.0.1")
        val inexistente = useCase.request("ninguem@saqz.test", "10.0.0.2")

        assertEquals(RequestCodeResult.Accepted, existente)
        assertEquals(RequestCodeResult.Accepted, inexistente)
    }

    @Test
    fun `provedor de identidade fora do ar sobe como indisponibilidade`() {
        accounts.unavailable = true

        assertFailsWith<PasswordAccountsUnavailable> { useCase.request("atleta@saqz.test", "10.0.0.1") }
    }

    @Test
    fun `recusa reenvio antes dos 60 segundos e libera no segundo 60`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        clock.advance(Duration.ofSeconds(59))
        assertEquals(RequestCodeResult.TooSoon(1), useCase.request("atleta@saqz.test", "10.0.0.1"))

        clock.advance(Duration.ofSeconds(1))
        assertEquals(RequestCodeResult.Accepted, useCase.request("atleta@saqz.test", "10.0.0.1"))
        assertEquals(2, notifier.sent.size)
    }

    @Test
    fun `a janela de reenvio vale igual para e-mail sem conta`() {
        useCase.request("ninguem@saqz.test", "10.0.0.1")

        clock.advance(Duration.ofSeconds(30))

        assertEquals(RequestCodeResult.TooSoon(30), useCase.request("ninguem@saqz.test", "10.0.0.1"))
    }

    @Test
    fun `pedir codigo novo invalida o anterior`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        clock.advance(Duration.ofSeconds(60))
        secrets.nextCode = "9999"
        useCase.request("atleta@saqz.test", "10.0.0.1")

        assertEquals(VerifyCodeResult.InvalidCode(4), verify("1234"))
        assertTrue(verify("9999") is VerifyCodeResult.Success)
    }

    @Test
    fun `limita pedidos por IP e libera quando a janela vira`() {
        repeat(PasswordReset.MAX_REQUESTS_PER_IP) { index ->
            assertEquals(RequestCodeResult.Accepted, useCase.request("pessoa$index@saqz.test", "10.0.0.9"))
        }

        assertEquals(RequestCodeResult.RateLimited(600), useCase.request("mais@saqz.test", "10.0.0.9"))
        assertEquals(RequestCodeResult.Accepted, useCase.request("outro@saqz.test", "10.0.0.8"))

        clock.advance(Duration.ofMinutes(10))
        assertEquals(RequestCodeResult.Accepted, useCase.request("depois@saqz.test", "10.0.0.9"))
    }

    @Test
    fun `limita verificacoes por IP`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        repeat(PasswordReset.MAX_VERIFICATIONS_PER_IP) { verify("0000") }

        assertEquals(VerifyCodeResult.RateLimited(600), verify("0000"))
    }

    @Test
    fun `o balde do pedido e o da verificacao nao se consomem`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        repeat(PasswordReset.MAX_VERIFICATIONS_PER_IP) { verify("0000") }

        clock.advance(Duration.ofSeconds(60))

        assertEquals(RequestCodeResult.Accepted, useCase.request("atleta@saqz.test", "10.0.0.1"))
    }

    @Test
    fun `troca o codigo por um token de curta duracao`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        assertEquals(VerifyCodeResult.Success("token-secreto", Duration.ofMinutes(5)), verify("1234"))
    }

    @Test
    fun `codigo errado devolve quantas tentativas restam`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        assertEquals(VerifyCodeResult.InvalidCode(4), verify("0000"))
        assertEquals(VerifyCodeResult.InvalidCode(3), verify("0001"))
        assertEquals(VerifyCodeResult.InvalidCode(2), verify("0002"))
        assertEquals(VerifyCodeResult.InvalidCode(1), verify("0003"))
    }

    @Test
    fun `a quinta tentativa errada mata o codigo`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        repeat(4) { verify("000$it") }

        assertEquals(VerifyCodeResult.AttemptLimit, verify("0009"))
        assertTrue(repository.codes.isEmpty())
        assertEquals(VerifyCodeResult.Expired, verify("1234"))
    }

    @Test
    fun `codigo expirado e distinto de codigo errado`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        clock.advance(Duration.ofMinutes(10))

        assertEquals(VerifyCodeResult.Expired, verify("1234"))
    }

    @Test
    fun `codigo vale ate o ultimo segundo da validade`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        clock.advance(Duration.ofMinutes(10).minusSeconds(1))

        assertTrue(verify("1234") is VerifyCodeResult.Success)
    }

    @Test
    fun `verificar e-mail sem codigo responde expirado sem dizer se a conta existe`() {
        assertEquals(VerifyCodeResult.Expired, verify("1234"))
        assertEquals(VerifyCodeResult.Expired, useCase.verify("ninguem@saqz.test", "1234", "10.0.0.1"))
    }

    @Test
    fun `emitir o token apaga o codigo`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        assertTrue(verify("1234") is VerifyCodeResult.Success)

        assertEquals(VerifyCodeResult.Expired, verify("1234"))
        assertNull(repository.codes.getValue("atleta@saqz.test").codeDigest)
    }

    @Test
    fun `tentativas erradas depois do token nao apagam o token emitido`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (verify("1234") as VerifyCodeResult.Success).token

        repeat(PasswordReset.MAX_ATTEMPTS + 1) { assertEquals(VerifyCodeResult.Expired, verify("000$it")) }

        assertEquals(ConfirmResetResult.Success, useCase.confirm(token, "senha-nova-forte"))
    }

    @Test
    fun `o mesmo codigo nao emite um segundo token por cima do primeiro`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val first = (verify("1234") as VerifyCodeResult.Success).token
        secrets.nextToken = "token-do-segundo"

        assertEquals(VerifyCodeResult.Expired, verify("1234"))
        assertEquals(ConfirmResetResult.InvalidToken, useCase.confirm("token-do-segundo", "senha-nova-forte"))
        assertEquals(ConfirmResetResult.Success, useCase.confirm(first, "senha-nova-forte"))
    }

    @Test
    fun `troca a senha e invalida codigo e token`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (verify("1234") as VerifyCodeResult.Success).token

        assertEquals(ConfirmResetResult.Success, useCase.confirm(token, "senha-nova-forte"))
        assertEquals("senha-nova-forte", accounts.passwords.getValue("atleta@saqz.test"))
        assertTrue(repository.codes.isEmpty())
        assertEquals(ConfirmResetResult.InvalidToken, useCase.confirm(token, "outra-senha-forte"))
    }

    @Test
    fun `token expirado nao troca a senha`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (verify("1234") as VerifyCodeResult.Success).token

        clock.advance(Duration.ofMinutes(5))

        assertEquals(ConfirmResetResult.InvalidToken, useCase.confirm(token, "senha-nova-forte"))
        assertEquals("senha-antiga", accounts.passwords.getValue("atleta@saqz.test"))
    }

    @Test
    fun `token desconhecido nao troca a senha`() {
        assertEquals(ConfirmResetResult.InvalidToken, useCase.confirm("token-inventado", "senha-nova-forte"))
    }

    @Test
    fun `provedor fora do ar na confirmacao nao vira token invalido`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (verify("1234") as VerifyCodeResult.Success).token
        accounts.unavailable = true

        assertFailsWith<PasswordAccountsUnavailable> { useCase.confirm(token, "senha-nova-forte") }
    }

    @Test
    fun `senha curta demais e recusada antes de consumir o token`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (verify("1234") as VerifyCodeResult.Success).token

        assertEquals(ConfirmResetResult.WeakPassword, useCase.confirm(token, "1234567"))
        assertEquals(ConfirmResetResult.WeakPassword, useCase.confirm(token, "a".repeat(129)))
        assertEquals(ConfirmResetResult.Success, useCase.confirm(token, "12345678"))
    }

    @Test
    fun `codigo gerado tem sempre quatro digitos`() {
        val secureSecrets = SecureResetSecrets()
        val generated = List(500) { secureSecrets.code() }

        assertTrue(generated.all { it.length == 4 && it.all(Char::isDigit) }, generated.first())
        assertTrue(generated.toSet().size > 1)
    }

    private fun verify(code: String) = useCase.verify("atleta@saqz.test", code, "10.0.0.1")

    private class MovableClock(private var now: Instant) : Clock() {
        fun advance(amount: Duration) {
            now = now.plus(amount)
        }

        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
    }

    private class FixedSecrets(var nextCode: String = "1234", var nextToken: String = "token-secreto") : ResetSecrets {
        override fun code(): String = nextCode
        override fun token(): String = nextToken
    }

    private class RecordingNotifier : ResetCodeNotifier {
        val sent = mutableListOf<Triple<String, String, Duration>>()
        var failure: RuntimeException? = null

        override fun send(recipient: String, code: String, validity: Duration) {
            failure?.let { throw it }
            sent += Triple(recipient, code, validity)
        }
    }

    private class FakePasswordAccounts(val passwords: MutableMap<String, String>) : PasswordAccounts {
        var unavailable = false

        override fun exists(email: String): Boolean {
            if (unavailable) throw PasswordAccountsUnavailable()
            return email in passwords
        }

        override fun updatePassword(email: String, newPassword: String): Boolean {
            if (unavailable) throw PasswordAccountsUnavailable()
            if (email !in passwords) return false
            passwords[email] = newPassword
            return true
        }
    }

    class StoredCode(
        val codeDigest: ResetDigest?,
        val attempts: Int,
        val createdAt: Instant,
        val expiresAt: Instant,
        val tokenDigest: ResetDigest? = null,
        val tokenExpiresAt: Instant? = null,
    )

    /** Espelha o adapter JDBC: código e token são mutuamente exclusivos na mesma linha. */
    private class InMemoryPasswordResetRepository : PasswordResetRepository {
        val codes = mutableMapOf<String, StoredCode>()
        private val buckets = mutableMapOf<String, RateLimitWindow>()

        override fun recordRateLimit(bucket: String, now: Instant, windowFloor: Instant): RateLimitWindow {
            val current = buckets[bucket]?.takeIf { it.startedAt.isAfter(windowFloor) } ?: RateLimitWindow(now, 0)
            return RateLimitWindow(current.startedAt, current.count + 1).also { buckets[bucket] = it }
        }

        override fun replaceCode(code: NewResetCode, resendFloor: Instant): ReplaceCodeOutcome {
            val current = codes[code.email]
            if (current != null && current.createdAt.isAfter(resendFloor)) {
                return ReplaceCodeOutcome.TooSoon(current.createdAt)
            }
            codes[code.email] = StoredCode(code.codeDigest, 0, code.createdAt, code.expiresAt)
            return ReplaceCodeOutcome.Replaced
        }

        override fun consumeAttempt(email: String, now: Instant, ceiling: Int): AttemptOutcome? {
            val stored = codes[email] ?: return null
            val digest = stored.codeDigest
            if (digest == null || !now.isBefore(stored.expiresAt)) return null
            if (stored.attempts >= ceiling) return AttemptOutcome.Exhausted

            val bumped = stored.attempts + 1
            codes[email] = StoredCode(digest, bumped, stored.createdAt, stored.expiresAt)
            return AttemptOutcome.Consumed(digest, bumped)
        }

        override fun issueToken(email: String, tokenDigest: ResetDigest, expiresAt: Instant): Boolean {
            val stored = codes[email] ?: return false
            if (stored.codeDigest == null) return false
            codes[email] = StoredCode(null, stored.attempts, stored.createdAt, stored.expiresAt, tokenDigest, expiresAt)
            return true
        }

        override fun retireCode(email: String) {
            if (codes[email]?.codeDigest != null) codes.remove(email)
        }

        override fun consumeToken(tokenDigest: ResetDigest, now: Instant): String? {
            val entry = codes.entries.firstOrNull { (_, stored) ->
                stored.tokenDigest?.matches(tokenDigest) == true && stored.tokenExpiresAt?.isAfter(now) == true
            } ?: return null
            codes.remove(entry.key)
            return entry.key
        }
    }
}
