package br.com.saqz.groups.presentation.positionpicker

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.PositionSummary
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeNativeNotifications
import br.com.saqz.groups.presentation.sampleAttendanceDetail
import br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PositionPickerViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun close() { Dispatchers.resetMain() }

    @Test fun profilePositionWithSpotsComesSelectedWithTheSuggestionBadge() = runTest {
        val vm = picker(profile = AthletePosition.CENTRAL)
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(AthletePosition.CENTRAL, state.selected)
        assertTrue(state.options.single { it.position == AthletePosition.CENTRAL }.suggested)
        // Posição sem vaga configurada (líbero 0) não aparece.
        assertTrue(state.options.none { it.position == AthletePosition.LIBERO })
        assertEquals("Vôlei do CERET", state.game?.groupName)
        assertEquals(120, state.game?.durationMinutes)
    }

    @Test fun fullProfilePositionOrNoProfileLeavesNothingSelected() = runTest {
        val full = picker(profile = AthletePosition.PONTA).state.value
        assertNull(full.selected)
        assertTrue(full.options.single { it.position == AthletePosition.PONTA }.suggested)
        assertFalse(full.canSubmit)
        val none = picker(profile = null).state.value
        assertNull(none.selected)
        assertTrue(none.options.none { it.suggested })
    }

    @Test fun pickingAFullPositionMarksItAndTheSubmitJoinsItsWaitlist() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(detail()))
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL)
        vm.onIntent(PositionPickerIntent.Select(AthletePosition.PONTA))
        val chosen = vm.state.value.selectedOption!!
        assertTrue(chosen.full)
        assertEquals(2, chosen.queue)
        assertFalse(vm.state.value.allFull)
        assertEquals(listOf(AthletePosition.CENTRAL, AthletePosition.OPOSTO), vm.state.value.openPositions)
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(AthletePosition.PONTA, attendance.lastAttendanceCommand?.position)
        assertEquals(AttendanceIntent.Confirm, attendance.lastAttendanceCommand?.intent)
    }

    @Test fun allPositionsFull() = runTest {
        val vm = picker(detail = detail(centralFull = true, opostoFull = true), profile = AthletePosition.CENTRAL)
        assertTrue(vm.state.value.allFull)
        assertNull(vm.state.value.selected)
    }

    @Test fun submitSendsThePositionAndClosesFromTheHome() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(detail()))
        val notifications = FakeNativeNotifications()
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL, notifications = notifications)
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(AthletePosition.CENTRAL, attendance.lastAttendanceCommand?.position)
        assertEquals(2, attendance.readCalls)
        assertEquals(PositionPickerEffect.Close, vm.effects.first())
        assertEquals(listOf("game-1"), notifications.dismissed)
    }

    @Test fun rereadThatFindsThePositionFullDoesNotSendAndExplains() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(detail(centralFull = true)))
        attendance.detailResults = mutableListOf(SaqzResult.Success(detail()))
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL)
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(0, attendance.respondCalls)
        val state = vm.state.value
        assertEquals(AthletePosition.CENTRAL, state.raced)
        assertFalse(state.sending)
        assertTrue(state.selectedOption!!.full)
        // O segundo toque é a escolha consciente da espera: agora envia.
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(1, attendance.respondCalls)
    }

    @Test fun serverWaitlistIsAResultNotAnError() = runTest {
        val waitlisted = sampleVersionedAttendanceMutation().let {
            it.copy(value = it.value.copy(attendance = AttendanceEntry("me", AttendanceStatus.Waitlisted, 1, 1, AthletePosition.CENTRAL)))
        }
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(detail()), respondResult = SaqzResult.Success(waitlisted))
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL, origin = PositionPickerOrigin.Link)
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(PositionPickerEffect.LinkResult(AttendanceStatus.Waitlisted), vm.effects.first())
        assertFalse(vm.state.value.submitFailed)
    }

    @Test fun failureShowsTheErrorInPlaceAndRetriesWithTheSameRequestId() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(detail()),
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)),
        )
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL)
        vm.onIntent(PositionPickerIntent.Submit)
        assertTrue(vm.state.value.submitFailed)
        assertFalse(vm.state.value.sending)
        val first = attendance.lastAttendanceCommand!!.requestId
        attendance.respondResult = SaqzResult.Success(sampleVersionedAttendanceMutation())
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(first, attendance.lastAttendanceCommand!!.requestId)
        assertFalse(vm.state.value.submitFailed)
    }

    @Test fun loadFailureOffersRetry() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity)))
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL)
        assertTrue(vm.state.value.loadError != null)
        attendance.detailResult = SaqzResult.Success(detail())
        vm.onIntent(PositionPickerIntent.Retry)
        assertNull(vm.state.value.loadError)
        assertEquals(AthletePosition.CENTRAL, vm.state.value.selected)
    }

    @Test fun sendingBlocksTouches() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(detail()))
        attendance.respondDeferred = CompletableDeferred()
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL)
        vm.onIntent(PositionPickerIntent.Submit)
        assertTrue(vm.state.value.sending)
        vm.onIntent(PositionPickerIntent.Select(AthletePosition.OPOSTO))
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(AthletePosition.CENTRAL, vm.state.value.selected)
        assertEquals(1, attendance.respondCalls)
        attendance.respondDeferred?.complete(SaqzResult.Success(sampleVersionedAttendanceMutation()))
    }

    @Test fun effectsFollowTheOrigin() = runTest {
        for ((origin, expected) in listOf(
            PositionPickerOrigin.Group to PositionPickerEffect.Close,
            PositionPickerOrigin.Game to PositionPickerEffect.Close,
            PositionPickerOrigin.Link to PositionPickerEffect.LinkResult(AttendanceStatus.Confirmed),
            PositionPickerOrigin.Push to PositionPickerEffect.OpenGame("group-1", "game-1"),
        )) {
            val vm = picker(profile = AthletePosition.CENTRAL, origin = origin)
            vm.onIntent(PositionPickerIntent.Submit)
            assertEquals(expected, vm.effects.first(), "origem $origin")
        }
        assertEquals(PositionPickerOrigin.Push, PositionPickerOrigin.from("push"))
    }

    @Test fun readWithoutPositionsConfirmsLikeAGameWithoutPositions() = runTest {
        val attendance = FakeAttendanceGateway(detailResult = SaqzResult.Success(sampleAttendanceDetail()))
        val vm = picker(attendance = attendance, profile = AthletePosition.CENTRAL)
        assertTrue(vm.state.value.withoutPositions)
        assertTrue(vm.state.value.canSubmit)
        vm.onIntent(PositionPickerIntent.Submit)
        assertEquals(1, attendance.respondCalls)
        assertNull(attendance.lastAttendanceCommand?.position)
        assertEquals(PositionPickerEffect.Close, vm.effects.first())
    }

    @Test fun survivesRecreationWithThePickedPosition() = runTest {
        val saved = SavedStateHandle()
        val first = picker(profile = AthletePosition.CENTRAL, savedState = saved)
        first.onIntent(PositionPickerIntent.Select(AthletePosition.OPOSTO))
        val recreated = picker(profile = AthletePosition.CENTRAL, savedState = saved)
        assertEquals(AthletePosition.OPOSTO, recreated.state.value.selected)
    }

    private fun picker(
        attendance: FakeAttendanceGateway? = null,
        detail: br.com.saqz.groups.domain.attendance.AttendanceDetail = detail(),
        profile: AthletePosition?,
        origin: PositionPickerOrigin = PositionPickerOrigin.Home,
        savedState: SavedStateHandle = SavedStateHandle(),
        notifications: FakeNativeNotifications? = null,
    ) = PositionPickerViewModel(
        groupId = "group-1",
        gameId = "game-1",
        origin = origin,
        savedState = savedState,
        attendanceGateway = attendance ?: FakeAttendanceGateway(detailResult = SaqzResult.Success(detail)),
        gameGateway = FakeGameGateway(),
        athleteGateway = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                OwnAthleteProfile(
                    "me",
                    "Bruno",
                    null,
                    listOf(
                        OwnAthleteMembership(
                            GroupId("group-1"),
                            "Vôlei do CERET",
                            GroupRole.ATHLETE,
                            profile,
                            AthleteMembershipType.MENSALISTA,
                            true,
                        ),
                    ),
                ),
            ),
        ),
        notifications = notifications,
    )
}

/** O quadro `Main`: ponteiro(a) 4/4 com 1 na espera, central 3/4, oposto(a) 1/2, levantador(a) 2/2, líbero 0. */
internal fun detail(centralFull: Boolean = false, opostoFull: Boolean = false) = sampleAttendanceDetail().copy(
    positions = listOf(
        PositionSummary(AthletePosition.LIBERO, 0, 0, 0, 0),
        PositionSummary(AthletePosition.PONTA, 4, 4, 0, 1),
        PositionSummary(AthletePosition.CENTRAL, 4, if (centralFull) 4 else 3, if (centralFull) 0 else 1, 0),
        PositionSummary(AthletePosition.OPOSTO, 2, if (opostoFull) 2 else 1, if (opostoFull) 0 else 1, 0),
        PositionSummary(AthletePosition.LEVANTADOR, 2, 2, 0, 0),
    ),
)
