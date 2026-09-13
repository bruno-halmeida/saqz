package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

fun interface FinancialActorResolver { fun resolve(identity: RequestIdentity): UUID }

class FinancialRegistrationRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID, @JsonProperty("acceptedTerms") val acceptedTerms: Boolean, @JsonProperty("termsVersion") val termsVersion: String,
    @JsonProperty("name") val name: String, @JsonProperty("email") val email: String, @JsonProperty("cpfCnpj") val cpfCnpj: String, @JsonProperty("mobilePhone") val mobilePhone: String,
    @JsonProperty("incomeCents") val incomeCents: Long, @JsonProperty("address") val address: String, @JsonProperty("addressNumber") val addressNumber: String, @JsonProperty("province") val province: String,
    @JsonProperty("postalCode") val postalCode: String, @JsonProperty("birthDate") val birthDate: String? = null, @JsonProperty("companyType") val companyType: String? = null,
)

data class FinancialAccountRecoveryRequest @JsonCreator constructor(@JsonProperty("requestId") val requestId: UUID)

@RestController
@RequestMapping("/api/receivables/accounts")
class FinancialAccountsController(
    private val actors: FinancialActorResolver,
    private val onboarding: OnboardFinancialAccount,
    private val store: FinancialOnboardingStore,
    private val clock: Clock,
) {
    @GetMapping("/me")
    fun mine(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<*> {
        val request = FinancialRequest(UUID.randomUUID(), actors.resolve(identity))
        val account = store.findOwned(request.actorUserId)
            ?: return result(FinancialResult.Failure(FinancialError.NOT_FOUND, request.requestId))
        return result(FinancialResult.Success(account, request.requestId))
    }

    @PostMapping
    fun begin(@AuthenticationPrincipal identity: RequestIdentity,
              @RequestBody body: FinancialRegistrationRequest): ResponseEntity<*> {
        val request = FinancialRequest(body.requestId, actors.resolve(identity))
        val birthDate = try { body.birthDate?.let(LocalDate::parse) }
        catch (_: Exception) { return result(FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId)) }
        val response = onboarding.begin(request, body.acceptedTerms, body.termsVersion, LegalRegistration(
            body.name, body.email, body.cpfCnpj, body.mobilePhone, body.incomeCents,
            body.address, body.addressNumber, body.province, body.postalCode, birthDate, body.companyType,
        ), clock.instant())
        if (response is FinancialResult.Success) {
            onboarding.provision(response.value.id, clock.instant())
            return result(FinancialResult.Success(store.findOwned(request.actorUserId)!!, request.requestId))
        }
        return result(response)
    }

    @PostMapping("/me/recover")
    fun recover(@AuthenticationPrincipal identity: RequestIdentity,
                @RequestBody body: FinancialAccountRecoveryRequest): ResponseEntity<*> = result(
        onboarding.recover(FinancialRequest(body.requestId, actors.resolve(identity)), clock.instant()),
    )

    @GetMapping("/me/documents")
    fun documents(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<*> = result(
        onboarding.refresh(FinancialRequest(UUID.randomUUID(), actors.resolve(identity)), clock.instant()),
    )

    @PostMapping("/me/documents/{documentId}", consumes = ["multipart/form-data"])
    fun upload(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable documentId: String,
               @RequestParam requestId: UUID, @RequestParam type: String,
               @RequestPart("documentFile") file: MultipartFile): ResponseEntity<*> {
        val request = FinancialRequest(requestId, actors.resolve(identity))
        if (file.size > 5 * 1024 * 1024) return result(FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId))
        return result(onboarding.upload(request, documentId, type, file.contentType.orEmpty(), file.bytes, clock.instant()))
    }

    private fun result(result: FinancialResult<*>): ResponseEntity<*> {
        val status = when (result) {
            is FinancialResult.Success -> 200
            is FinancialResult.Failure -> when (result.error) {
                FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
                FinancialError.INVALID_INPUT -> 400
                FinancialError.CONFLICT -> 409
                FinancialError.RESULT_PENDING -> 202
                else -> 503
            }
        }
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(result)
    }
}
