package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.group.GroupRole
import kotlin.jvm.JvmInline

enum class AssignableGroupRole {
    ADMIN,
    ATHLETE,
}

data class GroupMembership(
    val userId: String,
    val displayName: String,
    val role: GroupRole,
)

@JvmInline
value class GroupInviteUrl(val value: String)

data class RedeemedMembership(
    val groupId: GroupId,
    val role: GroupRole,
)

data class ChangeMembershipRoleCommand(
    val groupId: GroupId,
    val userId: String,
    val role: AssignableGroupRole,
)

@JvmInline
value class InviteCode(val value: String)

sealed interface GroupMembershipError : SaqzError {
    data object InvalidOrExpired : GroupMembershipError
    data class AttemptLimit(val retryAfterSeconds: Int?) : GroupMembershipError
    data class Validation(val details: ValidationDetails) : GroupMembershipError
    data class DataFailure(val error: DataError) : GroupMembershipError
}

interface GroupMembershipGateway {
    suspend fun listMemberships(
        groupId: GroupId,
    ): SaqzResult<List<GroupMembership>, GroupMembershipError>

    suspend fun changeRole(
        command: ChangeMembershipRoleCommand,
    ): SaqzResult<GroupMembership, GroupMembershipError>

    suspend fun rotateInvite(
        groupId: GroupId,
    ): SaqzResult<GroupInviteUrl, GroupMembershipError>

    suspend fun expireInvite(
        groupId: GroupId,
    ): EmptyResult<GroupMembershipError>

    suspend fun redeem(
        code: InviteCode,
    ): SaqzResult<RedeemedMembership, GroupMembershipError>
}
