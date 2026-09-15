package br.com.saqz.groups.domain.communication

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

/** Estado do vínculo do grupo Saqz com um grupo do WhatsApp. `NONE` = sem vínculo. */
enum class GroupWhatsAppStatus { ACTIVE, DISABLED, BROKEN, NONE }

/**
 * `groupJid`/`groupName` só existem quando [bound] é true; sem vínculo, [status] é
 * [GroupWhatsAppStatus.NONE] e os dois são nulos.
 */
data class GroupWhatsAppBinding(
    val bound: Boolean,
    val groupJid: String? = null,
    val groupName: String? = null,
    val status: GroupWhatsAppStatus = GroupWhatsAppStatus.NONE,
)

interface GroupWhatsAppGateway {
    suspend fun binding(groupId: GroupId): SaqzResult<GroupWhatsAppBinding, CommunicationError>

    suspend fun link(groupId: GroupId, inviteLink: String): SaqzResult<GroupWhatsAppBinding, CommunicationError>

    suspend fun setEnabled(groupId: GroupId, enabled: Boolean): SaqzResult<GroupWhatsAppBinding, CommunicationError>
}
