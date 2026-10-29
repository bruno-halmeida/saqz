package br.com.saqz.groups.application.settings

import br.com.saqz.groups.application.communication.AttendanceUpdatesMode
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.series.ScheduleSeriesSync
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.group.GroupProfileDefaultsValidation
import br.com.saqz.groups.domain.group.GroupProfileDefaultsValidator
import br.com.saqz.groups.domain.group.GroupValidationError
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.sharedkernel.moderation.ObjectionableText
import java.util.UUID

class UpdateGroupSettings(
    private val transactionRunner: TransactionRunner,
    private val readRepository: GroupReadRepository,
    private val settingsRepository: GroupSettingsRepository,
    private val accessPolicy: GroupAccessPolicy,
    private val scheduleSeries: ScheduleSeriesSync = ScheduleSeriesSync { },
) {
    /**
     * O perfil carrega os horários regulares, então a série semanal acompanha a gravação na hora,
     * como na agenda ([GroupScheduleService]); antes só a rotina de hora em hora a alcançava.
     */
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

        return updateProfile(actor, groupId, expectedVersion, input, profileValidation).also {
            // Fora da transação: os repositórios de série abrem a própria conexão e só enxergam o commit.
            if (it is UpdateGroupSettingsResult.Success) scheduleSeries.sync(groupId)
        }
    }

    private fun updateProfile(
        actor: UUID,
        groupId: UUID,
        expectedVersion: Long,
        input: UpdateGroupProfileInput,
        profileValidation: GroupProfileDefaultsValidation,
    ): UpdateGroupSettingsResult =
        transactionRunner.inTransaction {
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
            // O modo da lista no grupo do WhatsApp é do dono; o admin pode reenviar o valor atual com o resto do perfil.
            val currentMode = current.gameConfig?.attendanceUpdatesMode ?: AttendanceUpdatesMode.SCHEDULED
            if (input.attendanceUpdatesMode != null && input.attendanceUpdatesMode != currentMode && current.role != GroupRole.OWNER) {
                return@inTransaction UpdateGroupSettingsResult.AccessForbidden
            }
            val validProfile = (profileValidation as GroupProfileDefaultsValidation.Valid).value
            // A opção vale só com Escolha manual no resultado (DEB-150); ir para Ordem de chegada desliga no repositório.
            val resultingMode = validProfile.promotionMode ?: current.gameConfig?.promotionMode ?: PromotionMode.FIFO
            if (validProfile.advancePaymentRequired == true && resultingMode != PromotionMode.MANUAL) {
                return@inTransaction UpdateGroupSettingsResult.InvalidProfile(
                    listOf(GroupValidationError("advancePaymentRequired", GroupProfileDefaultsValidator.ADVANCE_PAYMENT_NEEDS_MANUAL)),
                )
            }
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
                        entryRequiresApproval = input.entryRequiresApproval,
                        monthlyLateJoinPolicy = input.monthlyLateJoinPolicy,
                        attendanceUpdatesMode = input.attendanceUpdatesMode,
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
                        write.settings.entryRequiresApproval,
                        write.settings.attendanceUpdatesMode,
                    ),
                )
            }
        }

    fun execute(
        actor: UUID,
        groupId: UUID,
        expectedVersion: Long,
        name: String,
        timeZone: String,
        entryRequiresApproval: Boolean? = null,
    ): UpdateGroupSettingsResult {
        val validName = runCatching { AccessName.from(name) }.getOrNull()
        val validTimeZone = runCatching { IanaTimeZone.from(timeZone) }.getOrNull()
        val invalidFields = buildSet {
            if (validName == null || ObjectionableText.contains(validName.value)) add(UpdateGroupSettingsField.NAME)
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
                        entryRequiresApproval = entryRequiresApproval,
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
                        write.settings.entryRequiresApproval,
                        write.settings.attendanceUpdatesMode,
                    ),
                )
            }
        }
    }
}
