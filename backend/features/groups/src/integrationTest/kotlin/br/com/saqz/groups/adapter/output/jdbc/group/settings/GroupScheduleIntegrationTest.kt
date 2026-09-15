package br.com.saqz.groups.adapter.output.jdbc.group.settings

import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.settings.*
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlin.test.*

class GroupScheduleIntegrationTest {
    private lateinit var jdbc: JdbcClient
    private lateinit var service: GroupScheduleService
    private lateinit var repository: JdbcGroupScheduleRepository
    private lateinit var transaction: JdbcTransactionRunner
    private val owner = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val schedule = GroupSchedule(true, listOf(ScheduleSlot(DayOfWeek.TUESDAY, LocalTime.of(19, 30))), 90, 720, true)

    @BeforeEach fun setup() {
        val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        jdbc = JdbcClient.create(source)
        transaction = JdbcTransactionRunner(source)
        repository = JdbcGroupScheduleRepository(source)
        service = GroupScheduleService(transaction, JdbcGroupReadRepository(source), repository)
        user(owner)
        jdbc.sql("""
            INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, version, created_at, updated_at,
                default_capacity, default_game_fee_cents)
            VALUES (:id, :owner, :key, 'Original', 'America/Sao_Paulo', 1, now(), now(), 18, 2500)
        """.trimIndent()).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
    }

    @Test fun `save and reopen persists all controls without requiring a complete profile`() {
        val result = assertIs<GroupScheduleResult.Success>(service.update(owner, group, 1, schedule))
        assertEquals(2, result.value.version)
        assertEquals(schedule, assertIs<GroupScheduleResult.Success>(service.read(owner, group)).value.schedule)
        assertEquals("Original|18|2500", jdbc.sql("SELECT name || '|' || default_capacity || '|' || default_game_fee_cents FROM access_groups WHERE id = :id")
            .param("id", group).query(String::class.java).single())
        assertTrue(transaction.inTransaction { repository.isPaused(group) })
    }

    @Test fun `recurrence off retains duration and lead and clears pause and slots`() {
        service.update(owner, group, 1, schedule)
        val manual = schedule.copy(recurring = false, slots = emptyList(), paused = false, durationMinutes = 150)
        assertEquals(manual, assertIs<GroupScheduleResult.Success>(service.update(owner, group, 2, manual)).value.schedule)
        assertFalse(transaction.inTransaction { repository.isPaused(group) })
        assertEquals(150, transaction.inTransaction {
            jdbc.sql("SELECT default_duration_minutes FROM access_groups WHERE id = :id")
                .param("id", group).query(Int::class.java).single()
        })
    }

    @Test fun `stale writer cannot overwrite slots or scalar settings`() {
        service.update(owner, group, 1, schedule)
        assertEquals(GroupScheduleResult.Conflict, service.update(owner, group, 1, schedule.copy(durationMinutes = 60)))
        assertEquals(schedule, assertIs<GroupScheduleResult.Success>(service.read(owner, group)).value.schedule)
    }

    @Test fun `admin may write but athlete and outsider cannot`() {
        val admin = UUID.randomUUID(); val athlete = UUID.randomUUID(); val outsider = UUID.randomUUID()
        listOf(admin, athlete, outsider).forEach(::user)
        member(admin, "ADMIN"); member(athlete, "ATHLETE")
        assertEquals(GroupScheduleResult.Forbidden, service.update(athlete, group, 1, schedule))
        assertEquals(GroupScheduleResult.NotFound, service.update(outsider, group, 1, schedule))
        assertIs<GroupScheduleResult.Success>(service.update(admin, group, 1, schedule))
    }

    @Test fun `invalid schedules do not increment version`() {
        listOf(schedule.copy(slots = emptyList()), schedule.copy(slots = schedule.slots + schedule.slots),
            schedule.copy(durationMinutes = 0), schedule.copy(confirmationLeadMinutes = -1),
            schedule.copy(recurring = false)).forEach {
            assertEquals(GroupScheduleResult.Invalid, service.update(owner, group, 1, it))
        }
        assertEquals(1, assertIs<GroupScheduleResult.Success>(service.read(owner, group)).value.version)
    }

    @Test fun `transaction rollback restores version and slots`() {
        assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                service.update(owner, group, 1, schedule)
                error("rollback")
            }
        }
        val restored = assertIs<GroupScheduleResult.Success>(service.read(owner, group)).value
        assertEquals(1, restored.version)
        assertTrue(restored.schedule.slots.isEmpty())
    }

    private fun user(id: UUID) {
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'User', now(), now())")
            .param("id", id).param("subject", id.toString()).update()
    }
    private fun member(id: UUID, role: String) {
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES (:group, :id, CAST(:role AS group_role), now(), now())")
            .param("group", group).param("id", id).param("role", role).update()
    }
}
