package br.com.saqz.groups.application.settings

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.GroupRole
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

/** DEB-048: dois horários fixos do mesmo grupo não podem se sobrepor; encostados valem. */
class GroupScheduleOverlapTest {
    private val saved = mutableListOf<GroupSchedule>()
    private val service = GroupScheduleService(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        object : GroupReadRepository {
            override fun find(key: GroupReadKey) =
                GroupReadSnapshot(key.groupId, AccessName.from("Grupo"), IanaTimeZone.from("UTC"), GroupRole.OWNER, 1)
        },
        object : GroupScheduleRepository {
            override fun read(groupId: UUID) = saved.lastOrNull()?.let { VersionedGroupSchedule(it, 1) }
            override fun update(groupId: UUID, expectedVersion: Long, schedule: GroupSchedule): Boolean { saved += schedule; return true }
        },
    )

    private fun schedule(duration: Int, vararg slots: Pair<DayOfWeek, String>) =
        GroupSchedule(true, slots.map { ScheduleSlot(it.first, LocalTime.parse(it.second)) }, duration, 60, false)

    private fun result(schedule: GroupSchedule) = service.update(UUID.randomUUID(), UUID.randomUUID(), 1, schedule)

    @Test fun `overlapping slots on the same day are invalid`() {
        assertEquals(GroupScheduleResult.Invalid, result(schedule(90, DayOfWeek.MONDAY to "19:00", DayOfWeek.MONDAY to "20:00")))
    }

    @Test fun `touching slots are accepted`() {
        assertIs<GroupScheduleResult.Success>(result(schedule(60, DayOfWeek.MONDAY to "19:00", DayOfWeek.MONDAY to "20:00")))
    }

    @Test fun `sunday night slot running into monday overlaps a monday early slot`() {
        assertEquals(GroupScheduleResult.Invalid, result(schedule(120, DayOfWeek.SUNDAY to "23:00", DayOfWeek.MONDAY to "00:30")))
    }

    @Test fun `different days never overlap`() {
        assertIs<GroupScheduleResult.Success>(result(schedule(120, DayOfWeek.MONDAY to "19:00", DayOfWeek.TUESDAY to "19:00")))
    }
}
