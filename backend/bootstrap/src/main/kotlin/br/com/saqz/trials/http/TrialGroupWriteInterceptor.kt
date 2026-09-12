package br.com.saqz.trials.http

import br.com.saqz.groups.adapter.input.http.GroupNotFoundException
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.sharedkernel.subscription.GroupPlanOwnerLookup
import br.com.saqz.sharedkernel.subscription.OrganizerTrialAccessLookup
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import br.com.saqz.sharedkernel.subscription.TrialStatus
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

/** Backend authority for all group HTTP writes, including athlete RSVP, not a UI-only paywall. */
class TrialGroupWriteInterceptor(
    private val actors: AuthenticatedActorResolver,
    private val groups: GroupPlanOwnerLookup,
    private val trials: OrganizerTrialAccessLookup,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method in setOf("GET", "HEAD", "OPTIONS")) return true
        val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)?.toString()
        if (request.method == "DELETE" && pattern == "/api/groups/{groupId}/memberships/me") return true
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        val rawGroup = variables?.get("groupId")?.toString() ?: return true
        val groupId = runCatching { UUID.fromString(rawGroup) }.getOrNull() ?: return true
        val identity = SecurityContextHolder.getContext().authentication?.principal as? RequestIdentity ?: return true
        val actor = actors.resolve(identity).userId
        val owner = groups.ownerForMember(groupId, actor) ?: throw GroupNotFoundException()
        val access = trials.forOwner(owner)
        val trialRoleChange = access.status == TrialStatus.ACTIVE && pattern == "/api/groups/{groupId}/memberships/{userId}/role"
        if (access.readOnly || trialRoleChange) throw SubscriptionRequiredException()
        return true
    }
}
