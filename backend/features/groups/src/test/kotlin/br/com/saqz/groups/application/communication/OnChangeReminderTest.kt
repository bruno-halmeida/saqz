package br.com.saqz.groups.application.communication

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Modo "a cada confirmação": lista no grupo do WhatsApp quando ela muda, com freio de 15 min e 12 por dia. */
class OnChangeReminderTest {
    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val repository = MemoryCommunicationRepository()
    private var frozen = false
    private val service = GroupCommunicationService(
        CountingTransaction(), FixedRoleGroups(group, GroupRole.OWNER), repository, GroupWriteAccess { !frozen },
    )
    private val candidate = ReminderCandidate(
        UUID.randomUUID(), group, owner, ReminderGame(LocalDate.of(2026, 9, 20), LocalTime.of(19, 30), "Arena Central"),
    )
    // 12h em São Paulo
    private val noon = Instant.parse("2026-09-18T15:00:00Z")

    init {
        repository.onChangeReminders = listOf(candidate)
    }

    @Test fun `the first pass publishes and marks the message as ON_CHANGE`() {
        roster(confirmed = listOf("Ana"))
        assertEquals(1, service.remindOnChanges(noon))
        val sent = repository.published.single()
        assertEquals(AttendanceUpdatesMode.ON_CHANGE, sent.reminderOrigin)
        assertEquals(MessageChannel.REMINDER, sent.channel)
        assertEquals(owner, sent.actor)
        assertTrue("✅ Confirmados:\nAna" in sent.body)
        assertEquals(1, repository.digests.getValue(candidate.gameId).dayCount)
    }

    @Test fun `no change publishes nothing`() {
        roster(confirmed = listOf("Ana"))
        service.remindOnChanges(noon)
        assertEquals(0, service.remindOnChanges(noon.plusMinutes(30)))
        assertEquals(1, repository.published.size)
    }

    @Test fun `publishes when a name changes bucket either way and when a new name arrives`() {
        roster(confirmed = listOf("Ana"))
        service.remindOnChanges(noon)
        roster(declined = listOf("Ana"))
        assertEquals(1, service.remindOnChanges(noon.plusMinutes(16)))
        roster(confirmed = listOf("Ana"))
        assertEquals(1, service.remindOnChanges(noon.plusMinutes(32)))
        roster(confirmed = listOf("Ana", "Bia"))
        assertEquals(1, service.remindOnChanges(noon.plusMinutes(48)))
        assertEquals(4, repository.published.size)
    }

    @Test fun `a change 5 minutes later waits and goes out after 15 minutes`() {
        roster(confirmed = listOf("Ana"))
        service.remindOnChanges(noon)
        roster(confirmed = listOf("Ana", "Bia"))
        assertEquals(0, service.remindOnChanges(noon.plusMinutes(5)))
        assertEquals(1, service.remindOnChanges(noon.plusMinutes(16)))
        assertTrue("Bia" in repository.published.last().body)
    }

    @Test fun `the 12th message of the day goes out, the 13th does not, and the next day resets`() {
        repeat(12) { index ->
            roster(confirmed = List(index + 1) { "Atleta $it" })
            assertEquals(1, service.remindOnChanges(noon.plusMinutes(20L * index - 200)), "mensagem ${index + 1}")
        }
        roster(confirmed = listOf("Outro"))
        assertEquals(0, service.remindOnChanges(noon.plusMinutes(60)))
        assertEquals(12, repository.digests.getValue(candidate.gameId).dayCount)
        // 00h05 do dia seguinte em São Paulo
        assertEquals(1, service.remindOnChanges(Instant.parse("2026-09-19T03:05:00Z")))
        assertEquals(1, repository.digests.getValue(candidate.gameId).dayCount)
    }

    @Test fun `a frozen group gets nothing`() {
        frozen = true
        roster(confirmed = listOf("Ana"))
        assertEquals(0, service.remindOnChanges(noon))
        assertTrue(repository.published.isEmpty())
    }

    @Test fun `the scheduled reminder is marked SCHEDULED and the manual one has no origin`() {
        repository.reminders = listOf(candidate)
        service.remindAutomatically()
        repository.gameForReminder = candidate.game
        service.remind(owner, group, candidate.gameId, UUID.randomUUID())
        assertEquals(listOf(AttendanceUpdatesMode.SCHEDULED, null), repository.published.map { it.reminderOrigin })
    }

    private fun roster(confirmed: List<String> = emptyList(), declined: List<String> = emptyList()) {
        repository.roster = ReminderRoster(confirmed, emptyList(), declined)
    }

    private fun Instant.plusMinutes(minutes: Long): Instant = plusSeconds(minutes * 60)
}
