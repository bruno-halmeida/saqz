package br.com.saqz.subscriptions.application

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PurchaseInformationTest {
    private val ownerUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val email = "owner@example.test"
    private val start = Instant.parse("2026-08-10T12:00:00Z")

    private lateinit var clock: TestClock
    private lateinit var emails: FakeSubscriptionEmailLookup
    private lateinit var reservations: FakePurchaseInformationReservationPort
    private lateinit var sender: RecordingPurchaseInformationSender
    private lateinit var checkoutTokens: FakeCheckoutLoginTokens
    private lateinit var operationalLog: RecordingPurchaseInformationOperationalLog
    private lateinit var useCase: SendPurchaseInformation

    @BeforeEach
    fun setUp() {
        clock = TestClock(start)
        emails = FakeSubscriptionEmailLookup(email)
        reservations = FakePurchaseInformationReservationPort()
        sender = RecordingPurchaseInformationSender()
        checkoutTokens = FakeCheckoutLoginTokens()
        operationalLog = RecordingPurchaseInformationOperationalLog()
        useCase = SendPurchaseInformation(
            emailLookup = emails,
            reservations = reservations,
            sender = sender,
            checkoutLinks = CheckoutLoginLinkFactory(checkoutTokens, "https://checkout.test/assinar/"),
            clock = clock,
            operationalLog = operationalLog,
        )
    }

    @Test
    fun `resolves the subscription email and sends purchase information`() {
        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.Success, result)
        assertEquals(listOf(email), sender.recipients)
        assertEquals(listOf("https://checkout.test/assinar/?t=${"A".repeat(43)}"), sender.checkoutLinks)
        assertEquals(listOf(ownerUserId), emails.lookups)
        assertEquals(listOf(ownerUserId), checkoutTokens.issuedOwners)
        assertEquals(1, reservations.completed.size)
        assertTrue(reservations.released.isEmpty())
        assertEquals(start, reservations.completed.single().second)
    }

    @Test
    fun `returns typed absence without reserving or sending when subscription email is missing`() {
        emails.email = null

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.EmailNotFound, result)
        assertTrue(reservations.reserved.isEmpty())
        assertTrue(sender.recipients.isEmpty())
        assertEquals(
            listOf(RecordingPurchaseInformationOperationalLog.Failure(ownerUserId, null, "lookup", "not_found")),
            operationalLog.failures,
        )
    }

    @Test
    fun `maps subscription email lookup failure to a typed failure`() {
        emails.failure = IllegalStateException("subscription lookup unavailable")

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.Failed, result)
        assertTrue(reservations.reserved.isEmpty())
        assertTrue(sender.recipients.isEmpty())
        assertEquals(
            listOf(
                RecordingPurchaseInformationOperationalLog.Failure(
                    ownerUserId,
                    null,
                    "lookup",
                    "IllegalStateException",
                ),
            ),
            operationalLog.failures,
        )
    }

    @Test
    fun `returns typed in-progress outcome without sending`() {
        reservations.next = PurchaseInformationReservationResult.InProgress(retryAfterSeconds = 17)

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.InProgress(retryAfterSeconds = 17), result)
        assertTrue(sender.recipients.isEmpty())
        assertTrue(reservations.completed.isEmpty())
    }

    @Test
    fun `returns typed rate limit outcome without sending`() {
        reservations.next = PurchaseInformationReservationResult.RateLimited(retryAfterSeconds = 31)

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.RateLimited(retryAfterSeconds = 31), result)
        assertTrue(sender.recipients.isEmpty())
        assertTrue(reservations.completed.isEmpty())
    }

    @Test
    fun `retry-after values must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            PurchaseInformationReservationResult.InProgress(retryAfterSeconds = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            PurchaseInformationReservationResult.RateLimited(retryAfterSeconds = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SendPurchaseInformationResult.InProgress(retryAfterSeconds = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SendPurchaseInformationResult.RateLimited(retryAfterSeconds = -1)
        }
    }

    @Test
    fun `maps checkout login issue failure to a typed failure without sending`() {
        checkoutTokens.failure = IllegalStateException("token store unavailable")

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.Failed, result)
        assertTrue(sender.recipients.isEmpty())
        assertEquals(1, reservations.released.size)
        assertEquals(
            listOf(
                RecordingPurchaseInformationOperationalLog.Failure(
                    ownerUserId,
                    "reservation-1",
                    "checkout-login",
                    "IllegalStateException",
                ),
            ),
            operationalLog.failures,
        )
    }

    @Test
    fun `maps durable reservation failure to a typed failure without sending`() {
        reservations.next = PurchaseInformationReservationResult.Failed

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.Failed, result)
        assertTrue(sender.recipients.isEmpty())
        assertTrue(reservations.completed.isEmpty())
        assertEquals(
            listOf(RecordingPurchaseInformationOperationalLog.Failure(ownerUserId, null, "reserve", "port_failure")),
            operationalLog.failures,
        )
    }

    @Test
    fun `dedupe reservation is a success without calling the sender`() {
        reservations.next = PurchaseInformationReservationResult.AlreadySent

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.Success, result)
        assertTrue(sender.recipients.isEmpty())
        assertTrue(reservations.completed.isEmpty())
    }

    @Test
    fun `sender failure returns failed even when releasing the reservation returns false`() {
        sender.failure = IllegalStateException("mailer unavailable")
        reservations.releaseResult = false

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.Failed, result)
        assertEquals(listOf(email), sender.recipients)
        assertEquals(1, reservations.released.size)
        assertTrue(reservations.completed.isEmpty())
        assertEquals(
            listOf(
                RecordingPurchaseInformationOperationalLog.Failure(
                    ownerUserId,
                    "reservation-1",
                    "send",
                    "IllegalStateException",
                ),
                RecordingPurchaseInformationOperationalLog.Failure(
                    ownerUserId,
                    "reservation-1",
                    "release",
                    "compare_and_set_false",
                ),
            ),
            operationalLog.failures,
        )
        assertTrue(operationalLog.failures.none { it.cause == "mailer unavailable" })
    }

    @Test
    fun `complete false does not report success after sender delivery`() {
        reservations.completeResult = false

        val result = useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(SendPurchaseInformationResult.Failed, result)
        assertEquals(listOf(email), sender.recipients)
        assertEquals(1, reservations.completionAttempts.size)
        assertTrue(reservations.completed.isEmpty())
        assertTrue(reservations.released.isEmpty())
        assertEquals(
            listOf(
                RecordingPurchaseInformationOperationalLog.Failure(
                    ownerUserId,
                    "reservation-1",
                    "complete",
                    "compare_and_set_false",
                ),
            ),
            operationalLog.failures,
        )
    }

    @Test
    fun `uses the injected clock for reservation and successful completion`() {
        clock.advance(Duration.ofMinutes(3))

        useCase.execute(SendPurchaseInformationCommand(ownerUserId))

        assertEquals(start.plusSeconds(180), reservations.reserved.single().second)
        assertEquals(start.plusSeconds(180), reservations.completed.single().second)
    }

    @Test
    fun `durable reservation decides dedupe after a successful send and allows it after fifteen minutes`() {
        useCase.execute(SendPurchaseInformationCommand(ownerUserId))
        clock.advance(Duration.ofMinutes(14).plusSeconds(59))

        assertEquals(
            SendPurchaseInformationResult.Success,
            useCase.execute(SendPurchaseInformationCommand(ownerUserId)),
        )
        assertEquals(1, sender.recipients.size)

        clock.advance(Duration.ofSeconds(1))

        assertEquals(
            SendPurchaseInformationResult.Success,
            useCase.execute(SendPurchaseInformationCommand(ownerUserId)),
        )
        assertEquals(2, sender.recipients.size)
    }

    @Test
    fun `durable reservation limits successful sends to three in one hour`() {
        repeat(3) {
            assertEquals(
                SendPurchaseInformationResult.Success,
                useCase.execute(SendPurchaseInformationCommand(ownerUserId)),
            )
            clock.advance(Duration.ofMinutes(16))
        }

        assertEquals(
            SendPurchaseInformationResult.RateLimited(retryAfterSeconds = 720),
            useCase.execute(SendPurchaseInformationCommand(ownerUserId)),
        )
        assertEquals(3, sender.recipients.size)

        clock.advance(Duration.ofMinutes(12))

        assertEquals(
            SendPurchaseInformationResult.Success,
            useCase.execute(SendPurchaseInformationCommand(ownerUserId)),
        )
        assertEquals(4, sender.recipients.size)
    }

    private class FakeSubscriptionEmailLookup(var email: String?) : SubscriptionEmailLookup {
        val lookups = mutableListOf<UUID>()
        var failure: Exception? = null

        override fun findEmail(ownerUserId: UUID): String? {
            lookups += ownerUserId
            failure?.let { throw it }
            return email
        }
    }

    private class FakePurchaseInformationReservationPort : PurchaseInformationReservationPort {
        var next: PurchaseInformationReservationResult? = null
        val reserved = mutableListOf<Pair<PurchaseInformationReservation, Instant>>()
        val completed = mutableListOf<Pair<PurchaseInformationReservation, Instant>>()
        val released = mutableListOf<PurchaseInformationReservation>()
        val completionAttempts = mutableListOf<Pair<PurchaseInformationReservation, Instant>>()
        var completeResult = true
        var releaseResult = true

        override fun reserve(
            ownerUserId: UUID,
            now: Instant,
        ): PurchaseInformationReservationResult {
            next?.let {
                next = null
                return it
            }
            val successfulSince = completed.filter {
                (_, sentAt) -> sentAt > now.minus(SendPurchaseInformation.RATE_LIMIT_WINDOW)
            }
            if (successfulSince.any { (_, sentAt) -> sentAt > now.minus(SendPurchaseInformation.DEDUPE_WINDOW) }) {
                return PurchaseInformationReservationResult.AlreadySent
            }
            if (successfulSince.size >= SendPurchaseInformation.MAX_SUCCESSFUL_SENDS) {
                return PurchaseInformationReservationResult.RateLimited(
                    retryAfterSeconds = secondsUntil(now, successfulSince.minOf { (_, sentAt) -> sentAt }),
                )
            }
            val reservation = PurchaseInformationReservation(ownerUserId, "reservation-${reserved.size + 1}")
            reserved += reservation to now
            return PurchaseInformationReservationResult.Reserved(reservation)
        }

        override fun complete(reservation: PurchaseInformationReservation, completedAt: Instant): Boolean {
            completionAttempts += reservation to completedAt
            if (completeResult) completed += reservation to completedAt
            return completeResult
        }

        override fun release(reservation: PurchaseInformationReservation): Boolean {
            released += reservation
            return releaseResult
        }

        private fun secondsUntil(now: Instant, sentAt: Instant): Int =
            Duration.between(now, sentAt.plus(SendPurchaseInformation.RATE_LIMIT_WINDOW))
                .seconds
                .toInt()
                .coerceAtLeast(1)
    }

    private class FakeCheckoutLoginTokens : CheckoutLoginTokens {
        val issuedOwners = mutableListOf<UUID>()
        var failure: Exception? = null
        var nextRaw: String = "A".repeat(43)

        override fun issue(ownerUserId: UUID, now: Instant): String {
            failure?.let { throw it }
            issuedOwners += ownerUserId
            return nextRaw
        }

        override fun findOpen(rawToken: String, now: Instant): OpenCheckoutLogin? = null

        override fun consume(id: UUID, consumedAt: Instant): Boolean = false
    }

    private class RecordingPurchaseInformationSender : PurchaseInformationSender {
        val recipients = mutableListOf<String>()
        val checkoutLinks = mutableListOf<String>()
        var failure: Exception? = null

        override fun send(recipient: String, checkoutLink: String) {
            recipients += recipient
            checkoutLinks += checkoutLink
            failure?.let { throw it }
        }
    }

    private class RecordingPurchaseInformationOperationalLog : PurchaseInformationOperationalLog {
        data class Failure(
            val ownerUserId: UUID,
            val reservationToken: String?,
            val type: String,
            val cause: String,
        )

        val failures = mutableListOf<Failure>()

        override fun failure(ownerUserId: UUID, reservationToken: String?, type: String, cause: String) {
            failures += Failure(ownerUserId, reservationToken, type, cause)
        }
    }

    private class TestClock(private var current: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
