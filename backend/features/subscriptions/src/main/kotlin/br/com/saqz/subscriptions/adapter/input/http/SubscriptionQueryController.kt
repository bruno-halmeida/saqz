package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.application.AsaasBillingType
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.GetMySubscriptionResult
import br.com.saqz.subscriptions.application.MySubscriptionView
import br.com.saqz.subscriptions.application.SubscriptionUsage
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionProvider
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class SubscriptionUsageResponse(
    val groupsUsed: Int,
    val groupsLimit: Int?,
)

data class MySubscriptionResponse(
    val status: SubscriptionStatus,
    val entitled: Boolean,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val pendingPlan: Plan?,
    val pendingPlanEffectiveAt: Instant?,
    val currentPeriodEnd: Instant,
    val paymentMethod: AsaasBillingType?,
    val usage: SubscriptionUsageResponse,
    val readOnly: Boolean,
    val pastDueSince: Instant?,
    val canceledAt: Instant?,
    val provider: SubscriptionProvider,
    val autoRenew: Boolean?,
    /** Campos novos e opcionais (apps 1.0.4 a 1.0.7 ignoram): ver [MySubscriptionView]. */
    val paymentConfirmed: Boolean,
    val payUrl: String?,
    /** Cortesia de plano ativa (DEB-097); ausente/nulo para quem não tem. */
    val courtesy: CourtesyResponse? = null,
)

data class CourtesyResponse(val plan: Plan, val endsAt: Instant?)

class SubscriptionNotFoundException : RuntimeException()

@RestController
class SubscriptionQueryController(
    private val actors: AuthenticatedActorResolver,
    private val getMySubscription: GetMySubscription,
) {
    @GetMapping("/subscriptions/me")
    fun me(@AuthenticationPrincipal identity: RequestIdentity): MySubscriptionResponse {
        val ownerUserId = actors.resolve(identity).userId
        return when (val result = getMySubscription.execute(ownerUserId)) {
            GetMySubscriptionResult.NotFound -> throw SubscriptionNotFoundException()
            is GetMySubscriptionResult.Found -> result.subscription.toResponse()
        }
    }

    /** Para o app novo: cortesia de quem só tem cortesia também aparece (o `/me` segue 404 para esse dono). */
    @GetMapping("/subscriptions/me/courtesy")
    fun myCourtesy(@AuthenticationPrincipal identity: RequestIdentity): CourtesyResponse {
        val view = getMySubscription.courtesy(actors.resolve(identity).userId) ?: throw SubscriptionNotFoundException()
        return CourtesyResponse(view.plan, view.endsAt)
    }
}

internal fun MySubscriptionView.toResponse() = MySubscriptionResponse(
    status = status,
    entitled = entitled,
    plan = plan,
    cycle = cycle,
    pendingPlan = pendingPlan,
    pendingPlanEffectiveAt = pendingPlanEffectiveAt,
    currentPeriodEnd = currentPeriodEnd,
    paymentMethod = paymentMethod,
    usage = usage.toResponse(),
    readOnly = readOnly,
    pastDueSince = pastDueSince,
    canceledAt = canceledAt,
    provider = provider,
    autoRenew = autoRenew,
    paymentConfirmed = paymentConfirmed,
    payUrl = payUrl,
    courtesy = courtesy?.let { CourtesyResponse(it.plan, it.endsAt) },
)

private fun SubscriptionUsage.toResponse() = SubscriptionUsageResponse(
    groupsUsed = groupsUsed,
    groupsLimit = groupsLimit,
)
