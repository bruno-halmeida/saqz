package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.monthlyinvite.CreateMonthlyInvitesCommand
import br.com.saqz.groups.application.monthlyinvite.InvalidateMonthlyInviteLink
import br.com.saqz.groups.application.monthlyinvite.InvalidateMonthlyInviteLinkResult
import br.com.saqz.groups.application.monthlyinvite.ManageMonthlyInvites
import br.com.saqz.groups.application.monthlyinvite.MonthlyFeeDefault
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteLinkState
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitePerson
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitePersonError
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitePhoneCheck
import br.com.saqz.groups.application.monthlyinvite.MonthlyInvitePlanLimit
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteResult
import br.com.saqz.groups.application.monthlyinvite.MonthlyInviteeDisplayStatus
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant
import java.util.UUID

data class MonthlyInvitePersonRequest @JsonCreator constructor(
    @JsonProperty("name") val name: String?,
    @JsonProperty("phone") val phone: String?,
)

data class CreateMonthlyInvitesRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?,
    @JsonProperty("people") val people: List<MonthlyInvitePersonRequest>?,
)

data class MonthlyInviteLinkResponse(val state: MonthlyInviteLinkState, val url: URI?, val expiresAt: Instant?)

data class MonthlyInviteeResponse(
    val id: UUID,
    val name: String,
    /** Os 11 dígitos, sem +55. */
    val phone: String,
    val status: MonthlyInviteeDisplayStatus,
    val createdAt: Instant,
)

data class MonthlyInvitePausedResponse(val retryAt: Instant)

data class MonthlyInvitesResponse(
    val link: MonthlyInviteLinkResponse,
    val invitees: List<MonthlyInviteeResponse>,
    val paused: MonthlyInvitePausedResponse?,
    val defaultFee: MonthlyFeeDefault?,
)

data class MonthlyInvitePhoneCheckResponse(val result: MonthlyInvitePhoneCheck, val name: String?, val userId: UUID?)

data class CreatedMonthlyInvitesResponse(val enqueued: Int, val link: MonthlyInviteUrlResponse)

data class MonthlyInviteUrlResponse(val url: URI, val expiresAt: Instant)

data class MonthlyInvitesEnqueuedResponse(val enqueued: Int)

class MonthlyFeeRequiredException : RuntimeException()

class WhatsAppUnavailableException : RuntimeException()

class MonthlyInviteeNotFoundException : RuntimeException()

class InvalidMonthlyInvitePeopleException(val errors: List<MonthlyInvitePersonError>) : RuntimeException()

class MonthlyInvitePlanLimitException(val limit: MonthlyInvitePlanLimit) : RuntimeException()

