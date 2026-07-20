package br.com.saqz.groups.application.read

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class GroupReadKey(
    val actorUserId: UUID,
    val groupId: UUID,
)

data class GroupReadSnapshot(
    val id: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val role: GroupRole?,
    val version: Long,
    val profileStatus: GroupProfileStatus = GroupProfileStatus.COMPLETE,
    val profile: GroupProfileReadModel? = null,
    val financeDefaults: GroupFinanceDefaultsReadModel? = null,
)

data class GroupView(
    val id: UUID,
    val name: AccessName,
    val timeZone: IanaTimeZone,
    val role: GroupRole,
    val version: Long,
    val profileStatus: GroupProfileStatus = GroupProfileStatus.COMPLETE,
    val profile: GroupProfileReadModel? = null,
    val financeDefaults: GroupFinanceDefaultsReadModel? = null,
)

data class GroupProfileReadModel(
    val modality: GroupModality?,
    val composition: GroupComposition?,
    val description: String?,
    val city: String?,
    val level: GroupLevel?,
    val customLevel: String?,
    val playStyle: CourtPlayStyle?,
    val customPlayStyle: String?,
    val defaultVenue: GroupVenueReadModel?,
    val regularSlots: List<GroupRegularSlotReadModel>,
    val defaultCapacity: Int?,
    val defaultConfirmationLeadMinutes: Int?,
)

data class GroupVenueReadModel(
    val id: UUID,
    val name: String,
    val address: String,
    val court: String?,
)

data class GroupRegularSlotReadModel(
    val id: UUID,
    val weekday: DayOfWeek,
    val startTime: LocalTime,
    val durationMinutes: Int,
)

data class GroupFinanceDefaultsReadModel(
    val defaultGameFeeCents: Long?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
)

sealed interface GetGroupResult {
    data class Success(val group: GroupView) : GetGroupResult

    data object GroupNotFound : GetGroupResult

    data object AccessForbidden : GetGroupResult
}
