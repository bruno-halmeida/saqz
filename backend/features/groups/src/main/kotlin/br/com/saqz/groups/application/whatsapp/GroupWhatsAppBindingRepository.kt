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

    /**
     * Cancela os jobs `PENDING` da fila de grupo daquele grupo Saqz (`status='CANCELLED'`,
     * `completed_at=now()`), devolvendo quantos foram cancelados. Cancelado não incrementa
     * `attempts`, como no worker. Usado ao substituir o vínculo por um novo JID.
     */
    fun cancelPendingByGroup(groupId: UUID): Int

    fun setEnabled(groupId: UUID, enabled: Boolean)

    fun markBroken(groupId: UUID)

    /** Telefones dos membros ativos (só dígitos), incluindo o owner — insumo do anti-sequestro. */
    fun memberPhones(groupId: UUID): List<String>
}
