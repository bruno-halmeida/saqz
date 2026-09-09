package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId

/** Removes only the authenticated user's membership; never deletes the group or its history. */
fun interface GroupDepartureGateway {
    suspend fun leave(groupId: GroupId): EmptyResult<GroupMembershipError>
}
