package br.com.saqz.groups.presentation.home

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeAdminGroup
import br.com.saqz.groups.domain.home.HomeAdminReadModel
import br.com.saqz.groups.domain.home.HomeGameToSettle
import br.com.saqz.groups.domain.home.HomeMonthlyCharges
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeMemberGroup
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeNextGame
import br.com.saqz.groups.domain.home.HomeOwnAttendance
import br.com.saqz.groups.domain.home.HomeOwnChargeGroup
import br.com.saqz.groups.domain.home.HomeOwnChargeOldest
import br.com.saqz.groups.domain.home.HomeOwnCharges
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.domain.home.HomeRosterPreview
import br.com.saqz.groups.domain.home.HomeRosterMember
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val now = GroupNowPort { Instant.parse("2026-07-28T12:00:00Z") }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success keeps the home aggregate first name and formatted member copy`() = runTest {
        val home = sampleHome(nextGame = sampleNextGame())
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(home)))

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Bruna", viewModel.state.value.displayName)
        assertEquals(home, viewModel.state.value.home)
        assertEquals("Terça", viewModel.state.value.member?.subtitle?.substringBefore(" tem"))
        assertEquals("9 de 12 confirmados", viewModel.state.value.member?.nextGame?.confirmedSummary)
        assertTrue(viewModel.state.value.member?.nextGame?.deadline?.contains("hoje") == true)
        assertFalse(viewModel.state.value.loadFailed)
    }

    @Test
    fun `home without next game formats the empty week subtitle`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        assertEquals("Semana sem jogo por aqui.", viewModel.state.value.member?.subtitle)
        assertNull(viewModel.state.value.member?.nextGame)
    }

    @Test
    fun `admin aggregate formats pending items and marks administered groups`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        nextGame = sampleNextGame(),
                        role = GroupRole.OWNER,
                        admin = HomeAdminReadModel(
                            groups = listOf(
                                HomeAdminGroup(
                                    id = GroupId("group-1"),
                                    name = "Vôlei do CERET",
                                    entryRequestCount = 2,
                                    monthlyCharges = HomeMonthlyCharges(3, 64000, "2026-07"),
                                    gameToSettle = HomeGameToSettle("game-1", "2026-07-28T00:00:00Z", "America/Sao_Paulo", 4, 32000),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("1 grupos · 3 coisas esperando você", viewModel.state.value.member?.adminSubtitle)
        assertEquals("R$ 640,00", viewModel.state.value.member?.admin?.groups?.single()?.monthlyCharges?.formattedTotal)
        assertEquals("JUL", viewModel.state.value.member?.admin?.groups?.single()?.monthlyCharges?.month)
        assertEquals("27/07", viewModel.state.value.member?.admin?.groups?.single()?.gameToSettle?.formattedDate)
        assertEquals(1, viewModel.state.value.member?.nextGame?.declinedCount)
        assertEquals(2, viewModel.state.value.member?.nextGame?.pendingCount)
        assertTrue(viewModel.state.value.member?.groups?.single()?.isAdmin == true)
    }

    @Test
    fun `settle date formats in the game timezone not UTC`() = runTest {
        // São Paulo 12/08 21:30 = 2026-08-13T00:30:00Z; em UTC seria "13/08".
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        admin = HomeAdminReadModel(
                            groups = listOf(
                                HomeAdminGroup(
                                    id = GroupId("group-1"),
                                    name = "Vôlei do CERET",
                                    entryRequestCount = 0,
                                    monthlyCharges = HomeMonthlyCharges(1, 100, "2026-07"),
                                    gameToSettle = HomeGameToSettle("game-9", "2026-08-13T00:30:00Z", "America/Sao_Paulo", 1, 100),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("12/08", viewModel.state.value.member?.admin?.groups?.single()?.gameToSettle?.formattedDate)
    }

    @Test
    fun `monthly label derives from the backend billing month not UTC now`() = runTest {
        // Na virada do mês: 2026-08-01T02:00:00Z é 2026-07-31 23:00 em SP.
        // O backend bucketiza em julho; o rótulo deve ser JUL, não AGO.
        val nowAtBoundary = GroupNowPort { Instant.parse("2026-08-01T02:00:00Z") }
        val viewModel = HomeViewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        admin = HomeAdminReadModel(
                            groups = listOf(
                                HomeAdminGroup(
                                    id = GroupId("group-1"),
                                    name = "Vôlei do CERET",
                                    entryRequestCount = 0,
                                    monthlyCharges = HomeMonthlyCharges(2, 64000, "2026-07"),
                                    gameToSettle = null,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            athleteGateway = FakeAthleteGateway(
                ownProfileResult = SaqzResult.Success(OwnAthleteProfile("me", "Bruna Silva", null, emptyList())),
            ),
            attendanceGateway = FakeAttendanceGateway(),
            now = nowAtBoundary,
        )

        assertEquals("JUL", viewModel.state.value.member?.admin?.groups?.single()?.monthlyCharges?.month)
    }

    @Test
    fun `formats game date and time in the game's timezone`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        nextGame = sampleNextGame(
                            startsAt = "2026-08-12T22:30:00Z",
                            confirmationDeadline = "2026-08-12T21:00:00Z",
                            zoneId = "America/Sao_Paulo",
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Qua, 12/08 · 19h30", viewModel.state.value.member?.nextGame?.dateTime)
        assertEquals("19h30", viewModel.state.value.member?.nextGame?.time)
    }

    @Test
    fun `waitlisted mensalista formats the reserva subtitle`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Waitlisted, 1))),
                ),
            ),
        )

        assertEquals(
            "Você está na reserva de Vôlei do CERET.",
            viewModel.state.value.member?.subtitle,
        )
        assertEquals(HomeWaitlistKind.Reserva, viewModel.state.value.member?.nextGame?.waitlistKind)
    }

    @Test
    fun `waitlisted avulso with mensalista priority formats the avulso list subtitle`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Waitlisted, 2)).copy(
                            membershipType = AthleteMembershipType.AVULSO,
                            mensalistaPriority = true,
                            rosterPreview = HomeRosterPreview(
                                confirmed = listOf(),
                                waitlisted = listOf(
                                    br.com.saqz.groups.domain.home.HomeRosterMember("Lucas Pereira", 1),
                                    br.com.saqz.groups.domain.home.HomeRosterMember("Bruna Silva", 2),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            "Você está na lista de espera de Vôlei do CERET.",
            viewModel.state.value.member?.subtitle,
        )
        assertEquals(HomeWaitlistKind.AvulsoList, viewModel.state.value.member?.nextGame?.waitlistKind)
        assertEquals(2, viewModel.state.value.member?.nextGame?.waitlistedRoster?.size)
        assertTrue(viewModel.state.value.member?.nextGame?.waitlistedRoster?.get(1)?.isSelf == true)
    }

    @Test
    fun `home failure exposes the standard retry error`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Failure(HomeError.Data(DataError.Server)),
            ),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(br.com.saqz.groups.presentation.GroupUiError.Network, viewModel.state.value.error)
    }

    @Test
    fun `retry ignores a slower response from the previous load generation`() = runTest {
        val first = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val second = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val viewModel = HomeViewModel(
            homeGateway = DeferredHomeGateway(first, second),
            athleteGateway = FakeAthleteGateway(),
            attendanceGateway = FakeAttendanceGateway(),
            now = now,
        )

        viewModel.onIntent(HomeIntent.Retry)
        second.complete(SaqzResult.Success(sampleHome(id = "second")))
        first.complete(SaqzResult.Success(sampleHome(id = "first")))

        assertEquals("second", viewModel.state.value.home?.member?.groups?.singleOrNull()?.id?.value)
    }

    @Test
    fun `refresh during member formatting cannot overwrite the newer generation`() = runTest {
        val first = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val second = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        var viewModel: HomeViewModel? = null
        var retryTriggered = false
        val nowDuringFormatting = GroupNowPort {
            if (!retryTriggered) {
                retryTriggered = true
                viewModel?.onIntent(HomeIntent.Retry)
                second.complete(SaqzResult.Success(sampleHome(id = "newer", nextGame = sampleNextGame())))
            }
            Instant.parse("2026-07-28T12:00:00Z")
        }

        val candidate = HomeViewModel(
            homeGateway = DeferredHomeGateway(first, second),
            athleteGateway = FakeAthleteGateway(),
            attendanceGateway = FakeAttendanceGateway(),
            now = nowDuringFormatting,
        )
        viewModel = candidate

        first.complete(SaqzResult.Success(sampleHome(id = "older", nextGame = sampleNextGame())))
        advanceUntilIdle()

        assertEquals("newer", candidate.state.value.home?.member?.groups?.single()?.id?.value)
    }

    @Test
    fun `response is optimistic and successful confirmation refreshes home with a toast`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val refreshed = sampleHome(
            nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Confirmed, null)),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame())),
                SaqzResult.Success(refreshed),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertTrue(viewModel.state.value.responding)

        response.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))
        advanceUntilIdle()

        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(HomeToast.Confirmed, viewModel.state.value.toast)
        assertFalse(viewModel.state.value.responding)
        assertEquals(refreshed, viewModel.state.value.home)
    }

    @Test
    fun `failed response restores the previous attendance and exposes the standard response error`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server)),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertNull(viewModel.state.value.member?.nextGame?.ownAttendance)
        assertTrue(viewModel.state.value.responseFailed)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `closed confirmation deadline does not mutate or send attendance response`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        nextGame = sampleNextGame(
                            confirmationDeadline = "2026-07-28T11:00:00Z",
                        ),
                    ),
                ),
            ),
            attendanceGateway = attendance,
        )

        assertFalse(viewModel.state.value.member?.nextGame?.confirmationOpen == true)
        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertNull(viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(0, attendance.respondCalls)
        assertFalse(viewModel.state.value.responding)
        assertFalse(viewModel.state.value.responseFailed)
    }

    @Test
    fun `respond rechecks the deadline after the home was loaded`() = runTest {
        var currentNow = Instant.parse("2026-07-28T20:00:00Z")
        val clock = GroupNowPort { currentNow }
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
            now = clock,
        )

        assertTrue(viewModel.state.value.member?.nextGame?.confirmationOpen == true)
        currentNow = Instant.parse("2026-07-28T22:00:00Z")
        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertNull(viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(0, attendance.respondCalls)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `avulso with mensalista priority uses waitlist optimistic state and server toast`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val initial = sampleHome(
            nextGame = sampleNextGame().copy(
                membershipType = AthleteMembershipType.AVULSO,
                mensalistaPriority = true,
            ),
        )
        val refreshed = initial.copy(
            member = initial.member.copy(
                nextGame = initial.member.nextGame?.copy(
                    ownAttendance = HomeOwnAttendance(AttendanceStatus.Waitlisted, 1),
                ),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(initial), SaqzResult.Success(refreshed)),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)

        response.complete(SaqzResult.Success(serverMutation(AttendanceStatus.Waitlisted, 1)))
        advanceUntilIdle()

        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(HomeToast.Waitlisted, viewModel.state.value.toast)
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.home?.member?.nextGame?.ownAttendance?.status)
    }

    @Test
    fun `avulso without prior attendance shows waitlist kind on optimistic confirm`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val initial = sampleHome(
            nextGame = sampleNextGame().copy(
                membershipType = AthleteMembershipType.AVULSO,
                mensalistaPriority = true,
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(initial)),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        // O kind já é AvulsoList no update otimista, antes do refresh — sem attendance
        // prévio o hero não pode mostrar "Reserva" para um avulso com prioridade.
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(HomeWaitlistKind.AvulsoList, viewModel.state.value.member?.nextGame?.waitlistKind)
    }

    @Test
    fun `reconciled waitlist copies the server position to the UI model before refresh`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val initial = sampleHome(nextGame = sampleNextGame().copy(confirmedCount = 12, capacity = 12))
        val refreshed = initial.copy(
            member = initial.member.copy(
                nextGame = initial.member.nextGame?.copy(
                    ownAttendance = HomeOwnAttendance(AttendanceStatus.Waitlisted, 3),
                ),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(initial), SaqzResult.Success(refreshed)),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        // Otimista: sem posição conhecida, o chip omite o número (não mostra "0º").
        assertNull(viewModel.state.value.member?.nextGame?.waitlistPosition)

        response.complete(SaqzResult.Success(serverMutation(AttendanceStatus.Waitlisted, 3)))
        advanceUntilIdle()

        // Reconciliado: a posição do servidor já está na UI, sem esperar o refresh.
        assertEquals(3L, viewModel.state.value.member?.nextGame?.waitlistPosition)
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)
    }

    @Test
    fun `waitlist preview appends self when server position is outside preview`() = runTest {
        val preview = (1..5).map { position ->
            HomeRosterMember("Pessoa $position", position.toLong())
        }
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Waitlisted, 7)).copy(
                            membershipType = AthleteMembershipType.AVULSO,
                            mensalistaPriority = true,
                            rosterPreview = HomeRosterPreview(
                                confirmed = emptyList(),
                                waitlisted = preview,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val rows = viewModel.state.value.member?.nextGame?.waitlistedRoster.orEmpty()
        assertEquals(6, rows.size)
        assertEquals(5L, rows[4].position)
        assertEquals(7L, rows.last().position)
        assertTrue(rows.last().isSelf)
        assertEquals("", rows.last().name)
    }

    @Test
    fun `full game uses waitlist optimistic state and server toast`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val initial = sampleHome(nextGame = sampleNextGame().copy(confirmedCount = 12, capacity = 12))
        val refreshed = initial.copy(
            member = initial.member.copy(
                nextGame = initial.member.nextGame?.copy(
                    ownAttendance = HomeOwnAttendance(AttendanceStatus.Waitlisted, 1),
                ),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(initial), SaqzResult.Success(refreshed)),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)

        response.complete(SaqzResult.Success(serverMutation(AttendanceStatus.Waitlisted, 1)))
        advanceUntilIdle()

        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(HomeToast.Waitlisted, viewModel.state.value.toast)
    }

    @Test
    fun `decline from waitlisted leaves the queue optimistically and toasts the freed spot`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val initial = sampleHome(
            nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Waitlisted, 2)).copy(
                membershipType = AthleteMembershipType.AVULSO,
                mensalistaPriority = true,
            ),
        )
        val refreshed = initial.copy(
            member = initial.member.copy(
                nextGame = initial.member.nextGame?.copy(
                    ownAttendance = HomeOwnAttendance(AttendanceStatus.Declined, null),
                ),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(initial), SaqzResult.Success(refreshed)),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
        assertEquals(AttendanceStatus.Declined, viewModel.state.value.member?.nextGame?.ownAttendance)

        response.complete(SaqzResult.Success(serverMutation(AttendanceStatus.Declined, null)))
        advanceUntilIdle()

        assertEquals(AttendanceStatus.Declined, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(HomeToast.Declined, viewModel.state.value.toast)
    }

    @Test
    fun `confirm from waitlisted is ignored`() = runTest {
        val attendance = FakeAttendanceGateway()
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Waitlisted, 1))),
                ),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(0, attendance.respondCalls)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `successful response keeps reconciled state when soft refresh fails`() = runTest {
        val initial = sampleHome(nextGame = sampleNextGame())
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Success(serverMutation(AttendanceStatus.Confirmed, null)),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(initial),
                SaqzResult.Failure(HomeError.Data(DataError.Server)),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(HomeToast.Confirmed, viewModel.state.value.toast)
        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `older response cannot overwrite a newer retry generation`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame())),
                SaqzResult.Success(sampleHome(id = "newer")),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        viewModel.onIntent(HomeIntent.Retry)
        response.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))
        advanceUntilIdle()

        assertEquals("newer", viewModel.state.value.home?.member?.groups?.single()?.id?.value)
        assertNull(viewModel.state.value.toast)
    }

    @Test
    fun `own charges format the banner the competence and the pix of each group`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges())),
            ),
        )

        val charges = viewModel.state.value.ownCharges
        assertEquals("Você tem R$ 140,00 em aberto em 2 grupos", charges?.bannerText)
        assertTrue(charges?.bannerContentDescription?.endsWith("ver suas cobranças.") == true)
        assertTrue(charges?.overdue == true)
        val monthly = charges?.groups?.first()
        assertEquals("Vôlei do CERET", monthly?.groupName)
        assertEquals("Mensalidade · Julho", monthly?.competence)
        assertEquals("R$ 80,00", monthly?.amountLabel)
        assertEquals("Venceu em 05/08", monthly?.dueLabel)
        assertEquals("2 cobranças em aberto", monthly?.countLabel)
        assertEquals("ceret@volei.com.br", monthly?.pix?.key)
        val game = charges?.groups?.last()
        assertEquals("Jogo avulso", game?.competence)
        assertEquals("Vence em 12/08", game?.dueLabel)
        assertNull(game?.countLabel)
        assertNull(game?.pix)
    }

    @Test
    fun `single group banner drops the group count and the overdue tone`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        ownCharges = HomeOwnCharges(
                            groupCount = 1,
                            totalCents = 8000,
                            groups = listOf(sampleOwnChargeGroup(overdue = false)),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Você tem R$ 80,00 em aberto", viewModel.state.value.ownCharges?.bannerText)
        assertFalse(viewModel.state.value.ownCharges?.overdue == true)
        assertEquals("Vence em 05/08", viewModel.state.value.ownCharges?.groups?.single()?.dueLabel)
    }

    @Test
    fun `home without pending charges leaves the notice off`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        assertNull(viewModel.state.value.ownCharges)
    }

    @Test
    fun `refresh drops the notice when the admin settles the charge`() = runTest {
        // A volta ao app é a única forma de saber que a cobrança foi baixada lá fora.
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges())),
                SaqzResult.Success(sampleHome()),
            ),
        )
        assertEquals(2, viewModel.state.value.ownCharges?.groups?.size)

        viewModel.onIntent(HomeIntent.Refresh)
        advanceUntilIdle()

        assertNull(viewModel.state.value.ownCharges)
    }

    @Test
    fun `refresh keeps the screen mounted when the reload fails`() = runTest {
        // Recarga por baixo: falhar na volta ao app não pode trocar a Home por erro.
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges())),
                SaqzResult.Failure(HomeError.Data(DataError.Connectivity)),
            ),
        )

        viewModel.onIntent(HomeIntent.Refresh)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(2, viewModel.state.value.ownCharges?.groups?.size)
    }

    @Test
    fun `copy pix emits the key of the group and ignores a group without one`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges())),
            ),
        )

        viewModel.onIntent(HomeIntent.CopyPix("group-2"))
        viewModel.onIntent(HomeIntent.CopyPix("group-1"))

        // O grupo sem chave não emitiu nada: o primeiro efeito é o do grupo que tem Pix.
        assertEquals(HomeEffect.CopyPix("ceret@volei.com.br"), viewModel.effects.first())
    }

    private fun viewModel(
        homeGateway: HomeGateway,
        attendanceGateway: FakeAttendanceGateway = FakeAttendanceGateway(),
        now: GroupNowPort = this.now,
    ) = HomeViewModel(
        homeGateway = homeGateway,
        athleteGateway = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(OwnAthleteProfile("me", "Bruna Silva", null, emptyList())),
        ),
        attendanceGateway = attendanceGateway,
        now = now,
    )
}

