package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.ErrorCode
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.CheckoutLoginLinkFactory
import br.com.saqz.subscriptions.application.CheckoutLoginTokens
import br.com.saqz.subscriptions.application.PurchaseInformationReservation
import br.com.saqz.subscriptions.application.PurchaseInformationReservationPort
import br.com.saqz.subscriptions.application.PurchaseInformationReservationResult
import br.com.saqz.subscriptions.application.PurchaseInformationSender
import br.com.saqz.subscriptions.application.SendPurchaseInformation
import br.com.saqz.subscriptions.application.SendPurchaseInformationCommand
import br.com.saqz.subscriptions.application.SubscriptionEmailLookup
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.annotation.RequestBody
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubscriptionPurchaseInformationControllerTest {
    private val ownerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val identity = RequestIdentity("firebase-subject", email = "attacker@example.test")
    private val ownerEmail = "owner@example.test"
    private val correlationId = "correlation-123"
    private val clock = Clock.fixed(Instant.parse("2026-08-10T20:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `success resolves only authenticated owner UUID and returns empty 204`() {
        val actors = RecordingActorResolver(ownerId)
        val sender = RecordingSender()
        val controller = controller(actors = actors, sender = sender)

        val response = controller.send(identity, request())

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNull(response.body)
        assertNull(response.headers.getFirst("Retry-After"))
        assertSame(identity, actors.identity)
        assertEquals(ownerId, actors.resolvedOwnerId)
        assertEquals(listOf(ownerEmail), sender.recipients)
    }

    @Test
    fun `malicious request body is ignored and cannot change authoritative recipient`() {
        val sender = RecordingSender()
        val controller = controller(sender = sender)
        val request = request().apply {
            contentType = "application/json"
            setContent("""{"email":"attacker@example.test"}""".toByteArray())
        }

        val response = controller.send(identity, request)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals(listOf(ownerEmail), sender.recipients)
        assertTrue(
            SubscriptionPurchaseInformationController::class.java
                .getDeclaredMethod("send", RequestIdentity::class.java, HttpServletRequest::class.java)
                .parameters
                .none { it.isAnnotationPresent(RequestBody::class.java) },
        )
    }

    @Test
    fun `already sent dedupe is also a bodyless 204 without sending again`() {
        val sender = RecordingSender()
        val controller = controller(
            reservationOutcome = PurchaseInformationReservationResult.AlreadySent,
            sender = sender,
        )

        val response = controller.send(identity, request())

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNull(response.body)
        assertTrue(sender.recipients.isEmpty())
    }

    @Test
    fun `missing email maps to validation problem without exposing recipient`() {
        val controller = controller(email = null)

        val response = controller.send(identity, request())
        val problem = assertNotNull(response.body as SubscriptionPurchaseInformationProblem)

        assertEquals(HttpStatus.valueOf(422), response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        assertEquals(ErrorCode.VALIDATION_FAILED, problem.code)
        assertEquals(422, problem.status)
        assertEquals(correlationId, problem.correlationId)
        assertEquals(mapOf("email" to listOf("must be available")), problem.fieldErrors)
        assertFalse(problem.toString().contains(ownerEmail))
        assertFalse(problem.toString().contains("attacker@example.test"))
    }

    @Test
    fun `rate limit maps exact retry-after to 429 problem`() {
        val controller = controller(
            reservationOutcome = PurchaseInformationReservationResult.RateLimited(37),
        )

        val response = controller.send(identity, request())
        val problem = assertNotNull(response.body as SubscriptionPurchaseInformationProblem)

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        assertEquals("37", response.headers.getFirst("Retry-After"))
        assertEquals(37, problem.retryAfterSeconds)
        assertEquals(ErrorCode.SUBSCRIPTION_PURCHASE_RATE_LIMITED, problem.code)
    }

    @Test
    fun `in progress maps exact retry-after to 503 problem`() {
        val controller = controller(
            reservationOutcome = PurchaseInformationReservationResult.InProgress(19),
        )

        val response = controller.send(identity, request())
        val problem = assertNotNull(response.body as SubscriptionPurchaseInformationProblem)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        assertEquals("19", response.headers.getFirst("Retry-After"))
        assertEquals(19, problem.retryAfterSeconds)
        assertEquals(ErrorCode.SUBSCRIPTION_PURCHASE_IN_PROGRESS, problem.code)
    }

    @Test
    fun `delivery failure maps to safe subscription problem without retry header`() {
        val controller = controller(
            reservationOutcome = PurchaseInformationReservationResult.Failed,
        )

        val response = controller.send(identity, request())
        val problem = assertNotNull(response.body as SubscriptionPurchaseInformationProblem)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.headers.contentType)
        assertNull(response.headers.getFirst("Retry-After"))
        assertEquals(ErrorCode.SUBSCRIPTION_PURCHASE_EMAIL_UNAVAILABLE, problem.code)
        assertNull(problem.fieldErrors)
        assertFalse(problem.toString().contains(ownerEmail))
    }

    private fun request() = MockHttpServletRequest().apply {
        addHeader("X-Correlation-ID", correlationId)
    }

    private fun controller(
        actors: RecordingActorResolver = RecordingActorResolver(ownerId),
        email: String? = ownerEmail,
        reservationOutcome: PurchaseInformationReservationResult =
            PurchaseInformationReservationResult.Reserved(PurchaseInformationReservation(ownerId, "token")),
        sender: RecordingSender = RecordingSender(),
    ) = SubscriptionPurchaseInformationController(
        actors = actors,
        sendPurchaseInformation = SendPurchaseInformation(
            emailLookup = SubscriptionEmailLookup { requestedOwnerId ->
                assertEquals(ownerId, requestedOwnerId)
                email
            },
            reservations = FixedReservationPort(reservationOutcome),
            sender = sender,
            checkoutLinks = CheckoutLoginLinkFactory(
                tokens = object : CheckoutLoginTokens {
                    override fun issue(ownerUserId: UUID, now: Instant) = "A".repeat(43)
                    override fun findOpen(rawToken: String, now: Instant) = null
                    override fun consume(id: UUID, consumedAt: Instant) = false
                },
                purchaseUrl = "https://checkout.test/assinar/",
            ),
            clock = clock,
        ),
    )

    private class RecordingActorResolver(private val ownerId: UUID) : SubscriptionActorResolver {
        var identity: RequestIdentity? = null
        var resolvedOwnerId: UUID? = null

        override fun resolve(identity: RequestIdentity): UUID {
            this.identity = identity
            resolvedOwnerId = ownerId
            return ownerId
        }
    }

    private class FixedReservationPort(
        private val outcome: PurchaseInformationReservationResult,
    ) : PurchaseInformationReservationPort {
        override fun reserve(
            ownerUserId: UUID,
            now: Instant,
        ): PurchaseInformationReservationResult = outcome

        override fun complete(reservation: PurchaseInformationReservation, completedAt: Instant): Boolean = true

        override fun release(reservation: PurchaseInformationReservation): Boolean = true
    }

    private class RecordingSender : PurchaseInformationSender {
        val recipients = mutableListOf<String>()

        override fun send(recipient: String, checkoutLink: String) {
            recipients += recipient
        }
    }
}
