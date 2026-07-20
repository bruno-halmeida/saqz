package br.com.saqz.groups.application.read

import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.GroupRole
import java.util.UUID

class GetGroup(
    private val repository: GroupReadRepository,
    private val accessPolicy: GroupAccessPolicy,
) {
    fun execute(actor: UUID, groupId: UUID): GetGroupResult {
        val snapshot = repository.find(GroupReadKey(actor, groupId))
            ?: return GetGroupResult.GroupNotFound
        return when (accessPolicy.authorize(snapshot.role, GroupAction.READ_GROUP)) {
            GroupAccessDecision.GroupNotFound -> GetGroupResult.GroupNotFound
            GroupAccessDecision.Forbidden -> GetGroupResult.AccessForbidden
            GroupAccessDecision.Allowed -> GetGroupResult.Success(
                GroupView(
                    id = snapshot.id,
                    name = snapshot.name,
                    timeZone = snapshot.timeZone,
                    role = requireNotNull(snapshot.role),
                    version = snapshot.version,
                    profileStatus = snapshot.profileStatus,
                    profile = snapshot.profile,
                    financeDefaults = snapshot.financeDefaults.takeIf {
                        snapshot.role == GroupRole.OWNER || snapshot.role == GroupRole.ADMIN
                    },
                ),
            )
        }
    }
}
