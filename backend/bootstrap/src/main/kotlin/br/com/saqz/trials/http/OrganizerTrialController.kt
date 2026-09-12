package br.com.saqz.trials.http

import br.com.saqz.groups.adapter.input.http.GroupNotFoundException
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccess
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.TrialStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class TrialAccessResponse(
    val status: TrialStatus,
    val startedAt: Instant?,
    val endsAt: Instant?,
    val serverTime: Instant,
    val readOnly: Boolean,
    val canCreateGroup: Boolean,
    @get:com.fasterxml.jackson.annotation.JsonProperty("isOwner") val isOwner: Boolean,
    val appUrl: String,
    val maxGroups: Int = 1,
    val maxAthletes: Int = 25,
)

@RestController
class OrganizerTrialController(
    private val actors: AuthenticatedActorResolver,
    private val trials: OrganizerTrialAccessLookup,
    private val groups: GroupPlanOwnerLookup,
    private val appUrl: String,
) {
    @GetMapping("/subscriptions/trial")
    fun mine(@AuthenticationPrincipal identity: RequestIdentity): TrialAccessResponse =
        trials.forOwner(actors.resolve(identity).userId).response(true)

    @GetMapping("/api/groups/{groupId}/trial")
    fun group(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID): TrialAccessResponse {
        val actorId = actors.resolve(identity).userId
        val owner = groups.ownerForMember(groupId, actorId) ?: throw GroupNotFoundException()
        return trials.forOwner(owner).response(owner == actorId)
    }

    private fun OrganizerTrialAccess.response(isOwner: Boolean) =
        TrialAccessResponse(status, startedAt, endsAt, serverTime, readOnly, isOwner && canCreateGroup, isOwner, appUrl)
}
