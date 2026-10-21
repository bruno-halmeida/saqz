package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.application.finance.charge.ChargeManagementRepository
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.groups.domain.finance.charge.ChargeStatus
import br.com.saqz.groups.domain.finance.charge.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.charge.ChargeTransitions
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Nota gravada no histórico da mensalidade dispensada ao isentar (DEB-115). */
const val MONTHLY_FEE_EXEMPTION_NOTE = "Isenção da mensalidade"

class UpdateAthlete(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val athleteRepository: AthleteRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val charges: ChargeManagementRepository? = null,
    private val now: () -> Instant = Instant::now,
    private val eventIds: () -> UUID = UUID::randomUUID,
) {
    fun execute(actor: UUID, command: UpdateAthleteCommand): UpdateAthleteResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, command.groupId))
            ?: return@inTransaction UpdateAthleteResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction UpdateAthleteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction UpdateAthleteResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        val current = athleteRepository.find(command.groupId, command.userId)
            ?: return@inTransaction UpdateAthleteResult.GroupNotFound
        if (command.membershipType == AthleteMembershipType.AVULSO && command.monthlyFeeExempt == true) {
            return@inTransaction UpdateAthleteResult.Invalid(
                mapOf("monthlyFeeExempt" to listOf("is only allowed for MENSALISTA")),
            )
        }
        // Mensalidade só existe para mensalista. Quem vira avulso perde o valor, o dia e a isenção (o app reenvia
        // os antigos ao trocar o tipo); sem isso o avulso ficaria com cobrança mensal que ninguém vê.
        val effective = if (command.membershipType == AthleteMembershipType.AVULSO) {
            command.copy(monthlyFeeCents = null, monthlyDueDay = null, monthlyFeeExempt = false)
        } else {
            command.copy(monthlyFeeExempt = command.monthlyFeeExempt ?: current.monthlyFeeExempt)
        }
        val errors = AthleteAttributeValidator.validate(
            group.profile?.modality,
            command.nickname,
            command.position,
            command.secondaryPosition,
            command.level,
            command.preferredSide,
            command.heightCm,
            effective.monthlyFeeCents,
            effective.monthlyDueDay,
        )
        val membershipErrors = if (command.membershipType == AthleteMembershipType.MENSALISTA) {
            buildMap<String, List<String>> {
                if ((command.monthlyFeeCents ?: group.financeDefaults?.monthlyFeeCents) == null) {
                    put("monthlyFeeCents", listOf("is required for MENSALISTA"))
                }
                if ((command.monthlyDueDay ?: group.financeDefaults?.monthlyDueDay) == null) {
                    put("monthlyDueDay", listOf("is required for MENSALISTA"))
                }
            }
        } else {
            emptyMap()
        }
        val allErrors = errors + membershipErrors.filterKeys { it !in errors }
        if (allErrors.isNotEmpty()) return@inTransaction UpdateAthleteResult.Invalid(allErrors)
        val updated = athleteRepository.update(effective)
        if (updated.monthlyFeeExempt && !current.monthlyFeeExempt) {
            waiveCurrentMonth(actor, command, ZoneId.of(group.timeZone.value))
        }
        UpdateAthleteResult.Success(updated)
    }

    // Ao isentar, a mensalidade pendente do mês atual (fuso do grupo) é dispensada como no "Dispensar" (FIN-002).
    // Meses anteriores ficam pendentes; cobrança com ordem eletrônica fica como está.
    private fun waiveCurrentMonth(actor: UUID, command: UpdateAthleteCommand, zone: ZoneId) {
        val repository = charges ?: return
        val at = now()
        val month = YearMonth.from(at.atZone(zone))
        repository.list(command.groupId, command.userId)
            .map { it.charge }
            .filter {
                it.identity == ChargeIdentity.Monthly(month) &&
                    it.status == ChargeStatus.PENDING &&
                    !it.electronicOrder
            }
            .forEach { charge ->
                val change = ChargeTransitions.apply(
                    charge,
                    ChargeStatusCommand(ChargeStatus.WAIVED, MONTHLY_FEE_EXEMPTION_NOTE),
                    actor,
                    at,
                    eventIds(),
                )
                repository.update(change, charge.version)
            }
    }
}
