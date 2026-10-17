package br.com.saqz.groups.adapter.input.http

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class AutoConfirmationRequest @JsonCreator constructor(
    @JsonProperty("enabled") val enabled: Boolean? = null,
)

data class AutoConfirmationResponse(val enabled: Boolean)

/**
 * A confirmação automática do mensalista foi removida (DEB-040). Os apps 1.0.4–1.0.6 ainda mostram a preferência
 * e chamam este endpoint: ele aceita e ignora o corpo, sem erro, e devolve sempre "desligado".
 */
@RestController
class AutoConfirmationController {
    @PutMapping("/api/groups/{groupId}/athletes/me/auto-confirmation")
    @Suppress("UNUSED_PARAMETER")
    fun update(
        @PathVariable groupId: String,
        @RequestBody(required = false) request: AutoConfirmationRequest?,
    ): AutoConfirmationResponse = AutoConfirmationResponse(enabled = false)
}
