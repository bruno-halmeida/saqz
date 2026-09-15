package br.com.saqz.groups.application.settings

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupRole
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class ScheduleSlot(val weekday: DayOfWeek, val startTime: LocalTime)
data class GroupSchedule(
    val recurring: Boolean,
    val slots: List<ScheduleSlot>,
    val durationMinutes: Int,
    val confirmationLeadMinutes: Int,
    val paused: Boolean,
)
data class VersionedGroupSchedule(val schedule: GroupSchedule, val version: Long)
interface GroupScheduleRepository {
    fun read(groupId: UUID): VersionedGroupSchedule?
    fun update(groupId: UUID, expectedVersion: Long, schedule: GroupSchedule): Boolean
}
sealed interface GroupScheduleResult {
    data class Success(val value: VersionedGroupSchedule) : GroupScheduleResult
    data object NotFound : GroupScheduleResult
    data object Forbidden : GroupScheduleResult
    data object Conflict : GroupScheduleResult
    data object Invalid : GroupScheduleResult
}
class GroupScheduleService(
    private val transaction: TransactionRunner,
    private val groups: GroupReadRepository,
    private val schedules: GroupScheduleRepository,
) {
    fun read(actor: UUID, groupId: UUID): GroupScheduleResult = transaction.inTransaction {
        authorize(actor, groupId)?.let { return@inTransaction it }
        schedules.read(groupId)?.let(GroupScheduleResult::Success) ?: GroupScheduleResult.NotFound
    }

    fun update(actor: UUID, groupId: UUID, expectedVersion: Long, schedule: GroupSchedule): GroupScheduleResult =
        transaction.inTransaction {
            authorize(actor, groupId)?.let { return@inTransaction it }
            if (!schedule.valid()) return@inTransaction GroupScheduleResult.Invalid
            if (!schedules.update(groupId, expectedVersion, schedule)) return@inTransaction GroupScheduleResult.Conflict
            GroupScheduleResult.Success(requireNotNull(schedules.read(groupId)))
        }

    private fun authorize(actor: UUID, groupId: UUID): GroupScheduleResult? =
        when (groups.find(GroupReadKey(actor, groupId))?.role) {
            null -> GroupScheduleResult.NotFound
            GroupRole.ATHLETE -> GroupScheduleResult.Forbidden
            GroupRole.OWNER, GroupRole.ADMIN -> null
        }
}
private fun GroupSchedule.valid(): Boolean =
    durationMinutes in 1..1440 && confirmationLeadMinutes in 0..10080 &&
        slots.size <= 14 && slots.distinct().size == slots.size &&
        slots.all { it.startTime.second == 0 && it.startTime.nano == 0 } &&
        (if (recurring) slots.isNotEmpty() else slots.isEmpty() && !paused)
