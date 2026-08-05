package br.com.saqz.groups.presentation.ui.finance.settlement

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.ChargeTotals
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseAction
import br.com.saqz.groups.domain.finance.ExpenseAudit
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseList
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceTotals
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.domain.finance.VersionedCharge
import br.com.saqz.groups.domain.finance.VersionedExpense
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleRosterEntry
import br.com.saqz.groups.presentation.sampleVersionedGame
import br.com.saqz.groups.presentation.sampleVersionedGroup
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameSettlementViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads only this game charges and derives progress monthly members local time and cost`() = runTest {
        val finance = SettlementFinanceGateway(charges = charges(), expenses = expenses())
        val viewModel = viewModel(finance)
        val state = viewModel.state.value

        assertEquals(1, state.monthlyMemberCount)
        assertEquals(1, state.paidDiaristCount)
        assertEquals(2, state.totalDiaristCount)
        assertEquals(1, state.pendingDiaristCount)
        assertEquals(0.5f, state.progress)
        assertEquals("04/08/2026 · 19:30", state.header?.dateTime)
        assertEquals(3, state.header?.playersCount)
        assertEquals(8_000L, state.costCents)
        assertEquals(listOf("member-avulso-paid", "member-avulso-pending"), state.diarists.map { it.memberId })
        assertFalse(state.isSummary)
    }

    @Test
    fun `opens court expense with the local date of a past game and includes its cost`() = runTest {
        val pastDate = "2026-08-03"
        val pastGame = sampleVersionedGame().game.copy(
            localDate = pastDate,
            startsAt = "2026-08-03T22:30:00Z",
            status = br.com.saqz.groups.domain.game.GameStatus.Completed,
            venue = br.com.saqz.groups.domain.game.GameVenue("venue", "CERET", "Rua", "Quadra 2"),
        )
        val finance = SettlementFinanceGateway(charges = charges(), expenses = expenses(pastDate))
        val viewModel = viewModel(finance, game = pastGame)

        viewModel.onIntent(GameSettlementIntent.OpenCourtExpense)

        assertEquals(GameSettlementEffect.OpenNewEntry("group-1", pastDate), viewModel.effects.first())
        assertEquals(8_000L, viewModel.state.value.costCents)
    }

    @Test
    fun `summary mode is derived when no game charge remains pending`() = runTest {
        val finance = SettlementFinanceGateway(
            charges = charges().map { charge ->
                if (charge.kind == ChargeKind.Game && charge.gameId == "game-1") {
                    charge.copy(status = ChargeStatus.Paid)
                } else {
                    charge
                }
            },
            expenses = expenses(),
        )
        val viewModel = viewModel(finance)

        assertTrue(viewModel.state.value.isSummary)
        assertEquals(14_000L, viewModel.state.value.receivedDiaristCents)
        assertEquals(6_000L, viewModel.state.value.resultCents)
    }

    @Test
    fun `recebi sends the selected method and refreshes derived progress`() = runTest {
        val finance = SettlementFinanceGateway(charges = charges(), expenses = expenses())
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.MarkReceived("game-pending", PaidMethod.Cash))

        assertEquals(PaidMethod.Cash, finance.lastCommand?.paidMethod)
        assertEquals("\"2\"", finance.lastVersion?.value)
        assertTrue(viewModel.state.value.isSummary)
        assertEquals(2, viewModel.state.value.paidDiaristCount)
        assertEquals(0, viewModel.state.value.pendingDiaristCount)
    }

    @Test
    fun `recebi network failure from the receipt sheet closes the sheet instead of reopening it`() = runTest {
        val finance = SettlementFinanceGateway(
            charges = charges(),
            expenses = expenses(),
            updateResult = SaqzResult.Failure(FinanceError.Data(br.com.saqz.domain.DataError.Connectivity)),
        )
        val viewModel = viewModel(finance)
        viewModel.onIntent(GameSettlementIntent.OpenReceipt("game-pending"))
        assertEquals("game-pending", viewModel.state.value.receiptSheetChargeId)

        viewModel.onIntent(GameSettlementIntent.MarkReceived("game-pending", PaidMethod.Pix))

        assertEquals(null, viewModel.state.value.receiptSheetChargeId)
        assertTrue(viewModel.state.value.operationFailed)
    }

    @Test
    fun `recebi version conflict from the receipt sheet closes the sheet instead of reopening it`() = runTest {
        val finance = SettlementFinanceGateway(
            charges = charges(),
            expenses = expenses(),
            updateResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val viewModel = viewModel(finance)
        viewModel.onIntent(GameSettlementIntent.OpenReceipt("game-pending"))
        assertEquals("game-pending", viewModel.state.value.receiptSheetChargeId)

        viewModel.onIntent(GameSettlementIntent.MarkReceived("game-pending", PaidMethod.Pix))

        assertEquals(null, viewModel.state.value.receiptSheetChargeId)
        assertTrue(viewModel.state.value.operationFailed)
    }

    @Test
    fun `charge action stays closed without a configured Pix`() = runTest {
        val group = sampleGroup(profile = sampleGroup().profile)
        val finance = SettlementFinanceGateway(charges = charges(), expenses = expenses())
        val viewModel = viewModel(finance, group = group)

        viewModel.onIntent(GameSettlementIntent.ChargeMissing)

        assertFalse(viewModel.state.value.chargeSheetOpen)
        assertEquals(null, viewModel.state.value.pix)
    }

    private fun viewModel(
        finance: SettlementFinanceGateway,
        group: br.com.saqz.groups.domain.group.Group = sampleGroup(
            profile = sampleGroup().profile?.copy(pixKey = "pix@saqz.com"),
        ),
        game: br.com.saqz.groups.domain.game.Game = sampleVersionedGame().game.copy(
            status = br.com.saqz.groups.domain.game.GameStatus.Completed,
            venue = br.com.saqz.groups.domain.game.GameVenue("venue", "CERET", "Rua", "Quadra 2"),
        ),
    ) = GameSettlementViewModel(
        groupId = "group-1",
        gameId = "game-1",
        gameGateway = FakeGameGateway(
            readResult = SaqzResult.Success(
                sampleVersionedGame().copy(game = game),
            ),
        ),
        groupGateway = FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(group))),
        attendanceGateway = FakeAttendanceGateway(
            rosterResult = SaqzResult.Success(
                AttendanceRoster(
                    confirmed = listOf(
                        AttendanceRosterMember("member-monthly", "Bia"),
                        AttendanceRosterMember("member-avulso-paid", "Camila"),
                        AttendanceRosterMember("member-avulso-pending", "Pedro"),
                    ),
                    waitlisted = emptyList(),
                ),
            ),
        ),
        athleteGateway = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(
                    sampleRosterEntry("member-monthly").copy(membershipType = AthleteMembershipType.MENSALISTA),
                    sampleRosterEntry("member-avulso-paid").copy(membershipType = AthleteMembershipType.AVULSO),
                    sampleRosterEntry("member-avulso-pending").copy(membershipType = AthleteMembershipType.AVULSO),
                ),
            ),
        ),
        organizerFinanceGateway = finance,
    )

    private fun charges() = listOf(
        Charge(
            id = "game-paid",
            groupId = GroupId("group-1"),
            memberId = "member-avulso-paid",
            kind = ChargeKind.Game,
            gameId = "game-1",
            amountCents = 7_000L,
            dueDate = "2026-08-04",
            status = ChargeStatus.Paid,
            version = 1L,
            audit = emptyList(),
        ),
        Charge(
            id = "game-pending",
            groupId = GroupId("group-1"),
            memberId = "member-avulso-pending",
            kind = ChargeKind.Game,
            gameId = "game-1",
            amountCents = 7_000L,
            dueDate = "2026-08-04",
            status = ChargeStatus.Pending,
            version = 2L,
            audit = emptyList(),
        ),
        Charge(
            id = "other-game",
            groupId = GroupId("group-1"),
            memberId = "member-avulso-pending",
            kind = ChargeKind.Game,
            gameId = "game-2",
            amountCents = 9_000L,
            dueDate = "2026-08-11",
            status = ChargeStatus.Pending,
            version = 3L,
            audit = emptyList(),
        ),
        Charge(
            id = "monthly",
            groupId = GroupId("group-1"),
            memberId = "member-monthly",
            kind = ChargeKind.Monthly,
            month = "2026-08",
            amountCents = 7_000L,
            dueDate = "2026-08-10",
            status = ChargeStatus.Pending,
            version = 4L,
            audit = emptyList(),
        ),
    )

    private fun expenses(expenseDate: String = "2026-08-04") = listOf(
        Expense(
            id = "court-expense",
            groupId = GroupId("group-1"),
            description = "Aluguel da quadra",
            amountCents = 8_000L,
            expenseDate = expenseDate,
            category = ExpenseCategory.Venue,
            status = ExpenseStatus.Active,
            version = 1L,
            audit = listOf(ExpenseAudit("admin", ExpenseAction.Created, "2026-08-04T22:00:00Z")),
            direction = FinanceDirection.Out,
        ),
    )
}

private class SettlementFinanceGateway(
    charges: List<Charge>,
    expenses: List<Expense>,
    private val updateResult: SaqzResult<VersionedCharge, FinanceError>? = null,
) : OrganizerFinanceGateway {
    private var currentCharges = charges
    private val currentExpenses = expenses
    var lastVersion: FinanceVersionToken? = null
    var lastCommand: ChargeStatusCommand? = null

    override suspend fun charges(groupId: GroupId) = SaqzResult.Success(
        ChargeList(currentCharges, ChargeTotals(0L, 0L, 0L, 0L)),
    )

    override suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ): SaqzResult<VersionedCharge, FinanceError> {
        lastVersion = version
        lastCommand = command
        updateResult?.let { return it }
        currentCharges = currentCharges.map { charge ->
            if (charge.id == chargeId) charge.copy(status = command.status) else charge
        }
        val charge = currentCharges.first { it.id == chargeId }
        return SaqzResult.Success(VersionedCharge(charge, FinanceVersionToken("\"3\"")))
    }

    override suspend fun expenses(groupId: GroupId) = SaqzResult.Success(
        ExpenseList(currentExpenses, currentExpenses.sumOf { it.amountCents }),
    )

    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) =
        error("not used in this screen")

    override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand) =
        error("not used in this screen")

    override suspend fun editExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken, command: ExpenseWriteCommand) =
        error("not used in this screen")

    override suspend fun voidExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken) =
        error("not used in this screen")

    override suspend fun totals(groupId: GroupId) =
        error("not used in this screen")
}
