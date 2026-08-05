package br.com.saqz.groups.presentation.ui.finance.settlement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.ExpenseList
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.VersionedCharge
import br.com.saqz.groups.domain.finance.VersionedExpense
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeFinanceStatementGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.ui.details.GroupDetailsRoot
import br.com.saqz.groups.presentation.ui.finance.sheets.FinanceSheetsTags
import br.com.saqz.groups.port.GroupNowPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * VUL-195: o retorno do acerto (GameSettlement) não bumpava o groupDetailsRefreshVersion — o
 * fix do VUL-179 tinha coberto só o GroupCashbox. Espelha o `GroupCashboxRootTest`
 * equivalente: prova que o `onMutationSuccess` do acerto chega ao detalhe do grupo. A "Saldo"
 * do card do detalhe vem do statementGateway (não do organizerFinanceGateway), por isso é ele
 * quem muda entre antes/depois do refresh.
 */
@OptIn(ExperimentalTestApi::class)
class GameSettlementRootTest {
    @Test
    fun `recebi diarist then back reloads the group details cashbox summary`() = runComposeUiTest {
        val charge = Charge(
            id = "game-pending",
            groupId = GroupId("group-1"),
            memberId = "member-1",
            kind = ChargeKind.Game,
            gameId = "game-1",
            amountCents = 7_000L,
            dueDate = "2026-08-04",
            status = ChargeStatus.Pending,
            version = 2,
            audit = emptyList(),
        )
        val settlementFinance = RootTestSettlementFinanceGateway(charge)
        val statementGateway = FakeFinanceStatementGateway(
            result = SaqzResult.Success(
                FinanceStatementPage(
                    month = "2026-08",
                    items = emptyList(),
                    summary = FinanceStatementSummary(0L, 0L, 0L, 0L),
                    limit = 20,
                    offset = 0,
                    hasMore = false,
                ),
            ),
        )
        val settlementViewModel = GameSettlementViewModel(
            groupId = "group-1",
            gameId = "game-1",
            gameGateway = FakeGameGateway(),
            groupGateway = FakeGroupGateway(),
            attendanceGateway = FakeAttendanceGateway(),
            athleteGateway = FakeAthleteGateway(),
            organizerFinanceGateway = settlementFinance,
        )
        val detailsViewModel = GroupDetailsViewModel(
            groupId = "group-1",
            groupGateway = FakeGroupGateway(),
            gameGateway = FakeGameGateway(),
            attendanceGateway = FakeAttendanceGateway(),
            athleteGateway = FakeAthleteGateway(),
            statementGateway = statementGateway,
            organizerFinanceGateway = FakeOrganizerFinanceGateway(),
            now = GroupNowPort { Instant.parse("2026-08-04T12:00:00Z") },
        )
        var showingSettlement by mutableStateOf(true)
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            SaqzTheme {
                if (showingSettlement) {
                    GameSettlementRoot(
                        groupId = "group-1",
                        gameId = "game-1",
                        onBack = { showingSettlement = false },
                        onOpenNewEntry = { _, _ -> },
                        onOpenCashbox = {},
                        onMutationSuccess = {
                            statementGateway.result = SaqzResult.Success(
                                FinanceStatementPage(
                                    month = "2026-08",
                                    items = emptyList(),
                                    summary = FinanceStatementSummary(7_000L, 0L, 7_000L, 7_000L),
                                    limit = 20,
                                    offset = 0,
                                    hasMore = false,
                                ),
                            )
                            refreshVersion++
                        },
                        viewModel = settlementViewModel,
                    )
                } else {
                    GroupDetailsRoot(
                        groupId = "group-1",
                        onBack = {},
                        onEffect = {},
                        viewModel = detailsViewModel,
                        refreshVersion = refreshVersion,
                    )
                }
            }
        }
        waitForIdle()

        onNodeWithTag(GameSettlementTags.receipt("game-pending")).performClick()
        waitForIdle()
        onNodeWithTag(FinanceSheetsTags.ReceiptConfirm).performClick()
        waitForIdle()
        onNodeWithContentDescription("Voltar").performClick()
        waitForIdle()

        onNodeWithText("Saldo R$\u00A070,00 · 0 mensalidades em aberto").assertExists()
        assertEquals(1, refreshVersion)
    }
}

private class RootTestSettlementFinanceGateway(
    private val charge: Charge,
) : OrganizerFinanceGateway {
    override suspend fun charges(groupId: GroupId) = SaqzResult.Success(ChargeList(listOf(charge)))

    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) =
        error("not used in this screen") as SaqzResult<ChargeList, FinanceError>

    override suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ) = SaqzResult.Success(VersionedCharge(charge.copy(status = command.status), FinanceVersionToken("\"3\"")))

    override suspend fun expenses(groupId: GroupId) = SaqzResult.Success(ExpenseList(emptyList(), 0L))

    override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand) =
        error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

    override suspend fun editExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
        command: ExpenseWriteCommand,
    ) = error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

    override suspend fun voidExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken) =
        error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

    override suspend fun totals(groupId: GroupId) =
        error("not used in this screen") as SaqzResult<FinanceTotals, FinanceError>
}
