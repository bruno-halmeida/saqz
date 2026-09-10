package br.com.saqz.groups.presentation.monthlygeneration

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.group.GroupFinanceDefaults
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleRosterEntry
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyGenerationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    @Test fun `loads group timezone defaults and only active monthly members without selecting or writing`() {
        val gateway = MonthlyRecordingGateway()
        val vm = monthlyVm(gateway)
        assertEquals("2026-08", vm.state.value.form.month)
        assertEquals("80,00", vm.state.value.form.amount)
        assertEquals("31/08/2026", vm.state.value.form.dueDate)
        assertEquals(listOf("ana", "bia"), vm.state.value.members.map { it.id })
        assertTrue(vm.state.value.form.selectedIds.isEmpty())
        assertTrue(gateway.commands.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    @Test fun `athlete or failed load cannot review or generate and retry recovers`() {
        val groups = FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = GroupRole.ATHLETE))))
        val gateway = MonthlyRecordingGateway()
        val vm = monthlyVm(gateway, groups = groups)
        assertTrue(vm.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, vm.state.value.error)
        vm.onIntent(MonthlyGenerationIntent.Review)
        vm.onIntent(MonthlyGenerationIntent.Confirm)
        assertTrue(gateway.commands.isEmpty())
        groups.readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Unknown))
        vm.onIntent(MonthlyGenerationIntent.Retry)
        assertTrue(vm.state.value.loadFailed)
        groups.readResult = SaqzResult.Success(sampleVersionedGroup())
        vm.onIntent(MonthlyGenerationIntent.Retry)
        assertFalse(vm.state.value.loadFailed)
        assertFalse(vm.state.value.isLoading)
    }

    @Test fun `invalid amounts month date and empty selection never reach review or API`() {
        val invalid = listOf(
            MonthlyForm("2026-08", "0", "10/08/2026", setOf("ana")),
            MonthlyForm("2026-08", "-1", "10/08/2026", setOf("ana")),
            MonthlyForm("2026-08", "1000000", "10/08/2026", setOf("ana")),
            MonthlyForm("2026-08", "9223372036854775807", "10/08/2026", setOf("ana")),
            MonthlyForm("2026-08", "1,234", "10/08/2026", setOf("ana")),
            MonthlyForm("2026-13", "80", "10/08/2026", setOf("ana")),
            MonthlyForm("2026-8", "80", "10/08/2026", setOf("ana")),
            MonthlyForm("2026-02", "80", "30/02/2026", setOf("ana")),
            MonthlyForm("2026-08", "80", "10/09/2026", setOf("ana")),
            MonthlyForm("2026-08", "80", "10/08/2026"),
        )
        invalid.forEach { form ->
            val gateway = MonthlyRecordingGateway()
            val vm = monthlyVm(gateway)
            vm.fill(form)
            vm.onIntent(MonthlyGenerationIntent.Review)
            vm.onIntent(MonthlyGenerationIntent.Confirm)
            assertFalse(vm.state.value.reviewing, form.toString())
            assertEquals(GroupUiError.Validation, vm.state.value.error, form.toString())
            assertTrue(gateway.commands.isEmpty())
        }
    }

    @Test fun `review cancel and direct confirm do not write and unknown member is ignored`() {
        val gateway = MonthlyRecordingGateway()
        val vm = monthlyVm(gateway)
        vm.fill(validForm)
        vm.onIntent(MonthlyGenerationIntent.ToggleMember("outsider"))
        vm.onIntent(MonthlyGenerationIntent.Confirm)
        assertTrue(gateway.commands.isEmpty())
        vm.onIntent(MonthlyGenerationIntent.Review)
        assertTrue(vm.state.value.reviewing)
        vm.onIntent(MonthlyGenerationIntent.Edit)
        vm.onIntent(MonthlyGenerationIntent.Confirm)
        assertFalse(vm.state.value.reviewing)
        assertTrue(gateway.commands.isEmpty())
        assertEquals(setOf("ana"), vm.state.value.form.selectedIds)
    }

    @Test fun `confirmed request has exact payload freezes edits and emits success only once`() = runTest(dispatcher) {
        val gateway = MonthlyRecordingGateway().apply { deferred = CompletableDeferred() }
        val vm = monthlyVm(gateway)
        val effects = mutableListOf<MonthlyGenerationEffect>()
        backgroundScope.launch(dispatcher) { vm.effects.collect { effects += it } }
        vm.fill(validForm)
        vm.onIntent(MonthlyGenerationIntent.Review)
        vm.onIntent(MonthlyGenerationIntent.Confirm)
        vm.onIntent(MonthlyGenerationIntent.Confirm)
        vm.onIntent(MonthlyGenerationIntent.AmountChanged("20"))
        vm.onIntent(MonthlyGenerationIntent.Edit)
        vm.onIntent(MonthlyGenerationIntent.Retry)
        assertTrue(vm.state.value.isSaving)
        assertTrue(vm.state.value.reviewing)
        assertEquals(validForm, vm.state.value.form)
        assertTrue(effects.isEmpty())
        val (group, sent) = gateway.commands.single()
        assertEquals(GroupId("selected-group"), group)
        assertEquals(MonthlyChargeCommand(sent.requestId, "2026-08", 12345, "2026-08-12", setOf("ana")), sent)
        assertEquals(36, sent.requestId.length)
        gateway.deferred!!.complete(SaqzResult.Success(ChargeList(emptyList())))
        vm.onIntent(MonthlyGenerationIntent.Confirm)
        assertEquals(listOf<MonthlyGenerationEffect>(MonthlyGenerationEffect.Generated), effects)
        assertEquals(1, gateway.commands.size)
        assertFalse(vm.state.value.isSaving)
    }

    @Test fun `failure retry reuses payload key across recreation but editing rotates it`() = runTest(dispatcher) {
        val gateway = MonthlyRecordingGateway().apply { result = SaqzResult.Failure(FinanceError.Data(DataError.Unknown)) }
        val saved = SavedStateHandle()
        val vm = monthlyVm(gateway, saved)
        vm.fill(validForm)
        vm.onIntent(MonthlyGenerationIntent.Review)
        vm.onIntent(MonthlyGenerationIntent.Confirm)
        assertEquals(GroupUiError.Network, vm.state.value.error)
        assertTrue(vm.state.value.reviewing)
        val recreated = monthlyVm(gateway, saved)
        assertEquals(validForm, recreated.state.value.form)
        recreated.onIntent(MonthlyGenerationIntent.Review)
        recreated.onIntent(MonthlyGenerationIntent.Confirm)
        assertEquals(gateway.commands[0], gateway.commands[1])
        recreated.onIntent(MonthlyGenerationIntent.Edit)
        recreated.onIntent(MonthlyGenerationIntent.AmountChanged("45"))
        recreated.onIntent(MonthlyGenerationIntent.Review)
        recreated.onIntent(MonthlyGenerationIntent.Confirm)
        assertNotEquals(gateway.commands[1].second.requestId, gateway.commands[2].second.requestId)
        assertEquals(4500L, gateway.commands[2].second.amountCents)
    }

    private fun MonthlyGenerationViewModel.fill(form: MonthlyForm) {
        onIntent(MonthlyGenerationIntent.MonthChanged(form.month))
        onIntent(MonthlyGenerationIntent.AmountChanged(form.amount))
        onIntent(MonthlyGenerationIntent.DueDateChanged(form.dueDate))
        form.selectedIds.forEach { onIntent(MonthlyGenerationIntent.ToggleMember(it)) }
    }
}

