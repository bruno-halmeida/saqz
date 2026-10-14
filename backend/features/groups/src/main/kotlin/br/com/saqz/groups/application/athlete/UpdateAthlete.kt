package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.AthleteMembershipType
import java.util.UUID

class UpdateAthlete(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val athleteRepository: AthleteRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, command: UpdateAthleteCommand): UpdateAthleteResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, command.groupId))
            ?: return@inTransaction UpdateAthleteResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction UpdateAthleteResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction UpdateAthleteResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        athleteRepository.find(command.groupId, command.userId)
            ?: return@inTransaction UpdateAthleteResult.GroupNotFound
        // Mensalidade só existe para mensalista. Quem vira avulso perde o valor e o dia (o app reenvia os
        // antigos ao trocar o tipo); sem isso o avulso ficaria com cobrança mensal que ninguém vê.
        val effective = if (command.membershipType == AthleteMembershipType.AVULSO) {
            command.copy(monthlyFeeCents = null, monthlyDueDay = null)
        } else {
            command
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
        UpdateAthleteResult.Success(athleteRepository.update(effective))
    }
}
