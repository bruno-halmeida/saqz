package br.com.saqz.groups.presentation

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.CashboxGateway
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OpeningBalance
import br.com.saqz.groups.domain.finance.UnchargedList
import br.com.saqz.groups.domain.finance.UnchargedReviewCommand
import br.com.saqz.groups.domain.finance.UnchargedReviewResult
import br.com.saqz.groups.domain.finance.UnchargedSummary
import kotlinx.coroutines.CompletableDeferred

/** Saldo inicial e diária retroativa em memória; por padrão o saldo inicial já foi respondido (R$ 0). */
class FakeCashboxGateway(
    var openingResult: SaqzResult<OpeningBalance, FinanceError> =
        SaqzResult.Success(OpeningBalance(0L, FinanceVersionToken("\"1\""))),
    var saveResult: SaqzResult<OpeningBalance, FinanceError>? = null,
    var summaryResult: SaqzResult<UnchargedSummary, FinanceError> =
        SaqzResult.Success(UnchargedSummary(0, emptyList())),
    var unchargedResult: SaqzResult<UnchargedList, FinanceError> =
        SaqzResult.Success(UnchargedList("game-1", null, emptyList())),
    var reviewResult: SaqzResult<UnchargedReviewResult, FinanceError> =
        SaqzResult.Success(UnchargedReviewResult(0, 0, 0, 0, emptyList())),
    var reviewDeferred: CompletableDeferred<SaqzResult<UnchargedReviewResult, FinanceError>>? = null,
    var saveDeferred: CompletableDeferred<SaqzResult<OpeningBalance, FinanceError>>? = null,
) : CashboxGateway {
    val saves = mutableListOf<Pair<FinanceVersionToken, Long>>()
    val reviews = mutableListOf<UnchargedReviewCommand>()
    var openingCalls = 0
        private set
    var summaryCalls = 0
        private set

    override suspend fun openingBalance(groupId: GroupId): SaqzResult<OpeningBalance, FinanceError> {
        openingCalls++
        return openingResult
    }

    override suspend fun saveOpeningBalance(groupId: GroupId, version: FinanceVersionToken, amountCents: Long) =
        run {
            saves += version to amountCents
            saveDeferred?.await() ?: saveResult
                ?: SaqzResult.Success(OpeningBalance(amountCents, FinanceVersionToken("\"${saves.size + 1}\"")))
        }

    override suspend fun unchargedSummary(groupId: GroupId): SaqzResult<UnchargedSummary, FinanceError> {
        summaryCalls++
        return summaryResult
    }

    override suspend fun uncharged(groupId: GroupId, gameId: String) = unchargedResult

    override suspend fun reviewUncharged(groupId: GroupId, gameId: String, command: UnchargedReviewCommand) = run {
        reviews += command
        reviewDeferred?.await() ?: reviewResult
    }
}
