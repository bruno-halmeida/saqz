package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.flags.FeatureFlags
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.client.CLIENT_APP_REQUEST_ATTRIBUTE
import br.com.saqz.sharedkernel.client.ClientApp
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class FeatureFlagResponse(val enabled: Boolean, val values: Map<String, Any>)
data class AppVersionResponse(val minimum: String?, val recommended: String?)

/** [platform] e [appVersion] ficam nulos quando a chamada não traz `X-Saqz-Platform` de Android ou iOS. */
data class FeaturesResponse(
    val flags: Map<String, FeatureFlagResponse>,
    val platform: String?,
    val appVersion: AppVersionResponse?,
)

/**
 * As flags da pessoa (FLG-001) e a versão mínima e recomendada da plataforma (FLG-007). Responde com e
 * sem login: antes do login só valem "ligada para todos", o interruptor e a versão. Nunca devolve
 * regra, lista, grupo nem porcentagem.
 */
@RestController
class FeaturesController(private val features: FeatureFlags) {
    @GetMapping("/api/features")
    fun features(@AuthenticationPrincipal identity: RequestIdentity?, request: HttpServletRequest): ResponseEntity<FeaturesResponse> {
        val client = ClientApp.fromRequestAttribute(request.getAttribute(CLIENT_APP_REQUEST_ATTRIBUTE))
        val userId = identity?.let { features.userIdBySubject(it.subject) }
        val person = features.forPerson(userId, client)
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(
            FeaturesResponse(
                flags = person.flags.mapValues { FeatureFlagResponse(it.value.enabled, it.value.values) },
                platform = person.platform?.name,
                appVersion = person.appVersions?.let { AppVersionResponse(it.minimum, it.recommended) },
            ),
        )
    }
}
