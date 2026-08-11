package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.CorrelationId
import br.com.saqz.sharedkernel.ErrorCode
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.SendPurchaseInformation
import br.com.saqz.subscriptions.application.SendPurchaseInformationCommand
import br.com.saqz.subscriptions.application.SendPurchaseInformationResult
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** The stable problem shape used by the existing API for this endpoint's direct responses. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SubscriptionPurchaseInformationProblem(
    val status: Int,
    val code: ErrorCode,
    val correlationId: String,
    val fieldErrors: Map<String, List<String>>? = null,
    val retryAfterSeconds: Int? = null,
)

@RestController
class SubscriptionPurchaseInformationController(
    private val actors: SubscriptionActorResolver,
    private val sendPurchaseInformation: SendPurchaseInformation,
) {
    @PostMapping("/subscriptions/me/purchase-information")
    fun send(
        @AuthenticationPrincipal identity: RequestIdentity,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        // The recipient is intentionally absent from this adapter. The use case resolves it
        // from the owner UUID after the authenticated actor has been resolved.
        val ownerUserId = actors.resolve(identity)
        return when (val result = sendPurchaseInformation.execute(SendPurchaseInformationCommand(ownerUserId))) {
            SendPurchaseInformationResult.Success ->
                ResponseEntity.status(HttpStatus.NO_CONTENT).build()

            SendPurchaseInformationResult.EmailNotFound -> problem(
                request = request,
                status = HttpStatus.valueOf(422),
                code = ErrorCode.VALIDATION_FAILED,
                fieldErrors = mapOf("email" to listOf("must be available")),
            )

            is SendPurchaseInformationResult.RateLimited -> problem(
                request = request,
                status = HttpStatus.TOO_MANY_REQUESTS,
                code = ErrorCode.SUBSCRIPTION_PURCHASE_RATE_LIMITED,
                retryAfterSeconds = result.retryAfterSeconds,
            )

            is SendPurchaseInformationResult.InProgress -> problem(
                request = request,
                status = HttpStatus.SERVICE_UNAVAILABLE,
                code = ErrorCode.SUBSCRIPTION_PURCHASE_IN_PROGRESS,
                retryAfterSeconds = result.retryAfterSeconds,
            )

            SendPurchaseInformationResult.Failed -> problem(
                request = request,
                status = HttpStatus.SERVICE_UNAVAILABLE,
                code = ErrorCode.SUBSCRIPTION_PURCHASE_EMAIL_UNAVAILABLE,
            )
        }
    }

    private fun problem(
        request: HttpServletRequest,
        status: HttpStatus,
        code: ErrorCode,
        fieldErrors: Map<String, List<String>>? = null,
        retryAfterSeconds: Int? = null,
    ): ResponseEntity<Any> {
        val builder = ResponseEntity.status(status)
        if (retryAfterSeconds != null) {
            builder.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
        }
        builder.contentType(MediaType.APPLICATION_PROBLEM_JSON)
        val body: Any = SubscriptionPurchaseInformationProblem(
            status = status.value(),
            code = code,
            correlationId = correlationId(request),
            fieldErrors = fieldErrors,
            retryAfterSeconds = retryAfterSeconds,
        )
        return builder.body(body)
    }

    private fun correlationId(request: HttpServletRequest): String =
        (request.getAttribute(CORRELATION_ATTRIBUTE) as? CorrelationId)?.value
            ?: request.getHeader(CORRELATION_HEADER).orEmpty()

    private companion object {
        const val CORRELATION_ATTRIBUTE = "br.com.saqz.correlationId"
        const val CORRELATION_HEADER = "X-Correlation-ID"
    }
}
