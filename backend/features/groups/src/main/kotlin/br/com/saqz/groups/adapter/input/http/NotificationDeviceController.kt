package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationPush
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class NotificationDeviceBody @JsonCreator constructor(
    @JsonProperty("token") val token: String,
    @JsonProperty("platform") val platform: String,
    @JsonProperty("liveActivityStartToken") val liveActivityStartToken: String? = null,
)
@RestController
class NotificationDeviceController(private val actors: VerifiedGroupActorResolver, private val devices: JdbcNotificationPush) {
    @PutMapping("/api/me/notification-devices/{installation}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun register(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable installation: UUID,
        @RequestBody body: NotificationDeviceBody) {
        val actor = actors.resolve(identity)
        val start = body.liveActivityStartToken
        val invalid = !body.token.isDeviceToken() || body.platform !in setOf("ANDROID", "IOS") ||
            (start != null && (!start.isDeviceToken() || body.platform != "IOS"))
        if (invalid) throw InvalidGroupRequestException(mapOf("device" to listOf("is invalid")), 422)
        devices.register(actor, installation, body.token, body.platform, start)
    }
    @DeleteMapping("/api/me/notification-devices/{installation}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregister(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable installation: UUID) =
        devices.unregister(actors.resolve(identity), installation)
}

/** Tokens do FCM e da Live Activity: 1 a 4096 caracteres, sem espaço nem caractere de controle. */
private fun String.isDeviceToken() = length in 1..4096 && none { it.isWhitespace() || it.isISOControl() }
