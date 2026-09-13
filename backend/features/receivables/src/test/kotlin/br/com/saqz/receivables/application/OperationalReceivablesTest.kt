package br.com.saqz.receivables.application

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class OperationalReceivablesTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val operation = OperationalOperation(
        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), OperationKind.CREATE_INSTRUMENT,
        UUID.randomUUID(), OperationStatus.UNKNOWN, 2, "PROVIDER_TIMEOUT", now.minusSeconds(60), now, now,
    )

    @Test
    fun `recovery observes before completing and keeps timeout unknown`() {
        val calls = mutableListOf<String>()
        val store = FakeStore(operation, calls)
        val service = RecoverOperationalFailure(store, OperationalRecoveryProbe {
            calls += "observe"
            throw java.net.http.HttpTimeoutException("timeout")
        }, Clock.fixed(now, ZoneOffset.UTC))

        val result = service.execute(command()) as OperationalRecoveryResultEnvelope.Done

        assertEquals(OperationalRecoveryResult.STILL_UNKNOWN, result.outcome.result)
        assertEquals(OperationStatus.UNKNOWN, result.outcome.operationStatus)
        assertEquals(listOf("reserve", "observe", "complete:UNKNOWN"), calls)
    }

    @Test
    fun `confirmed observation is audited once and replay does not query again`() {
        val calls = mutableListOf<String>()
        val stored = OperationalRecoveryOutcome(UUID.randomUUID(), operation.id,
            OperationalRecoveryResult.CONFIRMED, OperationStatus.SUCCEEDED)
        val replayStore = object : FakeStore(operation, calls) {
            override fun reserve(command: OperationalRecoveryCommand, now: Instant, leaseUntil: Instant) =
                OperationalRecoveryReservation.Replay(stored)
        }
        val service = RecoverOperationalFailure(replayStore, OperationalRecoveryProbe {
            fail("replay cannot touch provider")
        }, Clock.fixed(now, ZoneOffset.UTC))

        val result = service.execute(command()) as OperationalRecoveryResultEnvelope.Done

        assertEquals(stored, result.outcome)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `withdraw and refund are never recoverable`() {
        for (kind in listOf(OperationKind.WITHDRAW, OperationKind.REFUND)) {
            assertFalse(operation.copy(kind = kind).recoverable)
        }
    }

    @Test
    fun `invalid reason is rejected before persistence and provider`() {
        val calls = mutableListOf<String>()
        val service = RecoverOperationalFailure(FakeStore(operation, calls), OperationalRecoveryProbe {
            calls += "observe"; OperationalRecoveryObservation(OperationStatus.SUCCEEDED)
        }, Clock.fixed(now, ZoneOffset.UTC))

        assertEquals(OperationalRecoveryResultEnvelope.Invalid, service.execute(command(reason = " x ")))
        assertTrue(calls.isEmpty())
    }

    private fun command(reason: String = "Consulta após timeout") = OperationalRecoveryCommand(
        UUID.randomUUID(), operation.id, UUID.randomUUID(), reason,
    )

    private open class FakeStore(
        private val operation: OperationalOperation,
        private val calls: MutableList<String>,
    ) : OperationalReceivablesStore {
        override fun list(status: OperationStatus?, kind: OperationKind?, page: Int, size: Int) = error("unused")
        override fun detail(operationId: UUID) = error("unused")
        override fun reserve(command: OperationalRecoveryCommand, now: Instant, leaseUntil: Instant): OperationalRecoveryReservation {
            calls += "reserve"
            return OperationalRecoveryReservation.Claimed(UUID.randomUUID(), operation)
        }
        override fun complete(command: OperationalRecoveryCommand, token: UUID, observation: OperationalRecoveryObservation, now: Instant): OperationalRecoveryOutcome {
            calls += "complete:${observation.status}"
            return OperationalRecoveryOutcome(command.requestId, command.operationId,
                when (observation.status) {
                    OperationStatus.SUCCEEDED -> OperationalRecoveryResult.CONFIRMED
                    OperationStatus.REJECTED -> OperationalRecoveryResult.REJECTED
                    else -> OperationalRecoveryResult.STILL_UNKNOWN
                }, observation.status)
        }
    }
}