/** Convite de mensalistas pelo WhatsApp (CONV-010 a 012). Dono e admin; atleta recebe 403. */
@RestController
class MonthlyInviteController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val manage: ManageMonthlyInvites,
    private val invalidate: InvalidateMonthlyInviteLink,
) {
    @GetMapping("/api/groups/{groupId}/monthly-invites")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): MonthlyInvitesResponse = manage.list(actor(identity), parseId(groupId)).orThrow().let { view ->
        MonthlyInvitesResponse(
            link = MonthlyInviteLinkResponse(view.link.state, view.link.url, view.link.expiresAt),
            invitees = view.invitees.map { MonthlyInviteeResponse(it.id, it.name, it.phone, it.status, it.createdAt) },
            paused = view.pausedRetryAt?.let(::MonthlyInvitePausedResponse),
            defaultFee = view.defaultFee,
        )
    }

    @GetMapping("/api/groups/{groupId}/monthly-invites/check")
    fun check(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestParam("phone") phone: String,
    ): MonthlyInvitePhoneCheckResponse = manage.check(actor(identity), parseId(groupId), phone).orThrow()
        .let { MonthlyInvitePhoneCheckResponse(it.result, it.name, it.userId) }

    @PostMapping("/api/groups/{groupId}/monthly-invites")
    fun create(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @RequestBody body: CreateMonthlyInvitesRequest,
    ): CreatedMonthlyInvitesResponse {
        val requestId = body.requestId
            ?: throw InvalidGroupRequestException(mapOf("requestId" to listOf("is required")), 422)
        val people = body.people.orEmpty().map { MonthlyInvitePerson(it.name, it.phone) }
        return manage.create(actor(identity), parseId(groupId), CreateMonthlyInvitesCommand(requestId, people)).orThrow()
            .let { CreatedMonthlyInvitesResponse(it.enqueued, MonthlyInviteUrlResponse(it.url, it.expiresAt)) }
    }

    @PostMapping("/api/groups/{groupId}/monthly-invites/link")
    fun copyLink(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): MonthlyInviteUrlResponse = manage.copyLink(actor(identity), parseId(groupId)).orThrow()
        .let { MonthlyInviteUrlResponse(it.url, it.expiresAt) }

    @PostMapping("/api/groups/{groupId}/monthly-invites/{inviteeId}/resend")
    fun resend(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("inviteeId") inviteeId: String,
    ): MonthlyInvitesEnqueuedResponse = MonthlyInvitesEnqueuedResponse(
        manage.resend(actor(identity), parseId(groupId), parseInvitee(inviteeId)).orThrow(),
    )

    @PostMapping("/api/groups/{groupId}/monthly-invites/resend-all")
    fun resendAll(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): MonthlyInvitesEnqueuedResponse = MonthlyInvitesEnqueuedResponse(manage.resendAll(actor(identity), parseId(groupId)).orThrow())

    @DeleteMapping("/api/groups/{groupId}/monthly-invites/{inviteeId}")
    fun remove(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
        @PathVariable("inviteeId") inviteeId: String,
    ): ResponseEntity<Void> {
        manage.remove(actor(identity), parseId(groupId), parseInvitee(inviteeId)).orThrow()
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/api/groups/{groupId}/monthly-invites/link/invalidate")
    fun invalidateLink(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable("groupId") groupId: String,
    ): ResponseEntity<Void> = when (invalidate.execute(actor(identity), parseId(groupId))) {
        InvalidateMonthlyInviteLinkResult.GroupNotFound -> throw GroupNotFoundException()
        InvalidateMonthlyInviteLinkResult.AccessForbidden -> throw AccessForbiddenException()
        InvalidateMonthlyInviteLinkResult.Success -> ResponseEntity.noContent().build()
    }

    private fun <T> MonthlyInviteResult<T>.orThrow(): T = when (this) {
        is MonthlyInviteResult.Success -> value
        MonthlyInviteResult.GroupNotFound -> throw GroupNotFoundException()
        MonthlyInviteResult.AccessForbidden -> throw AccessForbiddenException()
        MonthlyInviteResult.InviteeNotFound -> throw MonthlyInviteeNotFoundException()
        MonthlyInviteResult.MonthlyFeeRequired -> throw MonthlyFeeRequiredException()
        MonthlyInviteResult.WhatsAppUnavailable -> throw WhatsAppUnavailableException()
        MonthlyInviteResult.InvalidPhone -> throw InvalidGroupRequestException(mapOf("phone" to listOf("must be a Brazilian mobile number")), 422)
        MonthlyInviteResult.EmptyBatch -> throw InvalidGroupRequestException(mapOf("people" to listOf("must not be empty")), 422)
        is MonthlyInviteResult.InvalidPeople -> throw InvalidMonthlyInvitePeopleException(errors)
        is MonthlyInviteResult.PlanLimit -> throw MonthlyInvitePlanLimitException(limit)
    }

    private fun actor(identity: RequestIdentity): UUID = actorResolver.resolve(identity)

    private fun parseId(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull() ?: throw GroupNotFoundException()

    private fun parseInvitee(raw: String): UUID =
        runCatching { UUID.fromString(raw) }.getOrNull() ?: throw MonthlyInviteeNotFoundException()
}
