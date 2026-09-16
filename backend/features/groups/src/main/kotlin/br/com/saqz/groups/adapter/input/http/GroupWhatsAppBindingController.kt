package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.whatsapp.LinkGroupWhatsApp
import br.com.saqz.groups.application.whatsapp.LinkGroupWhatsAppResult
import br.com.saqz.groups.application.whatsapp.ManageGroupWhatsAppBinding
import br.com.saqz.groups.application.whatsapp.ManageGroupWhatsAppBindingResult
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 409 do vínculo: JID já usado por outro grupo Saqz ou entrada ainda não confirmada. */
class WhatsAppGroupBindingConflictException : RuntimeException()

/** 502 do vínculo: instância desconectada ou provedor indisponível. */
class WhatsAppGroupBindingUnavailableException : RuntimeException()

data class LinkGroupWhatsAppRequest @JsonCreator constructor(
    @JsonProperty("inviteLink") val inviteLink: String?,
)

data class SetGroupWhatsAppEnabledRequest @JsonCreator constructor(
    @JsonProperty("enabled") val enabled: Boolean?,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class LinkGroupWhatsAppResponse(
    val groupJid: String,
    val groupName: String,
    val enabled: Boolean,
    val status: String,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GroupWhatsAppBindingResponse(
    val bound: Boolean,
    val groupJid: String? = null,
    val groupName: String? = null,
    val status: String? = null,
)

@RestController
class GroupWhatsAppBindingController(
    private val actors: VerifiedGroupActorResolver,
    private val linkGroupWhatsApp: LinkGroupWhatsApp,
    private val manageGroupWhatsAppBinding: ManageGroupWhatsAppBinding,
) {
    @PutMapping("/api/groups/{groupId}/whatsapp-binding")
    fun link(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
        @RequestBody request: LinkGroupWhatsAppRequest,
    ): LinkGroupWhatsAppResponse {
        val inviteLink = request.inviteLink?.trim()
        if (inviteLink.isNullOrBlank()) throw invalid("inviteLink")
        return when (val result = linkGroupWhatsApp.execute(actors.resolve(identity), groupId, inviteLink)) {
            is LinkGroupWhatsAppResult.Linked -> LinkGroupWhatsAppResponse(
                groupJid = result.binding.whatsappJid,
                groupName = result.binding.groupName,
                enabled = result.binding.enabled,
                status = result.binding.status().name,
            )
            LinkGroupWhatsAppResult.GroupNotFound -> throw GroupNotFoundException()
            LinkGroupWhatsAppResult.AccessForbidden -> throw AccessForbiddenException()
            LinkGroupWhatsAppResult.InvalidInvite,
            LinkGroupWhatsAppResult.UnresolvableAdmins,
            -> throw invalid("inviteLink")
            LinkGroupWhatsAppResult.JidInUse,
            LinkGroupWhatsAppResult.JoinPending,
            -> throw WhatsAppGroupBindingConflictException()
            LinkGroupWhatsAppResult.InstanceDisconnected,
            LinkGroupWhatsAppResult.ProviderUnavailable,
            -> throw WhatsAppGroupBindingUnavailableException()
        }
    }

    @GetMapping("/api/groups/{groupId}/whatsapp-binding")
    fun get(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
    ): GroupWhatsAppBindingResponse =
        manageGroupWhatsAppBinding.get(actors.resolve(identity), groupId).response()

    @PatchMapping("/api/groups/{groupId}/whatsapp-binding")
    fun setEnabled(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: UUID,
        @RequestBody request: SetGroupWhatsAppEnabledRequest,
    ): GroupWhatsAppBindingResponse {
        val enabled = request.enabled ?: throw invalid("enabled")
        return when (val result = manageGroupWhatsAppBinding.setEnabled(actors.resolve(identity), groupId, enabled)) {
            is ManageGroupWhatsAppBindingResult.Bound -> boundResponse(result)
            ManageGroupWhatsAppBindingResult.NotBound -> throw GroupNotFoundException()
            ManageGroupWhatsAppBindingResult.GroupNotFound -> throw GroupNotFoundException()
            ManageGroupWhatsAppBindingResult.AccessForbidden -> throw AccessForbiddenException()
        }
    }

    private fun ManageGroupWhatsAppBindingResult.response(): GroupWhatsAppBindingResponse = when (this) {
        is ManageGroupWhatsAppBindingResult.Bound -> boundResponse(this)
        ManageGroupWhatsAppBindingResult.NotBound -> GroupWhatsAppBindingResponse(bound = false)
        ManageGroupWhatsAppBindingResult.GroupNotFound -> throw GroupNotFoundException()
        ManageGroupWhatsAppBindingResult.AccessForbidden -> throw AccessForbiddenException()
    }

    private fun boundResponse(result: ManageGroupWhatsAppBindingResult.Bound) = GroupWhatsAppBindingResponse(
        bound = true,
        groupJid = result.binding.whatsappJid,
        groupName = result.binding.groupName,
        status = result.binding.status().name,
    )

    private fun invalid(field: String): Nothing =
        throw InvalidGroupRequestException(mapOf(field to listOf("is invalid")), 422)
}
