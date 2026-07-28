package br.com.saqz.access.application.passwordreset

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordResetTest {
    private val start: Instant = Instant.parse("2026-07-28T12:00:00Z")
    private val clock = MovableClock(start)
    private val repository = InMemoryPasswordResetRepository()
    private val accounts = FakePasswordAccounts(mutableMapOf("atleta@saqz.test" to "senha-antiga"))
    private val notifier = RecordingNotifier()
    private val secrets = FixedSecrets()
    private val useCase = PasswordReset(repository, accounts, notifier, secrets, clock)

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
    fun `guarda o hash e nunca o numero`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        val stored = repository.codes.getValue("atleta@saqz.test")
        assertTrue(stored.codeDigest.matches(ResetDigest.ofCode("atleta@saqz.test", "1234")))
        assertEquals("ResetDigest([REDACTED])", stored.codeDigest.toString())
        assertNotEquals(
            ResetDigest.ofCode("outro@saqz.test", "1234").toByteArray().toList(),
            stored.codeDigest.toByteArray().toList(),
        )
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

        assertEquals(VerifyCodeResult.InvalidCode(4), useCase.verify("atleta@saqz.test", "1234"))
        assertTrue(useCase.verify("atleta@saqz.test", "9999") is VerifyCodeResult.Success)
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
    fun `troca o codigo por um token de curta duracao`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        val result = useCase.verify("atleta@saqz.test", "1234")

        assertEquals(VerifyCodeResult.Success("token-secreto", Duration.ofMinutes(5)), result)
    }

    @Test
    fun `codigo errado devolve quantas tentativas restam`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        assertEquals(VerifyCodeResult.InvalidCode(4), useCase.verify("atleta@saqz.test", "0000"))
        assertEquals(VerifyCodeResult.InvalidCode(3), useCase.verify("atleta@saqz.test", "0001"))
        assertEquals(VerifyCodeResult.InvalidCode(2), useCase.verify("atleta@saqz.test", "0002"))
        assertEquals(VerifyCodeResult.InvalidCode(1), useCase.verify("atleta@saqz.test", "0003"))
    }

    @Test
    fun `a quinta tentativa errada mata o codigo`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        repeat(4) { useCase.verify("atleta@saqz.test", "000$it") }

        assertEquals(VerifyCodeResult.AttemptLimit, useCase.verify("atleta@saqz.test", "0009"))
        assertTrue(repository.codes.isEmpty())
        assertEquals(VerifyCodeResult.Expired, useCase.verify("atleta@saqz.test", "1234"))
    }

    @Test
    fun `codigo expirado e distinto de codigo errado`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        clock.advance(Duration.ofMinutes(10))

        assertEquals(VerifyCodeResult.Expired, useCase.verify("atleta@saqz.test", "1234"))
        assertTrue(repository.codes.isEmpty())
    }

    @Test
    fun `codigo vale ate o ultimo segundo da validade`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")

        clock.advance(Duration.ofMinutes(10).minusSeconds(1))

        assertTrue(useCase.verify("atleta@saqz.test", "1234") is VerifyCodeResult.Success)
    }

    @Test
    fun `verificar e-mail sem codigo responde expirado sem dizer se a conta existe`() {
        assertEquals(VerifyCodeResult.Expired, useCase.verify("atleta@saqz.test", "1234"))
        assertEquals(VerifyCodeResult.Expired, useCase.verify("ninguem@saqz.test", "1234"))
    }

    @Test
    fun `troca a senha e invalida codigo e token`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (useCase.verify("atleta@saqz.test", "1234") as VerifyCodeResult.Success).token

        assertEquals(ConfirmResetResult.Success, useCase.confirm(token, "senha-nova-forte"))
        assertEquals("senha-nova-forte", accounts.passwords.getValue("atleta@saqz.test"))
        assertTrue(repository.codes.isEmpty())
        assertEquals(ConfirmResetResult.InvalidToken, useCase.confirm(token, "outra-senha-forte"))
    }

    @Test
    fun `token expirado nao troca a senha`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (useCase.verify("atleta@saqz.test", "1234") as VerifyCodeResult.Success).token

        clock.advance(Duration.ofMinutes(5))

        assertEquals(ConfirmResetResult.InvalidToken, useCase.confirm(token, "senha-nova-forte"))
        assertEquals("senha-antiga", accounts.passwords.getValue("atleta@saqz.test"))
    }

    @Test
    fun `token desconhecido nao troca a senha`() {
        assertEquals(ConfirmResetResult.InvalidToken, useCase.confirm("token-inventado", "senha-nova-forte"))
    }

    @Test
    fun `senha curta demais e recusada antes de consumir o token`() {
        useCase.request("atleta@saqz.test", "10.0.0.1")
        val token = (useCase.verify("atleta@saqz.test", "1234") as VerifyCodeResult.Success).token

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

        override fun send(recipient: String, code: String, validity: Duration) {
            sent += Triple(recipient, code, validity)
        }
    }

    private class FakePasswordAccounts(val passwords: MutableMap<String, String>) : PasswordAccounts {
        override fun exists(email: String) = email in passwords

        override fun updatePassword(email: String, newPassword: String): Boolean {
            if (email !in passwords) return false
            passwords[email] = newPassword
            return true
        }
    }

    /** Espelha o adapter JDBC: a chave é o e-mail, então gravar sobrescreve o código anterior. */
    private class InMemoryPasswordResetRepository : PasswordResetRepository {
        val codes = mutableMapOf<String, StoredResetCode>()
        private val tokens = mutableMapOf<String, Pair<ResetDigest, Instant>>()
        private val ips = mutableMapOf<String, IpRequestWindow>()

        override fun recordIpRequest(ip: String, now: Instant, windowFloor: Instant): IpRequestWindow {
            val current = ips[ip]?.takeIf { it.startedAt.isAfter(windowFloor) } ?: IpRequestWindow(now, 0)
            return IpRequestWindow(current.startedAt, current.count + 1).also { ips[ip] = it }
        }

        override fun replaceCode(code: StoredResetCode, resendFloor: Instant): ReplaceCodeOutcome {
            val current = codes[code.email]
            if (current != null && current.createdAt.isAfter(resendFloor)) {
                return ReplaceCodeOutcome.TooSoon(current.createdAt)
            }
            codes[code.email] = code
            tokens.remove(code.email)
            return ReplaceCodeOutcome.Replaced
        }

        override fun findByEmail(email: String): StoredResetCode? = codes[email]

        override fun recordAttempt(email: String, attempts: Int) {
            codes[email] = codes.getValue(email).copy(attempts = attempts)
        }

        override fun issueToken(email: String, tokenDigest: ResetDigest, expiresAt: Instant) {
            tokens[email] = tokenDigest to expiresAt
        }

        override fun consumeToken(tokenDigest: ResetDigest, now: Instant): String? {
            val email = tokens.entries
                .firstOrNull { it.value.first.matches(tokenDigest) && it.value.second.isAfter(now) }
                ?.key
                ?: return null
            tokens.remove(email)
            codes.remove(email)
            return email
        }

        override fun delete(email: String) {
            codes.remove(email)
            tokens.remove(email)
        }
    }
}
