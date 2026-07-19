package br.com.saqz.groups.application.membership

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.PersistedMembershipRole
import java.util.UUID

data class AccessMembership(
    val userId: UUID,
    val displayName: AccessName,
    val role: GroupRole,
)

data class ChangeMemberRoleCommand(
    val groupId: UUID,
    val userId: UUID,
    val role: PersistedMembershipRole,
)

sealed interface ListAccessMembershipsResult {
    data class Success(val memberships: List<AccessMembership>) : ListAccessMembershipsResult

    data object GroupNotFound : ListAccessMembershipsResult

    data object AccessForbidden : ListAccessMembershipsResult
}

sealed interface ChangeMemberRoleResult {
    data class Success(val membership: AccessMembership) : ChangeMemberRoleResult

    data object GroupNotFound : ChangeMemberRoleResult

    data object AccessForbidden : ChangeMemberRoleResult

    data object OwnerImmutable : ChangeMemberRoleResult
}
