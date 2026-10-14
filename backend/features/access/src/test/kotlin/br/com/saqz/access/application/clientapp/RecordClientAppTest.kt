package br.com.saqz.access.application.clientapp

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.AppVersion
import br.com.saqz.sharedkernel.client.ClientApp
import br.com.saqz.sharedkernel.client.ClientPlatform
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecordClientAppTest {
    private val start = Instant.parse("2026-10-14T12:00:00Z")
    private val clock = MutableClock(start)
    private val store = FakeStore()
    private val record = RecordClientApp(store, clock)

    private val maria = RequestIdentity("maria")
    private val iosApp = ClientApp.Identified(ClientPlatform.IOS, AppVersion(1, 0, 3), 14)
    private val androidApp = ClientApp.Identified(ClientPlatform.ANDROID, AppVersion(1, 0, 3), 14)

    @Test
    fun `a client that did not identify itself never touches the store`() {
        record.execute(maria, ClientApp.Legacy)

        assertTrue(store.calls.isEmpty())
    }

    @Test
    fun `the first sighting is written with the instant it was seen and the refresh window`() {
        record.execute(maria, iosApp)

        assertEquals(listOf(Call("maria", iosApp, start, Duration.ofHours(6))), store.calls)
    }

    @Test
    fun `the same app inside six hours is answered from memory without a second call`() {
        record.execute(maria, iosApp)
        repeat(50) {
            clock.advance(Duration.ofMinutes(6))
            record.execute(maria, iosApp)
        }

        assertEquals(1, store.calls.size)
        assertTrue(clock.instant() < start.plus(Duration.ofHours(6)))
    }

    @Test
    fun `only after more than six hours the same app is written again to renew the last seen`() {
        record.execute(maria, iosApp)
        clock.advance(Duration.ofHours(6).minusSeconds(1))
        record.execute(maria, iosApp)
        assertEquals(1, store.calls.size)

        // Exatamente 6 h ainda não é "mais de 6 h".
        clock.advance(Duration.ofSeconds(1))
        record.execute(maria, iosApp)
        assertEquals(1, store.calls.size)

        clock.advance(Duration.ofSeconds(1))
        record.execute(maria, iosApp)

        assertEquals(2, store.calls.size)
        assertEquals(start.plus(Duration.ofHours(6)).plusSeconds(1), store.calls.last().now)
    }

    @Test
    fun `a new version is written at once, even seconds after the previous write`() {
        record.execute(maria, iosApp)
        clock.advance(Duration.ofSeconds(5))
        val upgraded = iosApp.copy(version = AppVersion(1, 0, 4), build = 15)

        record.execute(maria, upgraded)

        assertEquals(listOf(iosApp, upgraded), store.calls.map { it.app })
    }

    @Test
    fun `a new build with the same version is written at once`() {
        record.execute(maria, iosApp)

        record.execute(maria, iosApp.copy(build = 15))

        assertEquals(2, store.calls.size)
    }

    @Test
    fun `an app that gained a version it did not report before is written`() {
        val noVersion = ClientApp.Identified(ClientPlatform.WEB)
        record.execute(maria, noVersion)

        record.execute(maria, ClientApp.Identified(ClientPlatform.WEB, AppVersion(1, 0, 3)))

        assertEquals(2, store.calls.size)
    }

    @Test
    fun `the refresh window restarts at every write`() {
        record.execute(maria, iosApp)
        clock.advance(Duration.ofHours(5))
        val upgraded = iosApp.copy(build = 15)
        record.execute(maria, upgraded)

        clock.advance(Duration.ofHours(5))
        record.execute(maria, upgraded)

        assertEquals(2, store.calls.size)
        clock.advance(Duration.ofHours(1).plusSeconds(1))
        record.execute(maria, upgraded)
        assertEquals(3, store.calls.size)
    }

    @Test
    fun `platforms and users are tracked independently`() {
        record.execute(maria, iosApp)
        record.execute(maria, androidApp)
        record.execute(RequestIdentity("joao"), iosApp)
        record.execute(maria, iosApp)
        record.execute(maria, androidApp)
        record.execute(RequestIdentity("joao"), iosApp)

        assertEquals(
            listOf("maria" to iosApp, "maria" to androidApp, "joao" to iosApp),
            store.calls.map { it.subject to it.app },
        )
    }

    @Test
    fun `two devices of one user on different versions do not take turns in the database`() {
        val phone = iosApp
        val tablet = iosApp.copy(version = AppVersion(1, 0, 2), build = 13)
        record.execute(maria, phone)
        record.execute(maria, tablet)
        assertEquals(2, store.calls.size)

        repeat(40) { turn ->
            clock.advance(Duration.ofSeconds(30))
            record.execute(maria, if (turn % 2 == 0) phone else tablet)
        }

        assertEquals(2, store.calls.size)
        clock.advance(Duration.ofHours(6))
        record.execute(maria, tablet)
        assertEquals(3, store.calls.size)
        record.execute(maria, phone)
        assertEquals(4, store.calls.size)
    }

    @Test
    fun `an account that does not exist yet is asked again only after a minute`() {
        store.result = ClientAppRecordResult.UNKNOWN_USER
        record.execute(maria, iosApp)
        clock.advance(Duration.ofSeconds(59))
        record.execute(maria, iosApp)
        record.execute(maria, iosApp.copy(build = 15))
        assertEquals(1, store.calls.size)

        store.result = ClientAppRecordResult.WRITTEN
        clock.advance(Duration.ofSeconds(1))
        record.execute(maria, iosApp)

        assertEquals(2, store.calls.size)
        // Agora que gravou, volta à janela de 6 h.
        clock.advance(Duration.ofHours(1))
        record.execute(maria, iosApp)
        assertEquals(2, store.calls.size)
    }

    @Test
    fun `a failing store rethrows, waits a minute and then tries again`() {
        store.failure = IllegalStateException("database down")
        assertFailsWith<IllegalStateException> { record.execute(maria, iosApp) }
        clock.advance(Duration.ofSeconds(59))
        record.execute(maria, iosApp)
        assertEquals(1, store.calls.size)

        store.failure = null
        clock.advance(Duration.ofSeconds(1))
        record.execute(maria, iosApp)

        assertEquals(2, store.calls.size)
        clock.advance(Duration.ofHours(5))
        record.execute(maria, iosApp)
        assertEquals(2, store.calls.size)
    }

    @Test
    fun `an unchanged answer from the store also starts the six hour window`() {
        store.result = ClientAppRecordResult.UNCHANGED

        record.execute(maria, iosApp)
        clock.advance(Duration.ofHours(1))
        record.execute(maria, iosApp)

        assertEquals(1, store.calls.size)
    }

    @Test
    fun `memory stays bounded when many users are active`() {
        val small = RecordClientApp(store, clock, maxEntries = 3)
        listOf("a", "b", "c").forEach { small.execute(RequestIdentity(it), iosApp) }
        assertEquals(3, store.calls.size)

        // Cheio e nada vencido: zera tudo e segue gravando; quem já estava na memória volta a ser consultado.
        small.execute(RequestIdentity("d"), iosApp)
        small.execute(RequestIdentity("a"), iosApp)

        assertEquals(listOf("a", "b", "c", "d", "a"), store.calls.map { it.subject })
    }

    @Test
    fun `expired entries are dropped before the memory is cleared`() {
        val small = RecordClientApp(store, clock, maxEntries = 2)
        small.execute(RequestIdentity("a"), iosApp)
        clock.advance(Duration.ofHours(7))
        small.execute(RequestIdentity("b"), iosApp)

        small.execute(RequestIdentity("c"), iosApp)
        small.execute(RequestIdentity("b"), iosApp)

        // "a" venceu e saiu; "b" continua na memória e não voltou ao banco.
        assertEquals(listOf("a", "b", "c"), store.calls.map { it.subject })
    }

    private data class Call(val subject: String, val app: ClientApp.Identified, val now: Instant, val refreshAfter: Duration)

    private class FakeStore : ClientAppVersionStore {
        val calls = mutableListOf<Call>()
        var result = ClientAppRecordResult.WRITTEN
        var failure: RuntimeException? = null

        override fun record(
            subject: String,
            app: ClientApp.Identified,
            now: Instant,
            refreshAfter: Duration,
        ): ClientAppRecordResult {
            calls += Call(subject, app, now, refreshAfter)
            failure?.let { throw it }
            return result
        }
    }

    private class MutableClock(private var now: Instant) : Clock() {
        fun advance(by: Duration) {
            now = now.plus(by)
        }

        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?): Clock = this
    }
}
