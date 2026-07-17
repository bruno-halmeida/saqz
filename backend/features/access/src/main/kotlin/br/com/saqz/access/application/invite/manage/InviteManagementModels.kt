package br.com.saqz.access.application.invite.manage

import br.com.saqz.access.application.invite.InviteTokenDigest
import java.net.URI
import java.util.UUID

data class RotateInviteCommand(
    val groupId: UUID,
    val digest: InviteTokenDigest,
    val createdByUserId: UUID,
)

sealed interface RotateInviteResult {
    data class Success(val inviteUrl: URI) : RotateInviteResult

    data object GroupNotFound : RotateInviteResult

    data object AccessForbidden : RotateInviteResult
}

sealed interface ExpireInviteResult {
    data object Success : ExpireInviteResult

    data object GroupNotFound : ExpireInviteResult

    data object AccessForbidden : ExpireInviteResult
}
