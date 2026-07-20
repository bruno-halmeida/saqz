package br.com.saqz.groups.application.settings

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupProfileDefaultsValidation
import br.com.saqz.groups.domain.group.GroupProfileDefaultsValidator
import java.util.UUID

class UpdateGroupSettings(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val settingsRepository: GroupSettingsRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(
        actor: UUID,
        groupId: UUID,
        expectedVersion: Long,
        input: UpdateGroupProfileInput,
    ): UpdateGroupSettingsResult {
        val profileValidation = GroupProfileDefaultsValidator.validate(input.profile)
        if (profileValidation is GroupProfileDefaultsValidation.Invalid) {
            return UpdateGroupSettingsResult.InvalidProfile(profileValidation.errors)
        }

        return transactionRunner.inTransaction {
            val current = readRepository.find(GroupReadKey(actor, groupId))
                ?: return@inTransaction UpdateGroupSettingsResult.GroupNotFound
            when (accessPolicy.authorize(current.role, GroupAction.UPDATE_SETTINGS)) {
                GroupAccessDecision.GroupNotFound -> return@inTransaction UpdateGroupSettingsResult.GroupNotFound
                GroupAccessDecision.Forbidden -> return@inTransaction UpdateGroupSettingsResult.AccessForbidden
                GroupAccessDecision.Allowed -> Unit
            }
            if (current.version != expectedVersion) {
                return@inTransaction UpdateGroupSettingsResult.VersionConflict
            }
            val validProfile = (profileValidation as GroupProfileDefaultsValidation.Valid).value
            when (
                val write = settingsRepository.update(
                    UpdateGroupSettingsCommand(
                        groupId = groupId,
                        expectedVersion = expectedVersion,
                        name = AccessName.from(validProfile.name),
                        timeZone = current.timeZone,
                        profile = validProfile,
                        defaultVenueId = input.defaultVenueId,
                        regularSlotIds = input.regularSlotIds,
                    ),
                )
            ) {
                SettingsWriteResult.GroupNotFound -> UpdateGroupSettingsResult.GroupNotFound
                SettingsWriteResult.VersionConflict -> UpdateGroupSettingsResult.VersionConflict
                is SettingsWriteResult.Updated -> UpdateGroupSettingsResult.Success(
                    UpdatedGroupSettings(
                        write.settings.id,
                        write.settings.name,
                        write.settings.timeZone,
                        requireNotNull(current.role),
                        write.settings.version,
                        write.settings.profileStatus,
                    ),
                )
            }
        }
    }

    fun execute(
        actor: UUID,
        groupId: UUID,
        expectedVersion: Long,
        name: String,
        timeZone: String,
    ): UpdateGroupSettingsResult {
        val validName = runCatching { AccessName.from(name) }.getOrNull()
        val validTimeZone = runCatching { IanaTimeZone.from(timeZone) }.getOrNull()
        val invalidFields = buildSet {
            if (validName == null) add(UpdateGroupSettingsField.NAME)
            if (validTimeZone == null) add(UpdateGroupSettingsField.TIME_ZONE)
        }
        if (invalidFields.isNotEmpty()) return UpdateGroupSettingsResult.Invalid(invalidFields)

        return transactionRunner.inTransaction {
            val current = readRepository.find(GroupReadKey(actor, groupId))
                ?: return@inTransaction UpdateGroupSettingsResult.GroupNotFound
            when (accessPolicy.authorize(current.role, GroupAction.UPDATE_SETTINGS)) {
                GroupAccessDecision.GroupNotFound -> return@inTransaction UpdateGroupSettingsResult.GroupNotFound
                GroupAccessDecision.Forbidden -> return@inTransaction UpdateGroupSettingsResult.AccessForbidden
                GroupAccessDecision.Allowed -> Unit
            }
            if (current.version != expectedVersion) {
                return@inTransaction UpdateGroupSettingsResult.VersionConflict
            }
            when (
                val write = settingsRepository.update(
                    UpdateGroupSettingsCommand(
                        groupId,
                        expectedVersion,
                        requireNotNull(validName),
                        requireNotNull(validTimeZone),
                    ),
                )
            ) {
                SettingsWriteResult.GroupNotFound -> UpdateGroupSettingsResult.GroupNotFound
                SettingsWriteResult.VersionConflict -> UpdateGroupSettingsResult.VersionConflict
                is SettingsWriteResult.Updated -> UpdateGroupSettingsResult.Success(
                    UpdatedGroupSettings(
                        write.settings.id,
                        write.settings.name,
                        write.settings.timeZone,
                        requireNotNull(current.role),
                        write.settings.version,
                        write.settings.profileStatus,
                    ),
                )
            }
        }
    }
}
