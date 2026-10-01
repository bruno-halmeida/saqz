package br.com.saqz.groups.presentation.communication

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.CommunicationPage
import br.com.saqz.groups.domain.communication.InAppNotification
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeCommunicationGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.fakeBlocks
import br.com.saqz.groups.presentation.moderation.BlockedPeopleRepository
import br.com.saqz.groups.presentation.sampleCommunicationMessage
import br.com.saqz.groups.presentation.sampleGroup
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadModerationTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    @Test fun objectionableNoticeShowsTheSpecificErrorUntilTheDraftChanges() = runTest {
        val gateway = FakeCommunicationGateway().apply {
            publishResult = SaqzResult.Failure(
                CommunicationError(DataError.Validation(ValidationDetails(emptyList(), mapOf("body" to listOf("objectionable"))))),
            )
        }
        val vm = thread(gateway, notices = true)
        vm.onIntent(GroupThreadIntent.Draft("palavrão"))
        vm.onIntent(GroupThreadIntent.Send)

        assertTrue(vm.state.value.sendRejected)
        assertFalse(vm.state.value.sendFailed)
        assertEquals("palavrão", vm.state.value.draft)

        vm.onIntent(GroupThreadIntent.Draft("texto revisado"))
        assertFalse(vm.state.value.sendRejected)
    }

    @Test fun otherValidationFailuresKeepTheGenericError() = runTest {
        val gateway = FakeCommunicationGateway().apply {
            publishResult = SaqzResult.Failure(
                CommunicationError(DataError.Validation(ValidationDetails(emptyList(), mapOf("body" to listOf("too_long"))))),
            )
        }
        val vm = thread(gateway, notices = true)
        vm.onIntent(GroupThreadIntent.Draft("aviso"))
        vm.onIntent(GroupThreadIntent.Send)

        assertTrue(vm.state.value.sendFailed)
        assertFalse(vm.state.value.sendRejected)
    }

    @Test fun someoneElsesNoticeOpensReportAndBlockButOwnDoesNot() = runTest {
        val gateway = FakeCommunicationGateway().apply {
            messagesResult = SaqzResult.Success(
                CommunicationPage(
                    listOf(
                        sampleCommunicationMessage().copy(id = "mine", authorId = "me"),
                        sampleCommunicationMessage().copy(id = "hers", authorId = "bia", authorName = "Bia"),
                    ),
                    null,
                ),
            )
        }
        val vm = thread(gateway, notices = true)
        assertEquals(listOf(true, false), vm.state.value.messages.map { it.own })

        vm.onIntent(GroupThreadIntent.OpenMessageActions("mine"))
        assertNull(vm.state.value.actionsFor)

        vm.onIntent(GroupThreadIntent.OpenMessageActions("hers"))
        assertEquals("hers", vm.state.value.actionsFor?.id)
        vm.onIntent(GroupThreadIntent.ReportMessage)
        assertEquals(GroupThreadEffect.ReportMessage("group-1", "hers", "Bia"), vm.effects.first())
        assertNull(vm.state.value.actionsFor)

        vm.onIntent(GroupThreadIntent.OpenMessageActions("hers"))
        vm.onIntent(GroupThreadIntent.BlockAuthor)
        assertEquals(GroupThreadEffect.BlockAuthor("group-1", "bia", "Bia"), vm.effects.first())
    }

    @Test fun blockingSomeoneReloadsTheThreadSoTheirNoticesDisappear() = runTest {
        val gateway = FakeCommunicationGateway().apply {
            messagesResult = SaqzResult.Success(
                CommunicationPage(listOf(sampleCommunicationMessage().copy(id = "hers", authorId = "bia")), null),
            )
        }
        val blocks = fakeBlocks()
        val vm = thread(gateway, notices = true, blocks = blocks)
        assertEquals(listOf("hers"), vm.state.value.messages.map { it.id })

        gateway.messagesResult = SaqzResult.Success(CommunicationPage(emptyList(), null))
        blocks.block("bia", GroupId("group-1"))

        assertTrue(vm.state.value.messages.isEmpty())
        assertEquals(listOf<Long?>(null, null), gateway.cursors)
    }

    @Test fun notificationCenterReloadsAfterABlock() = runTest {
        val message = sampleCommunicationMessage().copy(channel = CommunicationChannel.NOTICE, authorId = "bia")
        val gateway = FakeCommunicationGateway().apply {
            inboxResult = SaqzResult.Success(CommunicationPage(listOf(InAppNotification(7, message, false)), null))
        }
        val blocks = fakeBlocks()
        val vm = NotificationCenterViewModel(false, gateway, blocks)
        assertEquals(1, vm.state.value.items.size)

        gateway.inboxResult = SaqzResult.Success(CommunicationPage(emptyList(), null))
        blocks.block("bia", GroupId("group-1"))

        assertTrue(vm.state.value.items.isEmpty())
    }

    private fun thread(
        gateway: FakeCommunicationGateway,
        notices: Boolean = false,
        blocks: BlockedPeopleRepository = fakeBlocks(),
    ) = GroupThreadViewModel(
        "group-1", notices, SavedStateHandle(), gateway,
        FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(sampleGroup(role = GroupRole.ADMIN)))),
        FakeAthleteGateway(), blocks,
    )
}
