package br.com.saqz.groups.presentation.ui.finance.groupcash

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.ChargeTotals
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceOverview
import br.com.saqz.groups.domain.finance.FinanceOverviewQuery
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementItem
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.VersionedCharge
import br.com.saqz.groups.domain.finance.VersionedExpense
import br.com.saqz.groups.domain.finance.ExpenseList
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand
import br.com.saqz.groups.domain.finance.FinanceOverviewGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupMembershipGateway
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGroup
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GroupCashboxViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load maps current month balance members debtors and pix`() = runTest {
        val viewModel = viewModel()
        val state = viewModel.state.value

        assertFalse(state.isLoading)
        assertEquals("Vôlei do CERET", state.groupName)
        assertEquals("agosto de 2026", state.monthLabel)
        assertEquals("3 mensalistas", state.monthlyMembersLabel)
        assertEquals("1/3", state.monthlyProgressLabel)
        assertEquals("R$\u00A070,00", state.receivedLabel)
        assertEquals("R$\u00A0140,00", state.balanceLabel)
        assertEquals("Camila, Pedro e Thiago estão com agosto em aberto", state.overdueBanner?.message)
        assertEquals(listOf("Camila", "Pedro", "Thiago"), state.debtors.map { it.name })
        assertEquals("pix@saqz.com", state.pix?.key)
        assertEquals("Vôlei do CERET", state.pix?.label)
    }

    @Test
    fun `overdue banner uses generic copy when debtors span multiple months`() = runTest {
        val julyCamila = defaultCharges[3].copy(
            id = "monthly-july-camila",
            kind = ChargeKind.Monthly,
            month = "2026-07",
            dueDate = "2026-07-10",
        )
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(chargeList(listOf(julyCamila, defaultCharges[1]))),
            ),
        )

        assertEquals("Camila e Pedro estão com mensalidades em aberto", viewModel.state.value.overdueBanner?.message)
        assertNull(viewModel.state.value.overdueBanner?.monthLabel)
    }

    @Test
    fun `no financial movement exposes the empty cashbox state`() = runTest {
        val empty = FakeOrganizerFinanceGateway(
            chargesResult = SaqzResult.Success(chargeList(emptyList())),
        )
        val viewModel = viewModel(
            organizer = empty,
            statement = FakeStatementGateway(SaqzResult.Success(statement(0L, 0L, 0L, 0L))),
        )

        assertTrue(viewModel.state.value.cashboxEmpty)
        assertEquals("0/0", viewModel.state.value.monthlyProgressLabel)
        assertTrue(viewModel.state.value.debtors.isEmpty())
    }

    @Test
    fun `pending charges keep the cashbox out of the empty state`() = runTest {
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(chargeList(listOf(defaultCharges[1]))),
            ),
            statement = FakeStatementGateway(SaqzResult.Success(statement(0L, 0L, 0L, 0L))),
        )

        assertFalse(viewModel.state.value.cashboxEmpty)
        assertEquals(listOf("Pedro"), viewModel.state.value.debtors.map { it.name })
    }

    @Test
    fun `accumulated balance keeps the cashbox out of the empty state`() = runTest {
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(chargeList(emptyList())),
            ),
            statement = FakeStatementGateway(SaqzResult.Success(statement(0L, 0L, 0L, 1L))),
        )

        assertFalse(viewModel.state.value.cashboxEmpty)
        assertEquals("R$\u00A00,01", viewModel.state.value.balanceLabel)
    }

    @Test
    fun `recebi removes debtor optimistically and sends pix plus quoted charge version`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxAction.MarkReceived("monthly-pending"))

        assertTrue(viewModel.state.value.debtors.none { it.chargeId == "monthly-pending" })
        assertEquals("2/3", viewModel.state.value.monthlyProgressLabel)
        assertEquals("\"2\"", organizer.lastVersion?.value)
        assertEquals(ChargeStatus.Paid, organizer.lastCommand?.status)
        assertEquals(PaidMethod.Pix, organizer.lastCommand?.paidMethod)
        assertEquals(before.balanceCents + 7000L, viewModel.state.value.balanceCents)
        assertNull(viewModel.state.value.updatingChargeId)
    }

    @Test
    fun `recebi failure restores debtor and previous totals`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(
            updateResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
        )
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxAction.MarkReceived("monthly-pending"))

        assertEquals(before.debtors, viewModel.state.value.debtors)
        assertEquals(before.balanceCents, viewModel.state.value.balanceCents)
        assertEquals(before.monthlyProgressLabel, viewModel.state.value.monthlyProgressLabel)
        assertTrue(viewModel.state.value.operationFailed)
        assertNull(viewModel.state.value.updatingChargeId)
    }

    @Test
    fun `recebi clears the overdue banner when the last overdue debtor is received`() = runTest {
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(chargeList(listOf(defaultCharges[3]))),
            ),
        )

        assertEquals("Camila está com agosto em aberto", viewModel.state.value.overdueBanner?.message)
        viewModel.onIntent(GroupCashboxAction.MarkReceived("game-pending"))

        assertNull(viewModel.state.value.overdueBanner)
    }

    @Test
    fun `recebi rebuilds the overdue banner with only the remaining overdue debtors`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxAction.MarkReceived("game-pending"))

        assertEquals("Pedro e Thiago estão com agosto em aberto", viewModel.state.value.overdueBanner?.message)
        assertEquals(listOf("monthly-pending", "monthly-thiago"), viewModel.state.value.debtors.map { it.chargeId })
    }

    @Test
    fun `register and full statement emit the same navigation effect while charging stays disabled`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxAction.Register)
        assertEquals(GroupCashboxEffect.OpenStatement("group-1"), viewModel.effects.first())

        viewModel.onIntent(GroupCashboxAction.ViewFullStatement)
        assertEquals(GroupCashboxEffect.OpenStatement("group-1"), viewModel.effects.first())

        viewModel.onIntent(GroupCashboxAction.ChargeMissing)
        assertFalse(viewModel.state.value.operationFailed)
    }

    @Test
    fun `load failure is exposed without inventing an offline queue`() = runTest {
        val viewModel = viewModel(
            group = FakeGroupGateway(
                readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Connectivity)),
            ),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertTrue(viewModel.state.value.debtors.isEmpty())
    }

    private fun viewModel(
        group: FakeGroupGateway = FakeGroupGateway(
            readResult = SaqzResult.Success(
                sampleVersionedGroup(
                    sampleGroup(
                        profile = sampleGroup().profile?.copy(
                            pixKey = "pix@saqz.com",
                            pixLabel = "Vôlei do CERET",
                        ),
                    ),
                ),
            ),
        ),
        memberships: FakeGroupMembershipGateway = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(
                listOf(
                    GroupMembership("member-camila", "Camila", br.com.saqz.groups.domain.group.GroupRole.ATHLETE),
                    GroupMembership("member-pedro", "Pedro", br.com.saqz.groups.domain.group.GroupRole.ATHLETE),
                    GroupMembership("member-thiago", "Thiago", br.com.saqz.groups.domain.group.GroupRole.ATHLETE),
                ),
            ),
        ),
        statement: FakeStatementGateway = FakeStatementGateway(SaqzResult.Success(statement(7_000L, 2_500L, 4_500L, 14_000L))),
        organizer: FakeOrganizerFinanceGateway = FakeOrganizerFinanceGateway(),
    ) = GroupCashboxViewModel(
        "group-1",
        group,
        memberships,
        statement,
        organizer,
        GroupNowPort { Instant.parse("2026-08-20T12:00:00Z") },
    )

    private fun statement(totalIn: Long, totalOut: Long, period: Long, accumulated: Long) = FinanceStatementPage(
        month = "2026-08",
        items = emptyList(),
        summary = FinanceStatementSummary(totalIn, totalOut, period, accumulated),
        limit = 20,
        offset = 0,
        hasMore = false,
    )

    private class FakeStatementGateway(
        private val result: SaqzResult<FinanceStatementPage, FinanceError>,
    ) : FinanceStatementGateway {
        override suspend fun statement(groupId: GroupId, query: FinanceStatementQuery) = result
    }

    private class FakeOrganizerFinanceGateway(
        var chargesResult: SaqzResult<ChargeList, FinanceError> = SaqzResult.Success(chargeList(defaultCharges)),
        var updateResult: SaqzResult<VersionedCharge, FinanceError> = SaqzResult.Success(
            VersionedCharge(defaultCharges.first(), FinanceVersionToken("\"3\"")),
        ),
    ) : OrganizerFinanceGateway {
        var lastVersion: FinanceVersionToken? = null
        var lastCommand: ChargeStatusCommand? = null

        override suspend fun charges(groupId: GroupId) = chargesResult

        override suspend fun updateChargeStatus(
            groupId: GroupId,
            chargeId: String,
            version: FinanceVersionToken,
            command: ChargeStatusCommand,
        ): SaqzResult<VersionedCharge, FinanceError> {
            lastVersion = version
            lastCommand = command
            return updateResult
        }

        override suspend fun generateMonthly(groupId: GroupId, command: br.com.saqz.groups.domain.finance.MonthlyChargeCommand) =
            error("not used in this screen")

        override suspend fun expenses(groupId: GroupId) = error("not used in this screen") as SaqzResult<ExpenseList, FinanceError>

        override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand) =
            error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

        override suspend fun editExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken, command: ExpenseWriteCommand) =
            error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

        override suspend fun voidExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken) =
            error("not used in this screen") as SaqzResult<VersionedExpense, FinanceError>

        override suspend fun totals(groupId: GroupId) = error("not used in this screen") as SaqzResult<FinanceTotals, FinanceError>
    }

    private companion object {
        fun chargeList(charges: List<Charge>) = ChargeList(
            charges = charges,
            totals = ChargeTotals(14_000L, 7_000L, 0L, 0L),
        )

        val defaultCharges = listOf(
            Charge("monthly-paid", GroupId("group-1"), "member-bia", ChargeKind.Monthly, month = "2026-08", amountCents = 7_000L, dueDate = "2026-08-10", status = ChargeStatus.Paid, version = 1, audit = emptyList()),
            Charge("monthly-pending", GroupId("group-1"), "member-pedro", ChargeKind.Monthly, month = "2026-08", amountCents = 7_000L, dueDate = "2026-08-11", status = ChargeStatus.Pending, version = 2, audit = emptyList()),
            Charge("monthly-thiago", GroupId("group-1"), "member-thiago", ChargeKind.Monthly, month = "2026-08", amountCents = 7_000L, dueDate = "2026-08-12", status = ChargeStatus.Pending, version = 3, audit = emptyList()),
            Charge("game-pending", GroupId("group-1"), "member-camila", ChargeKind.Game, amountCents = 3_000L, dueDate = "2026-08-08", status = ChargeStatus.Pending, version = 4, audit = emptyList()),
        )
    }
}
