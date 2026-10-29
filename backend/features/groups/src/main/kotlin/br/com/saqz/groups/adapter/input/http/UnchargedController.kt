package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.finance.uncharged.UnchargedDecision
import br.com.saqz.groups.application.finance.uncharged.UnchargedResult
import br.com.saqz.groups.application.finance.uncharged.UnchargedReview
import br.com.saqz.groups.application.finance.uncharged.UnchargedReviewCommand
import br.com.saqz.groups.application.finance.uncharged.UnchargedReviewItem
import br.com.saqz.groups.domain.finance.charge.PaidMethod
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class UnchargedGameSummaryResponse(
    val gameId: UUID, val title: String, val localDate: LocalDate, val localTime: LocalTime, val venueName: String, val peopleCount: Int,
)
data class UnchargedSummaryResponse(val peopleCount: Int, val games: List<UnchargedGameSummaryResponse>)
data class UnchargedPersonResponse(
    val memberId: UUID, val guestSeq: Int, val displayName: String, val hostDisplayName: String?, val kind: String,
    val noShow: Boolean, val leftGroup: Boolean, val decision: String?,
)
data class UnchargedGameResponse(val gameId: UUID, val suggestedAmountCents: Long?, val people: List<UnchargedPersonResponse>)

data class UnchargedReviewPersonRequest @JsonCreator constructor(
    @JsonProperty("memberId") val memberId: UUID?,
    @JsonProperty("guestSeq") val guestSeq: Int? = 0,
    @JsonProperty("decision") val decision: String?,
    @JsonProperty("paidMethod") val paidMethod: String? = null,
)
data class UnchargedReviewRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?,
    @JsonProperty("amountCents") val amountCents: Long? = null,
    @JsonProperty("people") val people: List<UnchargedReviewPersonRequest>?,
)
data class UnchargedReviewResponse(
    val charged: Int, val paid: Int, val skipped: Int, val ignored: Int, val charges: List<ChargeResponse>,
)

/** Diária retroativa (DEB-147, FIN-016): resumo do caixa, lista da revisão e confirmação em lote. Só gestor. */
@RestController
class UnchargedController(private val actors: VerifiedGroupActorResolver, private val review: UnchargedReview) {
    @GetMapping("/api/groups/{groupId}/finance/uncharged")
    fun summary(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String): UnchargedSummaryResponse {
        val games = unwrap(review.summary(actors.resolve(identity), uuid(groupId)))
        return UnchargedSummaryResponse(
            games.sumOf { it.peopleCount },
            games.map { UnchargedGameSummaryResponse(it.gameId, it.title, it.localDate, it.localTime, it.venueName, it.peopleCount) },
        )
    }

    @GetMapping("/api/groups/{groupId}/games/{gameId}/uncharged")
    fun game(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String, @PathVariable gameId: String): UnchargedGameResponse {
        val view = unwrap(review.game(actors.resolve(identity), uuid(groupId), uuid(gameId)))
        return UnchargedGameResponse(
            view.gameId, view.suggestedAmountCents,
            view.people.map {
                UnchargedPersonResponse(
                    it.memberId, it.guestSeq, it.displayName, it.hostDisplayName, it.kind.name, it.noShow, it.leftGroup,
                    if (it.skipped) UnchargedDecision.SKIP.name else null,
                )
            },
        )
    }

    @PostMapping("/api/groups/{groupId}/games/{gameId}/uncharged/review")
    fun confirm(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @RequestBody request: UnchargedReviewRequest,
    ): UnchargedReviewResponse {
        val actor = actors.resolve(identity); val group = uuid(groupId); val game = uuid(gameId)
        val command = UnchargedReviewCommand(
            request.requestId ?: invalid("requestId"),
            request.amountCents,
            (request.people ?: invalid("people")).mapIndexed { i, p ->
                UnchargedReviewItem(
                    p.memberId ?: invalid("people[$i].memberId"),
                    (p.guestSeq ?: 0).takeIf { it >= 0 } ?: invalid("people[$i].guestSeq"),
                    p.decision?.let { runCatching { UnchargedDecision.valueOf(it) }.getOrNull() } ?: invalid("people[$i].decision"),
                    p.paidMethod?.let { runCatching { PaidMethod.valueOf(it) }.getOrNull() ?: invalid("people[$i].paidMethod") },
                )
            },
        )
        val result = unwrap(review.review(actor, group, game, command))
        return UnchargedReviewResponse(
            result.outcome.charged, result.outcome.paid, result.outcome.skipped, result.outcome.ignored,
            result.charges.map { it.response() },
        )
    }

    private fun <T> unwrap(result: UnchargedResult<T>): T = when (result) {
        is UnchargedResult.Success -> result.value
        UnchargedResult.Hidden -> throw GameNotFoundException()
        UnchargedResult.Forbidden -> throw AccessForbiddenException()
        is UnchargedResult.Invalid -> invalid(result.field)
    }

    private fun uuid(value: String) = runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }
    private fun invalid(field: String): Nothing = throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}
