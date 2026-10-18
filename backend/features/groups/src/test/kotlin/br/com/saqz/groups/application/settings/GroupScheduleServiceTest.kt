package br.com.saqz.groups.application.settings

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.series.ScheduleSeriesSync
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** JOGO-010 · horário fixo do grupo: quem mexe, o que vale salvar e quando a série semanal é refeita. */
class GroupScheduleServiceTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private var role: GroupRole? = GroupRole.OWNER
    private var stored: VersionedGroupSchedule? = VersionedGroupSchedule(valid(), 4)
    private var accepts = true
    private val updates = mutableListOf<Triple<UUID, Long, GroupSchedule>>()
    private val synced = mutableListOf<UUID>()
    private val events = mutableListOf<String>()
    private val service = GroupScheduleService(
        object : TransactionRunner {
            override fun <T> inTransaction(block: () -> T): T {
                events += "begin"
                return block().also { events += "commit" }
            }
        },
        object : GroupReadRepository {
            override fun find(key: GroupReadKey) =
                if (key.groupId == group) GroupReadSnapshot(group, AccessName.from("Grupo"), IanaTimeZone.from("UTC"), role, 1) else null
        },
        object : GroupScheduleRepository {
            override fun read(groupId: UUID) = stored
            override fun update(groupId: UUID, expectedVersion: Long, schedule: GroupSchedule): Boolean {
                updates += Triple(groupId, expectedVersion, schedule)
                return accepts
            }
        },
        ScheduleSeriesSync { events += "sync"; synced += it },
    )

    private fun slot(day: DayOfWeek = DayOfWeek.WEDNESDAY, time: String = "19:30") = ScheduleSlot(day, LocalTime.parse(time))
    private fun valid(
        recurring: Boolean = true,
        slots: List<ScheduleSlot> = listOf(slot()),
        duration: Int = 90,
        lead: Int = 180,
        paused: Boolean = false,
    ) = GroupSchedule(recurring, slots, duration, lead, paused)
    private fun update(schedule: GroupSchedule) = service.update(actor, group, 4, schedule)
    private fun accepted(schedule: GroupSchedule) = update(schedule) is GroupScheduleResult.Success
    private fun rejected(schedule: GroupSchedule) = update(schedule) == GroupScheduleResult.Invalid

    @Test fun `ler a agenda, dono e admin leem, atleta não, de fora nem sabe do grupo`() {
        listOf(GroupRole.OWNER, GroupRole.ADMIN).forEach {
            role = it
            assertEquals(GroupScheduleResult.Success(stored!!), service.read(actor, group))
        }
        role = GroupRole.ATHLETE
        assertEquals(GroupScheduleResult.Forbidden, service.read(actor, group))
        role = null
        assertEquals(GroupScheduleResult.NotFound, service.read(actor, group))
        assertEquals(GroupScheduleResult.NotFound, service.read(actor, UUID.randomUUID()))
    }

    @Test fun `grupo sem agenda gravada dá não encontrado na leitura`() {
        stored = null
        assertEquals(GroupScheduleResult.NotFound, service.read(actor, group))
    }

    @Test fun `atleta e quem é de fora não alteram a agenda nem disparam a série`() {
        role = GroupRole.ATHLETE
        assertEquals(GroupScheduleResult.Forbidden, update(valid()))
        role = null
        assertEquals(GroupScheduleResult.NotFound, update(valid()))
        assertTrue(updates.isEmpty())
        assertTrue(synced.isEmpty())
    }

    @Test fun `agenda salva volta como foi lida de novo, com a versão nova`() {
        val schedule = valid(duration = 120)
        stored = VersionedGroupSchedule(schedule, 5)
        assertEquals(GroupScheduleResult.Success(VersionedGroupSchedule(schedule, 5)), update(schedule))
        assertEquals(listOf(Triple(group, 4L, schedule)), updates)
    }

    @Test fun `versão desatualizada é conflito e a série não é refeita`() {
        accepts = false
        assertEquals(GroupScheduleResult.Conflict, update(valid()))
        assertTrue(synced.isEmpty())
    }

    @Test fun `agenda inválida não é gravada nem refaz a série`() {
        assertTrue(rejected(valid(duration = 0)))
        assertTrue(updates.isEmpty())
        assertTrue(synced.isEmpty())
    }

    @Test fun `a série é refeita uma vez para o grupo, depois que a transação fechou`() {
        update(valid())
        assertEquals(listOf(group), synced)
        assertEquals(listOf("begin", "commit", "sync"), events)
    }

    @Test fun `duração vale de 1 a 1440 minutos`() {
        assertTrue(rejected(valid(duration = 0)))
        assertTrue(accepted(valid(duration = 1)))
        assertTrue(accepted(valid(duration = 1440)))
        assertTrue(rejected(valid(duration = 1441)))
        assertTrue(rejected(valid(duration = -5)))
    }

    @Test fun `antecedência da confirmação vale de 0 a 10080 minutos`() {
        assertTrue(rejected(valid(lead = -1)))
        assertTrue(accepted(valid(lead = 0)))
        assertTrue(accepted(valid(lead = 10080)))
        assertTrue(rejected(valid(lead = 10081)))
    }

    @Test fun `no máximo 14 horários, sem repetir o mesmo dia e hora`() {
        val fourteen = DayOfWeek.entries.flatMap { day -> listOf(slot(day, "08:00"), slot(day, "18:00")) }
        assertEquals(14, fourteen.size)
        assertTrue(accepted(valid(slots = fourteen, duration = 60)))
        assertTrue(rejected(valid(slots = fourteen + slot(DayOfWeek.MONDAY, "12:00"), duration = 60)))
        assertTrue(rejected(valid(slots = listOf(slot(), slot()))))
    }

    @Test fun `horário com segundos ou frações não vale`() {
        assertTrue(rejected(valid(slots = listOf(ScheduleSlot(DayOfWeek.MONDAY, LocalTime.of(19, 0, 30))))))
        assertTrue(rejected(valid(slots = listOf(ScheduleSlot(DayOfWeek.MONDAY, LocalTime.of(19, 0, 0, 1))))))
        assertTrue(accepted(valid(slots = listOf(ScheduleSlot(DayOfWeek.MONDAY, LocalTime.of(19, 0))))))
    }

    @Test fun `agenda recorrente precisa de pelo menos um horário, e pausar vale`() {
        assertTrue(rejected(valid(recurring = true, slots = emptyList())))
        assertTrue(accepted(valid(recurring = true, paused = true)))
    }

    @Test fun `agenda sem recorrência não tem horários nem pode estar pausada`() {
        assertTrue(accepted(valid(recurring = false, slots = emptyList(), paused = false)))
        assertTrue(rejected(valid(recurring = false, slots = listOf(slot()))))
        assertTrue(rejected(valid(recurring = false, slots = emptyList(), paused = true)))
    }

    @Test fun `horários que se sobrepõem entre o fim da semana e o começo da próxima são recusados`() {
        // domingo 23:30 + 90 min invade segunda 00:00
        val slots = listOf(slot(DayOfWeek.SUNDAY, "23:30"), slot(DayOfWeek.MONDAY, "00:30"))
        assertTrue(rejected(valid(slots = slots, duration = 90)))
        assertTrue(accepted(valid(slots = slots, duration = 60)))
        // encostado na ponta é permitido: domingo 23:30 + 60 min termina às 00:30 da segunda
        assertTrue(accepted(valid(slots = listOf(slot(DayOfWeek.SUNDAY, "23:30"), slot(DayOfWeek.MONDAY, "00:30")), duration = 60)))
    }

    @Test fun `a sobreposição conta a duração exata em minutos`() {
        val slots = listOf(slot(DayOfWeek.TUESDAY, "10:00"), slot(DayOfWeek.TUESDAY, "11:30"))
        assertTrue(rejected(valid(slots = slots, duration = 91)))
        assertTrue(accepted(valid(slots = slots, duration = 90)))
        // ordem inversa dos horários, mesma regra
        assertTrue(rejected(valid(slots = slots.reversed(), duration = 91)))
    }
}
