package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorRecurrenceGateway(private val network: AuthenticatedNetworkClient) : RecurrenceGateway {
    override suspend fun discover(accountId: String, groupId: String): SaqzResult<PaymentRecurrence?, ReceiptError> {
        if (accountId.isBlank() || groupId.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        val request = NetworkRequest(query = mapOf("accountId" to accountId, "groupId" to groupId))
        return when (val result = retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, "$RECURRENCES/current",
                EnvelopeTransport.serializer(PaymentRecurrenceTransport.serializer()), request)
        }) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(false))
            is NetworkResult.Success -> discovered(result.value, accountId, groupId)
        }
    }

    override suspend fun preview(command: RecurrencePreviewCommand): SaqzResult<RecurrenceReview, ReceiptError> {
        if (!command.valid()) return SaqzResult.Failure(ReceiptError.INVALID)
        return write(RECURRENCES + "/preview", RecurrenceReviewTransport.serializer(), command.requestId,
            Json.encodeToString(RecurrencePreviewTransport(command.requestId, command.accountId, command.groupId,
                command.method.name, command.firstDueDate))) { transport ->
            transport.domain().also { require(it.accountId == command.accountId && it.groupId == command.groupId &&
                it.method == command.method && it.firstDueDate == command.firstDueDate) }
        }
    }

    override suspend fun authorize(review: RecurrenceReview, command: RecurrenceAcceptanceCommand) =
        acceptance(RECURRENCES, review, command, stopped = null)

    override suspend fun get(recurrenceId: String): SaqzResult<PaymentRecurrence, ReceiptError> {
        if (recurrenceId.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        return read("$RECURRENCES/${recurrenceId.encodeURLPathPart()}", PaymentRecurrenceTransport.serializer()) { transport ->
            transport.domain().also { require(it.id == recurrenceId &&
                it.validFor(it.accountId, it.groupId, it.memberUserId)) }
        }
    }

    override suspend fun cancel(recurrence: PaymentRecurrence, requestId: String): SaqzResult<PaymentRecurrence, ReceiptError> {
        if (requestId.isBlank() || recurrence.id.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        return write("$RECURRENCES/${recurrence.id.encodeURLPathPart()}/cancel", PaymentRecurrenceTransport.serializer(),
            requestId, Json.encodeToString(RecurrenceCancelTransport(requestId))) { transport ->
            transport.domain().also { require(it.validCancelResult(recurrence)) }
        }
    }

    override suspend fun resume(stopped: PaymentRecurrence, review: RecurrenceReview, command: RecurrenceAcceptanceCommand) =
        acceptance("$RECURRENCES/${stopped.id.encodeURLPathPart()}/resume", review, command, stopped)

    override suspend fun recover(attempt: RecurrenceAttempt): SaqzResult<PaymentRecurrence?, ReceiptError> {
        if (!attempt.valid()) return SaqzResult.Failure(ReceiptError.INVALID)
        val path = "$RECURRENCES/by-request/${attempt.requestId.encodeURLPathPart()}"
        return when (val result = retryTransport(RetrySafety.Read) {
            network.execute(HttpMethod.Get, path, EnvelopeTransport.serializer(PaymentRecurrenceTransport.serializer()))
        }) {
            is NetworkResult.Failure -> if (result.error.isNotFound()) SaqzResult.Success(null)
                else SaqzResult.Failure(result.error.recoveryError())
            is NetworkResult.Success -> try {
                val envelope = result.value
                val recurrence = envelope.value?.domain()
                if (result.metadata.status == 202 || !envelope.correlates(attempt, recurrence))
                    SaqzResult.Failure(ReceiptError.UNCERTAIN) else SaqzResult.Success(recurrence)
            } catch (_: IllegalArgumentException) { SaqzResult.Failure(ReceiptError.UNCERTAIN) }
        }
    }

    private suspend fun acceptance(path: String, review: RecurrenceReview, command: RecurrenceAcceptanceCommand,
        stopped: PaymentRecurrence?): SaqzResult<PaymentRecurrence, ReceiptError> {
        if (!command.matches(review, stopped)) return SaqzResult.Failure(ReceiptError.INVALID)
        val body = Json.encodeToString(RecurrenceAcceptanceTransport(command.requestId, command.accountId, command.groupId,
            command.method.name, command.firstDueDate, command.fingerprint, command.accepted,
            RecurrencePayerTransport(command.payer.name, command.payer.cpfCnpj)))
        return write(path, PaymentRecurrenceTransport.serializer(), command.requestId, body) { transport ->
            transport.domain().also { recurrence ->
                require(recurrence.validFor(command.accountId, command.groupId, review.memberUserId) &&
                    recurrence.method == command.method && recurrence.firstDueDate == command.firstDueDate &&
                    recurrence.baseCents == review.baseCents && recurrence.feesCents == review.feesCents &&
                    recurrence.totalCents == review.totalCents && (stopped == null || recurrence.id != stopped.id))
            }
        }
    }

    private suspend fun <T, R> read(path: String, serializer: KSerializer<T>, map: (T) -> R): SaqzResult<R, ReceiptError> =
        call(HttpMethod.Get, path, serializer, null, null, map)

    private suspend fun <T, R> write(path: String, serializer: KSerializer<T>, requestId: String, body: String,
        map: (T) -> R): SaqzResult<R, ReceiptError> = call(HttpMethod.Post, path, serializer, requestId, body, map)

    private suspend fun <T, R> call(method: HttpMethod, path: String, serializer: KSerializer<T>, requestId: String?,
        body: String?, map: (T) -> R): SaqzResult<R, ReceiptError> {
        val writing = requestId != null
        val execute = suspend {
            network.execute(method, path, EnvelopeTransport.serializer(serializer), NetworkRequest(body = body))
        }
        val result = if (writing) execute() else retryTransport(RetrySafety.Read) { execute() }
        return when (result) {
            is NetworkResult.Failure -> SaqzResult.Failure(result.error.paymentError(writing))
            is NetworkResult.Success -> try {
                val envelope = result.value
                if (result.metadata.status == 202 || !envelope.validEnvelope(requestId))
                    SaqzResult.Failure(if (writing) ReceiptError.UNCERTAIN else ReceiptError.INVALID)
                else SaqzResult.Success(map(requireNotNull(envelope.value)))
            } catch (_: IllegalArgumentException) {
                SaqzResult.Failure(if (writing) ReceiptError.UNCERTAIN else ReceiptError.INVALID)
            }
        }
    }

    private fun RecurrencePreviewCommand.valid() = requestId.isNotBlank() && accountId.isNotBlank() && groupId.isNotBlank() &&
        firstDueDate.isPaymentDate()
    private fun RecurrenceAcceptanceCommand.valid() = requestId.isNotBlank() && accepted && accountId.isNotBlank() &&
        groupId.isNotBlank() && firstDueDate.isPaymentDate() &&
        fingerprint.matches(Regex("[a-f0-9]{64}")) && payer.name.trim().length in 2..120 &&
        payer.name.none(Char::isISOControl) && payer.cpfCnpj.matches(Regex("[0-9]{11}|[0-9]{14}"))
    private fun RecurrenceAttempt.valid() = requestId.isNotBlank() && actorId.isNotBlank() && accountId.isNotBlank() &&
        groupId.isNotBlank() && operation in setOf("AUTHORIZE", "RESUME", "CANCEL") &&
        (operation == "AUTHORIZE" || !recurrenceId.isNullOrBlank())

    private fun discovered(envelope: EnvelopeTransport<PaymentRecurrenceTransport>, accountId: String, groupId: String):
        SaqzResult<PaymentRecurrence?, ReceiptError> = try {
        if (envelope.error != null || envelope.requestId.isBlank()) return SaqzResult.Failure(ReceiptError.INVALID)
        val recurrence = envelope.value?.domain() ?: return SaqzResult.Success(null)
        if (recurrence.validFor(accountId, groupId, recurrence.memberUserId)) SaqzResult.Success(recurrence)
        else SaqzResult.Failure(ReceiptError.INVALID)
    } catch (_: IllegalArgumentException) { SaqzResult.Failure(ReceiptError.INVALID) }

    private fun PaymentRecurrence.validCancelResult(previous: PaymentRecurrence) =
        id == previous.id && groupId == previous.groupId && memberUserId == previous.memberUserId &&
            validFor(previous.accountId, previous.groupId, previous.memberUserId) && status in CANCELLING

    private fun EnvelopeTransport<PaymentRecurrenceTransport>.correlates(
        attempt: RecurrenceAttempt,
        recurrence: PaymentRecurrence?,
    ): Boolean {
        if (error != null || requestId != attempt.requestId || recurrence == null) return false
        if (!recurrence.validFor(attempt.accountId, attempt.groupId, attempt.actorId)) return false
        if (attempt.operation == "CANCEL" && (recurrence.id != attempt.recurrenceId || recurrence.status !in CANCELLING)) return false
        return attempt.operation != "RESUME" || recurrence.id != attempt.recurrenceId
    }

    private fun RecurrenceAcceptanceCommand.matches(review: RecurrenceReview, stopped: PaymentRecurrence?): Boolean {
        if (!valid() || fingerprint != review.fingerprint || accountId != review.accountId || groupId != review.groupId) return false
        if (method != review.method || firstDueDate != review.firstDueDate) return false
        return stopped == null || (stopped.status == "STOPPED" && stopped.accountId == accountId && stopped.groupId == groupId)
    }

    private fun <T> EnvelopeTransport<T>.validEnvelope(expectedRequest: String?) =
        error == null && value != null && requestId.isNotBlank() && (expectedRequest == null || requestId == expectedRequest)
}

private const val RECURRENCES = "api/receivables/recurrences"
private val CANCELLING = setOf("STOP_PENDING", "STOPPED")
private fun NetworkError.isNotFound() = when (this) {
    is NetworkError.HttpStatus -> status == 404
    is NetworkError.ApiProblemError -> problem.status == 404
    else -> false
}
private fun NetworkError.recoveryError() = when (this) {
    is NetworkError.HttpStatus -> status.paymentRecoveryError()
    is NetworkError.ApiProblemError -> problem.status.paymentRecoveryError()
    NetworkError.Unavailable -> ReceiptError.SIGNED_OUT
    else -> ReceiptError.UNCERTAIN
}
private fun Int.paymentRecoveryError() = when (this) {
    401 -> ReceiptError.SIGNED_OUT
    403 -> ReceiptError.DENIED
    409 -> ReceiptError.STALE
    else -> ReceiptError.UNCERTAIN
}
