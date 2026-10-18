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
import br.com.saqz.sharedkernel.subscription.TrialUsageLookup
import br.com.saqz.subscriptions.application.TrialCampaignStore
import br.com.saqz.subscriptions.application.TRIAL_ZONE
import br.com.saqz.subscriptions.application.TrialPromptStore
import br.com.saqz.subscriptions.application.trialDaysLeft
import br.com.saqz.subscriptions.application.TrialCouponSelection
import br.com.saqz.subscriptions.application.TrialOfferMode
import br.com.saqz.subscriptions.application.StartOrganizerTrial
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.time.Instant
import java.time.LocalDate
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
    val maxGroups: Int = requireNotNull(br.com.saqz.subscriptions.domain.OrganizerTrial.plan.maxGroups),
    val maxAthletes: Int? = br.com.saqz.subscriptions.domain.OrganizerTrial.plan.maxAthletes,
    val offerMode: TrialOfferMode = TrialOfferMode.ON,
    val canRedeemCoupon: Boolean = false,
    val selectedCouponCode: String? = null,
    val trialDays: Int = 14,
    val preauthorized: Boolean = false,
    /** Dias de calendário (São Paulo) até o fim do teste; só no teste ativo, para o dono. */
    val daysLeft: Int? = null,
    /** Uso real do teste até agora; só no teste ativo, para o dono. */
    val usage: TrialUsageResponse? = null,
    /** A folha de contagem do app já abriu hoje (dia de São Paulo) em algum aparelho do dono. */
    val countdownSeenToday: Boolean = false,
    /**
     * Primeiro instante em que um jogo do grupo já não pode começar (0h do dia do fim do plano ou do teste, no
     * fuso do grupo). Só na rota do grupo e só para dono e admins; o app tira dele o último dia que dá para marcar.
     */
    val gamesUntil: Instant? = null,
)

data class TrialUsageResponse(val games: Int, val confirmedAthletes: Int, val cashCents: Long)

class ApplyTrialCouponRequest { var code: String? = null }

@RestController
class OrganizerTrialController(
    private val actors: AuthenticatedActorResolver,
    private val trials: OrganizerTrialAccessLookup,
    private val groups: GroupPlanOwnerLookup,
    private val appUrl: String,
    private val campaigns: TrialCampaignStore? = null,
    private val eligibility: StartOrganizerTrial? = null,
    private val usageLookup: TrialUsageLookup? = null,
    private val prompts: TrialPromptStore? = null,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
    private val horizon: br.com.saqz.sharedkernel.subscription.GameCreationHorizon? = null,
) {
    @GetMapping("/subscriptions/trial")
    fun mine(@AuthenticationPrincipal identity: RequestIdentity): TrialAccessResponse =
        ownerResponse(actors.resolve(identity).userId)

    private fun ownerResponse(owner: UUID): TrialAccessResponse {
        val access = trials.forOwner(owner).response(true)
        val mode = campaigns?.mode() ?: TrialOfferMode.ON
        val canApply = mode != TrialOfferMode.OFF && eligibility?.isFirstTrialEligible(owner) == true
        val selected = if (canApply) campaigns?.selected(owner) else null
        val countdown = countdown(owner, access)
        val seenToday = countdown != null && prompts?.lastPromptDate(owner) == LocalDate.now(clock.withZone(TRIAL_ZONE))
        return access.copy(daysLeft = countdown?.first, usage = countdown?.second, countdownSeenToday = seenToday, offerMode = mode, canRedeemCoupon = canApply, selectedCouponCode = selected?.code,
            trialDays = selected?.trialDays ?: access.trialDays,
            preauthorized = access.canCreateGroup && campaigns?.isPreauthorized(owner) == true)
    }

    /** O app abriu a folha de contagem: vale por hoje, para este dono, em qualquer aparelho. */
    @PostMapping("/subscriptions/trial/countdown-prompt")
    fun countdownPrompted(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<Void> {
        val owner = actors.resolve(identity).userId
        val marked = prompts?.markPrompted(owner, LocalDate.now(clock.withZone(TRIAL_ZONE))) == true
        return if (marked) ResponseEntity.noContent().build() else ResponseEntity.status(409).build()
    }

    private fun countdown(owner: UUID, access: TrialAccessResponse): Pair<Int, TrialUsageResponse>? {
        val started = access.startedAt
        val ends = access.endsAt
        if (access.status != TrialStatus.ACTIVE || started == null || ends == null || usageLookup == null) return null
        val now = clock.instant()
        val usage = usageLookup.usageOf(owner, started, now)
        return trialDaysLeft(now, ends) to TrialUsageResponse(usage.games, usage.confirmedAthletes, usage.cashCents)
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun invalidBody(): ResponseEntity<Void> = ResponseEntity.badRequest().build()

    @PostMapping("/subscriptions/trial/enrollment")
    fun enroll(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<TrialAccessResponse> {
        val owner = actors.resolve(identity).userId
        val enrolled = campaigns?.enroll(owner) { eligibility?.isFirstTrialEligible(owner) == true } == true
        return if (enrolled) ResponseEntity.ok(ownerResponse(owner)) else ResponseEntity.status(409).build()
    }

    @PostMapping("/subscriptions/trial/coupon")
    fun apply(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody body: ApplyTrialCouponRequest): ResponseEntity<TrialAccessResponse> {
        val owner = actors.resolve(identity).userId
        val code = body.code?.trim().orEmpty()
        if (!code.matches(Regex("[a-zA-Z0-9]{1,32}"))) return ResponseEntity.badRequest().build()
        return when (campaigns?.select(owner, code) { eligibility?.isFirstTrialEligible(owner) == true }) {
            TrialCouponSelection.APPLIED -> ResponseEntity.ok(ownerResponse(owner))
            TrialCouponSelection.UNAVAILABLE -> ResponseEntity.badRequest().build()
            TrialCouponSelection.INELIGIBLE, null -> ResponseEntity.status(409).build()
        }
    }

    @GetMapping("/api/groups/{groupId}/trial")
    fun group(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID): TrialAccessResponse {
        val actorId = actors.resolve(identity).userId
        val owner = groups.ownerForMember(groupId, actorId) ?: throw GroupNotFoundException()
        val response = trials.forOwner(owner).response(owner == actorId)
        val gamesUntil = if (groups.isOrganizer(groupId, actorId)) horizon?.startsBefore(groupId) else null
        return response.copy(gamesUntil = gamesUntil?.takeIf { it != Instant.MAX })
    }

    private fun OrganizerTrialAccess.response(isOwner: Boolean) =
        TrialAccessResponse(status, startedAt, endsAt, serverTime, readOnly, isOwner && canCreateGroup, isOwner, appUrl,
            trialDays = if (startedAt != null && endsAt != null) java.time.Duration.between(startedAt, endsAt).toDays().toInt() else 14)
}
