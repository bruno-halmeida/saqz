package br.com.saqz.groups.application.create

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.groups.domain.group.GroupProfileDefaultsValidation
import br.com.saqz.groups.domain.group.GroupProfileDefaultsValidator
import br.com.saqz.groups.domain.group.GroupValidationError
import br.com.saqz.groups.domain.plan.PlanLimitPolicy
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import java.util.UUID

class CreateGroup(
    private val transactionRunner: TransactionRunner,
    private val repository: GroupCreationRepository,
    private val subscriptionLimits: SubscriptionLimits,
) {
    fun execute(
        actor: UUID,
        requestId: UUID,
        profile: GroupProfileDefaultsInput,
        timeZone: String,
    ): CreateGroupResult {
        val validTimeZone = runCatching { IanaTimeZone.from(timeZone) }.getOrNull()
        val profileValidation = GroupProfileDefaultsValidator.validate(profile)
        val errors = buildList {
            if (profileValidation is GroupProfileDefaultsValidation.Invalid) addAll(profileValidation.errors)
            if (validTimeZone == null) add(GroupValidationError("timeZone", "must be a valid IANA identifier"))
        }
        if (errors.isNotEmpty()) return CreateGroupResult.Invalid(errors)

        val stored = transactionRunner.inTransaction {
            repository.findByCreationKey(actor, requestId)?.let { return@inTransaction it }

            repository.lockOwnerForGroupLimit(actor)
            repository.findByCreationKey(actor, requestId)?.let { return@inTransaction it }

            val groupLimit = subscriptionLimits.groupLimitFor(actor)
            val ownedCount = repository.countOwnedGroups(actor)
            if (!PlanLimitPolicy.canCreateGroup(ownedCount, groupLimit)) {
                return@inTransaction null
            }

            repository.create(
                CreateGroupCommand(
                    ownerUserId = actor,
                    creationKey = requestId,
                    timeZone = requireNotNull(validTimeZone),
                    profile = (profileValidation as GroupProfileDefaultsValidation.Valid).value,
                ),
            )
        } ?: return CreateGroupResult.GroupLimitExceeded

        return CreateGroupResult.Success(
            CreatedGroup(
                id = stored.id,
                name = stored.name.value,
                timeZone = stored.timeZone.value,
                version = stored.version,
                role = GroupRole.OWNER,
                profileStatus = stored.profileStatus,
            ),
        )
    }
}
