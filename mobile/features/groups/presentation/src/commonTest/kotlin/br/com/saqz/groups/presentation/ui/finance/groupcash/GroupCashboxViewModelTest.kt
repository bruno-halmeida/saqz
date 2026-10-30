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
import br.com.saqz.groups.presentation.FakeCashboxGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.domain.finance.OpeningBalance
import br.com.saqz.groups.domain.finance.UnchargedGame
import br.com.saqz.groups.domain.finance.UnchargedSummary
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
        assertEquals("Camila, Pedro e Thiago estão com cobranças em aberto", state.overdueBanner?.message)
        assertNull(state.overdueBanner?.monthLabel)
        assertEquals(listOf("Camila", "Pedro", "Thiago"), state.debtors.map { it.name })
        assertEquals("pix@saqz.com", state.pix?.key)
        assertEquals("Vôlei do CERET", state.pix?.label)
    }

    @Test
    fun `copying the group Pix emits the key and shows the notice until dismissed DEB-151`() = runTest {
        val viewModel = viewModel()
        viewModel.onIntent(GroupCashboxIntent.CopyPix)
        assertEquals(GroupCashboxEffect.CopyPix("pix@saqz.com"), viewModel.effects.first())
        assertTrue(viewModel.state.value.pixCopied)
        viewModel.onIntent(GroupCashboxIntent.DismissPixCopied)
        assertFalse(viewModel.state.value.pixCopied)
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

        assertEquals("Camila e Pedro estão com cobranças em aberto", viewModel.state.value.overdueBanner?.message)
        assertNull(viewModel.state.value.overdueBanner?.monthLabel)
    }

    @Test
    fun `future pending charge uses a future due label`() = runTest {
        val futureCharge = defaultCharges[1].copy(dueDate = "2026-08-25")
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(chargeList(listOf(futureCharge))),
            ),
        )

        val debtor = viewModel.state.value.debtors.single()
        assertFalse(debtor.isOverdue)
        assertEquals("Vence em 25/08", debtor.dueLabel)
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
    fun `waived charges keep the cashbox out of the empty state`() = runTest {
        val waivedCharge = defaultCharges[1].copy(status = ChargeStatus.Waived)
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(chargeList(listOf(waivedCharge))),
            ),
            statement = FakeStatementGateway(SaqzResult.Success(statement(0L, 0L, 0L, 0L))),
        )

        assertFalse(viewModel.state.value.cashboxEmpty)
        assertEquals("0/1", viewModel.state.value.monthlyProgressLabel)
    }

    @Test
    fun `recebi removes debtor optimistically and sends pix plus quoted charge version`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.MarkReceived("monthly-pending"))

        assertTrue(viewModel.state.value.debtors.none { it.chargeId == "monthly-pending" })
        assertEquals("2/3", viewModel.state.value.monthlyProgressLabel)
        assertEquals("\"2\"", organizer.lastVersion?.value)
        assertEquals(ChargeStatus.Paid, organizer.lastCommand?.status)
        assertEquals(PaidMethod.Pix, organizer.lastCommand?.paidMethod)
        assertEquals(before.balanceCents + 7000L, viewModel.state.value.balanceCents)
        assertNull(viewModel.state.value.updatingChargeId)
    }

    @Test
    fun `successful recebi emits a mutation effect for the parent refresh`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.MarkReceived("monthly-pending"))

        assertEquals(GroupCashboxEffect.MutationSucceeded, viewModel.effects.first())
    }

    @Test
    fun `recebi failure restores debtor and previous totals`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(
            updateResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
        )
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.MarkReceived("monthly-pending"))

        assertEquals(before.debtors, viewModel.state.value.debtors)
        assertEquals(before.balanceCents, viewModel.state.value.balanceCents)
        assertEquals(before.monthlyProgressLabel, viewModel.state.value.monthlyProgressLabel)
        assertTrue(viewModel.state.value.operationFailed)
        assertNull(viewModel.state.value.updatingChargeId)
    }

    @Test
    fun `recebi network failure from the receipt sheet closes the sheet instead of reopening it`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(
            updateResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)),
        )
        val viewModel = viewModel(organizer = organizer)
        viewModel.onIntent(GroupCashboxIntent.OpenReceipt("monthly-pending"))
        assertEquals("monthly-pending", viewModel.state.value.receiptSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.MarkReceived("monthly-pending"))

        assertNull(viewModel.state.value.receiptSheetChargeId)
        assertTrue(viewModel.state.value.operationFailed)
    }

    @Test
    fun `recebi version conflict from the receipt sheet closes the sheet instead of reopening it`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(
            updateResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val viewModel = viewModel(organizer = organizer)
        viewModel.onIntent(GroupCashboxIntent.OpenReceipt("monthly-pending"))
        assertEquals("monthly-pending", viewModel.state.value.receiptSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.MarkReceived("monthly-pending"))

        assertNull(viewModel.state.value.receiptSheetChargeId)
        assertTrue(viewModel.state.value.operationFailed)
    }

    @Test
    fun `monthless game overdue banner stays generic for previous month`() = runTest {
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(
                    chargeList(listOf(defaultCharges[3].copy(dueDate = "2026-07-10"))),
                ),
            ),
        )

        assertEquals("Camila está com cobranças em aberto", viewModel.state.value.overdueBanner?.message)
        assertNull(viewModel.state.value.overdueBanner?.monthLabel)
        viewModel.onIntent(GroupCashboxIntent.MarkReceived("game-pending"))

        assertNull(viewModel.state.value.overdueBanner)
    }

    @Test
    fun `recebi rebuilds the overdue banner with only the remaining overdue debtors`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.MarkReceived("game-pending"))

        assertEquals("Pedro e Thiago estão com agosto em aberto", viewModel.state.value.overdueBanner?.message)
        assertEquals(listOf("monthly-pending", "monthly-thiago"), viewModel.state.value.debtors.map { it.chargeId })
    }

    @Test
    fun `register opens new entry and full statement stays on the statement route`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.Register)
        assertEquals(GroupCashboxEffect.OpenNewEntry("group-1"), viewModel.effects.first())

        viewModel.onIntent(GroupCashboxIntent.ViewFullStatement)
        assertEquals(GroupCashboxEffect.OpenStatement("group-1"), viewModel.effects.first())

        viewModel.onIntent(GroupCashboxIntent.ChargeMissing)
        assertTrue(viewModel.state.value.chargeSheetOpen)
        assertNull(viewModel.state.value.chargeSheetChargeId)
        assertFalse(viewModel.state.value.operationFailed)
    }

    @Test
    fun `charge missing does not open a sheet when there are no pending debtors`() = runTest {
        val viewModel = viewModel(
            organizer = FakeOrganizerFinanceGateway(
                chargesResult = SaqzResult.Success(chargeList(emptyList())),
            ),
        )

        viewModel.onIntent(GroupCashboxIntent.ChargeMissing)

        assertFalse(viewModel.state.value.chargeSheetOpen)
        assertNull(viewModel.state.value.chargeSheetChargeId)
    }

    @Test
    fun `charge notifications open without configured Pix`() = runTest {
        val viewModel = viewModel(
            group = FakeGroupGateway(
                readResult = SaqzResult.Success(sampleVersionedGroup(sampleGroup())),
            ),
        )

        assertTrue(viewModel.state.value.debtors.isNotEmpty())
        assertNull(viewModel.state.value.pix)

        viewModel.onIntent(GroupCashboxIntent.ChargeMissing)

        assertTrue(viewModel.state.value.chargeSheetOpen)
        assertNull(viewModel.state.value.chargeSheetChargeId)
    }

    @Test
    fun `individual charge does not open a sheet for an unknown debtor`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.ChargeIndividual("stale-charge"))

        assertFalse(viewModel.state.value.chargeSheetOpen)
        assertNull(viewModel.state.value.chargeSheetChargeId)
    }

    @Test
    fun `recebi opens the receipt sheet for the selected debtor`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.OpenReceipt("monthly-pending"))

        assertEquals("monthly-pending", viewModel.state.value.receiptSheetChargeId)
        assertFalse(viewModel.state.value.chargeSheetOpen)
    }

    @Test
    fun `receipt confirmation sends the selected paid method`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)

        viewModel.onIntent(GroupCashboxIntent.MarkReceived("monthly-pending", PaidMethod.Cash))

        assertEquals(PaidMethod.Cash, organizer.lastCommand?.paidMethod)
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

    @Test
    fun `only the charge of someone who withdrew carries the withdrew mark`() = runTest {
        val viewModel = viewModel(organizer = FakeOrganizerFinanceGateway(chargesResult = withdrewCharges()))

        val debtors = viewModel.state.value.debtors.associateBy { it.chargeId }
        assertTrue(debtors.getValue("game-pending").withdrew)
        assertFalse(debtors.getValue("monthly-pending").withdrew)
    }

    @Test
    fun `charges marked for review are listed apart from the debtors`() = runTest {
        val viewModel = viewModel(organizer = FakeOrganizerFinanceGateway(chargesResult = reviewCharges()))

        val state = viewModel.state.value
        val review = state.reviews.single()
        assertEquals("game-review", review.chargeId)
        assertEquals("Camila", review.name)
        assertEquals("Diarista · jogo de 08/08 · paga", review.referenceLabel)
        assertEquals(5L, review.chargeVersion)
        assertTrue(state.debtors.none { it.chargeId == "game-review" })
    }

    @Test
    fun `no mark means no review list`() = runTest {
        assertTrue(viewModel().state.value.reviews.isEmpty())
    }

    @Test
    fun `review opens a confirmation only for a listed charge`() = runTest {
        val viewModel = viewModel(organizer = FakeOrganizerFinanceGateway(chargesResult = reviewCharges()))

        viewModel.onIntent(GroupCashboxIntent.OpenReview("monthly-pending"))
        assertNull(viewModel.state.value.reviewSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.OpenReview("game-review"))
        assertEquals("game-review", viewModel.state.value.reviewSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.DismissReviewSheet)
        assertNull(viewModel.state.value.reviewSheetChargeId)
    }

    @Test
    fun `resolving sends the quoted version drops the mark and leaves the money alone`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(chargesResult = reviewCharges())
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value
        viewModel.onIntent(GroupCashboxIntent.OpenReview("game-review"))

        viewModel.onIntent(GroupCashboxIntent.ResolveReview("game-review"))

        assertEquals(FinanceVersionToken("\"5\""), organizer.lastResolveVersion)
        assertEquals("game-review", organizer.lastResolvedChargeId)
        assertEquals(0, organizer.updateCalls)
        val after = viewModel.state.value
        assertTrue(after.reviews.isEmpty())
        assertNull(after.reviewSheetChargeId)
        assertNull(after.updatingChargeId)
        assertFalse(after.reviewFailed)
        assertEquals(before.debtors, after.debtors)
        assertEquals(before.balanceCents, after.balanceCents)
        assertEquals(GroupCashboxEffect.MutationSucceeded, viewModel.effects.first())
    }

    @Test
    fun `resolving failure brings the mark back and says it failed`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(chargesResult = reviewCharges())
        organizer.resolveResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity))
        val viewModel = viewModel(organizer = organizer)
        viewModel.onIntent(GroupCashboxIntent.OpenReview("game-review"))

        viewModel.onIntent(GroupCashboxIntent.ResolveReview("game-review"))

        val failed = viewModel.state.value
        assertTrue(failed.reviewFailed)
        assertEquals(listOf("game-review"), failed.reviews.map { it.chargeId })
        assertNull(failed.updatingChargeId)
        assertNull(failed.reviewSheetChargeId)
    }

    @Test
    fun `double tap on resolve sends one write while the first is in flight`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val organizer = FakeOrganizerFinanceGateway(chargesResult = reviewCharges())
        organizer.beforeResolve = { reply.await() }
        val viewModel = viewModel(organizer = organizer)

        viewModel.onIntent(GroupCashboxIntent.ResolveReview("game-review"))
        viewModel.onIntent(GroupCashboxIntent.ResolveReview("game-review"))
        reply.complete(Unit)

        assertEquals(1, organizer.resolveCalls)
    }

    @Test
    fun `settled lists received and waived monthlies of the month without an electronic order`() = runTest {
        val charges = defaultCharges + listOf(
            defaultCharges.first().copy(id = "monthly-waived", memberId = "member-pedro", status = ChargeStatus.Waived),
            defaultCharges.first().copy(id = "monthly-electronic", memberId = "member-thiago", electronicOrder = true),
            defaultCharges.first().copy(id = "monthly-july", month = "2026-07"),
            defaultCharges.first().copy(id = "monthly-cancelled", status = ChargeStatus.Cancelled),
        )
        val viewModel = viewModel(organizer = FakeOrganizerFinanceGateway(chargesResult = SaqzResult.Success(chargeList(charges))))

        val settled = viewModel.state.value.settled
        assertEquals(listOf("monthly-paid", "monthly-waived"), settled.map { it.chargeId })
        assertTrue(settled.first().received)
        assertFalse(settled.last().received)
        viewModel.onIntent(GroupCashboxIntent.OpenUndo("monthly-electronic"))
        assertNull(viewModel.state.value.undoSheetChargeId)
        viewModel.onIntent(GroupCashboxIntent.OpenUndo("monthly-pending"))
        assertNull(viewModel.state.value.undoSheetChargeId)
        viewModel.onIntent(GroupCashboxIntent.OpenUndo("monthly-paid"))
        assertEquals("monthly-paid", viewModel.state.value.undoSheetChargeId)
        viewModel.onIntent(GroupCashboxIntent.DismissUndoSheet)
        assertNull(viewModel.state.value.undoSheetChargeId)
    }

    @Test
    fun `undo sends pending with the quoted version and the trimmed note then reloads the cashbox`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val paidBefore = viewModel.state.value.paidMonthlyCount
        organizer.beforeUpdate = {
            organizer.chargesResult = SaqzResult.Success(
                chargeList(defaultCharges.map { if (it.id == "monthly-paid") it.copy(status = ChargeStatus.Pending, version = 2) else it }),
            )
        }
        viewModel.onIntent(GroupCashboxIntent.OpenUndo("monthly-paid"))

        viewModel.onIntent(GroupCashboxIntent.Undo("monthly-paid", "  Lancei errado  "))

        assertEquals(FinanceVersionToken("\"1\""), organizer.lastVersion)
        assertEquals(ChargeStatusCommand(ChargeStatus.Pending, note = "Lancei errado"), organizer.lastCommand)
        assertEquals(2, organizer.chargeReads)
        val after = viewModel.state.value
        assertEquals(paidBefore - 1, after.paidMonthlyCount)
        assertTrue(after.settled.isEmpty())
        assertTrue(after.debtors.any { it.chargeId == "monthly-paid" })
        assertNull(after.undoSheetChargeId)
        assertNull(after.updatingChargeId)
        assertFalse(after.undoFailed)
        assertEquals(GroupCashboxEffect.MutationSucceeded, viewModel.effects.first())
    }

    @Test
    fun `undo without a note sends no note`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)

        viewModel.onIntent(GroupCashboxIntent.Undo("monthly-paid", "  "))

        assertEquals(ChargeStatusCommand(ChargeStatus.Pending, note = null), organizer.lastCommand)
    }

    @Test
    fun `undo failure keeps the charge settled and says it failed`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        organizer.updateResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity))
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value
        viewModel.onIntent(GroupCashboxIntent.OpenUndo("monthly-paid"))

        viewModel.onIntent(GroupCashboxIntent.Undo("monthly-paid"))

        val failed = viewModel.state.value
        assertTrue(failed.undoFailed)
        assertEquals(before.settled, failed.settled)
        assertNull(failed.updatingChargeId)
        assertNull(failed.undoSheetChargeId)
        assertEquals(1, organizer.chargeReads)
    }

    @Test
    fun `undo with an old version reloads the charges for the next try`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        organizer.updateResult = SaqzResult.Failure(FinanceError.Conflict)
        val viewModel = viewModel(organizer = organizer)

        viewModel.onIntent(GroupCashboxIntent.Undo("monthly-paid"))

        assertTrue(viewModel.state.value.undoFailed)
        assertEquals(2, organizer.chargeReads)
    }

    @Test
    fun `double tap on undo sends one write while the first is in flight`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val organizer = FakeOrganizerFinanceGateway()
        organizer.beforeUpdate = { reply.await() }
        val viewModel = viewModel(organizer = organizer)

        viewModel.onIntent(GroupCashboxIntent.Undo("monthly-paid"))
        viewModel.onIntent(GroupCashboxIntent.Undo("monthly-paid"))
        viewModel.onIntent(GroupCashboxIntent.OpenUndo("monthly-paid"))
        assertNull(viewModel.state.value.undoSheetChargeId)
        reply.complete(Unit)

        assertEquals(1, organizer.updateCalls)
    }

    @Test
    fun `undo of a charge that is not settled does nothing`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.Undo("monthly-pending"))

        assertEquals(before, viewModel.state.value)
        assertEquals(0, organizer.updateCalls)
    }

    @Test
    fun `resolving an unlisted charge does nothing`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.ResolveReview("monthly-paid"))

        assertEquals(before, viewModel.state.value)
        assertEquals(0, organizer.resolveCalls)
    }

    @Test
    fun `waive opens a confirmation only for the charge of someone who withdrew`() = runTest {
        val viewModel = viewModel(organizer = FakeOrganizerFinanceGateway(chargesResult = withdrewCharges()))

        viewModel.onIntent(GroupCashboxIntent.OpenWaive("monthly-pending"))
        assertNull(viewModel.state.value.waiveSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.OpenWaive("game-pending"))
        assertEquals("game-pending", viewModel.state.value.waiveSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.DismissWaiveSheet)
        assertNull(viewModel.state.value.waiveSheetChargeId)
    }

    @Test
    fun `waive sends the waived status with the charge version and takes the charge off the debtors`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(chargesResult = withdrewCharges())
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value
        viewModel.onIntent(GroupCashboxIntent.OpenWaive("game-pending"))

        viewModel.onIntent(GroupCashboxIntent.Waive("game-pending"))

        assertEquals(FinanceVersionToken("\"4\""), organizer.lastVersion)
        assertEquals(ChargeStatusCommand(ChargeStatus.Waived), organizer.lastCommand)
        val after = viewModel.state.value
        assertEquals(listOf("monthly-pending", "monthly-thiago"), after.debtors.map { it.chargeId })
        assertNull(after.waiveSheetChargeId)
        assertNull(after.updatingChargeId)
        assertFalse(after.waiveFailed)
        assertEquals(before.balanceCents, after.balanceCents)
        assertEquals(before.receivedCents, after.receivedCents)
    }

    @Test
    fun `waive success announces the mutation`() = runTest {
        val viewModel = viewModel(organizer = FakeOrganizerFinanceGateway(chargesResult = withdrewCharges()))

        viewModel.onIntent(GroupCashboxIntent.Waive("game-pending"))

        assertEquals(GroupCashboxEffect.MutationSucceeded, viewModel.effects.first())
    }

    @Test
    fun `waive failure brings the charge back and says it failed`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(
            chargesResult = withdrewCharges(),
            updateResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value
        viewModel.onIntent(GroupCashboxIntent.OpenWaive("game-pending"))

        viewModel.onIntent(GroupCashboxIntent.Waive("game-pending"))

        val after = viewModel.state.value
        assertEquals(before.debtors, after.debtors)
        assertTrue(after.waiveFailed)
        assertFalse(after.operationFailed)
        assertNull(after.waiveSheetChargeId)
        assertNull(after.updatingChargeId)
    }

    @Test
    fun `waive of a charge that did not withdraw does nothing`() = runTest {
        val organizer = FakeOrganizerFinanceGateway(chargesResult = withdrewCharges())
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.Waive("monthly-pending"))

        assertEquals(before, viewModel.state.value)
        assertEquals(0, organizer.updateCalls)
    }

    @Test
    fun `double tap on waive sends one write while the first is in flight`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val organizer = FakeOrganizerFinanceGateway(chargesResult = withdrewCharges())
        organizer.beforeUpdate = { reply.await() }
        val viewModel = viewModel(organizer = organizer)

        viewModel.onIntent(GroupCashboxIntent.Waive("game-pending"))
        viewModel.onIntent(GroupCashboxIntent.Waive("game-pending"))
        assertEquals("game-pending", viewModel.state.value.updatingChargeId)
        viewModel.onIntent(GroupCashboxIntent.OpenWaive("game-pending"))
        assertNull(viewModel.state.value.waiveSheetChargeId)
        reply.complete(Unit)

        assertEquals(1, organizer.updateCalls)
    }

    @Test
    fun `cancel opens a confirmation for any pending charge and not for an unknown one`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.OpenCancel("monthly-pending"))
        assertEquals("monthly-pending", viewModel.state.value.cancelSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.DismissCancelSheet)
        assertNull(viewModel.state.value.cancelSheetChargeId)

        viewModel.onIntent(GroupCashboxIntent.OpenCancel("monthly-paid"))
        assertNull(viewModel.state.value.cancelSheetChargeId)
    }

    @Test
    fun `cancel sends the cancelled status with the charge version and takes the charge off the debtors`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value
        viewModel.onIntent(GroupCashboxIntent.OpenCancel("monthly-pending"))

        viewModel.onIntent(GroupCashboxIntent.Cancel("monthly-pending"))

        assertEquals(FinanceVersionToken("\"2\""), organizer.lastVersion)
        assertEquals(ChargeStatusCommand(ChargeStatus.Cancelled), organizer.lastCommand)
        val after = viewModel.state.value
        assertEquals(listOf("game-pending", "monthly-thiago"), after.debtors.map { it.chargeId })
        assertNull(after.cancelSheetChargeId)
        assertNull(after.updatingChargeId)
        assertFalse(after.cancelFailed)
        assertEquals(before.balanceCents, after.balanceCents)
        assertEquals(before.receivedCents, after.receivedCents)
    }

    @Test
    fun `cancel success announces the mutation`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.Cancel("monthly-pending"))

        assertEquals(GroupCashboxEffect.MutationSucceeded, viewModel.effects.first())
    }

    @Test
    fun `cancel with an old version brings the charge back says it failed and reloads the charges`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        organizer.updateResult = SaqzResult.Failure(FinanceError.Conflict)
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.OpenCancel("monthly-pending"))
        viewModel.onIntent(GroupCashboxIntent.Cancel("monthly-pending"))

        val after = viewModel.state.value
        assertEquals(before.debtors, after.debtors)
        assertTrue(after.cancelFailed)
        assertFalse(after.waiveFailed)
        assertNull(after.cancelSheetChargeId)
        assertNull(after.updatingChargeId)
        assertEquals(2, organizer.chargeReads)
    }

    @Test
    fun `cancel refused for lack of permission brings the charge back and does not reload`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        organizer.updateResult = SaqzResult.Failure(FinanceError.Forbidden)
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.Cancel("game-pending"))

        val after = viewModel.state.value
        assertEquals(before.debtors, after.debtors)
        assertTrue(after.cancelFailed)
        assertNull(after.updatingChargeId)
        assertEquals(1, organizer.chargeReads)
    }

    @Test
    fun `cancel of a charge that is not a debtor does nothing`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val before = viewModel.state.value

        viewModel.onIntent(GroupCashboxIntent.Cancel("monthly-paid"))

        assertEquals(before, viewModel.state.value)
        assertEquals(0, organizer.updateCalls)
    }

    @Test
    fun `double tap on cancel sends one write while the first is in flight`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val organizer = FakeOrganizerFinanceGateway()
        organizer.beforeUpdate = { reply.await() }
        val viewModel = viewModel(organizer = organizer)

        viewModel.onIntent(GroupCashboxIntent.Cancel("monthly-pending"))
        viewModel.onIntent(GroupCashboxIntent.Cancel("monthly-pending"))
        assertEquals("monthly-pending", viewModel.state.value.updatingChargeId)
        viewModel.onIntent(GroupCashboxIntent.OpenCancel("monthly-thiago"))
        assertNull(viewModel.state.value.cancelSheetChargeId)
        reply.complete(Unit)

        assertEquals(1, organizer.updateCalls)
    }

    private fun groupWithPix(pixKey: String?) = FakeGroupGateway(
        readResult = SaqzResult.Success(
            sampleVersionedGroup(sampleGroup(profile = sampleGroup().profile?.copy(pixKey = pixKey))),
        ),
    )

    @Test
    fun `pix key notice needs a pending charge and no key`() = runTest {
        assertFalse(viewModel().state.value.pixKeyNotice)
        assertTrue(viewModel(group = groupWithPix(null)).state.value.pixKeyNotice)
        assertTrue(viewModel(group = groupWithPix("  ")).state.value.pixKeyNotice)
        assertFalse(
            viewModel(
                group = groupWithPix(null),
                organizer = FakeOrganizerFinanceGateway(chargesResult = SaqzResult.Success(chargeList(emptyList()))),
            ).state.value.pixKeyNotice,
        )
    }

    @Test
    fun `pix key notice is not shown while loading or after a load failure`() {
        assertFalse(GroupCashboxState(isLoading = true).pixKeyNotice)
        assertFalse(GroupCashboxState(isLoading = false, loadFailed = true).pixKeyNotice)
    }

    @Test
    fun `pix key notice shortcut opens the group settings`() = runTest {
        val viewModel = viewModel(group = groupWithPix(null))

        viewModel.onIntent(GroupCashboxIntent.OpenPixSetup)

        assertEquals(GroupCashboxEffect.OpenGroupSettings("group-1"), viewModel.effects.first())
    }

    @Test
    fun `pull refresh keeps the cashbox and ends not refreshing`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val loaded = viewModel.state.value.debtors
        organizer.beforeCharges = { reply.await() }

        viewModel.onIntent(GroupCashboxIntent.PullRefresh)
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(loaded, viewModel.state.value.debtors)

        reply.complete(Unit)
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(loaded, viewModel.state.value.debtors)
    }

    @Test
    fun `pull refresh failure keeps the cashbox and warns`() = runTest {
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val loaded = viewModel.state.value.debtors
        organizer.chargesResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity))

        viewModel.onIntent(GroupCashboxIntent.PullRefresh)

        val state = viewModel.state.value
        assertFalse(state.isRefreshing)
        assertFalse(state.loadFailed)
        assertTrue(state.refreshFailed)
        assertEquals(loaded, state.debtors)
        viewModel.onIntent(GroupCashboxIntent.DismissRefreshFailed)
        assertFalse(viewModel.state.value.refreshFailed)
    }

    @Test
    fun `pull during a load in flight rides it and warns when it fails`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        val loaded = viewModel.state.value.debtors
        organizer.beforeCharges = { reply.await() }
        organizer.chargesResult = SaqzResult.Failure(FinanceError.Data(DataError.Connectivity))
        viewModel.onIntent(GroupCashboxIntent.Refresh) // o primeiro, logo após o `init`, é descartado
        viewModel.onIntent(GroupCashboxIntent.Refresh)
        assertEquals(2, organizer.chargeReads)

        viewModel.onIntent(GroupCashboxIntent.PullRefresh)
        assertTrue(viewModel.state.value.isRefreshing)

        reply.complete(Unit)
        assertEquals(2, organizer.chargeReads)
        assertFalse(viewModel.state.value.isRefreshing)
        assertTrue(viewModel.state.value.refreshFailed)
        assertEquals(loaded, viewModel.state.value.debtors)
    }

    @Test
    fun `two pulls in a row fetch once`() = runTest {
        val reply = CompletableDeferred<Unit>()
        val organizer = FakeOrganizerFinanceGateway()
        val viewModel = viewModel(organizer = organizer)
        organizer.beforeCharges = { reply.await() }

        viewModel.onIntent(GroupCashboxIntent.PullRefresh)
        viewModel.onIntent(GroupCashboxIntent.PullRefresh)
        reply.complete(Unit)

        assertEquals(2, organizer.chargeReads)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `balance comes from the statement with the opening balance and the score is the month`() = runTest {
        val page = statement(88_000L, 64_000L, 24_000L, 49_000L).let {
            it.copy(
                summary = it.summary.copy(openingBalanceCents = 25_000L),
                items = listOf(
                    FinanceStatementItem("e1", "EXPENSE", FinanceDirection.Out, "Quadra", "VENUE", null,
                        "2026-08-18T22:00:00Z", -16_000L),
                    FinanceStatementItem("c1", "CHARGE", FinanceDirection.In, "Diária · Bia", "GAME", PaidMethod.Pix,
                        "2026-08-18T22:00:00Z", 2_000L),
                ),
            )
        }
        val viewModel = viewModel(
            statement = FakeStatementGateway(SaqzResult.Success(page)),
            cashbox = FakeCashboxGateway(openingResult = SaqzResult.Success(OpeningBalance(25_000L, FinanceVersionToken("\"2\"")))),
        )
        val state = viewModel.state.value

        assertEquals("R$\u00A0490,00", state.balanceLabel)
        assertEquals("R$\u00A0250", state.openingLabel)
        assertEquals("R$\u00A0880", state.monthInLabel)
        assertEquals("R$\u00A0640", state.monthOutLabel)
        assertEquals(listOf("Quadra"), state.monthOut.map { it.title })
        assertEquals("\u2212R$\u00A0160", state.monthOut.single().amountLabel)
        assertEquals("Terça, 18/08", state.monthOut.single().subtitle)
        assertEquals(listOf("Diária · Bia"), state.monthIn.map { it.title })
        assertEquals(CashboxTab.Due, state.tab)
        assertFalse(state.askOpening)
    }

    @Test
    fun `due number is the sum of every pending charge and tabs switch the list`() = runTest {
        val viewModel = viewModel()
        val state = viewModel.state.value

        assertEquals(formatShort(state.debtors.sumOf { it.amountCents }), state.dueLabel)
        viewModel.onIntent(GroupCashboxIntent.SelectTab(CashboxTab.Out))
        assertEquals(CashboxTab.Out, viewModel.state.value.tab)
    }

    @Test
    fun `zeroed cashbox asks the opening balance only when nobody answered and nothing was launched`() = runTest {
        val empty = FakeOrganizerFinanceGateway(chargesResult = SaqzResult.Success(ChargeList(emptyList())))
        val zeroed = viewModel(
            statement = FakeStatementGateway(SaqzResult.Success(statement(0L, 0L, 0L, 0L))),
            organizer = empty,
            cashbox = FakeCashboxGateway(openingResult = SaqzResult.Success(OpeningBalance(null, FinanceVersionToken("\"0\"")))),
        )
        assertTrue(zeroed.state.value.askOpening)

        val answered = viewModel(
            statement = FakeStatementGateway(SaqzResult.Success(statement(0L, 0L, 0L, 0L))),
            organizer = empty,
        )
        assertFalse(answered.state.value.askOpening)
    }

    @Test
    fun `saving the opening balance sends the cents with the read version and closes the sheet`() = runTest {
        val cashbox = FakeCashboxGateway(openingResult = SaqzResult.Success(OpeningBalance(25_000L, FinanceVersionToken("\"2\""))))
        val viewModel = viewModel(cashbox = cashbox)

        viewModel.onIntent(GroupCashboxIntent.OpenOpeningBalance)
        assertEquals("250,00", viewModel.state.value.openingInput)
        viewModel.onIntent(GroupCashboxIntent.OpeningInputChanged("300,00"))
        viewModel.onIntent(GroupCashboxIntent.SaveOpeningBalance)

        assertEquals(listOf(FinanceVersionToken("\"2\"") to 30_000L), cashbox.saves)
        assertFalse(viewModel.state.value.openingSheetOpen)
    }

    @Test
    fun `start from zero saves zero and a double tap saves once`() = runTest {
        val reply = CompletableDeferred<SaqzResult<OpeningBalance, FinanceError>>()
        val cashbox = FakeCashboxGateway(
            openingResult = SaqzResult.Success(OpeningBalance(null, FinanceVersionToken("\"0\""))),
            saveDeferred = reply,
        )
        val viewModel = viewModel(cashbox = cashbox)

        viewModel.onIntent(GroupCashboxIntent.StartFromZero)
        viewModel.onIntent(GroupCashboxIntent.StartFromZero)
        reply.complete(SaqzResult.Success(OpeningBalance(0L, FinanceVersionToken("\"1\""))))

        assertEquals(listOf(FinanceVersionToken("\"0\"") to 0L), cashbox.saves)
        assertEquals(0L, viewModel.state.value.openingBalanceCents)
    }

    @Test
    fun `invalid opening balance is refused without calling the server`() = runTest {
        val cashbox = FakeCashboxGateway()
        val viewModel = viewModel(cashbox = cashbox)

        viewModel.onIntent(GroupCashboxIntent.OpenOpeningBalance)
        viewModel.onIntent(GroupCashboxIntent.OpeningInputChanged(""))
        viewModel.onIntent(GroupCashboxIntent.SaveOpeningBalance)

        assertTrue(viewModel.state.value.openingInvalid)
        assertTrue(cashbox.saves.isEmpty())
    }

    @Test
    fun `opening balance conflict reloads the value and warns`() = runTest {
        val cashbox = FakeCashboxGateway(
            openingResult = SaqzResult.Success(OpeningBalance(25_000L, FinanceVersionToken("\"2\""))),
            saveResult = SaqzResult.Failure(FinanceError.Conflict),
        )
        val viewModel = viewModel(cashbox = cashbox)
        viewModel.onIntent(GroupCashboxIntent.OpenOpeningBalance)
        cashbox.openingResult = SaqzResult.Success(OpeningBalance(40_000L, FinanceVersionToken("\"3\"")))

        viewModel.onIntent(GroupCashboxIntent.OpeningInputChanged("300"))
        viewModel.onIntent(GroupCashboxIntent.SaveOpeningBalance)
        val state = viewModel.state.value

        assertTrue(state.openingConflict)
        assertTrue(state.openingSheetOpen)
        assertEquals(40_000L, state.openingBalanceCents)
        assertEquals("\"3\"", state.openingVersion)
        assertEquals("400,00", state.openingInput)
    }

    @Test
    fun `uncharged pending with one game opens its review and with many opens the game list`() = runTest {
        val one = viewModel(cashbox = FakeCashboxGateway(summaryResult = SaqzResult.Success(
            UnchargedSummary(4, listOf(UnchargedGame("g1", "Vôlei", "2026-10-20", "19:30", "CERET", 4))),
        )))
        assertEquals("20/10", one.state.value.uncharged?.singleGameDate)
        one.onIntent(GroupCashboxIntent.ReviewUncharged)
        assertEquals(GroupCashboxEffect.OpenUnchargedReview("group-1", "g1"), one.effects.first())

        val many = viewModel(cashbox = FakeCashboxGateway(summaryResult = SaqzResult.Success(
            UnchargedSummary(
                6,
                listOf(
                    UnchargedGame("g2", "Vôlei", "2026-10-20", "19:30", "CERET", 4),
                    UnchargedGame("g1", "Vôlei", "2026-10-13", "19:30", "CERET", 2),
                ),
            ),
        )))
        assertEquals(6, many.state.value.uncharged?.peopleCount)
        assertEquals("outubro", many.state.value.uncharged?.monthName)
        many.onIntent(GroupCashboxIntent.ReviewUncharged)
        assertEquals(GroupCashboxEffect.OpenUnchargedGames("group-1"), many.effects.first())
    }

    @Test
    fun `no one to decide means no uncharged strip`() = runTest {
        assertNull(viewModel().state.value.uncharged)
    }

    @Test
    fun `income and expense open their own screens`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupCashboxIntent.NewExpense)
        assertEquals(GroupCashboxEffect.OpenNewEntry("group-1", out = true), viewModel.effects.first())
        viewModel.onIntent(GroupCashboxIntent.NewIncome)
        assertEquals(GroupCashboxEffect.OpenNewEntry("group-1"), viewModel.effects.first())
    }

    private fun formatShort(cents: Long) = br.com.saqz.groups.presentation.ui.finance.formatBrlShort(cents)

    @Test
    fun `month lists follow has more in pages of at most 100 and the score stays from the summary`() = runTest {
        val queries = mutableListOf<FinanceStatementQuery>()
        fun item(id: String) = FinanceStatementItem(id, "EXPENSE", FinanceDirection.Out, "Quadra", "VENUE", null,
            "2026-08-18T22:00:00Z", -16_000L)
        val gateway = object : FinanceStatementGateway {
            override suspend fun statement(groupId: GroupId, query: FinanceStatementQuery): SaqzResult<FinanceStatementPage, FinanceError> {
                queries += query
                val base = statement(0L, 32_000L, 0L, 0L)
                return SaqzResult.Success(
                    if (query.offset == 0) base.copy(items = listOf(item("e1")), hasMore = true)
                    else base.copy(items = listOf(item("e2")), offset = query.offset, hasMore = false),
                )
            }
        }
        val state = viewModel(statement = gateway).state.value

        assertTrue(queries.all { it.limit <= 100 })
        assertEquals(listOf(0, 1), queries.map { it.offset })
        assertEquals(listOf("e1", "e2"), state.monthOut.map { it.id })
        assertEquals("R$\u00A0320", state.monthOutLabel)
    }

    @Test
    fun `host of a guest reads who was invited`() = runTest {
        val guest = Charge(
            id = "g", groupId = GroupId("group-1"), memberId = "member-camila", kind = ChargeKind.Game, gameId = "x",
            amountCents = 2_000L, dueDate = "2026-08-25", status = ChargeStatus.Pending, version = 1L, audit = emptyList(),
            guestDisplayName = "Júlia",
        )
        val state = viewModel(
            organizer = FakeOrganizerFinanceGateway(chargesResult = SaqzResult.Success(ChargeList(listOf(guest)))),
        ).state.value

        assertEquals("Convidou Júlia · R$\u00A020", state.debtors.single().cashMeta)
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
        statement: FinanceStatementGateway = FakeStatementGateway(SaqzResult.Success(statement(7_000L, 2_500L, 4_500L, 14_000L))),
        organizer: FakeOrganizerFinanceGateway = FakeOrganizerFinanceGateway(),
        cashbox: FakeCashboxGateway = FakeCashboxGateway(),
    ) = GroupCashboxViewModel(
        "group-1",
        group,
        memberships,
        statement,
        organizer,
        GroupNowPort { Instant.parse("2026-08-20T12:00:00Z") },
        cashbox,
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
        var updateCalls = 0
        var beforeUpdate: suspend () -> Unit = {}
        var resolveCalls = 0
        var lastResolveVersion: FinanceVersionToken? = null
        var lastResolvedChargeId: String? = null
        var beforeResolve: suspend () -> Unit = {}
        var resolveResult: SaqzResult<VersionedCharge, FinanceError> = SaqzResult.Success(
            VersionedCharge(defaultCharges.first(), FinanceVersionToken("\"4\"")),
        )

        var chargeReads = 0

        var beforeCharges: suspend () -> Unit = {}

        override suspend fun charges(groupId: GroupId): SaqzResult<ChargeList, FinanceError> {
            chargeReads++
            beforeCharges()
            return chargesResult
        }

        override suspend fun resolveChargeReview(
            groupId: GroupId,
            chargeId: String,
            version: FinanceVersionToken,
        ): SaqzResult<VersionedCharge, FinanceError> {
            resolveCalls++
            lastResolveVersion = version
            lastResolvedChargeId = chargeId
            beforeResolve()
            return resolveResult
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
        /** A diária de Camila é de quem confirmou e depois desistiu; as mensalidades não. */
        /** Jogo cancelado depois de a diária de Camila ser paga: a cobrança fica marcada para revisão. */
        fun reviewCharges() = SaqzResult.Success(
            chargeList(
                defaultCharges + Charge(
                    "game-review", GroupId("group-1"), "member-camila", ChargeKind.Game, amountCents = 3_000L,
                    dueDate = "2026-08-08", status = ChargeStatus.Paid, version = 5, audit = emptyList(),
                    reviewRequired = true,
                ),
            ),
        )

        fun withdrewCharges() = SaqzResult.Success(
            chargeList(defaultCharges.map { if (it.id == "game-pending") it.copy(withdrew = true) else it }),
        )

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
