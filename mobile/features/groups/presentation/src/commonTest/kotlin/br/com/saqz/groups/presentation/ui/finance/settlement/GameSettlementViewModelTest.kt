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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    fun `ending a confirmed settlement closes once without reloading or financial mutations`() = runTest {
        val finance = SettlementFinanceGateway(emptyList(), emptyList())
        val viewModel = viewModel(finance)
        val effects = mutableListOf<GameSettlementEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }
        val confirmed = viewModel.state.value

        viewModel.onIntent(GameSettlementIntent.EndSettlement)
        viewModel.onIntent(GameSettlementIntent.EndSettlement)

        assertEquals(listOf<GameSettlementEffect>(GameSettlementEffect.Closed), effects)
        assertEquals(confirmed, viewModel.state.value)
        assertEquals(1, finance.chargeReads)
        assertEquals(null, finance.lastCommand)
    }

    @Test
    fun `ending with a pending receipt or while its write is in flight never closes`() = runTest {
        val finance = SettlementFinanceGateway(charges(), expenses())
        val reply = CompletableDeferred<Unit>()
        finance.beforeUpdate = { reply.await() }
        val viewModel = viewModel(finance)
        val effects = mutableListOf<GameSettlementEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }

        viewModel.onIntent(GameSettlementIntent.EndSettlement)
        assertEquals(emptyList(), effects)
        viewModel.onIntent(GameSettlementIntent.MarkReceived("game-pending", PaidMethod.Pix))
        assertEquals("game-pending", viewModel.state.value.updatingChargeId)
        assertFalse(viewModel.state.value.isSummary)
        viewModel.onIntent(GameSettlementIntent.EndSettlement)
        assertEquals(emptyList(), effects)

        reply.complete(Unit)
        assertTrue(viewModel.state.value.isSummary)
        viewModel.onIntent(GameSettlementIntent.EndSettlement)
        assertEquals(listOf(GameSettlementEffect.MutationSucceeded, GameSettlementEffect.Closed), effects)
    }

    @Test
    fun `load error with zero cached pending charges is not a closed settlement`() {
        assertFalse(GameSettlementState(isLoading = false, loadFailed = true).isSummary)
    }

    @Test
    fun `failed remote reload cannot conclude an earlier confirmed summary`() = runTest {
        val finance = SettlementFinanceGateway(emptyList(), emptyList())
        val viewModel = viewModel(finance)
        val effects = mutableListOf<GameSettlementEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }
        finance.readFailure = FinanceError.Data(br.com.saqz.domain.DataError.Connectivity)

        viewModel.onIntent(GameSettlementIntent.Retry)
        assertTrue(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isSummary)
        viewModel.onIntent(GameSettlementIntent.EndSettlement)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `failed last receipt restores debt and cannot conclude`() = runTest {
        val finance = SettlementFinanceGateway(
            charges(), expenses(),
            updateResult = SaqzResult.Failure(FinanceError.Data(br.com.saqz.domain.DataError.Connectivity)),
        )
        val viewModel = viewModel(finance)
        val effects = mutableListOf<GameSettlementEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }

        viewModel.onIntent(GameSettlementIntent.MarkReceived("game-pending", PaidMethod.Pix))
        val failed = viewModel.state.value
        assertTrue(failed.operationFailed)
        assertFalse(failed.isSummary)
        assertEquals(1, failed.pendingDiaristCount)
        assertEquals(7_000L, failed.pendingDiaristCents)
        assertEquals(listOf("game-pending"), failed.debtors.map { it.chargeId })
        assertEquals(ChargeStatus.Pending, failed.diarists.first { it.chargeId == "game-pending" }.status)
        viewModel.onIntent(GameSettlementIntent.EndSettlement)
        assertEquals(emptyList(), effects)
    }

    @Test
    fun `cashbox carries this group and reopening reflects changed remote debt`() = runTest {
        val finance = SettlementFinanceGateway(emptyList(), emptyList())
        val closed = viewModel(finance)
        closed.onIntent(GameSettlementIntent.OpenCashbox)
        assertEquals(GameSettlementEffect.OpenCashbox("group-1"), closed.effects.first())
        closed.onIntent(GameSettlementIntent.EndSettlement)
        assertEquals(GameSettlementEffect.Closed, closed.effects.first())

        finance.currentCharges = charges()
        val reopened = viewModel(finance)
        assertFalse(reopened.state.value.isSummary)
        assertEquals(listOf("game-pending"), reopened.state.value.debtors.map { it.chargeId })
    }

    @Test
    fun `loads only this game charges and derives progress monthly members and local time without reading expenses`() = runTest {
        val finance = SettlementFinanceGateway(charges = charges(), expenses = expenses())
        val viewModel = viewModel(finance)
        val state = viewModel.state.value

        assertEquals(1, state.monthlyMemberCount)
        assertEquals(1, state.paidDiaristCount)
        assertEquals(2, state.totalDiaristCount)
        assertEquals(1, state.pendingDiaristCount)
        assertEquals(0.5f, state.progress)
        assertEquals("04/08/2026 · 19h30", state.header?.dateTime)
        assertEquals(3, state.header?.playersCount)
        assertEquals(0, finance.expensesCalls)
        assertEquals(listOf("member-avulso-paid", "member-avulso-pending"), state.diarists.map { it.memberId })
        assertFalse(state.isSummary)
    }

    @Test
    fun `guestChargeShowsTheGuestAndWhoPays`() = runTest {
        val finance = SettlementFinanceGateway(
            charges = listOf(
                Charge(
                    id = "host-charge",
                    groupId = GroupId("group-1"),
                    memberId = "member-host",
                    kind = ChargeKind.Game,
                    gameId = "game-1",
                    amountCents = 7_000L,
                    dueDate = "2026-08-04",
                    status = ChargeStatus.Pending,
                    version = 1L,
                    audit = emptyList(),
                ),
                Charge(
                    id = "guest-charge",
                    groupId = GroupId("group-1"),
                    memberId = "member-host",
                    kind = ChargeKind.Game,
                    gameId = "game-1",
                    amountCents = 7_000L,
                    dueDate = "2026-08-04",
                    status = ChargeStatus.Pending,
                    version = 2L,
                    audit = emptyList(),
                    guestDisplayName = "Rafa Moreira",
                ),
            ),
            expenses = emptyList(),
        )
        val viewModel = GameSettlementViewModel(
            groupId = "group-1",
            gameId = "game-1",
            gameGateway = FakeGameGateway(),
            groupGateway = FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup())),
            attendanceGateway = FakeAttendanceGateway(
                rosterResult = SaqzResult.Success(
                    AttendanceRoster(
                        confirmed = listOf(
                            AttendanceRosterMember(memberId = "member-host", displayName = "Host Nome"),
                            AttendanceRosterMember(
                                memberId = "member-host",
                                displayName = "Rafa Moreira",
                                guestSeq = 1,
                                hostDisplayName = "Host Nome",
                            ),
                        ),
                        waitlisted = emptyList(),
                    ),
                ),
            ),
            athleteGateway = FakeAthleteGateway(
                rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("member-host"))),
            ),
            organizerFinanceGateway = finance,
        )

        val diarists = viewModel.state.value.diarists
        assertEquals(2, diarists.size)
        val hostDiarist = diarists.first { it.chargeId == "host-charge" }
        val guestDiarist = diarists.first { it.chargeId == "guest-charge" }
        assertEquals("Host Nome", hostDiarist.name)
        assertEquals("Rafa Moreira", guestDiarist.name)
        assertEquals("Convidado de Host Nome · quem paga é Host Nome", guestDiarist.meta)
    }

    @Test
    fun `guestDoesNotCountAsMonthlyMember`() = runTest {
        val finance = SettlementFinanceGateway(charges = emptyList(), expenses = emptyList())
        val viewModel = GameSettlementViewModel(
            groupId = "group-1",
            gameId = "game-1",
            gameGateway = FakeGameGateway(),
            groupGateway = FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup())),
            attendanceGateway = FakeAttendanceGateway(
                rosterResult = SaqzResult.Success(
                    AttendanceRoster(
                        confirmed = listOf(
                            AttendanceRosterMember(memberId = "member-host", displayName = "Host Nome"),
                            AttendanceRosterMember(
                                memberId = "member-host",
                                displayName = "Rafa Moreira",
                                guestSeq = 1,
                                hostDisplayName = "Host Nome",
                            ),
                        ),
                        waitlisted = emptyList(),
                    ),
                ),
            ),
            athleteGateway = FakeAthleteGateway(
                rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("member-host"))),
            ),
            organizerFinanceGateway = finance,
        )

        assertEquals(1, viewModel.state.value.monthlyMemberCount)
    }

    @Test
    fun `opens court expense with the local date of a past game`() = runTest {
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
        assertEquals(0, finance.expensesCalls)
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
        assertEquals(0L, viewModel.state.value.pendingDiaristCents)
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
    fun `successful recebi emits a mutation effect for the parent refresh`() = runTest {
        val finance = SettlementFinanceGateway(charges = charges(), expenses = expenses())
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.MarkReceived("game-pending", PaidMethod.Cash))

        assertEquals(GameSettlementEffect.MutationSucceeded, viewModel.effects.first())
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
    fun `charge notifications open without configured Pix`() = runTest {
        val group = sampleGroup(profile = sampleGroup().profile)
        val finance = SettlementFinanceGateway(charges = charges(), expenses = expenses())
        val viewModel = viewModel(finance, group = group)

        viewModel.onIntent(GameSettlementIntent.ChargeMissing)

        assertTrue(viewModel.state.value.chargeSheetOpen)
        assertEquals(null, viewModel.state.value.pix)
    }

    @Test
    fun `only the diarist who withdrew carries the withdrew mark`() = runTest {
        val finance = SettlementFinanceGateway(withdrewCharges(), expenses())
        val viewModel = viewModel(finance)

        val diarists = viewModel.state.value.diarists.associateBy { it.chargeId }
        assertTrue(diarists.getValue("game-pending").withdrew)
        assertFalse(diarists.getValue("game-paid").withdrew)
        assertTrue(viewModel.state.value.debtors.single().withdrew)
    }

    @Test
    fun `waive opens a confirmation only for a pending charge of someone who withdrew`() = runTest {
        val viewModel = viewModel(SettlementFinanceGateway(withdrewCharges(), expenses()))

        viewModel.onIntent(GameSettlementIntent.OpenWaive("game-paid"))
        assertEquals(null, viewModel.state.value.waiveSheetChargeId)

        viewModel.onIntent(GameSettlementIntent.OpenWaive("game-pending"))
        assertEquals("game-pending", viewModel.state.value.waiveSheetChargeId)
        assertEquals(null, viewModel.state.value.receiptSheetChargeId)

        viewModel.onIntent(GameSettlementIntent.DismissWaiveSheet)
        assertEquals(null, viewModel.state.value.waiveSheetChargeId)
    }

    @Test
    fun `waive sends the waived status with the version and settles the game without counting it as received`() = runTest {
        val finance = SettlementFinanceGateway(withdrewCharges(), expenses())
        val viewModel = viewModel(finance)
        val effects = mutableListOf<GameSettlementEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(GameSettlementIntent.OpenWaive("game-pending"))

        viewModel.onIntent(GameSettlementIntent.Waive("game-pending"))

        assertEquals(FinanceVersionToken("\"2\""), finance.lastVersion)
        assertEquals(ChargeStatusCommand(ChargeStatus.Waived), finance.lastCommand)
        val after = viewModel.state.value
        assertTrue(after.isSummary)
        assertEquals(0, after.pendingDiaristCount)
        assertEquals(7_000L, after.receivedDiaristCents)
        assertEquals(ChargeStatus.Waived, after.diarists.first { it.chargeId == "game-pending" }.status)
        assertFalse(after.diarists.first { it.chargeId == "game-pending" }.withdrew)
        assertEquals(null, after.waiveSheetChargeId)
        assertFalse(after.waiveFailed)
        assertEquals(listOf<GameSettlementEffect>(GameSettlementEffect.MutationSucceeded), effects)
    }

    @Test
    fun `waive failure brings the charge back and cannot conclude`() = runTest {
        val finance = SettlementFinanceGateway(
            withdrewCharges(), expenses(),
            updateResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val viewModel = viewModel(finance)
        viewModel.onIntent(GameSettlementIntent.OpenWaive("game-pending"))

        viewModel.onIntent(GameSettlementIntent.Waive("game-pending"))

        val failed = viewModel.state.value
        assertTrue(failed.waiveFailed)
        assertFalse(failed.operationFailed)
        assertFalse(failed.isSummary)
        assertEquals(1, failed.pendingDiaristCount)
        assertEquals(listOf("game-pending"), failed.debtors.map { it.chargeId })
        assertEquals(null, failed.updatingChargeId)
        assertEquals(null, failed.waiveSheetChargeId)
    }

    @Test
    fun `waive of a charge that did not withdraw does nothing`() = runTest {
        val finance = SettlementFinanceGateway(charges(), expenses())
        val viewModel = viewModel(finance)
        val before = viewModel.state.value

        viewModel.onIntent(GameSettlementIntent.Waive("game-pending"))

        assertEquals(before, viewModel.state.value)
        assertEquals(0, finance.updateCalls)
    }

    @Test
    fun `double tap on waive sends one write while the first is in flight`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val finance = SettlementFinanceGateway(withdrewCharges(), expenses())
        finance.beforeUpdate = { reply.await() }
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.Waive("game-pending"))
        viewModel.onIntent(GameSettlementIntent.Waive("game-pending"))
        viewModel.onIntent(GameSettlementIntent.OpenWaive("game-pending"))
        assertEquals(null, viewModel.state.value.waiveSheetChargeId)
        reply.complete(Unit)

        assertEquals(1, finance.updateCalls)
    }

    @Test
    fun `a charge marked for review shows the mark and the others do not`() = runTest {
        val viewModel = viewModel(SettlementFinanceGateway(reviewCharges(), expenses()))

        val diarists = viewModel.state.value.diarists.associateBy { it.chargeId }
        assertTrue(diarists.getValue("game-paid").reviewRequired)
        assertFalse(diarists.getValue("game-pending").reviewRequired)
    }

    @Test
    fun `review opens a confirmation only for a charge marked for review`() = runTest {
        val viewModel = viewModel(SettlementFinanceGateway(reviewCharges(), expenses()))

        viewModel.onIntent(GameSettlementIntent.OpenReview("game-pending"))
        assertEquals(null, viewModel.state.value.reviewSheetChargeId)

        viewModel.onIntent(GameSettlementIntent.OpenReview("game-paid"))
        assertEquals("game-paid", viewModel.state.value.reviewSheetChargeId)

        viewModel.onIntent(GameSettlementIntent.DismissReviewSheet)
        assertEquals(null, viewModel.state.value.reviewSheetChargeId)
    }

    @Test
    fun `resolving sends the version keeps the status and clears the mark`() = runTest {
        val finance = SettlementFinanceGateway(reviewCharges(), expenses())
        val viewModel = viewModel(finance)
        val effects = mutableListOf<GameSettlementEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(GameSettlementIntent.OpenReview("game-paid"))

        viewModel.onIntent(GameSettlementIntent.ResolveReview("game-paid"))

        assertEquals(FinanceVersionToken("\"1\""), finance.lastResolveVersion)
        assertEquals(1, finance.resolveCalls)
        assertEquals(0, finance.updateCalls)
        val after = viewModel.state.value
        val row = after.diarists.first { it.chargeId == "game-paid" }
        assertFalse(row.reviewRequired)
        assertEquals(ChargeStatus.Paid, row.status)
        assertEquals(null, after.reviewSheetChargeId)
        assertEquals(null, after.updatingChargeId)
        assertFalse(after.reviewFailed)
        assertEquals(listOf<GameSettlementEffect>(GameSettlementEffect.MutationSucceeded), effects)
    }

    @Test
    fun `resolving failure brings the mark back and says so`() = runTest {
        val finance = SettlementFinanceGateway(reviewCharges(), expenses())
        finance.resolveResult = SaqzResult.Failure(FinanceError.Data(br.com.saqz.domain.DataError.Connectivity))
        val viewModel = viewModel(finance)
        viewModel.onIntent(GameSettlementIntent.OpenReview("game-paid"))

        viewModel.onIntent(GameSettlementIntent.ResolveReview("game-paid"))

        val failed = viewModel.state.value
        assertTrue(failed.reviewFailed)
        assertTrue(failed.diarists.first { it.chargeId == "game-paid" }.reviewRequired)
        assertEquals(null, failed.updatingChargeId)
        assertEquals(null, failed.reviewSheetChargeId)
        assertEquals(1, finance.chargeReads)
    }

    @Test
    fun `resolving with an old version reloads the charges for the next try`() = runTest {
        val finance = SettlementFinanceGateway(reviewCharges(), expenses())
        finance.resolveResult = SaqzResult.Failure(FinanceError.Conflict)
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.ResolveReview("game-paid"))

        assertTrue(viewModel.state.value.reviewFailed)
        assertEquals(2, finance.chargeReads)
    }

    @Test
    fun `double tap on resolve sends one write while the first is in flight`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val finance = SettlementFinanceGateway(reviewCharges(), expenses())
        finance.beforeResolve = { reply.await() }
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.ResolveReview("game-paid"))
        viewModel.onIntent(GameSettlementIntent.ResolveReview("game-paid"))
        viewModel.onIntent(GameSettlementIntent.OpenReview("game-paid"))
        assertEquals(null, viewModel.state.value.reviewSheetChargeId)
        reply.complete(Unit)

        assertEquals(1, finance.resolveCalls)
    }

    @Test
    fun `resolving a charge that is not marked does nothing`() = runTest {
        val finance = SettlementFinanceGateway(charges(), expenses())
        val viewModel = viewModel(finance)
        val before = viewModel.state.value

        viewModel.onIntent(GameSettlementIntent.ResolveReview("game-paid"))

        assertEquals(before, viewModel.state.value)
        assertEquals(0, finance.resolveCalls)
    }

    @Test
    fun `undo is offered only for received or waived charges without an electronic order`() = runTest {
        val all = charges().map {
            when (it.id) {
                "game-paid" -> it.copy(electronicOrder = false)
                "game-pending" -> it.copy(status = ChargeStatus.Waived)
                else -> it
            }
        } + charges().first { it.id == "game-paid" }.copy(id = "game-electronic", electronicOrder = true)
        val viewModel = viewModel(SettlementFinanceGateway(all, expenses()))

        val rows = viewModel.state.value.diarists.associateBy { it.chargeId }
        assertTrue(rows.getValue("game-paid").canUndo)
        assertTrue(rows.getValue("game-pending").canUndo)
        assertFalse(rows.getValue("game-electronic").canUndo)
        viewModel.onIntent(GameSettlementIntent.OpenUndo("game-electronic"))
        assertEquals(null, viewModel.state.value.undoSheetChargeId)
        viewModel.onIntent(GameSettlementIntent.OpenUndo("game-paid"))
        assertEquals("game-paid", viewModel.state.value.undoSheetChargeId)
        viewModel.onIntent(GameSettlementIntent.DismissUndoSheet)
        assertEquals(null, viewModel.state.value.undoSheetChargeId)
    }

    @Test
    fun `undo of a pending charge does nothing`() = runTest {
        val finance = SettlementFinanceGateway(charges(), expenses())
        val viewModel = viewModel(finance)
        val before = viewModel.state.value

        viewModel.onIntent(GameSettlementIntent.OpenUndo("other-game"))
        viewModel.onIntent(GameSettlementIntent.Undo("game-pending"))

        assertEquals(before, viewModel.state.value)
        assertEquals(0, finance.updateCalls)
    }

    @Test
    fun `undo sends pending with the version and the trimmed note then reloads`() = runTest {
        val finance = SettlementFinanceGateway(charges(), expenses())
        val viewModel = viewModel(finance)
        val effects = mutableListOf<GameSettlementEffect>()
        backgroundScope.launch(dispatcher) { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(GameSettlementIntent.OpenUndo("game-paid"))

        viewModel.onIntent(GameSettlementIntent.Undo("game-paid", "  Lancei errado  "))

        assertEquals(FinanceVersionToken("\"1\""), finance.lastVersion)
        assertEquals(ChargeStatusCommand(ChargeStatus.Pending, note = "Lancei errado"), finance.lastCommand)
        assertEquals(2, finance.chargeReads)
        val after = viewModel.state.value
        val row = after.diarists.first { it.chargeId == "game-paid" }
        assertEquals(ChargeStatus.Pending, row.status)
        assertFalse(row.canUndo)
        assertEquals(null, after.undoSheetChargeId)
        assertEquals(null, after.updatingChargeId)
        assertFalse(after.undoFailed)
        assertEquals(listOf<GameSettlementEffect>(GameSettlementEffect.MutationSucceeded), effects)
    }

    @Test
    fun `undo without a note sends no note`() = runTest {
        val finance = SettlementFinanceGateway(charges(), expenses())
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.Undo("game-paid", "   "))

        assertEquals(ChargeStatusCommand(ChargeStatus.Pending, note = null), finance.lastCommand)
    }

    @Test
    fun `undo failure keeps the charge and says so`() = runTest {
        val finance = SettlementFinanceGateway(
            charges = charges(),
            expenses = expenses(),
            updateResult = SaqzResult.Failure(FinanceError.Data(br.com.saqz.domain.DataError.Connectivity)),
        )
        val viewModel = viewModel(finance)
        viewModel.onIntent(GameSettlementIntent.OpenUndo("game-paid"))

        viewModel.onIntent(GameSettlementIntent.Undo("game-paid"))

        val failed = viewModel.state.value
        assertTrue(failed.undoFailed)
        assertEquals(ChargeStatus.Paid, failed.diarists.first { it.chargeId == "game-paid" }.status)
        assertEquals(null, failed.updatingChargeId)
        assertEquals(null, failed.undoSheetChargeId)
        assertEquals(1, finance.chargeReads)
    }

    @Test
    fun `undo with an old version reloads the charges for the next try`() = runTest {
        val finance = SettlementFinanceGateway(
            charges = charges(),
            expenses = expenses(),
            updateResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.Undo("game-paid"))

        assertTrue(viewModel.state.value.undoFailed)
        assertEquals(2, finance.chargeReads)
    }

    @Test
    fun `double tap on undo sends one write while the first is in flight`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val finance = SettlementFinanceGateway(charges(), expenses())
        finance.beforeUpdate = { reply.await() }
        val viewModel = viewModel(finance)

        viewModel.onIntent(GameSettlementIntent.Undo("game-paid"))
        viewModel.onIntent(GameSettlementIntent.Undo("game-paid"))
        viewModel.onIntent(GameSettlementIntent.OpenUndo("game-paid"))
        assertEquals(null, viewModel.state.value.undoSheetChargeId)
        reply.complete(Unit)

        assertEquals(1, finance.updateCalls)
    }

    private fun reviewCharges() = charges().map { if (it.id == "game-paid") it.copy(reviewRequired = true) else it }

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

    /** Pedro confirmou, desistiu e a diária dele segue pendente. */
    private fun withdrewCharges() = charges().map { if (it.id == "game-pending") it.copy(withdrew = true) else it }

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
    var currentCharges = charges
    private val currentExpenses = expenses
    var lastVersion: FinanceVersionToken? = null
    var lastCommand: ChargeStatusCommand? = null
    var chargeReads = 0
    var updateCalls = 0
    var beforeUpdate: suspend () -> Unit = {}
    var readFailure: FinanceError? = null
    var resolveCalls = 0
    var lastResolveVersion: FinanceVersionToken? = null
    var beforeResolve: suspend () -> Unit = {}
    var resolveResult: SaqzResult<VersionedCharge, FinanceError>? = null

    override suspend fun resolveChargeReview(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
    ): SaqzResult<VersionedCharge, FinanceError> {
        resolveCalls++
        lastResolveVersion = version
        beforeResolve()
        resolveResult?.let { return it }
        currentCharges = currentCharges.map { if (it.id == chargeId) it.copy(reviewRequired = false) else it }
        return SaqzResult.Success(VersionedCharge(currentCharges.first { it.id == chargeId }, FinanceVersionToken("\"4\"")))
    }

    override suspend fun charges(groupId: GroupId): SaqzResult<ChargeList, FinanceError> {
        chargeReads++
        readFailure?.let { return SaqzResult.Failure(it) }
        return SaqzResult.Success(ChargeList(currentCharges, ChargeTotals(0L, 0L, 0L, 0L)))
    }

    override suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ): SaqzResult<VersionedCharge, FinanceError> {
        updateCalls++
        lastVersion = version
        lastCommand = command
        beforeUpdate()
        updateResult?.let { return it }
        currentCharges = currentCharges.map { charge ->
            if (charge.id == chargeId) charge.copy(status = command.status) else charge
        }
        val charge = currentCharges.first { it.id == chargeId }
        return SaqzResult.Success(VersionedCharge(charge, FinanceVersionToken("\"3\"")))
    }

    var expensesCalls = 0

    override suspend fun expenses(groupId: GroupId): SaqzResult<ExpenseList, FinanceError> {
        expensesCalls++
        return SaqzResult.Success(ExpenseList(currentExpenses, currentExpenses.sumOf { it.amountCents }))
    }

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
