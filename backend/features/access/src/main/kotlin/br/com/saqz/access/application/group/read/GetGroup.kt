package br.com.saqz.access.application.group.read

import br.com.saqz.access.domain.GroupAccessDecision
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupAction
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
                ),
            )
        }
    }
}
