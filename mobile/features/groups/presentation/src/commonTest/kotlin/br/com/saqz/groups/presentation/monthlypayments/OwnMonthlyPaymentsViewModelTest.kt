package br.com.saqz.groups.presentation.monthlypayments

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteFinanceGateway
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OwnMonthlyPaymentsViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    @Test
    fun onlyOwnMonthlyChargesAreDisplayedWithEveryStatusAndNoHistoryTruncation() = runTest {
        val charges = ChargeStatus.entries.map { charge(it.name, it) } +
            (1..8).map { charge("history-$it", ChargeStatus.Paid) } +
            charge("other-user").copy(memberId = "another") +
            charge("other-group").copy(groupId = GroupId("another")) +
            charge("game").copy(kind = ChargeKind.Game)
        val vm = OwnMonthlyPaymentsViewModel(athletes(), FakeAthleteFinanceGateway(SaqzResult.Success(ChargeList(charges))))
        val group = vm.state.value.groups.single()
        assertEquals("Vôlei", group.name)
        assertEquals(listOf("Pending"), group.charges.pending.map { it.id })
        assertEquals(11, group.charges.history.size)
        assertEquals(setOf(OwnChargeStatusUi.Paid, OwnChargeStatusUi.Waived, OwnChargeStatusUi.Cancelled), group.charges.history.map { it.status }.toSet())
        assertEquals("Vencimento 10/09/2026", group.charges.pending.single().dueLabel)
        assertTrue(group.charges.pending.single().amountLabel.contains("70,00"))
    }

    @Test
    fun emptyAndFailedLoadsAreDistinctAndRetryFetchesFreshData() = runTest {
        val gateway = athletes()
        val finance = FakeAthleteFinanceGateway(SaqzResult.Failure(FinanceError.Data(DataError.Connectivity)))
        val vm = OwnMonthlyPaymentsViewModel(gateway, finance)
        assertTrue(vm.state.value.groups.single().charges.failed)
        finance.ownChargesResult = SaqzResult.Success(ChargeList(emptyList()))
        vm.onIntent(OwnMonthlyPaymentsIntent.Retry)
        assertFalse(vm.state.value.groups.single().charges.failed)
        assertTrue(vm.state.value.groups.single().charges.pending.isEmpty())
        gateway.ownProfileResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Connectivity))
        vm.onIntent(OwnMonthlyPaymentsIntent.Retry)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.groups.isEmpty())
        gateway.ownProfileResult = SaqzResult.Success(OwnAthleteProfile("me", "Ana", null, emptyList()))
        vm.onIntent(OwnMonthlyPaymentsIntent.Retry)
        assertNull(vm.state.value.error)
        assertTrue(vm.state.value.groups.isEmpty())
    }

    private fun athletes() = FakeAthleteGateway(ownProfileResult = SaqzResult.Success(OwnAthleteProfile(
        "me", "Ana", null, listOf(OwnAthleteMembership(GroupId("group-1"), "Vôlei", GroupRole.ATHLETE, null, AthleteMembershipType.MENSALISTA, true)),
    )))

    private fun charge(id: String, status: ChargeStatus = ChargeStatus.Pending) = Charge(
        id = id, groupId = GroupId("group-1"), memberId = "me", kind = ChargeKind.Monthly,
        month = "2026-09", amountCents = 7000, dueDate = "2026-09-10", status = status, version = 1, audit = emptyList(),
    )
}
