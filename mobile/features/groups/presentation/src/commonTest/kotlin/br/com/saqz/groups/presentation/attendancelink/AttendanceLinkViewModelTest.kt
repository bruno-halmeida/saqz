package br.com.saqz.groups.presentation.attendancelink

import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.*
import br.com.saqz.groups.domain.athlete.EntryRequestAthleteProfile
import br.com.saqz.groups.domain.athlete.OwnEntryRequest
import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.attendance.share.*
import br.com.saqz.groups.domain.membership.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AttendanceLinkViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun close() { Dispatchers.resetMain() }

    @Test fun linkConfirmsTheAuthenticatedUserAndShowsWaitlistInsteadOfFalseConfirmation() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(destination, vm.state.value.destination)
        assertEquals(listOf(destination), gateway.targets)
        assertEquals(AttendanceIntent.Confirm, gateway.commands.single().intent)
        gateway.status = AttendanceStatus.Waitlisted
        val waitlisted = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Waitlisted, waitlisted.state.value.phase)
        assertEquals(gateway.commands.first().requestId, gateway.commands.last().requestId)
        assertEquals("00000000-0000-0000-0000-000000000000", gateway.commands.first().requestId)
    }

    @Test fun confirmThenDeclineThenConfirmAgainByLinkShowsTheWaitlistTheServerReturnedNotAConfirmation() = runTest {
        val gateway = AttendanceFake()
        assertEquals(AttendanceLinkPhase.Confirmed, AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake()).state.value.phase)
        gateway.status = AttendanceStatus.Declined
        val declined = AttendanceLinkViewModel(code, true, SharingFake(), gateway, InviteFake())
        declined.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(AttendanceLinkPhase.Declined, declined.state.value.phase)
        // Prazo vencido ou jogo cheio: o servidor devolve a espera no segundo "Vou".
        gateway.status = AttendanceStatus.Waitlisted
        val again = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Waitlisted, again.state.value.phase)
        assertNotEquals(AttendanceLinkPhase.Confirmed, again.state.value.phase)
        // Com vaga, o mesmo caminho confirma de verdade.
        gateway.status = AttendanceStatus.Confirmed
        assertEquals(AttendanceLinkPhase.Confirmed, AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake()).state.value.phase)
    }

    @Test fun undoAfterTheLinkConfirmedRecordsTheDeclineWithItsOwnRequestId() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(declineCode, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        gateway.status = AttendanceStatus.Declined
        vm.onIntent(AttendanceLinkIntent.Undo)
        assertEquals(AttendanceLinkPhase.Declined, vm.state.value.phase)
        assertEquals(destination, vm.state.value.destination)
        assertEquals(AttendanceIntent.Decline, gateway.commands.last().intent)
        assertNotEquals(gateway.commands.first().requestId, gateway.commands.last().requestId)
        // Depois de desfeito, outro toque não grava nada.
        vm.onIntent(AttendanceLinkIntent.Undo)
        assertEquals(2, gateway.commands.size)
    }

    @Test fun undoFailureKeepsTheConfirmationAndAllowsAnotherTry() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        gateway.error = AttendanceError.Data(DataError.Connectivity)
        vm.onIntent(AttendanceLinkIntent.Undo)
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(false, vm.state.value.declining)
        gateway.error = null
        gateway.status = AttendanceStatus.Declined
        vm.onIntent(AttendanceLinkIntent.Undo)
        assertEquals(AttendanceLinkPhase.Declined, vm.state.value.phase)
    }

    @Test fun undoOnlyExistsAfterAnAnswer() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, true, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        vm.onIntent(AttendanceLinkIntent.Undo)
        assertEquals(emptyList(), gateway.commands)
    }

    @Test fun repeatedConfirmRequestShowsWhateverTheServerReturnsAndNeverAssumesConfirmed() = runTest {
        val gateway = AttendanceFake().apply { status = AttendanceStatus.Waitlisted }
        val first = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Waitlisted, first.state.value.phase)
        // Repetição do mesmo requestId: o servidor responde o estado atual, e é ele que a tela mostra.
        val repeated = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Waitlisted, repeated.state.value.phase)
        assertEquals(gateway.commands.first().requestId, gateway.commands.last().requestId)
        // Se o último estado já é "não vou", o "Vou" repetido não vira confirmação.
        gateway.status = AttendanceStatus.Declined
        val afterDecline = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Failed, afterDecline.state.value.phase)
    }

    @Test fun recordedLinkAnswerReportsAttendanceAnsweredFromTheLinkSurfaceAndAFailureDoesNot() = runTest {
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        SaqzAnalytics.track = { name, params -> recorded += name to params }
        try {
            AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake())
            AttendanceLinkViewModel(code, false, SharingFake(),
                AttendanceFake().apply { error = AttendanceError.Data(DataError.Connectivity) }, InviteFake())
        } finally {
            SaqzAnalytics.reset()
        }
        assertEquals(
            listOf("attendance_answered" to mapOf("surface" to "link", "answer" to "confirm")),
            recorded.filter { it.first == "attendance_answered" },
        )
    }

    @Test fun recordedLinkAnswerDismissesTheAttendanceWindowAndAFailureKeepsIt() = runTest {
        val notifications = br.com.saqz.groups.presentation.FakeNativeNotifications()
        AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake(), notifications)
        AttendanceLinkViewModel(code, false, SharingFake(),
            AttendanceFake().apply { error = AttendanceError.Data(DataError.Connectivity) }, InviteFake(), notifications)
        assertEquals(1, notifications.dismissed.size)
    }

    @Test fun expiredLinkCannotSubmitAttendanceAndDeadlineFailureIsNotSuccess() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Invalid, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
        gateway.error = AttendanceError.DeadlinePassed
        val closed = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Invalid, closed.state.value.phase)
        assertNull(closed.state.value.destination)
    }

    @Test fun organizerPlanPausedShowsTheNeutralPhaseInsteadOfAFailureToRetry() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(
            code, false, SharingFake(expired = true), gateway, InviteFake(failure = InviteError.GroupNotReceiving),
        )
        assertEquals(AttendanceLinkPhase.NotReceiving, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
        vm.onIntent(AttendanceLinkIntent.Retry)
        assertEquals(AttendanceLinkPhase.NotReceiving, vm.state.value.phase)
    }

    @Test fun eachClosedReasonShowsItsOwnPhaseWithoutTryingToJoinOrAnswer() = runTest {
        mapOf(
            AttendanceLinkClosedReason.GameStarted to AttendanceLinkPhase.GameStarted,
            AttendanceLinkClosedReason.GameCancelled to AttendanceLinkPhase.GameCancelled,
            AttendanceLinkClosedReason.GameUnpublished to AttendanceLinkPhase.GameUnpublished,
        ).forEach { (reason, phase) ->
            val gateway = AttendanceFake()
            val invites = InviteFake(InviteRedeemStatus.JOINED)
            val vm = AttendanceLinkViewModel(code, false, SharingFake(closed = reason), gateway, invites)
            assertEquals(phase, vm.state.value.phase, reason.name)
            assertNull(vm.state.value.destination)
            assertTrue(gateway.commands.isEmpty())
            assertTrue(invites.codes.isEmpty())
        }
    }

    @Test fun errorWithoutReasonKeepsTheGenericInvalidMessageAndStillTriesTheInvite() = runTest {
        val invites = InviteFake()
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true), AttendanceFake(), invites)
        assertEquals(AttendanceLinkPhase.Invalid, vm.state.value.phase)
        assertEquals(listOf(InviteCode(code)), invites.codes)
    }

    @Test fun closedReasonAfterJoiningTheGroupShowsItsOwnPhase() = runTest {
        val vm = AttendanceLinkViewModel(code, false,
            SharingFake(expired = true, firstOnly = true, closed = AttendanceLinkClosedReason.GameCancelled),
            AttendanceFake(), InviteFake(InviteRedeemStatus.JOINED))
        assertEquals(AttendanceLinkPhase.GameCancelled, vm.state.value.phase)
    }

    @Test fun closedReasonWinsOverANotReceivingGroupAndNotReceivingStillWorksWithoutReason() = runTest {
        val invites = InviteFake(failure = InviteError.GroupNotReceiving)
        val closed = AttendanceLinkViewModel(code, false,
            SharingFake(closed = AttendanceLinkClosedReason.GameStarted), AttendanceFake(), invites)
        assertEquals(AttendanceLinkPhase.GameStarted, closed.state.value.phase)
        assertTrue(invites.codes.isEmpty())
        val notReceiving = AttendanceLinkViewModel(code, false, SharingFake(expired = true), AttendanceFake(), invites)
        assertEquals(AttendanceLinkPhase.NotReceiving, notReceiving.state.value.phase)
    }

    @Test fun retryPreservesRequestIdentityAndSuccessfulStateDoesNotResubmit() = runTest {
        val gateway = AttendanceFake().apply { error = AttendanceError.Data(DataError.Connectivity) }
        val vm = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Failed, vm.state.value.phase)
        gateway.error = null
        vm.retry()
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(1, gateway.commands.map { it.requestId }.distinct().size)
        vm.retry()
        assertEquals(2, gateway.commands.size)
    }

    @Test fun newcomerWithoutPendingRegistrationJoinsAndIsConfirmedStraightAway() = runTest {
        val gateway = AttendanceFake()
        val invite = InviteFake(InviteRedeemStatus.JOINED)
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true, firstOnly = true), gateway, invite)
        assertEquals(listOf(InviteCode(code)), invite.codes)
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(AttendanceIntent.Confirm, gateway.commands.single().intent)
    }

    @Test fun newcomerWhoStillNeedsTheRegistrationGoesThereBeforeAnyAttendanceWrite() = runTest {
        val gateway = AttendanceFake()
        val invite = InviteFake(InviteRedeemStatus.JOINED)
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true, firstOnly = true, registration = true), gateway, invite)
        assertEquals(AttendanceLinkPhase.Registration, vm.state.value.phase)
        assertEquals(AttendanceLinkEffect.Register("group"), vm.effects.first())
        assertTrue(gateway.commands.isEmpty())
        val incomplete = AttendanceLinkViewModel(code, false, SharingFake(registration = true), gateway, InviteFake())
        assertEquals(AttendanceLinkEffect.Register("group"), incomplete.effects.first())
        assertTrue(gateway.commands.isEmpty())
    }

    @Test fun newcomerOfADeclineLinkSeesTheWarningInsteadOfBeingConfirmed() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(expired = true, firstOnly = true), gateway, InviteFake(InviteRedeemStatus.JOINED))
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
    }

    @Test fun approvalRequiredDoesNotSkipMembershipAndRegistrationGates() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true), gateway, InviteFake(InviteRedeemStatus.PENDING))
        assertEquals(AttendanceLinkPhase.Pending, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
    }

    @Test fun declineLinkWarnsBeforeWritingAndCarriesItsOwnRequestId() = runTest {
        val gateway = AttendanceFake().apply { status = AttendanceStatus.Declined }
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake())
        // O link do "não vou" não escreve nada: abre o aviso e espera o toque.
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        assertEquals(destination, vm.state.value.destination)
        assertTrue(gateway.commands.isEmpty())
        vm.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(AttendanceLinkPhase.Declined, vm.state.value.phase)
        assertEquals(AttendanceIntent.Decline, gateway.commands.single().intent)
        // Confirmar e declinar o mesmo código são comandos distintos: requestId distinto.
        assertEquals("737a8188-8f96-9da4-abb2-b9c0c7ced5dc", gateway.commands.single().requestId)
        gateway.status = AttendanceStatus.Confirmed
        val confirm = AttendanceLinkViewModel(declineCode, false, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.Confirmed, confirm.state.value.phase)
        assertEquals("030a1118-1f26-2d34-3b42-4950575e656c", gateway.commands.last().requestId)
    }

    @Test fun declinePastTheDeadlineIsInvalidAndNeverFakesTheAnswer() = runTest {
        val gateway = AttendanceFake().apply { error = AttendanceError.DeadlinePassed }
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake())
        vm.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(AttendanceLinkPhase.Invalid, vm.state.value.phase)
        assertEquals(AttendanceIntent.Decline, gateway.commands.single().intent)
    }

    @Test fun declineGoesThroughRegistrationBeforeShowingTheWarning() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(registration = true), gateway, InviteFake())
        assertEquals(AttendanceLinkEffect.Register("group"), vm.effects.first())
        assertEquals(AttendanceLinkPhase.Registration, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
    }

    @Test fun declineOnlyReactsToTheWarningIntent() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake())
        vm.onIntent(AttendanceLinkIntent.Retry)
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
        vm.onIntent(AttendanceLinkIntent.Decline)
        vm.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(1, gateway.commands.size)
    }

    @Test fun secondDeclineWhileTheFirstIsInFlightIsIgnoredAndTheSheetStaysLockedUntilTheAnswer() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = AttendanceFake().apply { status = AttendanceStatus.Declined; hold = gate }
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake())
        vm.onIntent(AttendanceLinkIntent.Decline)
        vm.onIntent(AttendanceLinkIntent.Decline)
        vm.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(1, gateway.commands.size)
        assertTrue(vm.state.value.declining)
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        gate.complete(Unit)
        assertEquals(AttendanceLinkPhase.Declined, vm.state.value.phase)
        assertFalse(vm.state.value.declining)
        assertEquals(1, gateway.commands.size)
    }

    @Test fun declineFailureReleasesTheSheetToTryAgainWithTheSameRequestId() = runTest {
        val gateway = AttendanceFake().apply { error = AttendanceError.Data(DataError.Connectivity) }
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake())
        vm.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(AttendanceLinkPhase.Failed, vm.state.value.phase)
        assertFalse(vm.state.value.declining)
        vm.onIntent(AttendanceLinkIntent.Retry)
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        vm.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(2, gateway.commands.size)
        assertEquals(gateway.commands.first().requestId, gateway.commands.last().requestId)
    }

    @Test fun declineLinkOpensTheSheetWithoutWritingAndLeavingItWritesNothing() = runTest {
        val gateway = AttendanceFake()
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake())
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        // Fechar a folha (alça, scrim, voltar) é navegação da tela: não há intent que escreva.
        vm.onIntent(AttendanceLinkIntent.Retry)
        assertEquals(AttendanceLinkPhase.DeclineSheet, vm.state.value.phase)
        assertTrue(gateway.commands.isEmpty())
    }

    @Test fun declineServerFailureNeverReachesTheDeclinedPhase() = runTest {
        val gateway = AttendanceFake().apply { error = AttendanceError.Conflict }
        val vm = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake())
        vm.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(1, gateway.commands.size)
        assertEquals(AttendanceLinkPhase.Failed, vm.state.value.phase)
    }

    @Test fun appDownloadIsOfferedOnlyWhenThePortHasAStoreAndOpensIt() = runTest {
        val port = br.com.saqz.groups.presentation.FakeAppDownload()
        val offered = AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake(), appDownload = port)
        assertTrue(offered.appDownloadAvailable)
        offered.onIntent(AttendanceLinkIntent.OpenAppDownload)
        assertEquals(listOf("https://play.example/saqz"), port.opened)

        val noStore = br.com.saqz.groups.presentation.FakeAppDownload(url = null)
        val withoutStore = AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake(), appDownload = noStore)
        assertFalse(withoutStore.appDownloadAvailable)
        withoutStore.onIntent(AttendanceLinkIntent.OpenAppDownload)
        assertTrue(noStore.opened.isEmpty())

        val native = AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake())
        assertFalse(native.appDownloadAvailable)
        native.onIntent(AttendanceLinkIntent.OpenAppDownload)
    }

    @Test fun memberSeesTheGameCardFromTheGameDataAndKeepsItThroughTheDeclineSheetAndTheAnswer() = runTest {
        val games = br.com.saqz.groups.presentation.FakeGameGateway()
        val card = AttendanceLinkGame("TUESDAY", "04/08", "19h30", "CERET", null, capacity = 12, confirmedCount = 8)
        val confirmed = AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake(), games = games)
        assertEquals(AttendanceLinkPhase.Confirmed, confirmed.state.value.phase)
        assertEquals(card, confirmed.state.value.game)

        val declining = AttendanceLinkViewModel(declineCode, true, SharingFake(), AttendanceFake(), InviteFake(), games = games)
        assertEquals(AttendanceLinkPhase.DeclineSheet, declining.state.value.phase)
        assertEquals(card, declining.state.value.game)
        assertFalse(card.groupNext)
        val gateway = AttendanceFake().apply { status = AttendanceStatus.Declined }
        val answered = AttendanceLinkViewModel(declineCode, true, SharingFake(), gateway, InviteFake(), games = games)
        answered.onIntent(AttendanceLinkIntent.Decline)
        assertEquals(AttendanceLinkPhase.Declined, answered.state.value.phase)
        assertEquals(card, answered.state.value.game)
    }

    @Test fun failureReadingTheGameLeavesNoCardAndTheAnswerGoesOnAnyway() = runTest {
        val games = br.com.saqz.groups.presentation.FakeGameGateway(
            readResult = SaqzResult.Failure(br.com.saqz.groups.domain.game.GameError.Data(DataError.Connectivity)),
        )
        val vm = AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake(), games = games)
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertNull(vm.state.value.game)
    }

    @Test fun withoutTheGamePortThereIsNoCard() = runTest {
        assertNull(AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake()).state.value.game)
    }

    @Test fun pendingApprovalShowsTheCardFromThePreviewWithTheHiddenVenue() = runTest {
        val preview = InvitePreview(
            groupName = "Grupo", inviterName = "Ana", entryRequiresApproval = true,
            nextGame = InviteNextGame("2026-08-04T22:30:00Z", "Local após a aprovação", null),
        )
        val vm = AttendanceLinkViewModel(
            code, false, SharingFake(expired = true), AttendanceFake(),
            InviteFake(InviteRedeemStatus.PENDING, preview = preview),
            timeZonePort = br.com.saqz.groups.presentation.FakeGroupSystemTimeZonePort(),
        )
        assertEquals(AttendanceLinkPhase.Pending, vm.state.value.phase)
        assertEquals(
            AttendanceLinkGame("TUESDAY", "04/08", "19h30", "Local após a aprovação", null, groupNext = true),
            vm.state.value.game,
        )
    }

    @Test fun pendingApprovalWithoutAPreviewGameShowsNoCard() = runTest {
        val vm = AttendanceLinkViewModel(
            code, false, SharingFake(expired = true), AttendanceFake(),
            InviteFake(InviteRedeemStatus.PENDING, preview = InvitePreview("Grupo", "Ana", true)),
        )
        assertEquals(AttendanceLinkPhase.Pending, vm.state.value.phase)
        assertNull(vm.state.value.game)
    }

    @Test fun pendingKeepsGroupInviterAndWhetherTheRegistrationIsAlreadySaved() = runTest {
        val preview = InvitePreview("Vôlei da Quinta", "Ana Lima", true)
        val withoutProfile = AttendanceLinkViewModel(
            code, false, SharingFake(expired = true), AttendanceFake(),
            InviteFake(InviteRedeemStatus.PENDING, preview = preview), athletes = athletes(null),
        )
        assertEquals("group", withoutProfile.state.value.pendingGroupId)
        assertEquals("Vôlei da Quinta", withoutProfile.state.value.pendingGroupName)
        assertEquals("Ana Lima", withoutProfile.state.value.pendingInviterName)
        assertFalse(withoutProfile.state.value.pendingRegistrationSaved)
        val saved = AttendanceLinkViewModel(
            code, false, SharingFake(expired = true), AttendanceFake(),
            InviteFake(InviteRedeemStatus.PENDING, preview = preview), athletes = athletes(EntryRequestAthleteProfile(nickname = "Ana")),
        )
        assertTrue(saved.state.value.pendingRegistrationSaved)
        // Falha na leitura do pedido: a fase segue e o cadastro conta como não guardado.
        val failing = AttendanceLinkViewModel(
            code, false, SharingFake(expired = true), AttendanceFake(),
            InviteFake(InviteRedeemStatus.PENDING, preview = preview),
            athletes = br.com.saqz.groups.presentation.FakeAthleteGateway(),
        )
        assertEquals(AttendanceLinkPhase.Pending, failing.state.value.phase)
        assertFalse(failing.state.value.pendingRegistrationSaved)
    }

    @Test fun refreshOnPendingNeverGoesBackToLoadingAndUpdatesTheSavedRegistration() = runTest {
        val preview = InvitePreview("Vôlei da Quinta", "Ana Lima", true)
        val gate = CompletableDeferred<Unit>()
        val invites = InviteFake(InviteRedeemStatus.PENDING, preview = preview)
        val gateway = br.com.saqz.groups.presentation.FakeAthleteGateway(ownEntryRequestResult = entryRequest(null))
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true), AttendanceFake(), invites, athletes = gateway)
        val phases = mutableListOf<AttendanceLinkPhase>()
        val watcher = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { phases += it.phase } }
        gateway.ownEntryRequestResult = entryRequest(EntryRequestAthleteProfile(nickname = "Ana"))
        invites.hold = gate
        vm.onIntent(AttendanceLinkIntent.Refresh)
        assertTrue(vm.state.value.refreshing)
        assertEquals("Vôlei da Quinta", vm.state.value.pendingGroupName)
        // Outro toque com o primeiro em voo não refaz o pedido.
        vm.onIntent(AttendanceLinkIntent.Refresh)
        assertEquals(2, invites.codes.size)
        gate.complete(Unit)
        assertFalse(vm.state.value.refreshing)
        assertTrue(vm.state.value.pendingRegistrationSaved)
        assertEquals(AttendanceLinkPhase.Pending, vm.state.value.phase)
        assertFalse(AttendanceLinkPhase.Loading in phases)
        watcher.cancel()
    }

    @Test fun refreshOnceApprovedFollowsTheNormalFlowAndConfirms() = runTest {
        val gateway = AttendanceFake()
        val invites = InviteFake(InviteRedeemStatus.PENDING, preview = InvitePreview("Grupo", "Ana", true))
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true, firstOnly = true), gateway, invites)
        assertEquals(AttendanceLinkPhase.Pending, vm.state.value.phase)
        invites.status = InviteRedeemStatus.JOINED
        vm.onIntent(AttendanceLinkIntent.Refresh)
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(AttendanceIntent.Confirm, gateway.commands.single().intent)
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun refreshFailureKeepsThePendingScreenAndReleasesTheButton() = runTest {
        val invites = InviteFake(InviteRedeemStatus.PENDING, preview = InvitePreview("Grupo", "Ana", true))
        val vm = AttendanceLinkViewModel(code, false, SharingFake(expired = true), AttendanceFake(), invites)
        invites.status = null
        vm.onIntent(AttendanceLinkIntent.Refresh)
        assertEquals(AttendanceLinkPhase.Pending, vm.state.value.phase)
        assertFalse(vm.state.value.refreshing)
    }

    @Test fun refreshOutsideThePendingPhaseDoesNothing() = runTest {
        val gateway = AttendanceFake()
        val invites = InviteFake()
        val vm = AttendanceLinkViewModel(code, false, SharingFake(), gateway, invites)
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        vm.onIntent(AttendanceLinkIntent.Refresh)
        assertEquals(AttendanceLinkPhase.Confirmed, vm.state.value.phase)
        assertEquals(1, gateway.commands.size)
        assertTrue(invites.codes.isEmpty())
    }

    @Test fun waitlistPositionAndTheCardCountsReachTheState() = runTest {
        val gateway = AttendanceFake().apply { status = AttendanceStatus.Waitlisted; waitlistPosition = 3 }
        val vm = AttendanceLinkViewModel(code, false, SharingFake(), gateway, InviteFake(), games = br.com.saqz.groups.presentation.FakeGameGateway())
        assertEquals(AttendanceLinkPhase.Waitlisted, vm.state.value.phase)
        assertEquals(3L, vm.state.value.waitlistPosition)
        assertEquals(12, vm.state.value.game?.capacity)
        assertEquals(8, vm.state.value.game?.confirmedCount)
    }

    @Test fun theCardIsReloadedAfterTheAnswerSoTheCountIncludesThePerson() = runTest {
        val games = br.com.saqz.groups.presentation.FakeGameGateway()
        AttendanceLinkViewModel(code, false, SharingFake(), AttendanceFake(), InviteFake(), games = games)
        // Uma leitura antes da resposta (cartão) e outra depois dela (contagem).
        assertEquals(2, games.readCalls)
    }

    private fun entryRequest(profile: EntryRequestAthleteProfile?) = SaqzResult.Success(
        OwnEntryRequest(GroupId("group"), "Vôlei da Quinta", null, null, null, profile),
    )

    private fun athletes(profile: EntryRequestAthleteProfile?) =
        br.com.saqz.groups.presentation.FakeAthleteGateway(ownEntryRequestResult = entryRequest(profile))

    private class InviteFake(
        var status: InviteRedeemStatus? = null,
        private val failure: InviteError = InviteError.InvalidOrExpired("INVITE_INVALID"),
        private val preview: InvitePreview? = null,
    ) : InviteGateway {
        val codes = mutableListOf<InviteCode>()
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun preview(code: InviteCode): SaqzResult<InvitePreview, InviteError> =
            preview?.let { SaqzResult.Success(it) } ?: SaqzResult.Failure(InviteError.InvalidOrExpired("INVITE_INVALID"))
        override suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError> {
            codes += code
            hold?.await()
            val status = status
            return if (status == null) SaqzResult.Failure(failure)
            else SaqzResult.Success(InviteRedeem(status, GroupId("group"), "ATHLETE"))
        }
    }

    private class SharingFake(
        private val expired: Boolean = false, private val registration: Boolean = false, private val firstOnly: Boolean = false,
        private val closed: AttendanceLinkClosedReason? = null,
    ) : AttendanceSharingGateway {
        private var resolutions = 0
        override suspend fun resolveLink(code: AttendanceLinkCode): SaqzResult<AttendanceLinkDestination, AttendanceSharingError> {
            resolutions++
            val genericFirst = expired && firstOnly && resolutions == 1
            if (closed != null && !genericFirst) return SaqzResult.Failure(AttendanceSharingError.GameClosed(closed))
            return if (expired && (!firstOnly || resolutions == 1)) SaqzResult.Failure(AttendanceSharingError.InvalidOrExpired)
            else SaqzResult.Success(destination.copy(registrationRequired = registration))
        }
    }
    private class AttendanceFake : AttendanceGateway {
        val commands = mutableListOf<SelfAttendanceCommand>()
        val targets = mutableListOf<AttendanceLinkDestination>()
        var status = AttendanceStatus.Confirmed
        var waitlistPosition: Long? = null
        var error: AttendanceError? = null
        /** Segura a resposta até o teste liberar: é o que mantém a primeira chamada em voo. */
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun respond(groupId: GroupId, gameId: String, command: SelfAttendanceCommand):
            SaqzResult<VersionedAttendanceMutation, AttendanceError> {
            commands += command
            targets += AttendanceLinkDestination(groupId, gameId)
            hold?.await()
            error?.let { return SaqzResult.Failure(it) }
            return SaqzResult.Success(VersionedAttendanceMutation(
                AttendanceMutation(AttendanceEntry("logged-user", status, waitlistPosition = waitlistPosition, version = 1), promotedCount = 0,
                    detail = AttendanceDetail(confirmedCount = 1, availableSpots = 0, waitlistCount = 0, capacity = 1)),
                AttendanceVersionToken("1")))
        }
        override suspend fun read(groupId: GroupId, gameId: String) = error("unused")
        override suspend fun roster(groupId: GroupId, gameId: String) = error("unused")
        override suspend fun promote(groupId: GroupId, gameId: String, command: AttendancePromotionCommand) = error("unused")
        override suspend fun override(groupId: GroupId, gameId: String, command: OverrideAttendanceCommand) = error("unused")
        override suspend fun capacity(groupId: GroupId, gameId: String, version: AttendanceVersionToken, command: AttendanceCapacityCommand) =
            error("unused")
    }
    private companion object {
        val code = "A".repeat(43)
        /** 32 bytes distintos; confirmar usa a metade inicial e declinar a final. */
        val declineCode = "AwoRGB8mLTQ7QklQV15lbHN6gYiPlp2kq7K5wMfO1dw"
        val destination = AttendanceLinkDestination(GroupId("group"), "game")
    }
}
