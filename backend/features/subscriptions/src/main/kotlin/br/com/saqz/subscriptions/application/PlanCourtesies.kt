package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.PlanCourtesy
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Dono de grupo da lista de conferência da cortesia em lote. */
data class CourtesyCandidate(
    val ownerUserId: UUID,
    val displayName: String?,
    val email: String?,
    val groups: List<String>,
    /** Plano que o dono tem hoje (assinatura ou cortesia ativa, o maior, como no `SubscriptionPlanLookup`); nulo = nenhum. */
    val effectivePlan: Plan?,
    val hasCourtesy: Boolean,
)

/** Cortesia ativa com os dados de quem a recebeu, para a lista do painel. */
data class ActiveCourtesy(
    val courtesy: PlanCourtesy,
    val displayName: String?,
    val email: String?,
)

interface PlanCourtesyRepository {
    /** A cortesia ativa agora, se houver. */
    fun findActive(ownerUserId: UUID, now: Instant): PlanCourtesy?

    /** Todas, da mais nova para a mais antiga. */
    fun history(ownerUserId: UUID): List<PlanCourtesy>

    fun userExists(userId: UUID): Boolean

    /** Revoga a que estiver aberta (ativa ou vencida) e grava a nova, numa transação só. */
    fun grant(courtesy: PlanCourtesy): PlanCourtesy

    /** Revoga a aberta; false quando o dono não tinha cortesia ativa. */
    fun revoke(ownerUserId: UUID, by: UUID, reason: String?, now: Instant): Boolean

    fun listActive(now: Instant): List<ActiveCourtesy>

    /** Donos de pelo menos um grupo vivo, com conta ativa. */
    fun eligibleOwners(now: Instant): List<CourtesyCandidate>
}

class InvalidCourtesyException(val field: String) : RuntimeException(field)

class CourtesyUserNotFoundException : RuntimeException()

data class CourtesyBatchResult(val ownerUserId: UUID, val granted: Boolean, val error: String? = null)

/** Casos de uso da cortesia no painel administrativo. */
class PlanCourtesies(
    private val repository: PlanCourtesyRepository,
    private val plans: SubscriptionPlanLookup,
    private val clock: Clock,
) {
    fun active(ownerUserId: UUID): PlanCourtesy? = repository.findActive(ownerUserId, clock.instant())

    /** Null quando o usuário não existe. */
    fun view(ownerUserId: UUID): Pair<PlanCourtesy?, List<PlanCourtesy>>? {
        if (!repository.userExists(ownerUserId)) return null
        return repository.findActive(ownerUserId, clock.instant()) to repository.history(ownerUserId)
    }

    fun grant(admin: UUID, ownerUserId: UUID, plan: Plan, endsAt: Instant?, reason: String): PlanCourtesy {
        val now = clock.instant()
        val courtesy = validated(admin, ownerUserId, plan, endsAt, reason, now)
        if (!repository.userExists(ownerUserId)) throw CourtesyUserNotFoundException()
        return repository.grant(courtesy)
    }

    fun revoke(admin: UUID, ownerUserId: UUID, reason: String?): Boolean {
        if (!repository.userExists(ownerUserId)) throw CourtesyUserNotFoundException()
        return repository.revoke(ownerUserId, admin, reason?.trim()?.takeIf { it.isNotEmpty() }, clock.instant())
    }

    fun listActive(): List<ActiveCourtesy> = repository.listActive(clock.instant())

    /** Lista de conferência: não grava nada. */
    fun preview(plan: Plan, endsAt: Instant?, reason: String): List<CourtesyCandidate> {
        validateTerms(endsAt, reason, clock.instant())
        return eligible(clock.instant())
    }

    /** Grava só para os [ownerIds] que estão na lista elegível; os demais voltam com erro `NOT_ELIGIBLE`. */
    fun grantBatch(admin: UUID, ownerIds: List<UUID>, plan: Plan, endsAt: Instant?, reason: String): List<CourtesyBatchResult> {
        val now = clock.instant()
        validateTerms(endsAt, reason, now)
        val eligible = repository.eligibleOwners(now).map { it.ownerUserId }.toSet()
        return ownerIds.distinct().map { owner ->
            if (owner !in eligible) return@map CourtesyBatchResult(owner, false, "NOT_ELIGIBLE")
            runCatching { repository.grant(validated(admin, owner, plan, endsAt, reason, now)) }
                .fold({ CourtesyBatchResult(owner, true) }, { CourtesyBatchResult(owner, false, "FAILED") })
        }
    }

    private fun eligible(now: Instant) = repository.eligibleOwners(now).map { it.copy(effectivePlan = plans.findEntitlingPlan(it.ownerUserId)?.plan) }

    private fun validated(admin: UUID, owner: UUID, plan: Plan, endsAt: Instant?, reason: String, now: Instant): PlanCourtesy {
        validateTerms(endsAt, reason, now)
        return PlanCourtesy(UUID.randomUUID(), owner, plan, endsAt, reason.trim(), admin, now)
    }

    private fun validateTerms(endsAt: Instant?, reason: String, now: Instant) {
        if (reason.isBlank()) throw InvalidCourtesyException("reason")
        if (endsAt != null && !endsAt.isAfter(now)) throw InvalidCourtesyException("endsAt")
    }
}