private fun serverMutation(
    status: AttendanceStatus,
    waitlistPosition: Long?,
) = br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation().let { mutation ->
    mutation.copy(
        value = mutation.value.copy(
            attendance = mutation.value.attendance.copy(
                status = status,
                waitlistPosition = waitlistPosition,
            ),
        ),
    )
}

private class SequenceHomeGateway(
    private vararg val results: SaqzResult<HomeReadModel, HomeError>,
) : HomeGateway {
    private var reads = 0

    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> =
        results.getOrElse(reads++) { results.last() }
}

private class DeferredHomeGateway(
    private val first: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
    private val second: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
) : HomeGateway {
    private var reads = 0

    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> =
        if (reads++ == 0) first.await() else second.await()
}

private fun sampleOwnChargeGroup(
    id: String = "group-1",
    name: String = "Vôlei do CERET",
    count: Int = 2,
    totalCents: Long = 8000,
    nextDueDate: String = "2026-08-05",
    overdue: Boolean = true,
    pixKey: String? = "ceret@volei.com.br",
    oldest: HomeOwnChargeOldest = HomeOwnChargeOldest.Monthly("2026-07"),
) = HomeOwnChargeGroup(
    groupId = GroupId(id),
    groupName = name,
    count = count,
    totalCents = totalCents,
    nextDueDate = nextDueDate,
    overdue = overdue,
    pixKey = pixKey,
    pixLabel = "Ana Souza · Nubank",
    oldest = oldest,
)

