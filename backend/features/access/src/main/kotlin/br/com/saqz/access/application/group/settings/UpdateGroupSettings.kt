package br.com.saqz.access.application.group.settings

import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupAccessDecision
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupAction
import br.com.saqz.access.domain.IanaTimeZone
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
                    ),
                )
            }
        }
    }
}
