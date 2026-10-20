package br.com.saqz.groups.presentation.members

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.MonthlyInviteError
import br.com.saqz.groups.domain.membership.MonthlyInviteLink
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInvitee
import br.com.saqz.groups.domain.membership.MonthlyInviteeStatus
import br.com.saqz.groups.presentation.FakeMonthlyInviteGateway
import br.com.saqz.groups.presentation.sampleMonthlyInvites
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

@OptIn(ExperimentalCoroutinesApi::class)
class GroupInviteesViewModelTest {
    private val gateway = FakeMonthlyInviteGateway()

    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun close() { Dispatchers.resetMain() }

    private fun invitee(id: String, status: MonthlyInviteeStatus) =
        MonthlyInvitee(id, "Pessoa $id", "1198888000$id", status, "2026-10-20T15:00:00Z")

    private fun withRows(vararg statuses: MonthlyInviteeStatus, state: MonthlyInviteLinkState = MonthlyInviteLinkState.ACTIVE) {
        gateway.listResult = SaqzResult.Success(
            sampleMonthlyInvites(
                link = MonthlyInviteLink(state, "https://saqz.app/?saqz_invite=44DF8SA8", "2026-10-27T15:00:00Z"),
                invitees = statuses.mapIndexed { i, s -> invitee("$i", s) },
            ),
        )
    }

    @Test fun `owner and admin see the section with the link validity`() {
        withRows(MonthlyInviteeStatus.WAITING)
        val vm = GroupInviteesViewModel("group-1", gateway)
        val state = vm.state.value
        assertTrue(state.visible)
        assertEquals(1, state.invitees.size)
        assertEquals("27 de out", state.linkUntil)
    }

    @Test fun `athlete gets 403 and the section and chip disappear`() {
        gateway.listResult = SaqzResult.Failure(MonthlyInviteError.Forbidden)
        val vm = GroupInviteesViewModel("group-1", gateway)
        assertFalse(vm.state.value.visible)
    }

    @Test fun `resend all appears only with waiting failed paused or expired rows`() {
        withRows(MonthlyInviteeStatus.QUEUED, MonthlyInviteeStatus.NO_WHATSAPP)
        assertFalse(GroupInviteesViewModel("group-1", gateway).state.value.canResendAll)
        for (status in listOf(MonthlyInviteeStatus.WAITING, MonthlyInviteeStatus.FAILED, MonthlyInviteeStatus.PAUSED, MonthlyInviteeStatus.EXPIRED)) {
            withRows(MonthlyInviteeStatus.QUEUED, status)
            assertTrue(GroupInviteesViewModel("group-1", gateway).state.value.canResendAll, status.name)
        }
    }

    @Test fun `row actions call the api close the sheet and reload`() {
        withRows(MonthlyInviteeStatus.FAILED)
        val vm = GroupInviteesViewModel("group-1", gateway)
        vm.onIntent(GroupInviteesIntent.Open("0"))
        assertEquals("Pessoa 0", vm.state.value.selected?.name)
        vm.onIntent(GroupInviteesIntent.ResendSelected)
        assertNull(vm.state.value.selected)
        assertEquals(GroupInviteesNotice.Resent(1), vm.state.value.notice)
        vm.onIntent(GroupInviteesIntent.Open("0"))
        vm.onIntent(GroupInviteesIntent.RemoveSelected)
        assertEquals(GroupInviteesNotice.Removed("Pessoa 0"), vm.state.value.notice)
        vm.onIntent(GroupInviteesIntent.ResendAll)
        assertEquals(listOf("resend 0", "remove 0", "resendAll"), gateway.calls)
        assertEquals(4, gateway.listCalls)
    }

    @Test fun `invalidate asks first and then calls the api`() {
        withRows(MonthlyInviteeStatus.WAITING)
        val vm = GroupInviteesViewModel("group-1", gateway)
        vm.onIntent(GroupInviteesIntent.AskInvalidate)
        assertTrue(vm.state.value.confirmInvalidate)
        assertTrue(gateway.calls.isEmpty())
        vm.onIntent(GroupInviteesIntent.ConfirmInvalidate)
        assertFalse(vm.state.value.confirmInvalidate)
        assertEquals(listOf("invalidate"), gateway.calls)
        assertEquals(GroupInviteesNotice.Invalidated, vm.state.value.notice)
    }

    @Test fun `copy link renews and copies the url`() = runTest {
        withRows(MonthlyInviteeStatus.PAUSED)
        val vm = GroupInviteesViewModel("group-1", gateway)
        vm.onIntent(GroupInviteesIntent.CopyLink)
        assertEquals(GroupInviteesEffect.Copy("https://saqz.app/?saqz_invite=44DF8SA8"), vm.effects.first())
        assertEquals(GroupInviteesNotice.LinkCopied, vm.state.value.notice)
    }

    @Test fun `whatsapp off on resend tells to copy the link`() {
        withRows(MonthlyInviteeStatus.WAITING)
        gateway.actionResult = SaqzResult.Failure(MonthlyInviteError.WhatsAppUnavailable)
        val vm = GroupInviteesViewModel("group-1", gateway)
        vm.onIntent(GroupInviteesIntent.ResendAll)
        assertEquals(GroupInviteesNotice.WhatsAppUnavailable, vm.state.value.notice)
        gateway.actionResult = SaqzResult.Failure(MonthlyInviteError.DataFailure(DataError.Server))
        vm.onIntent(GroupInviteesIntent.ResendAll)
        assertEquals(GroupInviteesNotice.Failed, vm.state.value.notice)
    }

    @Test fun `invite monthly opens the new screen`() = runTest {
        val vm = GroupInviteesViewModel("group-1", gateway)
        vm.onIntent(GroupInviteesIntent.InviteMonthly)
        assertEquals(GroupInviteesEffect.OpenMonthlyInvite("group-1"), vm.effects.first())
    }
}
