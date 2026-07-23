package br.com.saqz.groups.domain.finance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceTest {
    @Test fun `charge kinds are complete`() = assertEquals(2, ChargeKind.entries.size)
    @Test fun `game charge kind is represented`() = assertEquals(ChargeKind.Game, ChargeKind.entries[0])
    @Test fun `monthly charge kind is represented`() = assertEquals(ChargeKind.Monthly, ChargeKind.entries[1])
    @Test fun `charge statuses are complete`() = assertEquals(4, ChargeStatus.entries.size)
    @Test fun `pending charge status is represented`() = assertEquals(ChargeStatus.Pending, ChargeStatus.entries[0])
    @Test fun `paid charge status is represented`() = assertEquals(ChargeStatus.Paid, ChargeStatus.entries[1])
    @Test fun `waived charge status is represented`() = assertEquals(ChargeStatus.Waived, ChargeStatus.entries[2])
    @Test fun `cancelled charge status is represented`() = assertEquals(ChargeStatus.Cancelled, ChargeStatus.entries[3])
    @Test fun `expense categories are complete`() = assertEquals(4, ExpenseCategory.entries.size)
    @Test fun `venue expense category is represented`() = assertEquals(ExpenseCategory.Venue, ExpenseCategory.entries[0])
    @Test fun `equipment expense category is represented`() = assertEquals(ExpenseCategory.Equipment, ExpenseCategory.entries[1])
    @Test fun `referee expense category is represented`() = assertEquals(ExpenseCategory.Referee, ExpenseCategory.entries[2])
    @Test fun `other expense category is represented`() = assertEquals(ExpenseCategory.Other, ExpenseCategory.entries[3])
    @Test fun `expense statuses are complete`() = assertEquals(2, ExpenseStatus.entries.size)
    @Test fun `active expense status is represented`() = assertEquals(ExpenseStatus.Active, ExpenseStatus.entries[0])
    @Test fun `voided expense status is represented`() = assertEquals(ExpenseStatus.Voided, ExpenseStatus.entries[1])
    @Test fun `expense actions are complete`() = assertEquals(3, ExpenseAction.entries.size)
    @Test fun `created expense action is represented`() = assertEquals(ExpenseAction.Created, ExpenseAction.entries[0])
    @Test fun `edited expense action is represented`() = assertEquals(ExpenseAction.Edited, ExpenseAction.entries[1])
    @Test fun `voided expense action is represented`() = assertEquals(ExpenseAction.Voided, ExpenseAction.entries[2])
    @Test fun `charge preserves integral cents`() = assertEquals(2_500L, charge().amountCents)
    @Test fun `charge preserves monthly identity`() = assertEquals("2026-08", charge().copy(kind = ChargeKind.Monthly, gameId = null, month = "2026-08").month)
    @Test fun `charge preserves review requirement`() = assertTrue(charge().reviewRequired)
    @Test fun `charge audit preserves status transition`() = assertEquals(ChargeStatus.Paid, charge().audit.single().newStatus)
    @Test fun `charge audit note remains optional`() = assertNull(charge().audit.single().copy(note = null).note)
    @Test fun `charge totals preserve every status bucket`() = assertEquals(listOf(2_500L, 5_000L, 1_000L, 7_000L), listOf(chargeTotals().pendingCents, chargeTotals().paidCents, chargeTotals().waivedCents, chargeTotals().cancelledCents))
    @Test fun `athlete charge list may omit organizer totals`() = assertNull(ChargeList(listOf(charge())).totals)
    @Test fun `finance version preserves quoted opaque value`() = assertEquals("\"5\"", FinanceVersionToken("\"5\"").value)
    @Test fun `versioned charge retains aggregate and version`() = assertEquals("charge-1", VersionedCharge(charge(), FinanceVersionToken("\"5\"")).charge.id)
    @Test fun `monthly command preserves idempotency key`() = assertEquals("request-1", monthlyCommand().requestId)
    @Test fun `monthly command preserves cents and members`() = assertEquals(7_000L to setOf("member-1", "member-2"), monthlyCommand().amountCents to monthlyCommand().memberIds)
    @Test fun `status command preserves optional note`() = assertEquals("Ajuste manual", ChargeStatusCommand(ChargeStatus.Waived, "Ajuste manual").note)
    @Test fun `expense preserves integral cents`() = assertEquals(12_345L, expense().amountCents)
    @Test fun `expense preserves custom category and notes`() = assertEquals("Água" to "Compra manual", expense().customCategory to expense().notes)
    @Test fun `expense audit preserves actor action and time`() = assertEquals(listOf("actor-1", ExpenseAction.Created, "2026-08-12T10:00:00Z"), expense().audit.single().let { listOf(it.actorId, it.action, it.occurredAt) })
    @Test fun `expense list preserves active total cents`() = assertEquals(12_345L, ExpenseList(listOf(expense()), 12_345L).activeTotalCents)
    @Test fun `versioned expense retains version`() = assertEquals("\"2\"", VersionedExpense(expense(), FinanceVersionToken("\"2\"")).version.value)
    @Test fun `expense write command preserves nullable request id`() = assertNull(expenseCommand().requestId)
    @Test fun `expense write command preserves dates and category`() = assertEquals("2026-08-12" to ExpenseCategory.Other, expenseCommand().expenseDate to expenseCommand().category)
    @Test fun `finance totals preserve charge and expense cents`() = assertEquals(listOf(2_500L, 5_000L, 1_000L, 7_000L, 12_345L), totals().let { listOf(it.pendingChargeCents, it.paidChargeCents, it.waivedChargeCents, it.cancelledChargeCents, it.activeExpenseCents) })
    @Test fun `validation error preserves safe global and field details`() = assertEquals(mapOf("amountCents" to listOf("is invalid")), assertIs<FinanceError.Validation>(FinanceError.Validation(DataError.Validation(ValidationDetails(listOf("invalid"), mapOf("amountCents" to listOf("is invalid")))))).error.details.fieldMessages)
    @Test fun `permanent finance errors remain distinct`() = assertEquals(6, setOf(FinanceError.HiddenResource, FinanceError.Forbidden, FinanceError.Conflict, FinanceError.PreconditionRequired, FinanceError.InvalidLifecycle, FinanceError.Authentication).size)
    @Test fun `shared finance data error is retained`() = assertEquals(DataError.Connectivity, assertIs<FinanceError.Data>(FinanceError.Data(DataError.Connectivity)).error)
    @Test fun `athlete capability exposes own charges only`() = runTest {
        val gateway = FakeAthleteGateway()
        assertEquals("charge-1", assertIs<SaqzResult.Success<ChargeList>>(gateway.ownCharges(GROUP)).value.charges.single().id)
    }
    @Test fun `organizer capability exposes charge expense and totals operations`() = runTest {
        val gateway = FakeOrganizerGateway()
        assertIs<SaqzResult.Success<ChargeList>>(gateway.charges(GROUP))
        assertIs<SaqzResult.Success<VersionedCharge>>(gateway.updateChargeStatus(GROUP, "charge-1", FinanceVersionToken("v1"), ChargeStatusCommand(ChargeStatus.Paid)))
        assertIs<SaqzResult.Success<ExpenseList>>(gateway.expenses(GROUP))
        assertIs<SaqzResult.Success<FinanceTotals>>(gateway.totals(GROUP))
    }

    private fun chargeTotals() = ChargeTotals(2_500L, 5_000L, 1_000L, 7_000L)

    private fun charge() = Charge(
        id = "charge-1",
        groupId = GROUP,
        memberId = "member-1",
        kind = ChargeKind.Game,
        gameId = "game-1",
        amountCents = 2_500L,
        dueDate = "2026-08-12",
        status = ChargeStatus.Pending,
        reviewRequired = true,
        version = 5L,
        audit = listOf(ChargeAudit("actor-1", ChargeStatus.Pending, ChargeStatus.Paid, "Recebido", "2026-08-12T10:00:00Z")),
    )

    private fun monthlyCommand() = MonthlyChargeCommand("request-1", "2026-08", 7_000L, "2026-08-10", linkedSetOf("member-1", "member-2"))

    private fun expense() = Expense(
        id = "expense-1",
        groupId = GROUP,
        description = "Água do jogo",
        amountCents = 12_345L,
        expenseDate = "2026-08-12",
        category = ExpenseCategory.Other,
        customCategory = "Água",
        notes = "Compra manual",
        status = ExpenseStatus.Active,
        version = 1L,
        audit = listOf(ExpenseAudit("actor-1", ExpenseAction.Created, "2026-08-12T10:00:00Z")),
    )

    private fun expenseCommand() = ExpenseWriteCommand(
        description = "Água do jogo",
        amountCents = 12_345L,
        expenseDate = "2026-08-12",
        category = ExpenseCategory.Other,
        customCategory = "Água",
        notes = "Compra manual",
    )

    private fun totals() = FinanceTotals(2_500L, 5_000L, 1_000L, 7_000L, 12_345L)

    private inner class FakeAthleteGateway : AthleteFinanceGateway {
        override suspend fun ownCharges(groupId: GroupId) = SaqzResult.Success(ChargeList(listOf(charge())))
    }

    private inner class FakeOrganizerGateway : OrganizerFinanceGateway {
        override suspend fun charges(groupId: GroupId) = SaqzResult.Success(ChargeList(listOf(charge()), chargeTotals()))
        override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) = SaqzResult.Success(ChargeList(listOf(charge())))
        override suspend fun updateChargeStatus(groupId: GroupId, chargeId: String, version: FinanceVersionToken, command: ChargeStatusCommand) = SaqzResult.Success(VersionedCharge(charge(), FinanceVersionToken("v2")))
        override suspend fun expenses(groupId: GroupId) = SaqzResult.Success(ExpenseList(listOf(expense()), 12_345L))
        override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand) = SaqzResult.Success(VersionedExpense(expense(), FinanceVersionToken("v1")))
        override suspend fun editExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken, command: ExpenseWriteCommand) = SaqzResult.Success(VersionedExpense(expense(), FinanceVersionToken("v2")))
        override suspend fun voidExpense(groupId: GroupId, expenseId: String, version: FinanceVersionToken) = SaqzResult.Success(VersionedExpense(expense().copy(status = ExpenseStatus.Voided), FinanceVersionToken("v3")))
        override suspend fun totals(groupId: GroupId) = SaqzResult.Success(totals())
    }

    private companion object {
        val GROUP = GroupId("group-1")
    }
}
