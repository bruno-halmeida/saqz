package br.com.saqz.groups.application.whatsapp

import java.util.UUID

/**
 * Persistência do vínculo grupo Saqz ↔ grupo WhatsApp.
 *
 * Um grupo tem no máximo um vínculo e um JID pertence a no máximo um grupo Saqz.
 * [upsert] substitui o vínculo existente do grupo (novo link colado).
 */
interface GroupWhatsAppBindingRepository {
    fun find(groupId: UUID): GroupWhatsAppBinding?

    /** Vínculo detentor do JID, se houver — o anti-colisão do vínculo por JID. */
    fun findByJid(whatsappJid: String): GroupWhatsAppBinding?

    fun upsert(binding: GroupWhatsAppBinding)

    fun setEnabled(groupId: UUID, enabled: Boolean)

    fun markBroken(groupId: UUID)

    /** Telefones dos membros ativos (só dígitos), incluindo o owner — insumo do anti-sequestro. */
    fun memberPhones(groupId: UUID): List<String>
}
