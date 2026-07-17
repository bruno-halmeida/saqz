package br.com.saqz.access.application.invite.manage

import java.util.UUID

interface InviteManagementRepository {
    fun rotate(command: RotateInviteCommand)

    fun expire(groupId: UUID)
}
