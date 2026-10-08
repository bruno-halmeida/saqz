package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupRole
import java.time.Clock
import java.util.UUID

sealed interface ManageGroupWhatsAppBindingResult {
    data class Bound(val binding: GroupWhatsAppBinding) : ManageGroupWhatsAppBindingResult

    data object NotBound : ManageGroupWhatsAppBindingResult

    data object GroupNotFound : ManageGroupWhatsAppBindingResult

    data object AccessForbidden : ManageGroupWhatsAppBindingResult
}

/**
 * Leitura e habilitação do vínculo, restritas ao gestor (owner/admin) do grupo Saqz.
 *
 * Desabilitar preserva o vínculo (a fila é quem cancela pendentes, na task da fila); reabilitar
 * apenas devolve a flag, sem novo `join` nem reenvio. Não existe unbind na v1. O status
 * ACTIVE/DISABLED/BROKEN vem de [GroupWhatsAppBinding.status].
 */
class ManageGroupWhatsAppBinding(
    private val groups: GroupReadRepository,
    private val bindings: GroupWhatsAppBindingRepository,
    private val directory: WhatsAppGroupDirectory? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun get(actor: UUID, groupId: UUID): ManageGroupWhatsAppBindingResult = withManager(actor, groupId) {
        bindings.find(groupId)?.let { ManageGroupWhatsAppBindingResult.Bound(revalidated(it)) }
            ?: ManageGroupWhatsAppBindingResult.NotBound
    }

    /**
     * A leitura confere no WhatsApp, com a mesma regra da entrega: número removido do grupo (ou
     * grupo que deixou de existir) quebra o vínculo na hora, sem esperar a próxima mensagem.
     * Provedor fora do ar ou instância desconectada não quebram nada: fica o que está gravado.
     */
    private fun revalidated(binding: GroupWhatsAppBinding): GroupWhatsAppBinding {
        val directory = directory ?: return binding
        if (binding.brokenAt != null) return binding
        val member = try {
            directory.isMember(binding.whatsappJid)
        } catch (error: DirectoryError) {
            if (error !is DirectoryError.NotInGroup) return binding
            false
        }
        if (member) return binding
        bindings.markBroken(binding.groupId)
        return binding.copy(brokenAt = clock.instant())
    }

    fun setEnabled(actor: UUID, groupId: UUID, enabled: Boolean): ManageGroupWhatsAppBindingResult =
        withManager(actor, groupId) {
            val binding = bindings.find(groupId) ?: return@withManager ManageGroupWhatsAppBindingResult.NotBound
            bindings.setEnabled(groupId, enabled)
            ManageGroupWhatsAppBindingResult.Bound(binding.copy(enabled = enabled))
        }

    private inline fun withManager(
        actor: UUID,
        groupId: UUID,
        block: () -> ManageGroupWhatsAppBindingResult,
    ): ManageGroupWhatsAppBindingResult {
        val group = groups.find(GroupReadKey(actor, groupId)) ?: return ManageGroupWhatsAppBindingResult.GroupNotFound
        if (group.role != GroupRole.OWNER && group.role != GroupRole.ADMIN) {
            return ManageGroupWhatsAppBindingResult.AccessForbidden
        }
        return block()
    }
}
