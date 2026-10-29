package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.finance.openingbalance.OpeningBalanceResult
import br.com.saqz.groups.application.finance.openingbalance.OpeningBalanceService
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class OpeningBalanceRequest @JsonCreator constructor(@JsonProperty("amountCents") val amountCents: Long?)
data class OpeningBalanceResponse(val amountCents: Long?, val version: Long)

/** Saldo inicial do caixa (DEB-148, FIN-006). Só gestor; o PUT pede `If-Match` com a versão do GET. */
@RestController
class OpeningBalanceController(private val actors: VerifiedGroupActorResolver, private val service: OpeningBalanceService) {
    @GetMapping("/api/groups/{groupId}/finance/opening-balance")
    fun read(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String): ResponseEntity<OpeningBalanceResponse> =
        respond(service.read(actors.resolve(identity), uuid(groupId)))

    @PutMapping("/api/groups/{groupId}/finance/opening-balance")
    fun update(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestHeader("If-Match", required = false) ifMatch: String?,
        @RequestBody request: OpeningBalanceRequest,
    ): ResponseEntity<OpeningBalanceResponse> {
        val actor = actors.resolve(identity); val group = uuid(groupId)
        return respond(service.update(actor, group, QuotedResourceVersion.parseRequired(ifMatch), request.amountCents))
    }

    private fun respond(result: OpeningBalanceResult): ResponseEntity<OpeningBalanceResponse> = when (result) {
        is OpeningBalanceResult.Success ->
            ResponseEntity.ok().eTag(result.value.version.toString()).body(OpeningBalanceResponse(result.value.amountCents, result.value.version))
        OpeningBalanceResult.Hidden -> throw GameNotFoundException()
        OpeningBalanceResult.Forbidden -> throw AccessForbiddenException()
        OpeningBalanceResult.Conflict -> throw VersionConflictException()
        OpeningBalanceResult.InvalidAmount -> throw InvalidGroupRequestException(mapOf("amountCents" to listOf("must be between 0 and 99999999 cents")))
    }

    private fun uuid(value: String) = runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }
}