private fun sampleOwnCharges() = HomeOwnCharges(
    groupCount = 2,
    totalCents = 14000,
    groups = listOf(
        sampleOwnChargeGroup(),
        sampleOwnChargeGroup(
            id = "group-2",
            name = "Vôlei Pacaembu",
            count = 1,
            totalCents = 6000,
            nextDueDate = "2026-08-12",
            overdue = false,
            pixKey = null,
            oldest = HomeOwnChargeOldest.Game,
        ),
    ),
)

private fun sampleHome(
    id: String = "group-1",
    nextGame: HomeNextGame? = null,
    admin: HomeAdminReadModel? = null,
    role: GroupRole = GroupRole.ATHLETE,
    ownCharges: HomeOwnCharges? = null,
) = HomeReadModel(
    member = HomeMemberReadModel(
        nextGame = nextGame,
        lastCompletedGame = null,
        groups = listOf(
            HomeMemberGroup(
                id = GroupId(id),
                name = "Vôlei do CERET",
                role = role,
                memberCount = 12,
                gamesPlayed = 3,
            ),
        ),
    ),
    admin = admin,
    ownCharges = ownCharges,
)

private fun sampleNextGame(
    attendance: HomeOwnAttendance? = null,
    startsAt: String = "2026-07-28T19:30:00-03:00",
    confirmationDeadline: String = "2026-07-28T18:00:00-03:00",
    zoneId: String = "America/Sao_Paulo",
) = HomeNextGame(
    groupId = GroupId("group-1"),
    groupName = "Vôlei do CERET",
    gameId = "game-1",
    local = "CERET — Quadra 2 · Tatuapé",
    zoneId = zoneId,
    startsAt = startsAt,
    confirmationDeadline = confirmationDeadline,
    capacity = 12,
    confirmedCount = 9,
    declinedCount = 1,
    pendingCount = 2,
    waitlistCount = 0,
    ownAttendance = attendance,
    membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA,
    mensalistaPriority = false,
    rosterPreview = HomeRosterPreview(confirmed = listOf(), waitlisted = listOf()),
)
