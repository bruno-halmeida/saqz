package br.com.saqz.groups.application.invite.manage

import java.util.UUID

interface InviteManagementRepository {
    /** Troca o convite do grupo: o código curto novo vale; o token longo antigo e o curto anterior deixam de valer. */
    fun rotate(command: RotateInviteCommand)

    /**
     * Garante que o grupo tenha código curto SEM invalidar o token longo legado: cria o convite se não
     * existir ou preenche o código curto de um convite antigo.
     */
    fun ensureShortCode(groupId: UUID, shortCode: String, createdByUserId: UUID)

    fun isShortCodeTaken(shortCode: String): Boolean

    fun findMetadata(groupId: UUID): InviteMetadata?

    fun expire(groupId: UUID)
}
