package br.com.saqz.groups.application.invite.manage

import br.com.saqz.groups.application.invite.InviteCode
import java.net.URI
import java.time.Instant
import java.util.UUID

data class RotateInviteCommand(
    val groupId: UUID,
    val code: InviteCode,
    val createdByUserId: UUID,
    val expiresAt: Instant?,
    val revision: UUID = UUID.randomUUID(),
)

sealed interface RotateInviteResult {
    data class Success(val code: String, val inviteUrl: URI, val expiresAt: Instant?, val revision: UUID) : RotateInviteResult

    data object GroupNotFound : RotateInviteResult

    data object AccessForbidden : RotateInviteResult
}

data class InviteMetadata(
    val expiresAt: Instant?,
    val createdAt: Instant,
    val createdByName: String,
    val revision: UUID = UUID.randomUUID(),
    val shortCode: String? = null,
)

data class InviteMetadataView(
    val active: Boolean,
    val expiresAt: Instant?,
    val createdAt: Instant?,
    val createdByName: String?,
    val revision: UUID? = null,
    /** Código curto para mostrar (`44DF-8SA8`); a URL já o leva embutido. */
    val code: String? = null,
    val inviteUrl: URI? = null,
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
