package br.com.saqz.groups.presentation.communication

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.CommunicationMessage
import br.com.saqz.groups.domain.communication.CommunicationPage
import br.com.saqz.groups.domain.communication.InAppNotification
import br.com.saqz.groups.domain.communication.NotificationPreferences
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeCommunicationGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.sampleCommunicationMessage
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    @Test fun athleteReadsNoticesButCannotPublishAndCanSendChat() = runTest {
        val gateway = FakeCommunicationGateway()
        val notices = thread(gateway, notices = true, role = GroupRole.ATHLETE)
        notices.onIntent(GroupThreadIntent.Draft("Aviso"))
        notices.onIntent(GroupThreadIntent.Send)
        assertFalse(notices.state.value.canPost)
        assertTrue(gateway.publications.isEmpty())
        val chat = thread(gateway, role = GroupRole.ATHLETE)
        chat.onIntent(GroupThreadIntent.Draft("Vamos jogar?"))
        chat.onIntent(GroupThreadIntent.Send)
        assertEquals(CommunicationChannel.CHAT, gateway.publications.single().channel)
        assertEquals(GroupId("group-1"), gateway.publications.single().group)
        assertEquals("Vamos jogar?", gateway.publications.single().body)
        assertEquals("", chat.state.value.draft)
        assertEquals("message-1", chat.state.value.messages.single().id)
    }
    @Test fun inFlightSendIsSingleAndFailedRetryKeepsBodyAndRequestIdAcrossRestoration() = runTest {
        val pending = CompletableDeferred<SaqzResult<CommunicationMessage, CommunicationError>>()
        val gateway = FakeCommunicationGateway().apply { publishBlock = { pending.await() } }
        val saved = SavedStateHandle()
        val vm = thread(gateway, saved = saved)
        vm.onIntent(GroupThreadIntent.Draft("  Mensagem  "))
        vm.onIntent(GroupThreadIntent.Send)
        vm.onIntent(GroupThreadIntent.Send)
        vm.onIntent(GroupThreadIntent.Draft("não deve mudar"))
        assertTrue(vm.state.value.sending)
        assertEquals(1, gateway.publications.size)
        pending.complete(SaqzResult.Failure(CommunicationError(DataError.Timeout)))
        advanceUntilIdle()
        assertTrue(vm.state.value.sendFailed)
        assertEquals("  Mensagem  ", vm.state.value.draft)
        assertTrue(vm.state.value.messages.isEmpty())
        gateway.publishBlock = null
        val restored = thread(gateway, saved = saved)
        restored.onIntent(GroupThreadIntent.Send)
        assertEquals(gateway.publications[0], gateway.publications[1])
        assertNotNull(gateway.publications[0].requestId)
        assertFalse(restored.state.value.sendFailed)
        assertEquals("", restored.state.value.draft)
    }
    @Test fun paginationKeepsPreviousMessagesAndFailureExposesRetry() = runTest {
        val gateway = FakeCommunicationGateway().apply { messagesResult = SaqzResult.Success(CommunicationPage(listOf(sampleCommunicationMessage()), 8)) }
        val vm = thread(gateway)
        gateway.messagesResult = SaqzResult.Failure(CommunicationError(DataError.Connectivity))
        vm.onIntent(GroupThreadIntent.More)
        assertTrue(vm.state.value.pageFailed)
        assertEquals(1, vm.state.value.messages.size)
        gateway.messagesResult = SaqzResult.Success(CommunicationPage(listOf(sampleCommunicationMessage().copy(id = "older")), null))
        vm.onIntent(GroupThreadIntent.More)
        assertEquals(listOf("message-1", "older"), vm.state.value.messages.map { it.id })
        assertEquals(listOf<Long?>(null, 8, 8), gateway.cursors)
    }
    @Test fun preferencesAreSavedOnlyAfterSuccessAndFailedRequestRetainsDraft() = runTest {
        val gateway = FakeCommunicationGateway().apply { saveResult = SaqzResult.Failure(CommunicationError(DataError.Connectivity)) }
        val vm = NotificationCenterViewModel(true, gateway)
        val draft = NotificationPreferences(false, true, false)
        vm.onIntent(NotificationCenterIntent.Preferences(draft))
        vm.onIntent(NotificationCenterIntent.Save)
        assertTrue(vm.state.value.actionFailed)
        assertFalse(vm.state.value.saved)
        assertEquals(draft, vm.state.value.preferences)
        gateway.saveResult = null
        vm.onIntent(NotificationCenterIntent.Save)
        assertEquals(listOf(draft, draft), gateway.preferences)
        assertTrue(vm.state.value.saved)
        assertFalse(vm.state.value.actionFailed)
    }
    @Test fun openingNotificationMarksOnlySelectedItemAndDoesNotNavigateOnFailure() = runTest {
        val message = sampleCommunicationMessage().copy(channel = CommunicationChannel.REMINDER, gameId = "game-1")
        val gateway = FakeCommunicationGateway().apply {
            inboxResult = SaqzResult.Success(CommunicationPage(listOf(InAppNotification(7, message, false), InAppNotification(8, message, false)), null))
            readResult = SaqzResult.Failure(CommunicationError(DataError.Connectivity))
        }
        val vm = NotificationCenterViewModel(false, gateway)
        val effects = mutableListOf<NotificationCenterEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        vm.onIntent(NotificationCenterIntent.Open(7))
        assertTrue(effects.isEmpty())
        assertTrue(vm.state.value.items.none { it.read })
        gateway.readResult = SaqzResult.Success(Unit)
        vm.onIntent(NotificationCenterIntent.Open(7))
        assertEquals(listOf(7L, 7L), gateway.reads)
        assertEquals(listOf<NotificationCenterEffect>(NotificationCenterEffect.Open("group-1", CommunicationChannel.REMINDER, "game-1")), effects)
        assertTrue(vm.state.value.items.first().read)
        assertFalse(vm.state.value.items.last().read)
    }
    private fun thread(gateway: FakeCommunicationGateway, notices: Boolean = false, role: GroupRole = GroupRole.ADMIN, saved: SavedStateHandle = SavedStateHandle()) =
        GroupThreadViewModel("group-1", notices, saved, gateway, FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = role)))))
}
