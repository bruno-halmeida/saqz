package br.com.saqz.access.application.emailverification

import br.com.saqz.access.application.passwordreset.RateLimitWindow
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestEmailVerificationTest {
    private val clock = MovableClock(Instant.parse("2026-08-18T15:00:00Z"))
    private val links = RecordingLinks()
    private val mailer = RecordingMailer()
    private val sends = InMemorySendLog()
    private val useCase = RequestEmailVerification(links, mailer, sends, clock)

    @Test
    fun `envia o link para quem ainda nao confirmou`() {
        assertEquals(RequestVerificationResult.Accepted, useCase.request(unverified(), "10.0.0.1"))

        assertEquals(listOf("atleta@saqz.test"), links.emails)
        assertEquals(listOf("atleta@saqz.test" to "https://confirm.test/action"), mailer.sent)
    }

    @Test
    fun `nao manda nada se o e-mail ja esta confirmado`() {
        assertEquals(RequestVerificationResult.Accepted, useCase.request(verified(), "10.0.0.1"))

        assertTrue(links.emails.isEmpty())
        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `nao manda nada se o token nao tem e-mail`() {
        assertEquals(
            RequestVerificationResult.Accepted,
            useCase.request(RequestIdentity("subject", email = null, emailVerified = false), "10.0.0.1"),
        )

        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `normaliza o e-mail antes de pedir o link`() {
        useCase.request(unverified(email = "  Atleta@SAQZ.test "), "10.0.0.1")

        assertEquals("atleta@saqz.test", links.emails.single())
        assertEquals("atleta@saqz.test", mailer.sent.single().first)
    }

    @Test
    fun `reenvio antes de um minuto devolve o quanto falta`() {
        useCase.request(unverified(), "10.0.0.1")
        clock.advance(Duration.ofSeconds(18))

        val result = useCase.request(unverified(), "10.0.0.1")

        assertEquals(RequestVerificationResult.TooSoon(42), result)
        assertEquals(1, mailer.sent.size)
    }

    @Test
    fun `reenvio depois de um minuto gera um link novo`() {
        useCase.request(unverified(), "10.0.0.1")
        clock.advance(Duration.ofSeconds(60))

        assertEquals(RequestVerificationResult.Accepted, useCase.request(unverified(), "10.0.0.1"))
        assertEquals(2, mailer.sent.size)
    }

    @Test
    fun `estouro da cota devolve o fim da janela`() {
        repeat(RequestEmailVerification.MAX_PER_SUBJECT) {
            clock.advance(Duration.ofSeconds(60))
            assertEquals(RequestVerificationResult.Accepted, useCase.request(unverified(), "10.0.0.1"))
        }
        clock.advance(Duration.ofSeconds(60))

        val result = useCase.request(unverified(), "10.0.0.1")

        assertEquals(RequestVerificationResult.RateLimited(120), result)
        assertEquals(RequestEmailVerification.MAX_PER_SUBJECT, mailer.sent.size)
    }

    @Test
    fun `estouro por IP devolve o fim da janela mesmo mudando de conta`() {
        repeat(RequestEmailVerification.MAX_PER_IP) { index ->
            assertEquals(
                RequestVerificationResult.Accepted,
                useCase.request(
                    RequestIdentity("subject-$index", "pessoa$index@saqz.test", emailVerified = false),
                    "10.0.0.1",
                ),
            )
        }

        val result = useCase.request(unverified(), "10.0.0.1")

        assertEquals(RequestVerificationResult.RateLimited(600), result)
        assertEquals(RequestEmailVerification.MAX_PER_IP, mailer.sent.size)
    }

    @Test
    fun `conta que sumiu no provedor responde aceito sem e-mail`() {
        links.missing = true

        assertEquals(RequestVerificationResult.Accepted, useCase.request(unverified(), "10.0.0.1"))
        assertTrue(mailer.sent.isEmpty())
    }

    @Test
    fun `falha de entrega nao muda a resposta`() {
        mailer.failing = true

        assertEquals(RequestVerificationResult.Accepted, useCase.request(unverified(), "10.0.0.1"))
    }

    private fun unverified(email: String = "atleta@saqz.test") =
        RequestIdentity("subject-1", email, emailVerified = false)

    private fun verified() = RequestIdentity("subject-1", "atleta@saqz.test", emailVerified = true)

    private class RecordingLinks : VerificationLinkGenerator {
        val emails = mutableListOf<String>()
        var missing = false
        override fun generate(email: String): String? {
            emails += email
            return if (missing) null else "https://confirm.test/action"
        }
    }

    private class RecordingMailer : VerificationLinkMailer {
        val sent = mutableListOf<Pair<String, String>>()
        var failing = false
        override fun send(recipient: String, confirmationLink: String) {
            if (failing) error("SMTP fora do ar")
            sent += recipient to confirmationLink
        }
    }

    private class InMemorySendLog : VerificationSendLog {
        private val windows = mutableMapOf<String, RateLimitWindow>()
        override fun record(bucket: String, now: Instant, windowFloor: Instant): RateLimitWindow {
            val current = windows[bucket]
            val next = if (current == null || !current.startedAt.isAfter(windowFloor)) {
                RateLimitWindow(now, 1)
            } else {
                RateLimitWindow(current.startedAt, current.count + 1)
            }
            windows[bucket] = next
            return next
        }
    }

    private class MovableClock(private var now: Instant) : Clock() {
        fun advance(amount: Duration) {
            now = now.plus(amount)
        }
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
}
