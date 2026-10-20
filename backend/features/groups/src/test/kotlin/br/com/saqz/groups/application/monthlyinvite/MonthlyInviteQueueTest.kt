package br.com.saqz.groups.application.monthlyinvite

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import br.com.saqz.groups.application.invite.InviteLinkFactory
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonthlyInviteQueueTest {
    private val start = Instant.parse("2026-10-20T15:00:00Z")
    private val clock = MutableClock(start)
    private val store = FakeStore()
    private val sends = mutableListOf<Pair<Instant, WhatsAppNotification>>()
    private val failing = mutableSetOf<String>()
    private val checks = mutableMapOf<String, Boolean?>()
    private val sleeps = mutableListOf<Duration>()
    private val delays = ArrayDeque(listOf(5L, 15L, 9L, 12L, 7L, 5L, 15L, 10L, 6L, 8L, 11L, 13L, 14L, 5L, 5L, 5L, 5L, 5L))
    private val sender = NotificationWhatsAppSender { message ->
        sends += clock.current to message
        if (message.phone in failing) WhatsAppDelivery.Retry() else WhatsAppDelivery.Accepted
    }
    private val queue = MonthlyInviteQueue(
        store,
        sender,
        WhatsAppNumberCheck { phone -> checks[phone] },
        InviteLinkFactory { code -> URI("https://saqz.app/?saqz_invite=${code.value}") },
        clock,
        nextDelay = { Duration.ofSeconds(delays.removeFirst()) },
        sleep = { duration ->
            sleeps += duration
            clock.current = clock.current.plus(duration)
        },
    )

    @Test
    fun `lote de 3 sai um a um com a espera sorteada entre eles e o texto fixo`() {
        val ids = store.add("Grupo A", "Ana", "+5511988880001", "Bia", "+5511988880002", "Caio", "+5511988880003")
        queue.enqueue(ids)

        drain()

        assertEquals(listOf("+5511988880001", "+5511988880002", "+5511988880003"), sends.map { it.second.phone })
        assertEquals(listOf(start, start.plusSeconds(5), start.plusSeconds(20)), sends.map { it.first })
        assertEquals(
            "E aí, Ana! 🏐 O Dona Lu te colocou na lista de mensalistas do Grupo A aqui no Saqz. Entra por aqui e confirma " +
                "presença nos jogos direto pelo app: https://saqz.app/?saqz_invite=44DF8SA8\nSe não era pra você, é só ignorar.",
            sends.first().second.body,
        )
        assertEquals("monthly-invite-${ids[0]}", sends.first().second.trackId)
        assertTrue(ids.all { store.rows.getValue(it).status == MonthlyInviteeStatus.WAITING })
        assertEquals(start, store.rows.getValue(ids[0]).sentAt)
    }

    @Test
    fun `espera padrao fica entre 5 e 15 segundos`() {
        repeat(200) {
            val delay = MonthlyInviteQueue.randomDelay()
            assertTrue(delay >= Duration.ofSeconds(5) && delay <= Duration.ofSeconds(15), delay.toString())
        }
    }

    @Test
    fun `dois grupos ao mesmo tempo dividem uma fila so, na ordem de chegada`() {
        val a = store.add("Grupo A", "Ana", "+5511988880001", "Bia", "+5511988880002")
        val b = store.add("Grupo B", "Caio", "+5511988880003")
        queue.enqueue(a.take(1))
        queue.enqueue(b)
        queue.enqueue(a.drop(1))

        drain()

        assertEquals(listOf("+5511988880001", "+5511988880003", "+5511988880002"), sends.map { it.second.phone })
        assertTrue(sends.zipWithNext().all { (first, second) -> second.first > first.first })
    }

    @Test
    fun `numero sem whatsapp vira Sem WhatsApp e checagem sem resposta deixa o envio seguir`() {
        val ids = store.add("Grupo A", "Ana", "+5511988880090", "Bia", "+5511988880002")
        checks["+5511988880090"] = false
        checks["+5511988880002"] = null
        queue.enqueue(ids)

        drain()

        assertEquals(MonthlyInviteeStatus.NO_WHATSAPP, store.rows.getValue(ids[0]).status)
        assertEquals(MonthlyInviteeStatus.WAITING, store.rows.getValue(ids[1]).status)
        assertEquals(listOf("+5511988880002"), sends.map { it.second.phone })
    }

    @Test
    fun `envio que falha tenta 3 vezes com intervalo crescente e fica Falhou`() {
        val ids = store.add("Grupo A", "Ana", "+5511988880091")
        failing += "+5511988880091"
        queue.enqueue(ids)

        drain()

        assertEquals(3, sends.size)
        assertEquals(listOf(Duration.ofSeconds(10), Duration.ofSeconds(20)), sleeps.take(2))
        assertEquals(MonthlyInviteeStatus.FAILED, store.rows.getValue(ids[0]).status)
        assertEquals(3, store.rows.getValue(ids[0]).attempts)
        assertNull(queue.pausedUntil())
    }

    @Test
    fun `falha que se recupera na segunda tentativa fica Aguardando`() {
        val ids = store.add("Grupo A", "Ana", "+5511988880001")
        var calls = 0
        val flaky = MonthlyInviteQueue(
            store,
            { calls += 1; if (calls == 1) WhatsAppDelivery.Failed else WhatsAppDelivery.Accepted },
            { null },
            { URI("https://saqz.app/?saqz_invite=${it.value}") },
            clock,
            sleep = { clock.current = clock.current.plus(it) },
        )
        flaky.enqueue(ids)
        flaky.processNext()

        assertEquals(MonthlyInviteeStatus.WAITING, store.rows.getValue(ids[0]).status)
        assertEquals(2, store.rows.getValue(ids[0]).attempts)
    }

    @Test
    fun `tres falhas seguidas pausam a fila para todos e os pendentes ficam Pausado`() {
        val failed = store.add("Grupo A", "Ana", "+5511988880091", "Bia", "+5511988880092", "Caio", "+5511988880093")
        val other = store.add("Grupo B", "Davi", "+5511988880004", "Eva", "+5511988880005")
        failing += listOf("+5511988880091", "+5511988880092", "+5511988880093")
        queue.enqueue(failed + other)

        repeat(3) { queue.processNext() }

        val pausedAt = queue.pausedUntil()!!
        assertEquals(Duration.ofMinutes(10), Duration.between(clock.current.minus(sleeps.last()), pausedAt))
        assertTrue(other.all { store.rows.getValue(it).status == MonthlyInviteeStatus.PAUSED })
        assertEquals(9, sends.size)
    }

    @Test
    fun `retomada apos 10 min com envio ok segue a fila e as linhas Pausado voltam a Na fila`() {
        pauseWithTwoPending()
        val pausedUntil = queue.pausedUntil()!!

        queue.processNext()

        assertTrue(sends.last().first >= pausedUntil)
        assertNull(queue.pausedUntil())
        assertEquals(MonthlyInviteeStatus.QUEUED, store.rows.getValue(queue.pendingIds().single()).status)
        drain()
        assertEquals(MonthlyInviteeStatus.WAITING, store.rows.getValue(store.byPhone("+5511988880005")).status)
    }

    @Test
    fun `retomada apos 10 min com envio falhando pausa de novo por mais 10 min`() {
        pauseWithTwoPending()
        failing += "+5511988880004"
        val firstPause = queue.pausedUntil()!!

        queue.processNext()

        val secondPause = queue.pausedUntil()!!
        assertTrue(secondPause > firstPause)
        assertEquals(MonthlyInviteeStatus.FAILED, store.rows.getValue(store.byPhone("+5511988880004")).status)
        assertEquals(MonthlyInviteeStatus.PAUSED, store.rows.getValue(store.byPhone("+5511988880005")).status)
    }

    @Test
    fun `reenviar com a fila pausada nao fura a pausa e volta ao fim da fila`() {
        pauseWithTwoPending()
        val retry = store.byPhone("+5511988880091")
        store.rows[retry] = store.rows.getValue(retry).copy(status = MonthlyInviteeStatus.PAUSED)
        queue.enqueue(listOf(retry))
        val sentBefore = sends.size

        assertEquals(listOf(store.byPhone("+5511988880004"), store.byPhone("+5511988880005"), retry), queue.pendingIds())
        assertEquals(sentBefore, sends.size)
    }

    @Test
    fun `reinicio devolve Na fila e Pausado a fila e deixa Aguardando como esta`() {
        val ids = store.add("Grupo A", "Ana", "+5511988880001", "Bia", "+5511988880002", "Caio", "+5511988880003")
        store.rows[ids[1]] = store.rows.getValue(ids[1]).copy(status = MonthlyInviteeStatus.WAITING)
        store.rows[ids[2]] = store.rows.getValue(ids[2]).copy(status = MonthlyInviteeStatus.PAUSED)

        queue.restore()

        assertEquals(listOf(ids[0], ids[2]), queue.pendingIds())
        assertEquals(MonthlyInviteeStatus.QUEUED, store.rows.getValue(ids[2]).status)
        drain()
        assertEquals(listOf("+5511988880001", "+5511988880003"), sends.map { it.second.phone })
    }

    @Test
    fun `linha removida ou link invalidado enquanto esperava nao manda nada`() {
        val ids = store.add("Grupo A", "Ana", "+5511988880001", "Bia", "+5511988880002")
        store.rows.remove(ids[0])
        queue.enqueue(ids)
        store.link = store.link.copy(shortCode = null, invalidatedAt = start)

        drain()

        assertTrue(sends.isEmpty())
        assertEquals(MonthlyInviteeStatus.FAILED, store.rows.getValue(ids[1]).status)
        assertFalse(queue.processNext())
    }

    @Test
    fun `reenviar para todos pelos quatro estados chega a fila, um por vez`() {
        val ids = store.add(
            "Grupo A", "Ana", "+5511988880001", "Bia", "+5511988880002", "Caio", "+5511988880003", "Davi", "+5511988880004",
        )
        listOf(MonthlyInviteeStatus.WAITING, MonthlyInviteeStatus.FAILED, MonthlyInviteeStatus.PAUSED, MonthlyInviteeStatus.WAITING)
            .forEachIndexed { index, status -> store.rows[ids[index]] = store.rows.getValue(ids[index]).copy(status = MonthlyInviteeStatus.QUEUED) }
        queue.enqueue(ids)

        drain()

        assertEquals(4, sends.size)
        assertTrue(sends.zipWithNext().all { (a, b) -> Duration.between(a.first, b.first) >= Duration.ofSeconds(5) })
    }

    private fun pauseWithTwoPending() {
        store.add("Grupo A", "Ana", "+5511988880091", "Bia", "+5511988880092", "Caio", "+5511988880093")
        store.add("Grupo B", "Davi", "+5511988880004", "Eva", "+5511988880005")
        failing += listOf("+5511988880091", "+5511988880092", "+5511988880093")
        queue.enqueue(store.order.toList())
        repeat(3) { queue.processNext() }
    }

    private fun drain() {
        while (queue.processNext()) Unit
    }

    private inner class FakeStore : MonthlyInviteQueueStore {
        val rows = linkedMapOf<UUID, MonthlyInvitee>()
        val order = mutableListOf<UUID>()
        val groups = mutableMapOf<UUID, String>()
        var link = MonthlyInviteLink(UUID.randomUUID(), "44DF8SA8", start.plus(Duration.ofDays(7)), null)

        fun add(group: String, vararg people: String): List<UUID> = people.toList().chunked(2).map { (name, phone) ->
            val id = UUID.randomUUID()
            rows[id] = MonthlyInvitee(
                id, link.groupId, name, MonthlyInviteePhone.fromStored(phone), MonthlyInviteeStatus.QUEUED, 0, null, null, start,
            )
            groups[id] = group
            order += id
            id
        }

        fun byPhone(phone: String) = rows.values.first { it.phone.value == phone }.id

        override fun context(inviteeId: UUID) = rows[inviteeId]?.let {
            MonthlyInviteSendContext(it.id, it.name, it.phone.value, it.status, groups.getValue(it.id), "Dona Lu", link)
        }

        override fun markSent(inviteeId: UUID, attempts: Int, sentAt: Instant) {
            rows.computeIfPresent(inviteeId) { _, row ->
                row.copy(status = MonthlyInviteeStatus.WAITING, attempts = attempts, sentAt = sentAt)
            }
        }

        override fun markFailed(inviteeId: UUID, attempts: Int) {
            rows.computeIfPresent(inviteeId) { _, row -> row.copy(status = MonthlyInviteeStatus.FAILED, attempts = attempts) }
        }

        override fun markNoWhatsApp(inviteeId: UUID) {
            rows.computeIfPresent(inviteeId) { _, row -> row.copy(status = MonthlyInviteeStatus.NO_WHATSAPP) }
        }

        override fun markPending(inviteeIds: List<UUID>, status: MonthlyInviteeStatus) {
            inviteeIds.forEach { id ->
                rows.computeIfPresent(id) { _, row ->
                    if (row.status == MonthlyInviteeStatus.QUEUED || row.status == MonthlyInviteeStatus.PAUSED) row.copy(status = status) else row
                }
            }
        }

        override fun pendingAtStartup() = rows.values
            .filter { it.status == MonthlyInviteeStatus.QUEUED || it.status == MonthlyInviteeStatus.PAUSED }
            .map { it.id }
    }
}
