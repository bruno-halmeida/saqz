package br.com.saqz.receivables.application

import java.time.Clock
import java.time.Instant
import java.util.UUID

data class OperationalOperation(
    val id: UUID,
    val accountId: UUID,
    val requestId: UUID,
    val kind: OperationKind,
    val resourceId: UUID,
    val status: OperationStatus,
    val attempts: Int,
    val failureCode: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val nextAttemptAt: Instant,
) {
    val recoverable: Boolean get() = status in setOf(OperationStatus.UNKNOWN, OperationStatus.RUNNING) &&
        kind in RECOVERABLE_KINDS

    companion object {
        val RECOVERABLE_KINDS = setOf(OperationKind.CREATE_INSTRUMENT, OperationKind.CANCEL_INSTRUMENT)
    }
}

data class OperationalPage<T>(val items: List<T>, val page: Int, val size: Int, val total: Long) {
    val hasNext: Boolean get() = page.toLong() * size < total
}

data class OperationalAudit(
    val actorUserId: UUID,
    val requestId: UUID,
    val action: String,
    val reason: String,
    val result: OperationalRecoveryResult?,
    val operationStatus: OperationStatus?,
    val createdAt: Instant,
)

data class OperationalOperationDetail(val operation: OperationalOperation, val audit: List<OperationalAudit>)

enum class OperationalRecoveryResult { CONFIRMED, REJECTED, STILL_UNKNOWN, NOT_RECOVERABLE }

data class OperationalRecoveryOutcome(
    val requestId: UUID,
    val operationId: UUID,
    val result: OperationalRecoveryResult,
    val operationStatus: OperationStatus,
)

data class OperationalRecoveryCommand(
    val requestId: UUID,
    val operationId: UUID,
    val actorUserId: UUID,
    val reason: String,
)

sealed interface OperationalRecoveryReservation {
    data class Claimed(val token: UUID, val operation: OperationalOperation) : OperationalRecoveryReservation
    data class Replay(val outcome: OperationalRecoveryOutcome) : OperationalRecoveryReservation
    data object NotFound : OperationalRecoveryReservation
    data object NotRecoverable : OperationalRecoveryReservation
    data object Conflict : OperationalRecoveryReservation
    data object Busy : OperationalRecoveryReservation
}

interface OperationalReceivablesStore {
    fun list(status: OperationStatus?, kind: OperationKind?, page: Int, size: Int): OperationalPage<OperationalOperation>
    fun detail(operationId: UUID): OperationalOperationDetail?
    fun reserve(command: OperationalRecoveryCommand, now: Instant, leaseUntil: Instant): OperationalRecoveryReservation
    fun complete(command: OperationalRecoveryCommand, token: UUID, observation: OperationalRecoveryObservation, now: Instant): OperationalRecoveryOutcome
}

data class OperationalRecoveryObservation(val status: OperationStatus) {
    init { require(status in setOf(OperationStatus.SUCCEEDED, OperationStatus.REJECTED, OperationStatus.UNKNOWN, OperationStatus.RUNNING)) }
}

fun interface OperationalRecoveryProbe {
    /** Query/reconcile an existing remote resource. Implementations must never create a new resource. */
    fun observe(operation: OperationalOperation): OperationalRecoveryObservation
}

class RecoverOperationalFailure(
    private val store: OperationalReceivablesStore,
    private val probe: OperationalRecoveryProbe,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun execute(command: OperationalRecoveryCommand): OperationalRecoveryResultEnvelope {
        if (command.reason.trim().length !in 3..500 || command.reason != command.reason.trim()) {
            return OperationalRecoveryResultEnvelope.Invalid
        }
        val now = clock.instant()
        return when (val reservation = store.reserve(command, now, now.plusSeconds(90))) {
            is OperationalRecoveryReservation.Claimed -> {
                val observation = try { probe.observe(reservation.operation) }
                catch (_: Exception) { OperationalRecoveryObservation(OperationStatus.UNKNOWN) }
                OperationalRecoveryResultEnvelope.Done(store.complete(command, reservation.token, observation, clock.instant()))
            }
            is OperationalRecoveryReservation.Replay -> OperationalRecoveryResultEnvelope.Done(reservation.outcome)
            OperationalRecoveryReservation.NotFound -> OperationalRecoveryResultEnvelope.NotFound
            OperationalRecoveryReservation.NotRecoverable -> OperationalRecoveryResultEnvelope.NotRecoverable
            OperationalRecoveryReservation.Conflict -> OperationalRecoveryResultEnvelope.Conflict
            OperationalRecoveryReservation.Busy -> OperationalRecoveryResultEnvelope.Busy
        }
    }
}

sealed interface OperationalRecoveryResultEnvelope {
    data class Done(val outcome: OperationalRecoveryOutcome) : OperationalRecoveryResultEnvelope
    data object Invalid : OperationalRecoveryResultEnvelope
    data object NotFound : OperationalRecoveryResultEnvelope
    data object NotRecoverable : OperationalRecoveryResultEnvelope
    data object Conflict : OperationalRecoveryResultEnvelope
    data object Busy : OperationalRecoveryResultEnvelope
}

enum class OperationalNoticeAudience { OPERATIONS, PLAN_OWNERS }

data class OperationalNotice(
    val id: UUID,
    val requestId: UUID,
    val audience: OperationalNoticeAudience,
    val title: String,
    val message: String,
    val startsAt: Instant,
    val endsAt: Instant?,
    val createdAt: Instant,
)

data class PublishOperationalNotice(
    val requestId: UUID,
    val actorUserId: UUID,
    val audience: OperationalNoticeAudience,
    val title: String,
    val message: String,
    val startsAt: Instant,
    val endsAt: Instant?,
)

interface OperationalNotices {
    fun publish(command: PublishOperationalNotice, now: Instant): OperationalNotice
    fun list(audience: OperationalNoticeAudience?, page: Int, size: Int): OperationalPage<OperationalNotice>
    fun active(audience: OperationalNoticeAudience, at: Instant): List<OperationalNotice>
}

class OperationalNoticeConflict : RuntimeException("Operational notice request conflict")
