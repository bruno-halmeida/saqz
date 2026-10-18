package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.ChargeChange
import br.com.saqz.groups.domain.finance.charge.ChargeEvent
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.groups.domain.finance.charge.ChargeStatus
import br.com.saqz.groups.domain.finance.charge.ChargeStatusCommand
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Dono e admin dão baixa, dispensam, cancelam e desfazem (DEB-052); atleta só vê as próprias cobranças. */
class ChargeManagementTest {
    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val admin = UUID.randomUUID()
    private val athlete = UUID.randomUUID()
    private val outsider = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val eventId = UUID.randomUUID()
    private val repository = Repository()
    private val management = ChargeManagement(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        repository,
        { NOW },
        { eventId },
    )

    @Test
    fun `organizador lista todas as cobrancas do grupo com os totais por situacao`() {
        repository.add(charge(athlete, 1000, ChargeStatus.PENDING))
        repository.add(charge(other, 2000, ChargeStatus.PAID))
        repository.add(charge(other, 300, ChargeStatus.WAIVED))
        repository.add(charge(athlete, 40, ChargeStatus.CANCELLED))

        listOf(owner, admin).forEach {
            val result = assertIs<ChargeListResult.Success>(management.listOrganizer(it, group))
            assertEquals(4, result.charges.size)
            val totals = result.totals!!
            assertEquals(listOf(1000L, 2000L, 300L, 40L), listOf(totals.pendingCents, totals.paidCents, totals.waivedCents, totals.cancelledCents))
        }
    }

    @Test
    fun `lista do organizador esconde o grupo de quem nao e membro e nega ao atleta`() {
        assertEquals(ChargeListResult.Hidden, management.listOrganizer(outsider, group))
        assertEquals(ChargeListResult.Forbidden, management.listOrganizer(athlete, group))
    }

    @Test
    fun `atleta lista so as proprias cobrancas e sem totais, dono tambem pela lista propria`() {
        val mine = charge(athlete, 1000, ChargeStatus.PENDING)
        repository.add(mine)
        repository.add(charge(other, 2000, ChargeStatus.PAID))

        val result = assertIs<ChargeListResult.Success>(management.listOwn(athlete, group))

        assertEquals(listOf(mine.id), result.charges.map { it.charge.id })
        assertEquals(null, result.totals)
        assertEquals(emptyList(), assertIs<ChargeListResult.Success>(management.listOwn(owner, group)).charges)
        assertEquals(ChargeListResult.Hidden, management.listOwn(outsider, group))
    }

    @Test
    fun `dar baixa grava a nova situacao, a versao seguinte e o evento com quem fez e quando`() {
        val pending = charge(athlete, 1000, ChargeStatus.PENDING, version = 5)
        repository.add(pending)

        val result = assertIs<ChargeStatusResult.Success>(
            management.status(admin, group, pending.id, 5, ChargeStatusCommand(ChargeStatus.PAID, "Recebido no pix")),
        )

        assertEquals(ChargeStatus.PAID, result.value.charge.status)
        assertEquals(6L, result.value.charge.version)
        val event = result.value.events.last()
        assertEquals(ChargeEvent(eventId, pending.id, admin, ChargeStatus.PENDING, ChargeStatus.PAID, "Recebido no pix", NOW), event)
        assertEquals(1, result.value.events.size)
        assertEquals(ChargeStatus.PAID, repository.stored(pending.id).charge.status)
        assertEquals(5L, repository.updatedFromVersion)
    }

    @Test
    fun `os eventos anteriores ficam e o novo entra no fim`() {
        val pending = charge(athlete, 1000, ChargeStatus.PENDING)
        val earlier = ChargeEvent(UUID.randomUUID(), pending.id, owner, ChargeStatus.PENDING, ChargeStatus.PENDING, null, NOW.minusSeconds(60))
        repository.add(pending, listOf(earlier))

        val result = assertIs<ChargeStatusResult.Success>(management.status(owner, group, pending.id, 1, ChargeStatusCommand(ChargeStatus.WAIVED)))

        assertEquals(listOf(earlier.id, eventId), result.value.events.map { it.id })
    }

    @Test
    fun `quem nao e membro nao ve, atleta nao altera e cobranca de outro grupo some`() {
        val pending = charge(athlete, 1000, ChargeStatus.PENDING)
        repository.add(pending)
        val command = ChargeStatusCommand(ChargeStatus.PAID)

        assertEquals(ChargeStatusResult.Hidden, management.status(outsider, group, pending.id, 1, command))
        assertEquals(ChargeStatusResult.Forbidden, management.status(athlete, group, pending.id, 1, command))
        assertEquals(ChargeStatusResult.Hidden, management.status(owner, group, UUID.randomUUID(), 1, command))
        assertEquals(ChargeStatus.PENDING, repository.stored(pending.id).charge.status)
    }

    @Test
    fun `versao velha ou gravacao perdida e conflito e nada muda`() {
        val pending = charge(athlete, 1000, ChargeStatus.PENDING, version = 2)
        repository.add(pending)
        val command = ChargeStatusCommand(ChargeStatus.PAID)

        assertEquals(ChargeStatusResult.Conflict, management.status(owner, group, pending.id, 1, command))
        assertEquals(ChargeStatusResult.Conflict, management.status(owner, group, pending.id, 3, command))
        repository.rejectUpdate = true
        assertEquals(ChargeStatusResult.Conflict, management.status(owner, group, pending.id, 2, command))
        assertEquals(ChargeStatus.PENDING, repository.stored(pending.id).charge.status)
    }

