package br.com.saqz.groups.domain.group

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

data class GroupScheduleSlot(val weekday: GroupWeekday, val startTime: String)
data class GroupSchedule(
    val recurring: Boolean,
    val slots: List<GroupScheduleSlot>,
    val durationMinutes: Int,
    val confirmationLeadMinutes: Int,
    val paused: Boolean,
)
data class VersionedGroupSchedule(val schedule: GroupSchedule, val versionToken: GroupVersionToken)
interface GroupScheduleGateway {
    suspend fun readSchedule(groupId: GroupId): SaqzResult<VersionedGroupSchedule, GroupProfileError>
    suspend fun updateSchedule(
        groupId: GroupId,
        versionToken: GroupVersionToken,
        schedule: GroupSchedule,
    ): SaqzResult<VersionedGroupSchedule, GroupProfileError>
}
