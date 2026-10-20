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

    /** True se o código existe no convite público ou no link de mensalistas: um código nunca vale nos dois. */
    fun isShortCodeTaken(shortCode: String): Boolean

    fun findMetadata(groupId: UUID): InviteMetadata?

    /** Desativa o convite e lembra que foi desativado: a leitura não recria sozinha um convite depois disso. */
    fun expire(groupId: UUID)

    /** True se o grupo já teve o convite desativado por um admin (e ainda não gerou outro). */
    fun wasDeactivated(groupId: UUID): Boolean
}
