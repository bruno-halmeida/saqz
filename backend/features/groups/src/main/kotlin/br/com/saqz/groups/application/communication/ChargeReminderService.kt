package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

/** [notificationCount] avisos saíram agora; [alreadyRemindedCount] cobranças já tinham sido cobradas nas últimas 12 h. */
data class ChargeReminderReceipt(val notificationCount: Int, val alreadyRemindedCount: Int = 0)
data class PendingReminderCharge(val id: UUID, val memberId: UUID, val amountCents: Long)
data class ChargeReminderRequest(val chargeIds: Set<UUID>, val receipt: ChargeReminderReceipt)
interface ChargeReminderStore {
    fun previous(group: UUID, actor: UUID, request: UUID): ChargeReminderRequest?
    fun pending(group: UUID, ids: Set<UUID>): List<PendingReminderCharge>

    /** Cobranças da lista que já geraram aviso ao devedor dentro da janela de [REMINDER_COOLDOWN_HOURS] horas. */
    fun recentlyReminded(ids: Set<UUID>): Set<UUID>

    /** Grava o pedido com todas as [charges] e avisa só as que não estão em [skipped]. */
    fun create(group: UUID, actor: UUID, request: UUID, charges: List<PendingReminderCharge>, skipped: Set<UUID> = emptySet()): ChargeReminderReceipt
}

const val REMINDER_COOLDOWN_HOURS = 12

class ChargeReminderService(
    private val transaction: TransactionRunner,
    private val groups: GroupReadRepository,
    private val communication: GroupCommunicationRepository,
    private val store: ChargeReminderStore,
) {
    fun send(actor: UUID, group: UUID, request: UUID, ids: List<UUID>): CommunicationResult<ChargeReminderReceipt> =
        transaction.inTransaction {
            if (!communication.lockGroup(group)) return@inTransaction failure(CommunicationError.NOT_FOUND)
            val role = groups.find(GroupReadKey(actor, group))?.role
                ?: return@inTransaction failure(CommunicationError.NOT_FOUND)
            if (role == GroupRole.ATHLETE) return@inTransaction failure(CommunicationError.FORBIDDEN)
            if (ids.size !in 1..200 || ids.distinct().size != ids.size) return@inTransaction failure(CommunicationError.INVALID)
            val selection = ids.toSet()
            store.previous(group, actor, request)?.let {
                return@inTransaction if (it.chargeIds == selection) CommunicationResult.Success(it.receipt)
                else failure(CommunicationError.CONFLICT)
            }
            val charges = store.pending(group, selection)
            if (charges.size != ids.size) return@inTransaction failure(CommunicationError.CONFLICT)
            CommunicationResult.Success(store.create(group, actor, request, charges, store.recentlyReminded(selection)))
        }
    private fun failure(error: CommunicationError) = CommunicationResult.Failure(error)
}
