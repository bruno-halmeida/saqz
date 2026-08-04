package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.read.GroupSummaryReadModel
import br.com.saqz.groups.application.read.ListGroups
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class GroupSummaryResponse(
    val id: UUID,
    val name: String,
    val timeZone: String,
    val role: GroupRole,
    val profileStatus: GroupProfileStatus,
    val modality: GroupModality?,
    val city: String?,
    val memberCount: Int,
)

data class GroupListResponse(
    val groups: List<GroupSummaryResponse>,
)

@RestController
class AccessGroupListController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val listGroups: ListGroups,
) {
    @GetMapping("/api/groups")
    fun list(@AuthenticationPrincipal identity: RequestIdentity): GroupListResponse =
        GroupListResponse(
            groups = listGroups.execute(actorResolver.resolve(identity)).map { it.toResponse() },
        )
}

private fun GroupSummaryReadModel.toResponse() = GroupSummaryResponse(
    id = id,
    name = name.value,
    timeZone = timeZone.value,
    role = role,
    profileStatus = profileStatus,
    modality = modality,
    city = city,
    memberCount = memberCount,
)