    @Test
    fun `transicao proibida e observacao invalida voltam como tal, sem gravar`() {
        val paid = charge(athlete, 1000, ChargeStatus.PAID)
        val pending = charge(athlete, 1000, ChargeStatus.PENDING)
        repository.add(paid)
        repository.add(pending)

        assertEquals(ChargeStatusResult.InvalidTransition, management.status(owner, group, paid.id, 1, ChargeStatusCommand(ChargeStatus.WAIVED)))
        assertEquals(ChargeStatusResult.InvalidNote, management.status(owner, group, pending.id, 1, ChargeStatusCommand(ChargeStatus.PAID, "x")))
        assertEquals(null, repository.updatedFromVersion)
    }

    @Test
    fun `desfazer a baixa volta a pendente`() {
        val paid = charge(athlete, 1000, ChargeStatus.PAID, version = 2)
        repository.add(paid)

        val result = assertIs<ChargeStatusResult.Success>(management.status(owner, group, paid.id, 2, ChargeStatusCommand(ChargeStatus.PENDING, "Lancei errado")))

        assertEquals(ChargeStatus.PENDING, result.value.charge.status)
        assertEquals(3L, result.value.charge.version)
    }

    @Test
    fun `resolver a revisao limpa a marca, devolve a versao seguinte e grava com o autor e a hora`() {
        val flagged = charge(athlete, 1000, ChargeStatus.PAID, version = 4, review = true)
        repository.add(flagged)

        val result = assertIs<ChargeReviewResult.Success>(management.resolveReview(admin, group, flagged.id, 4))

        assertEquals(false, result.value.charge.reviewRequired)
        assertEquals(5L, result.value.charge.version)
        assertEquals(listOf(flagged.id, group, admin, 4L, NOW, eventId), repository.resolved)
    }

    @Test
    fun `resolver sem revisao pendente devolve a cobranca como esta sem olhar a versao`() {
        val clean = charge(athlete, 1000, ChargeStatus.PAID, version = 4)
        repository.add(clean)

        val result = assertIs<ChargeReviewResult.Success>(management.resolveReview(owner, group, clean.id, 1))

        assertEquals(clean, result.value.charge)
        assertEquals(null, repository.resolved)
    }

    @Test
    fun `resolver revisao respeita papel, existencia, versao e disputa`() {
        val flagged = charge(athlete, 1000, ChargeStatus.PAID, version = 4, review = true)
        repository.add(flagged)

        assertEquals(ChargeReviewResult.Hidden, management.resolveReview(outsider, group, flagged.id, 4))
        assertEquals(ChargeReviewResult.Forbidden, management.resolveReview(athlete, group, flagged.id, 4))
        assertEquals(ChargeReviewResult.Hidden, management.resolveReview(owner, group, UUID.randomUUID(), 4))
        assertEquals(ChargeReviewResult.Conflict, management.resolveReview(owner, group, flagged.id, 3))
        repository.rejectResolve = true
        assertEquals(ChargeReviewResult.Conflict, management.resolveReview(owner, group, flagged.id, 4))
        assertTrue(repository.stored(flagged.id).charge.reviewRequired)
    }

    private fun charge(member: UUID, cents: Long, status: ChargeStatus, version: Long = 1, review: Boolean = false) =
        Charge(UUID.randomUUID(), group, member, ChargeIdentity.Game(UUID.randomUUID()), cents, LocalDate.of(2026, 8, 10), status, version, review)

    private inner class Repository : ChargeManagementRepository {
        private val rows = linkedMapOf<UUID, ChargeWithEvents>()
        var updatedFromVersion: Long? = null
        var rejectUpdate = false
        var rejectResolve = false
        var resolved: List<Any>? = null

        fun add(charge: Charge, events: List<ChargeEvent> = emptyList()) { rows[charge.id] = ChargeWithEvents(charge, events) }
        fun stored(id: UUID) = rows.getValue(id)

        override fun role(actorId: UUID, groupId: UUID) = when (actorId) {
            owner -> GroupRole.OWNER
            admin -> GroupRole.ADMIN
            athlete -> GroupRole.ATHLETE
            else -> null
        }
        override fun list(groupId: UUID, memberId: UUID?) = rows.values.filter { memberId == null || it.charge.memberId == memberId }
        override fun find(groupId: UUID, chargeId: UUID) = rows[chargeId]
        override fun update(change: ChargeChange, expectedVersion: Long): Boolean {
            if (rejectUpdate) return false
            updatedFromVersion = expectedVersion
            rows[change.charge.id] = ChargeWithEvents(change.charge, rows.getValue(change.charge.id).events + change.event)
            return true
        }
        override fun resolveReview(chargeId: UUID, groupId: UUID, actorId: UUID, expectedVersion: Long, at: Instant, resolutionId: UUID): Boolean {
            if (rejectResolve) return false
            resolved = listOf(chargeId, groupId, actorId, expectedVersion, at, resolutionId)
            return true
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z")
    }
}