internal val validForm = MonthlyForm("2026-08", "123,45", "12/08/2026", setOf("ana"))

internal fun monthlyVm(
    gateway: MonthlyRecordingGateway = MonthlyRecordingGateway(),
    saved: SavedStateHandle = SavedStateHandle(),
    groups: FakeGroupGateway = FakeGroupGateway(SaqzResult.Success(sampleVersionedGroup(
        sampleGroup().copy(financeDefaults = GroupFinanceDefaults(null, 8000, 31)),
    ))),
    athletes: FakeAthleteGateway = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(
        sampleRosterEntry("ana").copy(displayName = "Ana"),
        sampleRosterEntry("bia").copy(displayName = "Bia"),
        sampleRosterEntry("inactive").copy(active = false),
        sampleRosterEntry("avulso").copy(membershipType = AthleteMembershipType.AVULSO),
    ))),
) = MonthlyGenerationViewModel(
    "selected-group", saved, groups, athletes, gateway,
    GroupNowPort { Instant.parse("2026-09-01T01:00:00Z") },
)

internal class MonthlyRecordingGateway : OrganizerFinanceGateway by FakeOrganizerFinanceGateway() {
    val commands = mutableListOf<Pair<GroupId, MonthlyChargeCommand>>()
    var result: SaqzResult<ChargeList, FinanceError> = SaqzResult.Success(ChargeList(emptyList()))
    var deferred: CompletableDeferred<SaqzResult<ChargeList, FinanceError>>? = null
    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand): SaqzResult<ChargeList, FinanceError> {
        commands += groupId to command
        return deferred?.await() ?: result
    }
}
