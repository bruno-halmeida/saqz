package br.com.saqz.groups.application.finance.openingbalance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.util.UUID

/** Saldo inicial do caixa (DEB-148, FIN-006). [amountCents] nulo = ninguém respondeu ainda. */
data class OpeningBalance(val amountCents: Long?, val version: Long)

interface OpeningBalanceRepository {
    fun role(actorId: UUID, groupId: UUID): GroupRole?
    fun find(groupId: UUID): OpeningBalance?
    /** Grava se a versão bate, com o evento de histórico; nulo em conflito. */
    fun update(groupId: UUID, expectedVersion: Long, amountCents: Long, actorId: UUID, at: Instant): OpeningBalance?
}

sealed interface OpeningBalanceResult {
    data class Success(val value: OpeningBalance) : OpeningBalanceResult
    data object Hidden : OpeningBalanceResult
    data object Forbidden : OpeningBalanceResult
    data object Conflict : OpeningBalanceResult
    data object InvalidAmount : OpeningBalanceResult
}

/** Só gestor lê e muda. Zero ou positivo, sem data; dívida antiga entra como Saída. */
class OpeningBalanceService(
    private val transaction: TransactionRunner,
    private val repository: OpeningBalanceRepository,
    private val now: () -> Instant,
) {
    fun read(actor: UUID, group: UUID): OpeningBalanceResult = authorized(actor, group) {
        repository.find(group)?.let(OpeningBalanceResult::Success) ?: OpeningBalanceResult.Hidden
    }

    fun update(actor: UUID, group: UUID, expectedVersion: Long, amountCents: Long?): OpeningBalanceResult =
        transaction.inTransaction {
            authorized(actor, group) {
                if (amountCents == null || amountCents !in 0..99_999_999) return@authorized OpeningBalanceResult.InvalidAmount
                repository.update(group, expectedVersion, amountCents, actor, now())?.let(OpeningBalanceResult::Success)
                    ?: OpeningBalanceResult.Conflict
            }
        }

    private fun authorized(actor: UUID, group: UUID, block: () -> OpeningBalanceResult): OpeningBalanceResult =
        when (repository.role(actor, group)) {
            null -> OpeningBalanceResult.Hidden
            GroupRole.ATHLETE -> OpeningBalanceResult.Forbidden
            GroupRole.OWNER, GroupRole.ADMIN -> block()
        }
}
