package br.com.saqz.groups.application.communication

import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Cobrar" em lote: só dono e admin, até 200 cobranças, sem avisar de novo quem já foi cobrado nas últimas 12 h. */
class ChargeReminderServiceTest {
    private val group = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val request = UUID.randomUUID()
    private val groups = FixedRoleGroups(group, GroupRole.ADMIN)
    private val repository = MemoryCommunicationRepository()
    private val store = MemoryStore()
    private val service = ChargeReminderService(CountingTransaction(), groups, repository, store)
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test fun `dono e admin cobram, atleta não`() {
        store.charges = pending(a)
        groups.role = GroupRole.OWNER
        assertTrue(service.send(actor, group, request, listOf(a)) is CommunicationResult.Success)
        groups.role = GroupRole.ATHLETE
        assertEquals(failure(CommunicationError.FORBIDDEN), service.send(actor, group, UUID.randomUUID(), listOf(a)))
    }

    @Test fun `quem não é do grupo ou grupo inexistente recebe não encontrado`() {
        groups.role = null
        assertEquals(failure(CommunicationError.NOT_FOUND), service.send(actor, group, request, listOf(a)))
        groups.role = GroupRole.OWNER
        repository.groupExists = false
        assertEquals(failure(CommunicationError.NOT_FOUND), service.send(actor, group, request, listOf(a)))
        assertTrue(store.created.isEmpty())
    }

    @Test fun `a lista tem de ter de 1 a 200 cobranças sem repetição`() {
        assertEquals(failure(CommunicationError.INVALID), service.send(actor, group, request, emptyList()))
        assertEquals(failure(CommunicationError.INVALID), service.send(actor, group, request, listOf(a, a)))
        assertEquals(failure(CommunicationError.INVALID), service.send(actor, group, request, List(201) { UUID.randomUUID() }))
        val ids = List(200) { UUID.randomUUID() }
        store.charges = ids.map { PendingReminderCharge(it, UUID.randomUUID(), 1000) }
        assertTrue(service.send(actor, group, request, ids) is CommunicationResult.Success)
        assertTrue(store.created.single().charges.size == 200)
    }

    @Test fun `a mesma tentativa repetida devolve o recibo antigo, com outra seleção é conflito`() {
        val receipt = ChargeReminderReceipt(notificationCount = 2, alreadyRemindedCount = 1)
        store.previous = ChargeReminderRequest(setOf(a, b), receipt)
        assertEquals(CommunicationResult.Success(receipt), service.send(actor, group, request, listOf(b, a)))
        assertEquals(failure(CommunicationError.CONFLICT), service.send(actor, group, request, listOf(a)))
        assertTrue(store.created.isEmpty())
    }

    @Test fun `cobrança que não está mais pendente neste grupo derruba o pedido inteiro`() {
        store.charges = pending(a)
        assertEquals(failure(CommunicationError.CONFLICT), service.send(actor, group, request, listOf(a, b)))
        assertTrue(store.created.isEmpty())
    }

    @Test fun `grava todas as cobranças e pula só as cobradas na janela das 12 horas`() {
        store.charges = pending(a, b)
        store.reminded = setOf(b)
        val result = service.send(actor, group, request, listOf(a, b))
        assertEquals(CommunicationResult.Success(ChargeReminderReceipt(1, 1)), result)
        val call = store.created.single()
        assertEquals(Triple(group, actor, request), Triple(call.group, call.actor, call.request))
        assertEquals(setOf(a, b), call.charges.map { it.id }.toSet())
        assertEquals(setOf(b), call.skipped)
        assertEquals(setOf(a, b), store.askedRecent)
        assertEquals(12, REMINDER_COOLDOWN_HOURS)
    }

    private fun failure(error: CommunicationError) = CommunicationResult.Failure(error)
    private fun pending(vararg ids: UUID) = ids.map { PendingReminderCharge(it, UUID.randomUUID(), 2500) }

    private class Created(val group: UUID, val actor: UUID, val request: UUID, val charges: List<PendingReminderCharge>, val skipped: Set<UUID>)

    private class MemoryStore : ChargeReminderStore {
        var previous: ChargeReminderRequest? = null
        var charges = emptyList<PendingReminderCharge>()
        var reminded = emptySet<UUID>()
        var askedRecent = emptySet<UUID>()
        val created = mutableListOf<Created>()
        override fun previous(group: UUID, actor: UUID, request: UUID) = previous
        override fun pending(group: UUID, ids: Set<UUID>) = charges.filter { it.id in ids }
        override fun recentlyReminded(ids: Set<UUID>): Set<UUID> { askedRecent = ids; return reminded }
        override fun create(group: UUID, actor: UUID, request: UUID, charges: List<PendingReminderCharge>, skipped: Set<UUID>): ChargeReminderReceipt {
            created += Created(group, actor, request, charges, skipped)
            return ChargeReminderReceipt(charges.size - skipped.size, skipped.size)
        }
    }
}
