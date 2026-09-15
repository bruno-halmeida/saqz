package br.com.saqz.groups.application.whatsapp

import java.time.Instant
import java.util.UUID

/**
 * Vínculo de um grupo Saqz a um grupo do WhatsApp.
 *
 * O canal nasce habilitado no vínculo; [brokenAt] registra a quebra detectada na
 * revalidação de entrega (nunca uma falha de envio) e nunca é limpo por [enabled].
 */
data class GroupWhatsAppBinding(
    val groupId: UUID,
    val whatsappJid: String,
    val inviteCode: String,
    val groupName: String,
    val instanceJid: String,
    val enabled: Boolean,
    val brokenAt: Instant?,
    val createdBy: UUID,
) {
    fun status(): GroupWhatsAppBindingStatus = when {
        brokenAt != null -> GroupWhatsAppBindingStatus.BROKEN
        !enabled -> GroupWhatsAppBindingStatus.DISABLED
        else -> GroupWhatsAppBindingStatus.ACTIVE
    }
}

enum class GroupWhatsAppBindingStatus { ACTIVE, DISABLED, BROKEN }