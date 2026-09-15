package br.com.saqz.groups.presentation.whatsappbinding

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.GroupWhatsAppBinding
import br.com.saqz.groups.domain.communication.GroupWhatsAppGateway
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import kotlinx.coroutines.Dispatchers
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WhatsAppBindingViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun close() { Dispatchers.resetMain() }

    @Test fun loadShowsActiveBindingFromGateway() = runTest {
        val gateway = FakeGroupWhatsAppGateway(
            binding = GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.ACTIVE),
        )
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        assertEquals(listOf(GroupId("group-1")), gateway.bindingCalls)
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertFalse(state.error)
        assertTrue(state.bound)
        assertEquals("Vôlei do CERET", state.groupName)
        assertEquals(GroupWhatsAppStatus.ACTIVE, state.status)
    }

    @Test fun unboundGroupLoadsAsNoneWithEmptyInvite() = runTest {
        val viewModel = WhatsAppBindingViewModel("group-1", FakeGroupWhatsAppGateway())
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertFalse(state.bound)
        assertEquals(GroupWhatsAppStatus.NONE, state.status)
        assertEquals("", state.inviteLink)
    }

    @Test fun loadFailureShowsErrorAndStaysUnbound() = runTest {
        val viewModel = WhatsAppBindingViewModel("group-1", FakeGroupWhatsAppGateway(shouldFail = true))
        val state = viewModel.state.value
        assertFalse(state.loading)
        assertTrue(state.error)
        assertFalse(state.bound)
    }

    @Test fun blankLinkIsIgnoredWithoutCallingGatewayOrEmitting() = runTest {
        val gateway = FakeGroupWhatsAppGateway()
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        viewModel.onIntent(WhatsAppBindingIntent.ChangeInviteLink("   "))
        viewModel.onIntent(WhatsAppBindingIntent.Link)
        assertTrue(gateway.linkCalls.isEmpty())
        assertFalse(viewModel.state.value.saving)
        assertFalse(viewModel.state.value.error)
    }

    @Test fun linkSendsInviteAndConfirmsReturnedGroupName() = runTest {
        val gateway = FakeGroupWhatsAppGateway(
            linked = GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.ACTIVE),
        )
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        viewModel.onIntent(WhatsAppBindingIntent.ChangeInviteLink("https://chat.whatsapp.com/abc"))
        viewModel.onIntent(WhatsAppBindingIntent.Link)
        assertEquals(listOf(GroupId("group-1") to "https://chat.whatsapp.com/abc"), gateway.linkCalls)
        val state = viewModel.state.value
        assertTrue(state.bound)
        assertEquals(GroupWhatsAppStatus.ACTIVE, state.status)
        assertEquals("Vôlei do CERET", state.confirmedGroupName)
        assertEquals("", state.inviteLink)
        assertFalse(state.saving)
        assertEquals(WhatsAppBindingEffect.Saved, viewModel.effects.first())
    }

    @Test fun linkFailureShowsErrorAndKeepsTypedInvite() = runTest {
        val gateway = FakeGroupWhatsAppGateway(shouldFail = true)
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        viewModel.onIntent(WhatsAppBindingIntent.ChangeInviteLink("chat.whatsapp.com/abc"))
        viewModel.onIntent(WhatsAppBindingIntent.Link)
        val state = viewModel.state.value
        assertTrue(state.error)
        assertFalse(state.saving)
        assertEquals("chat.whatsapp.com/abc", state.inviteLink)
    }

    @Test fun disablingActiveBindingPersistsDisabledState() = runTest {
        val gateway = FakeGroupWhatsAppGateway(
            binding = GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.ACTIVE),
        )
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        viewModel.onIntent(WhatsAppBindingIntent.SetEnabled(false))
        assertEquals(listOf(GroupId("group-1") to false), gateway.enabledCalls)
        assertEquals(GroupWhatsAppStatus.DISABLED, viewModel.state.value.status)
        assertTrue(viewModel.state.value.bound)
        assertEquals(WhatsAppBindingEffect.Saved, viewModel.effects.first())
    }

    @Test fun enablingDisabledBindingRetakesActiveState() = runTest {
        val gateway = FakeGroupWhatsAppGateway(
            binding = GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.DISABLED),
        )
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        viewModel.onIntent(WhatsAppBindingIntent.SetEnabled(true))
        assertEquals(listOf(GroupId("group-1") to true), gateway.enabledCalls)
        assertEquals(GroupWhatsAppStatus.ACTIVE, viewModel.state.value.status)
    }

    @Test fun enableIntentIsIgnoredWhenThereIsNoBinding() = runTest {
        val gateway = FakeGroupWhatsAppGateway()
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        viewModel.onIntent(WhatsAppBindingIntent.SetEnabled(true))
        assertTrue(gateway.enabledCalls.isEmpty())
        assertEquals(GroupWhatsAppStatus.NONE, viewModel.state.value.status)
    }

    @Test fun brokenBindingCanBeRetaken() = runTest {
        val gateway = FakeGroupWhatsAppGateway(
            binding = GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.BROKEN),
        )
        val viewModel = WhatsAppBindingViewModel("group-1", gateway)
        assertTrue(viewModel.state.value.canToggle)
        viewModel.onIntent(WhatsAppBindingIntent.SetEnabled(true))
        assertEquals(listOf(GroupId("group-1") to true), gateway.enabledCalls)
    }

    private class FakeGroupWhatsAppGateway(
        var binding: GroupWhatsAppBinding = GroupWhatsAppBinding(bound = false),
        var linked: GroupWhatsAppBinding = GroupWhatsAppBinding(true, "123@g.us", "Vôlei do CERET", GroupWhatsAppStatus.ACTIVE),
        var shouldFail: Boolean = false,
    ) : GroupWhatsAppGateway {
        val bindingCalls = mutableListOf<GroupId>()
        val linkCalls = mutableListOf<Pair<GroupId, String>>()
        val enabledCalls = mutableListOf<Pair<GroupId, Boolean>>()

        override suspend fun binding(groupId: GroupId): SaqzResult<GroupWhatsAppBinding, CommunicationError> {
            bindingCalls += groupId
            return failure() ?: SaqzResult.Success(binding)
        }

        override suspend fun link(groupId: GroupId, inviteLink: String): SaqzResult<GroupWhatsAppBinding, CommunicationError> {
            linkCalls += groupId to inviteLink
            return failure() ?: SaqzResult.Success(linked)
        }

        override suspend fun setEnabled(groupId: GroupId, enabled: Boolean): SaqzResult<GroupWhatsAppBinding, CommunicationError> {
            enabledCalls += groupId to enabled
            val next = binding.copy(
                bound = true,
                status = if (enabled) GroupWhatsAppStatus.ACTIVE else GroupWhatsAppStatus.DISABLED,
            )
            return failure() ?: SaqzResult.Success(next)
        }

        private fun failure(): SaqzResult.Failure<CommunicationError>? =
            if (shouldFail) SaqzResult.Failure(CommunicationError(DataError.Connectivity)) else null
    }
}