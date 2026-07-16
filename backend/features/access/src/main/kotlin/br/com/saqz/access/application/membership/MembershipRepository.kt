package br.com.saqz.access.application.membership

import java.util.UUID

interface MembershipRepository {
    fun list(groupId: UUID): List<AccessMembership>

    fun find(groupId: UUID, userId: UUID): AccessMembership?

    fun change(command: ChangeMemberRoleCommand): AccessMembership
}
