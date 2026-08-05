package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import br.com.saqz.groups.presentation.ui.finance.sheets.FinanceSheetsTags
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.ChargeTotals
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
import br.com.saqz.groups.presentation.FakeAthleteFinanceGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeFinanceStatementGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupMembershipGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.ui.details.GroupDetailsRoot
import br.com.saqz.groups.port.GroupNowPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class GroupCashboxRootTest {
    @Test
    fun `recebi then back reloads the group details cashbox summary`() = runComposeUiTest {
        val charge = Charge(
            id = "monthly-pending",
            groupId = GroupId("group-1"),
            memberId = "member-1",
            kind = ChargeKind.Monthly,
            month = "2026-08",
            amountCents = 7_000L,
            dueDate = "2026-08-20",
            status = ChargeStatus.Pending,
            version = 1,
            audit = emptyList(),
        )
        val cashboxGateway = RootTestOrganizerFinanceGateway(charge)
        val detailsGateway = FakeOrganizerFinanceGateway(
            chargesResult = SaqzResult.Success(ChargeList(listOf(charge))),
        )
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
        val now = GroupNowPort { Instant.parse("2026-08-20T12:00:00Z") }
        val cashboxViewModel = GroupCashboxViewModel(
            groupId = "group-1",
            groupGateway = FakeGroupGateway(),
            membershipGateway = FakeGroupMembershipGateway(),
            statementGateway = statementGateway,
            organizerFinanceGateway = cashboxGateway,
            now = now,
        )
        val detailsViewModel = GroupDetailsViewModel(
            groupId = "group-1",
            groupGateway = FakeGroupGateway(),
            gameGateway = FakeGameGateway(),
            attendanceGateway = FakeAttendanceGateway(),
            athleteGateway = FakeAthleteGateway(),
            statementGateway = statementGateway,
            organizerFinanceGateway = detailsGateway,
            athleteFinanceGateway = FakeAthleteFinanceGateway(),
            now = now,
        )
        var showingCashbox by mutableStateOf(false)
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            // VUL-205: o detalhe entra primeiro e o caixa empilha por cima, como no
            // `SaqzNavHost` — e cada lado dentro de um `SaveableStateProvider`, que é o que o
            // `NavDisplay` dá a cada entrada: o conteúdo sai de composição enquanto a outra
            // tela está na frente, mas o estado salvo dele fica. Sem isso o detalhe montaria
            // pela primeira vez já com o contador incrementado, que é o caso de entrada
            // **nova** — e aí não recarregar é o certo, porque a ViewModel acabou de carregar.
            val stateHolder = rememberSaveableStateHolder()
            SaqzTheme {
                if (showingCashbox) {
                    stateHolder.SaveableStateProvider("cashbox") {
                        GroupCashboxRoot(
                            groupId = "group-1",
                            onBack = { showingCashbox = false },
                            onMutationSuccess = {
                                detailsGateway.chargesResult = SaqzResult.Success(ChargeList(emptyList()))
                                refreshVersion++
                            },
                            onOpenStatement = {},
                            viewModel = cashboxViewModel,
                        )
                    }
                } else {
                    stateHolder.SaveableStateProvider("details") {
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
        }
        waitForIdle()
        runOnIdle { showingCashbox = true }
        waitForIdle()

        onNodeWithText("Recebi").performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Fechar").assertCountEquals(2)
        onNodeWithTag(FinanceSheetsTags.ReceiptConfirm).performClick()
        waitForIdle()
        onNodeWithContentDescription("Voltar").performClick()
        waitForIdle()

        onNodeWithText("Saldo R$\u00A00,00 · 0 mensalidades em aberto").assertExists()
        assertEquals(1, refreshVersion)
    }

    @Test
    fun `refresh version reloads the cashbox after a saved new entry`() = runComposeUiTest {
        val charge = Charge(
            id = "monthly-pending",
            groupId = GroupId("group-1"),
            memberId = "member-1",
            kind = ChargeKind.Monthly,
            month = "2026-08",
            amountCents = 7_000L,
            dueDate = "2026-08-20",
            status = ChargeStatus.Pending,
            version = 1,
            audit = emptyList(),
        )
        val cashboxGateway = RootTestOrganizerFinanceGateway(charge)
        val cashboxViewModel = GroupCashboxViewModel(
            groupId = "group-1",
            groupGateway = FakeGroupGateway(),
            membershipGateway = FakeGroupMembershipGateway(),
            statementGateway = FakeFinanceStatementGateway(
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
            ),
            organizerFinanceGateway = cashboxGateway,
            now = GroupNowPort { Instant.parse("2026-08-20T12:00:00Z") },
        )
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            SaqzTheme {
                GroupCashboxRoot(
                    groupId = "group-1",
                    onBack = {},
                    onOpenStatement = {},
                    refreshVersion = refreshVersion,
                    viewModel = cashboxViewModel,
                )
            }
        }
        waitForIdle()

        cashboxGateway.chargesResult = SaqzResult.Success(
            ChargeList(emptyList(), ChargeTotals(0L, 0L, 0L, 0L)),
        )
        refreshVersion++
        waitForIdle()

        onNodeWithTag(GroupCashboxTags.Empty).assertExists()
    }
}

private class RootTestOrganizerFinanceGateway(
    private val charge: Charge,
) : OrganizerFinanceGateway {
    var chargesResult: SaqzResult<ChargeList, FinanceError> = SaqzResult.Success(ChargeList(listOf(charge)))

    override suspend fun charges(groupId: GroupId) =
        chargesResult

    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) =
        error("not used in this screen") as SaqzResult<ChargeList, FinanceError>

    override suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ) = SaqzResult.Success(VersionedCharge(charge, FinanceVersionToken("\"2\"")))

    override suspend fun expenses(groupId: GroupId) =
        error("not used in this screen") as SaqzResult<ExpenseList, FinanceError>

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
