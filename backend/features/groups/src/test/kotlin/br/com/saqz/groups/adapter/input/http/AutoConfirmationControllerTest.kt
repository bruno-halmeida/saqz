package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.attendance.AutoConfirmAttendance
import br.com.saqz.groups.application.attendance.AutoConfirmationGame
import br.com.saqz.groups.application.attendance.AutoConfirmationOptInUpdate
import br.com.saqz.groups.application.attendance.AutoConfirmationRepository
import br.com.saqz.groups.application.attendance.AttendanceEvent
import br.com.saqz.groups.application.attendance.AttendanceRecord
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.attendance.AutoConfirmationCandidate
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AutoConfirmationControllerTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private lateinit var repository: RecordingRepository
    private lateinit var controller: AutoConfirmationController

    @BeforeEach
    fun setup() {
        repository = RecordingRepository()
        controller = AutoConfirmationController(
            VerifiedGroupActorResolver { actor },
            AutoConfirmAttendance(
                object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
                repository,
                { Instant.parse("2026-08-03T12:00:00Z") },
            ),
        )
    }

    @Test
    fun `member can toggle own opt in`() {
        val response = controller.update(ID, "$group", AutoConfirmationRequest(true))

        assertEquals(true, response.enabled)
        assertEquals(listOf(group to (actor to true)), repository.updates)
    }

    @Test
    fun `avulso receives a clear validation error`() {
        repository.result = AutoConfirmationOptInUpdate.NotMensalista

        val failure = assertFailsWith<InvalidGroupRequestException> {
            controller.update(ID, "$group", AutoConfirmationRequest(true))
        }

        assertEquals(422, failure.status)
        assertEquals(listOf("only MENSALISTA members may opt in"), failure.fieldErrors["enabled"])
    }

    @Test
    fun `disabled group receives a clear validation error`() {
        repository.result = AutoConfirmationOptInUpdate.FeatureDisabled

        val failure = assertFailsWith<InvalidGroupRequestException> {
            controller.update(ID, "$group", AutoConfirmationRequest(false))
        }

        assertEquals(422, failure.status)
        assertEquals(listOf("group auto-confirmation is disabled"), failure.fieldErrors["enabled"])
    }

    private class RecordingRepository : AutoConfirmationRepository {
        var result: AutoConfirmationOptInUpdate = AutoConfirmationOptInUpdate.Success(true)
        val updates = mutableListOf<Pair<UUID, Pair<UUID, Boolean>>>()

        override fun updateOwnOptIn(groupId: UUID, memberId: UUID, enabled: Boolean): AutoConfirmationOptInUpdate {
            updates += groupId to (memberId to enabled)
            return result
        }

        override fun lockGame(groupId: UUID, gameId: UUID): AutoConfirmationGame? = error("unused")
        override fun lockOccurrence(groupId: UUID, seriesId: UUID, localDate: LocalDate, slotKey: UUID): AutoConfirmationGame? = error("unused")
        override fun candidates(gameId: UUID): List<AutoConfirmationCandidate> = error("unused")
        override fun nextWaitlistSequence(groupId: UUID, gameId: UUID): Long = error("unused")
        override fun save(record: AttendanceRecord) = error("unused")
        override fun append(event: AttendanceEvent) = error("unused")
    }

    private companion object {
        val ID = RequestIdentity("subject", emailVerified = true, displayName = "Player")
    }
}
