package br.com.saqz.groups.application.invite.manage

import java.util.UUID

interface InviteManagementRepository {
    fun rotate(command: RotateInviteCommand)

    fun expire(groupId: UUID)
}
