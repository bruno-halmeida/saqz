package br.com.saqz.groups.application.invite.manage

import br.com.saqz.groups.application.invite.InviteTokenDigest
import java.net.URI
import java.time.Instant
import java.util.UUID

data class RotateInviteCommand(
    val groupId: UUID,
    val digest: InviteTokenDigest,
    val createdByUserId: UUID,
    val expiresAt: Instant,
)

sealed interface RotateInviteResult {
    data class Success(val inviteUrl: URI, val expiresAt: Instant) : RotateInviteResult

    data object GroupNotFound : RotateInviteResult

    data object AccessForbidden : RotateInviteResult
}

data class InviteMetadata(
    val expiresAt: Instant,
    val createdAt: Instant,
    val createdByName: String,
)

data class InviteMetadataView(
    val active: Boolean,
    val expiresAt: Instant?,
    val createdAt: Instant?,
    val createdByName: String?,
)

sealed interface GetInviteMetadataResult {
    data class Success(val metadata: InviteMetadataView) : GetInviteMetadataResult

    data object GroupNotFound : GetInviteMetadataResult

    data object AccessForbidden : GetInviteMetadataResult
}

sealed interface ExpireInviteResult {
    data object Success : ExpireInviteResult

    data object GroupNotFound : ExpireInviteResult

    data object AccessForbidden : ExpireInviteResult
}
