package br.com.saqz.subscriptions.application

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SendPurchaseInformationCommand(
    val ownerUserId: UUID,
)

sealed interface SendPurchaseInformationResult {
    data object Success : SendPurchaseInformationResult

    data object EmailNotFound : SendPurchaseInformationResult

    data class InProgress(val retryAfterSeconds: Int) : SendPurchaseInformationResult {
        init {
            require(retryAfterSeconds > 0) { "retryAfterSeconds must be positive" }
        }
    }

    data class RateLimited(val retryAfterSeconds: Int) : SendPurchaseInformationResult {
        init {
            require(retryAfterSeconds > 0) { "retryAfterSeconds must be positive" }
        }
    }

    data object Failed : SendPurchaseInformationResult
}

/** Resolves the recipient through a subscriptions-owned port. */
fun interface SubscriptionEmailLookup {
    fun findEmail(ownerUserId: UUID): String?
}

/** Sends the purchase information after the application has secured a reservation. */
fun interface PurchaseInformationSender {
    fun send(recipient: String)
}

fun interface PurchaseInformationOperationalLog {
    fun failure(ownerUserId: UUID, reservationToken: String?, type: String, cause: String)
}

data class PurchaseInformationReservation(
    val ownerUserId: UUID,
    val token: String,
)

sealed interface PurchaseInformationReservationResult {
    data class Reserved(val reservation: PurchaseInformationReservation) : PurchaseInformationReservationResult

    /** A successful send exists inside the deduplication window. */
    data object AlreadySent : PurchaseInformationReservationResult

    /** Another worker currently owns the durable reservation. */
    data class InProgress(val retryAfterSeconds: Int) : PurchaseInformationReservationResult {
        init {
            require(retryAfterSeconds > 0) { "retryAfterSeconds must be positive" }
        }
    }

    /** The owner reached the successful-send limit for the current window. */
    data class RateLimited(val retryAfterSeconds: Int) : PurchaseInformationReservationResult {
        init {
            require(retryAfterSeconds > 0) { "retryAfterSeconds must be positive" }
        }
    }

    data object Failed : PurchaseInformationReservationResult
}

/**
 * Durable state for purchase-information delivery. Implementations must atomically apply
 * [SendPurchaseInformation.DEDUPE_WINDOW], [SendPurchaseInformation.RATE_LIMIT_WINDOW],
 * and [SendPurchaseInformation.MAX_SUCCESSFUL_SENDS] in [reserve].
 */
interface PurchaseInformationReservationPort {
    fun reserve(ownerUserId: UUID, now: Instant): PurchaseInformationReservationResult

    /** Records one successful send and starts its deduplication window. */
    fun complete(reservation: PurchaseInformationReservation, completedAt: Instant): Boolean

    /** Releases only an uncompleted reservation after a sender failure. */
    fun release(reservation: PurchaseInformationReservation): Boolean
}

class SendPurchaseInformation(
    private val emailLookup: SubscriptionEmailLookup,
    private val reservations: PurchaseInformationReservationPort,
    private val sender: PurchaseInformationSender,
    private val clock: Clock = Clock.systemUTC(),
    private val operationalLog: PurchaseInformationOperationalLog =
        PurchaseInformationOperationalLog { _, _, _, _ -> },
) {
    fun execute(command: SendPurchaseInformationCommand): SendPurchaseInformationResult {
        val email = try {
            emailLookup.findEmail(command.ownerUserId)
        } catch (cause: Exception) {
            logFailure(command.ownerUserId, null, "lookup", cause)
            return SendPurchaseInformationResult.Failed
        }?.trim()?.takeIf(String::isNotEmpty) ?: run {
            logFailure(command.ownerUserId, null, "lookup", "not_found")
            return SendPurchaseInformationResult.EmailNotFound
        }

        val now = clock.instant()
        return when (val outcome = try {
            reservations.reserve(command.ownerUserId, now)
        } catch (cause: Exception) {
            logFailure(command.ownerUserId, null, "reserve", cause)
            PurchaseInformationReservationResult.Failed
        }) {
            is PurchaseInformationReservationResult.Reserved -> send(email, outcome.reservation)
            PurchaseInformationReservationResult.AlreadySent -> SendPurchaseInformationResult.Success
            is PurchaseInformationReservationResult.InProgress ->
                SendPurchaseInformationResult.InProgress(outcome.retryAfterSeconds)
            is PurchaseInformationReservationResult.RateLimited ->
                SendPurchaseInformationResult.RateLimited(outcome.retryAfterSeconds)
            PurchaseInformationReservationResult.Failed -> {
                logFailure(command.ownerUserId, null, "reserve", "port_failure")
                SendPurchaseInformationResult.Failed
            }
        }
    }

    private fun send(
        email: String,
        reservation: PurchaseInformationReservation,
    ): SendPurchaseInformationResult {
        try {
            sender.send(email)
        } catch (cause: Exception) {
            logFailure(reservation.ownerUserId, reservation.token, "send", cause)
            val released = try {
                reservations.release(reservation)
            } catch (releaseCause: Exception) {
                logFailure(reservation.ownerUserId, reservation.token, "release", releaseCause)
                null
            }
            if (released == false) {
                logFailure(reservation.ownerUserId, reservation.token, "release", "compare_and_set_false")
            }
            return SendPurchaseInformationResult.Failed
        }

        return try {
            if (reservations.complete(reservation, clock.instant())) {
                SendPurchaseInformationResult.Success
            } else {
                logFailure(reservation.ownerUserId, reservation.token, "complete", "compare_and_set_false")
                SendPurchaseInformationResult.Failed
            }
        } catch (cause: Exception) {
            logFailure(reservation.ownerUserId, reservation.token, "complete", cause)
            SendPurchaseInformationResult.Failed
        }
    }

    private fun logFailure(ownerUserId: UUID, reservationToken: String?, type: String, cause: Exception) =
        operationalLog.failure(ownerUserId, reservationToken, type, cause::class.simpleName ?: "unknown")

    private fun logFailure(ownerUserId: UUID, reservationToken: String?, type: String, cause: String) =
        operationalLog.failure(ownerUserId, reservationToken, type, cause)

    companion object {
        val DEDUPE_WINDOW: Duration = Duration.ofMinutes(15)
        val RATE_LIMIT_WINDOW: Duration = Duration.ofHours(1)
        const val MAX_SUCCESSFUL_SENDS = 3
    }
}
