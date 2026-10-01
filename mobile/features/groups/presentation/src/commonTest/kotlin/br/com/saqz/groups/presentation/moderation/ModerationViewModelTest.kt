package br.com.saqz.groups.presentation.moderation

import br.com.saqz.domain.DataError
import br.com.saqz.domain.EmptyResult
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.moderation.ContentReport
import br.com.saqz.groups.domain.moderation.ModerationError
import br.com.saqz.groups.domain.moderation.ReportReason
import br.com.saqz.groups.domain.moderation.ReportTargetType
import br.com.saqz.groups.presentation.FakeModerationGateway
import br.com.saqz.groups.presentation.blockedPerson
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    private val target = ReportTargetUi("group-1", ReportTargetType.MESSAGE, "message-1", "Bia")

    @Test fun reportNeedsAReasonAndSendsTheTrimmedDetails() = runTest {
        val gateway = FakeModerationGateway()
        val vm = ModerationViewModel(gateway, BlockedPeopleRepository(gateway))
        vm.onIntent(ModerationIntent.StartReport(target))
        assertFalse(vm.state.value.report!!.canSend)

        vm.onIntent(ModerationIntent.SubmitReport)
        assertTrue(gateway.reports.isEmpty())

        vm.onIntent(ModerationIntent.SelectReason(ReportReason.HARASSMENT))
        vm.onIntent(ModerationIntent.UpdateDetails("  ameaçou a galera  "))
        assertTrue(vm.state.value.report!!.canSend)
        vm.onIntent(ModerationIntent.SubmitReport)

        assertEquals(
            ContentReport(GroupId("group-1"), ReportTargetType.MESSAGE, "message-1", ReportReason.HARASSMENT, "ameaçou a galera"),
            gateway.reports.single(),
        )
        assertNull(vm.state.value.report)
        assertEquals(ModerationFeedback.ReportSent, vm.state.value.feedback)
        vm.onIntent(ModerationIntent.DismissFeedback)
        assertNull(vm.state.value.feedback)
    }

    @Test fun blankDetailsAreOmittedAndLongDetailsAreCapped() = runTest {
        val gateway = FakeModerationGateway()
        val vm = ModerationViewModel(gateway, BlockedPeopleRepository(gateway))
        vm.onIntent(ModerationIntent.StartReport(target))
        vm.onIntent(ModerationIntent.UpdateDetails("a".repeat(1200)))
        assertEquals(ContentReport.MAX_DETAILS_LENGTH, vm.state.value.report!!.details.length)
        vm.onIntent(ModerationIntent.UpdateDetails("   "))
        vm.onIntent(ModerationIntent.SelectReason(ReportReason.SPAM))
        vm.onIntent(ModerationIntent.SubmitReport)
        assertNull(gateway.reports.single().details)
    }

    @Test fun reportFailureKeepsTheSheetAndCannotBeDismissedWhileSending() = runTest {
        val pending = CompletableDeferred<EmptyResult<ModerationError>>()
        val gateway = FakeModerationGateway().apply { reportBlock = { pending.await() } }
        val vm = ModerationViewModel(gateway, BlockedPeopleRepository(gateway))
        vm.onIntent(ModerationIntent.StartReport(target))
        vm.onIntent(ModerationIntent.SelectReason(ReportReason.OFFENSIVE))
        vm.onIntent(ModerationIntent.SubmitReport)
        vm.onIntent(ModerationIntent.SubmitReport)
        vm.onIntent(ModerationIntent.DismissReport)
        vm.onIntent(ModerationIntent.SelectReason(ReportReason.SPAM))

        assertTrue(vm.state.value.report!!.sending)
        assertEquals(ReportReason.OFFENSIVE, vm.state.value.report!!.reason)
        assertEquals(1, gateway.reports.size)

        pending.complete(SaqzResult.Failure(ModerationError.DataFailure(DataError.Connectivity)))
        advanceUntilIdle()
        assertTrue(vm.state.value.report!!.failed)
        assertFalse(vm.state.value.report!!.sending)
        assertNull(vm.state.value.feedback)

        vm.onIntent(ModerationIntent.DismissReport)
        assertNull(vm.state.value.report)
    }

    @Test fun confirmingABlockUpdatesTheSharedListAndAnnouncesTheChange() = runTest {
        val gateway = FakeModerationGateway()
        val blocks = BlockedPeopleRepository(gateway)
        val changes = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { blocks.changes.collect { changes += it } }
        val vm = ModerationViewModel(gateway, blocks)

        vm.onIntent(ModerationIntent.StartBlock("group-1", "bia", "Bia"))
        assertEquals(BlockPromptUi("group-1", "bia", "Bia"), vm.state.value.block)
        assertTrue(gateway.blocks.isEmpty())

        vm.onIntent(ModerationIntent.ConfirmBlock)
        assertEquals(listOf("bia" to GroupId("group-1")), gateway.blocks)
        assertNull(vm.state.value.block)
        assertEquals(ModerationFeedback.Blocked, vm.state.value.feedback)
        assertEquals(setOf("bia"), blocks.blocked.value)
        assertEquals(1, changes.size)
    }

    @Test fun failedBlockStaysOpenWithTheErrorAndChangesNothing() = runTest {
        val gateway = FakeModerationGateway(blockResult = SaqzResult.Failure(ModerationError.DataFailure(DataError.Server)))
        val blocks = BlockedPeopleRepository(gateway)
        val vm = ModerationViewModel(gateway, blocks)

        vm.onIntent(ModerationIntent.StartBlock("group-1", "bia", "Bia"))
        vm.onIntent(ModerationIntent.ConfirmBlock)

        assertTrue(vm.state.value.block!!.failed)
        assertTrue(blocks.blocked.value.isEmpty())
        vm.onIntent(ModerationIntent.DismissBlock)
        assertNull(vm.state.value.block)
    }

    @Test fun unblockNeedsNoConfirmationAndReportsTheOutcome() = runTest {
        val gateway = FakeModerationGateway(blockedResult = SaqzResult.Success(listOf(blockedPerson("bia"))))
        val blocks = BlockedPeopleRepository(gateway).also { it.refresh() }
        val vm = ModerationViewModel(gateway, blocks)

        vm.onIntent(ModerationIntent.Unblock("bia"))
        assertEquals(listOf("bia"), gateway.unblocks)
        assertTrue(blocks.blocked.value.isEmpty())
        assertEquals(ModerationFeedback.Unblocked, vm.state.value.feedback)

        gateway.unblockResult = SaqzResult.Failure(ModerationError.DataFailure(DataError.Connectivity))
        vm.onIntent(ModerationIntent.Unblock("leo"))
        assertEquals(ModerationFeedback.UnblockFailed, vm.state.value.feedback)
    }

    @Test fun failedRefreshKeepsTheLastKnownList() = runTest {
        val gateway = FakeModerationGateway(blockedResult = SaqzResult.Success(listOf(blockedPerson("bia"))))
        val blocks = BlockedPeopleRepository(gateway)
        blocks.refresh()
        gateway.blockedResult = SaqzResult.Failure(ModerationError.DataFailure(DataError.Connectivity))
        blocks.refresh()
        assertEquals(setOf("bia"), blocks.blocked.value)
    }
}
