package br.com.saqz.groups.data.finance

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.CashboxGateway
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OpeningBalance
import br.com.saqz.groups.domain.finance.ReviewDecision
import br.com.saqz.groups.domain.finance.UnchargedDecision
import br.com.saqz.groups.domain.finance.UnchargedGame
import br.com.saqz.groups.domain.finance.UnchargedKind
import br.com.saqz.groups.domain.finance.UnchargedList
import br.com.saqz.groups.domain.finance.UnchargedPerson
import br.com.saqz.groups.domain.finance.UnchargedReviewCommand
import br.com.saqz.groups.domain.finance.UnchargedReviewResult
import br.com.saqz.groups.domain.finance.UnchargedSummary
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class OpeningBalanceTransport(
    val amountCents: Long? = null,
    val version: Long,
)

@Serializable
private data class OpeningBalanceRequest(val amountCents: Long)

@Serializable
private data class UnchargedGameTransport(
    val gameId: String,
    val title: String,
    val localDate: String,
    val localTime: String,
    val venueName: String? = null,
    val peopleCount: Int,
)

@Serializable
private data class UnchargedSummaryTransport(
    val peopleCount: Int,
    val games: List<UnchargedGameTransport> = emptyList(),
)

@Serializable
private data class UnchargedPersonTransport(
    val memberId: String,
    val guestSeq: Int = 0,
    val displayName: String,
    val hostDisplayName: String? = null,
    val kind: String,
    val noShow: Boolean = false,
    val leftGroup: Boolean = false,
    val decision: String? = null,
)

@Serializable
private data class UnchargedListTransport(
    val gameId: String,
    val suggestedAmountCents: Long? = null,
    val people: List<UnchargedPersonTransport> = emptyList(),
)

@Serializable
private data class UnchargedReviewPersonRequest(
    val memberId: String,
    val guestSeq: Int,
    val decision: String,
    val paidMethod: String? = null,
)

@Serializable
private data class UnchargedReviewRequest(
    val requestId: String,
    val amountCents: Long? = null,
    val people: List<UnchargedReviewPersonRequest>,
)

@Serializable
private data class UnchargedReviewTransport(
    val charged: Int,
    val paid: Int,
    val skipped: Int,
    val ignored: Int,
    val charges: List<ChargeTransport> = emptyList(),
)

class KtorCashboxGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : CashboxGateway {
    private val json = Json { explicitNulls = false }

    override suspend fun openingBalance(groupId: GroupId) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, openingRoute(groupId), OpeningBalanceTransport.serializer())
        }.toOpeningBalanceResult()

    override suspend fun saveOpeningBalance(groupId: GroupId, version: FinanceVersionToken, amountCents: Long) =
        network.execute(
            HttpMethod.Put,
            openingRoute(groupId),
            OpeningBalanceTransport.serializer(),
            NetworkRequest(
                json.encodeToString(OpeningBalanceRequest(amountCents)),
                mapOf(HttpHeaders.IfMatch to version.value),
            ),
        ).toOpeningBalanceResult()

    override suspend fun unchargedSummary(groupId: GroupId) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/finance/uncharged",
                UnchargedSummaryTransport.serializer(),
            )
        }.mapFinance { it.toDomain() }

    override suspend fun uncharged(groupId: GroupId, gameId: String) =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Get,
                "api/groups/${groupId.value}/games/$gameId/uncharged",
                UnchargedListTransport.serializer(),
            )
        }.mapFinance { it.toDomain() }

    override suspend fun reviewUncharged(groupId: GroupId, gameId: String, command: UnchargedReviewCommand) =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "api/groups/${groupId.value}/games/$gameId/uncharged/review",
                UnchargedReviewTransport.serializer(),
                NetworkRequest(json.encodeToString(command.toRequest())),
            )
        }.mapFinance { it.toDomain() }

    private fun openingRoute(groupId: GroupId) = "api/groups/${groupId.value}/finance/opening-balance"
}

/** O ETag vence; sem ele, a versão do corpo vira o token entre aspas, como o servidor manda no ETag. */
private fun NetworkResult<OpeningBalanceTransport>.toOpeningBalanceResult(): SaqzResult<OpeningBalance, FinanceError> =
    when (this) {
        is NetworkResult.Failure -> SaqzResult.Failure(error.toFinanceError())
        is NetworkResult.Success -> {
            val token = metadata.header(HttpHeaders.ETag) ?: "\"${value.version}\""
            SaqzResult.Success(OpeningBalance(value.amountCents, FinanceVersionToken(token)))
        }
    }

private fun UnchargedSummaryTransport.toDomain() = UnchargedSummary(
    peopleCount = peopleCount,
    games = games.map {
        UnchargedGame(it.gameId, it.title, it.localDate, it.localTime, it.venueName, it.peopleCount)
    },
)

private fun UnchargedListTransport.toDomain() = UnchargedList(
    gameId = gameId,
    suggestedAmountCents = suggestedAmountCents,
    people = people.map {
        UnchargedPerson(
            memberId = it.memberId,
            guestSeq = it.guestSeq,
            displayName = it.displayName,
            hostDisplayName = it.hostDisplayName,
            kind = if (it.kind.uppercase() == "GUEST") UnchargedKind.Guest else UnchargedKind.Avulso,
            noShow = it.noShow,
            leftGroup = it.leftGroup,
            decision = if (it.decision?.uppercase() == "SKIP") UnchargedDecision.Skip else null,
        )
    },
)

private fun UnchargedReviewTransport.toDomain() = UnchargedReviewResult(
    charged = charged,
    paid = paid,
    skipped = skipped,
    ignored = ignored,
    charges = charges.map { it.toDomain() },
)

private fun UnchargedReviewCommand.toRequest() = UnchargedReviewRequest(
    requestId = requestId,
    amountCents = amountCents,
    people = people.map {
        UnchargedReviewPersonRequest(
            memberId = it.memberId,
            guestSeq = it.guestSeq,
            decision = when (it.decision) {
                ReviewDecision.Charge -> "CHARGE"
                ReviewDecision.Paid -> "PAID"
                ReviewDecision.Skip -> "SKIP"
            },
            paidMethod = it.paidMethod?.toTransport().takeIf { _ -> it.decision == ReviewDecision.Paid },
        )
    },
)

