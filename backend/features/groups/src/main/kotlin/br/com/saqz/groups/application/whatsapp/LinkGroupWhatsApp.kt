package br.com.saqz.groups.application.whatsapp

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

sealed interface LinkGroupWhatsAppResult {
    data class Linked(val binding: GroupWhatsAppBinding) : LinkGroupWhatsAppResult

    data object GroupNotFound : LinkGroupWhatsAppResult

    data object AccessForbidden : LinkGroupWhatsAppResult

    /** Link inválido/expirado, grupo inexistente ou código sem formato reconhecível. */
    data object InvalidInvite : LinkGroupWhatsAppResult

    /** Nenhum admin do grupo WhatsApp tem telefone que bata com membro ativo do grupo Saqz. */
    data object UnresolvableAdmins : LinkGroupWhatsAppResult

    /** `join` ok mas a instância não apareceu em `Participants` (aprovação pendente). */
    data object JoinPending : LinkGroupWhatsAppResult

    /** O JID já pertence a outro grupo Saqz. */
    data object JidInUse : LinkGroupWhatsAppResult

    data object InstanceDisconnected : LinkGroupWhatsAppResult

    data object ProviderUnavailable : LinkGroupWhatsAppResult
}

/**
 * Fluxo AC1 do vínculo, na ordem do design: gestor → código do convite → instância conectada →
 * `inviteInfo` → anti-sequestro → `join` → reconfirmação via `group/info` → vínculo habilitado.
 *
 * O `join` nunca é confiado (retorna Group vazio mesmo em sucesso): a confirmação é
 * [WhatsAppGroupDirectory.groupInfo] + [WhatsAppGroupDirectory.isMember]. O JID já vinculado
 * a outro grupo Saqz é rejeitado antes de entrar; o mesmo grupo re-vinculando é upsert normal.
 */
class LinkGroupWhatsApp(
    private val transactionRunner: TransactionRunner,
    private val groups: GroupReadRepository,
    private val bindings: GroupWhatsAppBindingRepository,
    private val directory: WhatsAppGroupDirectory,
) {
    fun execute(actor: UUID, groupId: UUID, inviteLink: String): LinkGroupWhatsAppResult {
        val group = groups.find(GroupReadKey(actor, groupId)) ?: return LinkGroupWhatsAppResult.GroupNotFound
        if (group.role != GroupRole.OWNER && group.role != GroupRole.ADMIN) {
            return LinkGroupWhatsAppResult.AccessForbidden
        }

        val code = inviteCode(inviteLink) ?: return LinkGroupWhatsAppResult.InvalidInvite

        val instanceJid = try {
            directory.instanceStatus()
        } catch (error: DirectoryError) {
            return if (error is DirectoryError.Disconnected) {
                LinkGroupWhatsAppResult.InstanceDisconnected
            } else {
                LinkGroupWhatsAppResult.ProviderUnavailable
            }
        }

        val invite = try {
            directory.inviteInfo(code)
        } catch (error: DirectoryError) {
            return when (error) {
                DirectoryError.InvalidInvite, DirectoryError.NotInGroup -> LinkGroupWhatsAppResult.InvalidInvite
                DirectoryError.Disconnected -> LinkGroupWhatsAppResult.InstanceDisconnected
                is DirectoryError.Unavailable -> LinkGroupWhatsAppResult.ProviderUnavailable
            }
        }
        if (invite.jid.isBlank()) return LinkGroupWhatsAppResult.InvalidInvite

        val existing = bindings.findByJid(invite.jid)
        if (existing != null && existing.groupId != groupId) return LinkGroupWhatsAppResult.JidInUse

        val memberPhones = bindings.memberPhones(groupId).toSet()
        if (invite.admins.none { it in memberPhones }) return LinkGroupWhatsAppResult.UnresolvableAdmins

        try {
            directory.join(code)
        } catch (error: DirectoryError) {
            return when (error) {
                DirectoryError.InvalidInvite, DirectoryError.NotInGroup -> LinkGroupWhatsAppResult.InvalidInvite
                DirectoryError.Disconnected -> LinkGroupWhatsAppResult.InstanceDisconnected
                is DirectoryError.Unavailable -> LinkGroupWhatsAppResult.ProviderUnavailable
            }
        }

        val confirmed = try {
            directory.groupInfo(invite.jid)
        } catch (error: DirectoryError) {
            return when (error) {
                DirectoryError.InvalidInvite, DirectoryError.NotInGroup -> LinkGroupWhatsAppResult.JoinPending
                DirectoryError.Disconnected -> LinkGroupWhatsAppResult.InstanceDisconnected
                is DirectoryError.Unavailable -> LinkGroupWhatsAppResult.ProviderUnavailable
            }
        }

        val member = try {
            directory.isMember(invite.jid)
        } catch (error: DirectoryError) {
            return when (error) {
                DirectoryError.InvalidInvite, DirectoryError.NotInGroup -> LinkGroupWhatsAppResult.JoinPending
                DirectoryError.Disconnected -> LinkGroupWhatsAppResult.InstanceDisconnected
                is DirectoryError.Unavailable -> LinkGroupWhatsAppResult.ProviderUnavailable
            }
        }
        if (!member) return LinkGroupWhatsAppResult.JoinPending

        val binding = GroupWhatsAppBinding(
            groupId = groupId,
            whatsappJid = invite.jid,
            inviteCode = code,
            groupName = confirmed.name.ifBlank { invite.name },
            instanceJid = instanceJid,
            enabled = true,
            brokenAt = null,
            createdBy = actor,
        )
        transactionRunner.inTransaction {
            val replaced = bindings.find(groupId)?.whatsappJid?.let { it != invite.jid } ?: false
            bindings.upsert(binding)
            if (replaced) bindings.cancelPendingByGroup(groupId)
        }
        return LinkGroupWhatsAppResult.Linked(binding)
    }

    private fun inviteCode(inviteLink: String): String? =
        INVITE_CODE.find(inviteLink.trim())?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private companion object {
        private val INVITE_CODE = Regex("(?:chat\\.whatsapp\\.com/)?([A-Za-z0-9]{10,50})")
    }
}
