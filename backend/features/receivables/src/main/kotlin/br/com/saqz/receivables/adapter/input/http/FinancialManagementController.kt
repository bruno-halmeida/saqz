package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.util.UUID

class RegistrationCorrectionRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID,
    @JsonProperty("email") val email: String,
    @JsonProperty("phone") val phone: String?,
    @JsonProperty("mobilePhone") val mobilePhone: String,
    @JsonProperty("site") val site: String?,
    @JsonProperty("incomeCents") val incomeCents: Long,
    @JsonProperty("postalCode") val postalCode: String,
    @JsonProperty("address") val address: String,
    @JsonProperty("addressNumber") val addressNumber: String,
    @JsonProperty("complement") val complement: String?,
    @JsonProperty("province") val province: String,
)
data class RegistrationCorrectionRecoveryRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID,
)

@RestController
@RequestMapping("/api/receivables/accounts/{accountId}")
class FinancialManagementController(
    private val actors: FinancialActorResolver,
    private val service: ManageFinancialRegistration,
    private val clock: Clock,
) {
    @GetMapping("/management")
    fun view(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID) = response(
        service.view(accountId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @PostMapping("/corrections")
    fun correct(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                @RequestBody body: RegistrationCorrectionRequest) = response(service.correct(accountId,
        FinancialRequest(body.requestId, actors.resolve(identity)), RegistrationCorrection(body.email, body.phone,
            body.mobilePhone, body.site, body.incomeCents, body.postalCode, body.address, body.addressNumber,
            body.complement, body.province), clock.instant()))

    @PostMapping("/corrections/{requestId}/recover")
    fun recover(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                @PathVariable requestId: UUID, @RequestBody body: RegistrationCorrectionRecoveryRequest): ResponseEntity<*> {
        if (requestId != body.requestId) return response(FinancialResult.Failure(FinancialError.INVALID_INPUT, body.requestId))
        return response(service.recover(accountId, FinancialRequest(requestId, actors.resolve(identity)), clock.instant()))
    }

    private fun response(result: FinancialResult<*>): ResponseEntity<*> = ResponseEntity.status(when (result) {
        is FinancialResult.Success -> 200
        is FinancialResult.Failure -> when (result.error) {
            FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
            FinancialError.INVALID_INPUT -> 400
            FinancialError.CONFLICT -> 409
            FinancialError.RESULT_PENDING -> 202
            else -> 503
        }
    }).header("Cache-Control", "no-store").body(result)
}
