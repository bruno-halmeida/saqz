package br.com.saqz.groups.application.settings

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.groups.domain.group.GroupValidationError
import br.com.saqz.groups.domain.group.ValidGroupProfileDefaults
import java.util.UUID

data class UpdateGroupSettingsCommand(
    val groupId: UUID,
    val expectedVersion: Long,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val profile: ValidGroupProfileDefaults? = null,
    val defaultVenueId: UUID? = null,
    val regularSlotIds: List<UUID?> = emptyList(),
)

data class UpdateGroupProfileInput(
    val profile: GroupProfileDefaultsInput,
    val defaultVenueId: UUID? = null,
    val regularSlotIds: List<UUID?> = emptyList(),
)

data class StoredGroupSettings(
    val id: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val version: Long,
    val profileStatus: GroupProfileStatus = GroupProfileStatus.COMPLETE,
)

data class UpdatedGroupSettings(
    val id: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val role: GroupRole,
    val version: Long,
    val profileStatus: GroupProfileStatus = GroupProfileStatus.COMPLETE,
)

enum class UpdateGroupSettingsField {
    NAME,
    TIME_ZONE,
}

sealed interface SettingsWriteResult {
    data class Updated(val settings: StoredGroupSettings) : SettingsWriteResult

    data object VersionConflict : SettingsWriteResult

    data object GroupNotFound : SettingsWriteResult
}

sealed interface UpdateGroupSettingsResult {
    data class Success(val settings: UpdatedGroupSettings) : UpdateGroupSettingsResult

    data class Invalid(val fields: Set<UpdateGroupSettingsField>) : UpdateGroupSettingsResult

    data class InvalidProfile(val errors: List<GroupValidationError>) : UpdateGroupSettingsResult

    data object GroupNotFound : UpdateGroupSettingsResult

    data object AccessForbidden : UpdateGroupSettingsResult

    data object VersionConflict : UpdateGroupSettingsResult
}
