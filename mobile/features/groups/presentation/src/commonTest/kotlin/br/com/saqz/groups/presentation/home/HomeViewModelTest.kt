package br.com.saqz.groups.presentation.home

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeAdminGroup
import br.com.saqz.groups.domain.home.HomeAdminReadModel
import br.com.saqz.groups.domain.home.HomeGameToReview
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
import br.com.saqz.groups.presentation.FakeGroupEntryRequestGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import br.com.saqz.groups.presentation.AutoRetry
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
        val home = sampleHome(nextGame = sampleNextGame(mensalistaPriority = true))
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(home)))

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Bruna", viewModel.state.value.displayName)
        assertEquals(home, viewModel.state.value.home)
        assertEquals("9 de 12 confirmados", viewModel.state.value.member?.nextGame?.confirmedSummary)
        assertTrue(viewModel.state.value.member?.nextGame?.deadline?.contains("hoje") == true)
        assertFalse(viewModel.state.value.loadFailed)
    }

    @Test
    fun `pending entry requests reach the member block with group id and name`() = runTest {
        val base = sampleHome()
        val home = base.copy(
            member = base.member.copy(
                pendingEntryRequests = listOf(
                    br.com.saqz.groups.domain.home.HomePendingEntryRequest(GroupId("g-9"), "Volei RV A", "2026-10-13T02:36:00Z"),
                ),
            ),
        )
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(home)))

        assertEquals(
            listOf(HomePendingRequestUi("g-9", "Volei RV A")),
            viewModel.state.value.member?.pendingRequests,
        )
    }

    private fun homeWithPendingRequest(): HomeReadModel {
        val base = sampleHome()
        return base.copy(
            member = base.member.copy(
                pendingEntryRequests = listOf(
                    br.com.saqz.groups.domain.home.HomePendingEntryRequest(GroupId("g-9"), "Volei RV A", "2026-10-13T02:36:00Z"),
                ),
            ),
        )
    }

    @Test
    fun `asking to cancel only opens the confirmation and dismissing it calls nothing`() = runTest {
        val entries = FakeGroupEntryRequestGateway()
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(homeWithPendingRequest())),
            entryRequests = entries,
        )

        viewModel.onIntent(HomeIntent.RequestCancelEntryRequest("g-9"))
        assertEquals("g-9", viewModel.state.value.cancelEntryCandidate?.groupId)
        viewModel.onIntent(HomeIntent.DismissCancelEntryRequest)

        assertNull(viewModel.state.value.cancelEntryCandidate)
        assertTrue(entries.canceledOwnGroupIds.isEmpty())
        assertEquals(1, viewModel.state.value.member?.pendingRequests?.size)
    }

    @Test
    fun `confirming the cancellation calls the api once and drops the card and toasts`() = runTest {
        val entries = FakeGroupEntryRequestGateway()
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(homeWithPendingRequest())),
            entryRequests = entries,
        )

        viewModel.onIntent(HomeIntent.RequestCancelEntryRequest("g-9"))
        viewModel.onIntent(HomeIntent.ConfirmCancelEntryRequest)
        advanceUntilIdle()

        assertEquals(listOf("g-9"), entries.canceledOwnGroupIds)
        assertTrue(viewModel.state.value.member?.pendingRequests.orEmpty().isEmpty())
        assertEquals(HomeToast.EntryRequestCanceled, viewModel.state.value.toast)
        assertFalse(viewModel.state.value.cancelingEntry)
        assertNull(viewModel.state.value.cancelEntryCandidate)
    }

    @Test
    fun `a second confirmation while the cancellation is in flight is ignored`() = runTest {
        val entries = FakeGroupEntryRequestGateway().apply { cancelOwnDeferred = CompletableDeferred() }
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(homeWithPendingRequest())),
            entryRequests = entries,
        )

        viewModel.onIntent(HomeIntent.RequestCancelEntryRequest("g-9"))
        viewModel.onIntent(HomeIntent.ConfirmCancelEntryRequest)
        viewModel.onIntent(HomeIntent.RequestCancelEntryRequest("g-9"))
        viewModel.onIntent(HomeIntent.ConfirmCancelEntryRequest)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.cancelingEntry)
        assertEquals(listOf("g-9"), entries.canceledOwnGroupIds)

        entries.cancelOwnDeferred!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("g-9"), entries.canceledOwnGroupIds)
        assertFalse(viewModel.state.value.cancelingEntry)
    }

    @Test
    fun `cancellation failure keeps the card and warns`() = runTest {
        val entries = FakeGroupEntryRequestGateway(
            cancelOwnResult = SaqzResult.Failure(
                br.com.saqz.groups.domain.membership.EntryRequestError.DataFailure(br.com.saqz.domain.DataError.Connectivity),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(homeWithPendingRequest())),
            entryRequests = entries,
        )

        viewModel.onIntent(HomeIntent.RequestCancelEntryRequest("g-9"))
        viewModel.onIntent(HomeIntent.ConfirmCancelEntryRequest)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.member?.pendingRequests?.size)
        assertEquals(HomeToast.EntryRequestCancelFailed, viewModel.state.value.toast)
        assertFalse(viewModel.state.value.cancelingEntry)
    }

    @Test
    fun `home without next game keeps the member block without a hero`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

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

        assertEquals("1 grupo · 3 coisas esperando você", viewModel.state.value.member?.adminSubtitle)
        assertEquals("R$ 640,00", viewModel.state.value.member?.admin?.groups?.single()?.monthlyCharges?.formattedTotal)
        assertEquals("JUL", viewModel.state.value.member?.admin?.groups?.single()?.monthlyCharges?.month)
        assertEquals("27/07", viewModel.state.value.member?.admin?.groups?.single()?.gameToSettle?.formattedDate)
        assertEquals(1, viewModel.state.value.member?.nextGame?.declinedCount)
        assertEquals(2, viewModel.state.value.member?.nextGame?.pendingCount)
        assertTrue(viewModel.state.value.member?.groups?.single()?.isAdmin == true)
    }

    @Test
    fun `game to review maps the date in the game timezone and counts the other games`() = runTest {
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
                                    entryRequestCount = 0,
                                    monthlyCharges = HomeMonthlyCharges(0, 0, "2026-07"),
                                    gameToSettle = null,
                                    gameToReview = HomeGameToReview("game-7", "2026-08-13T00:30:00Z", "America/Sao_Paulo", 3, 2),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val review = viewModel.state.value.member?.admin?.groups?.single()?.gameToReview
        assertEquals("game-7", review?.gameId)
        assertEquals("12/08", review?.formattedDate)
        assertEquals(3, review?.reviewCount)
        assertEquals(1, review?.otherGameCount)
        assertEquals("1 grupo · 1 coisa esperando você", viewModel.state.value.member?.adminSubtitle)
    }

    @Test
    fun `group without game to review has none and an empty count does not show it`() = runTest {
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
                                    entryRequestCount = 1,
                                    monthlyCharges = HomeMonthlyCharges(0, 0, "2026-07"),
                                    gameToSettle = null,
                                    gameToReview = HomeGameToReview("game-7", "2026-08-13T00:30:00Z", "America/Sao_Paulo", 0, 1),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertNull(viewModel.state.value.member?.admin?.groups?.single()?.gameToReview)
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
    fun `formats the hero display and meta in the game's timezone`() = runTest {
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

        assertEquals("Quarta, 19h30", viewModel.state.value.member?.nextGame?.display)
        assertEquals("12 de agosto · CERET — Quadra 2 · Tatuapé", viewModel.state.value.member?.nextGame?.meta)
    }

    @Test
    fun `closed confirmation deadline formats the closed labels for athlete and admin`() = runTest {
        // `now` do teste é 2026-07-28T12:00Z; o prazo abaixo já passou.
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(nextGame = sampleNextGame(confirmationDeadline = "2026-07-27T18:00:00-03:00", mensalistaPriority = true)),
                ),
            ),
        )

        val game = viewModel.state.value.member?.nextGame
        assertEquals(false, game?.confirmationOpen)
        // Prazo vencido com o jogo por começar: o "Vou" segue aberto e o cartão explica a espera.
        assertEquals(true, game?.responseOpen)
        assertEquals("", game?.deadline)
        assertEquals("Prioridade encerrada 27/07 · 18h00", game?.adminHeroDeadlineLabel)
    }

    private fun nextGameWith(
        confirmationDeadline: String = "2026-07-28T18:00:00-03:00",
        startsAt: String = "2026-07-28T19:30:00-03:00",
        mensalistaPriority: Boolean = true,
    ) = viewModel(
        homeGateway = SequenceHomeGateway(
            SaqzResult.Success(
                sampleHome(
                    nextGame = sampleNextGame(
                        startsAt = startsAt,
                        confirmationDeadline = confirmationDeadline,
                        mensalistaPriority = mensalistaPriority,
                    ),
                ),
            ),
        ),
    ).state.value.member?.nextGame

    @Test
    fun `priority window today says the mensalista priority ends today`() = runTest {
        val game = nextGameWith()

        assertEquals(true, game?.confirmationOpen)
        assertEquals("Prioridade dos mensalistas até hoje às 18h00.", game?.deadline)
        assertEquals("Prioridade até 28/07 · 18h00", game?.adminHeroDeadlineLabel)
    }

    @Test
    fun `priority window tomorrow says the mensalista priority ends tomorrow`() = runTest {
        val game = nextGameWith(confirmationDeadline = "2026-07-29T10:00:00-03:00", startsAt = "2026-07-29T19:30:00-03:00")

        assertEquals("Prioridade dos mensalistas até amanhã às 10h00.", game?.deadline)
        assertEquals("Prioridade até 29/07 · 10h00", game?.adminHeroDeadlineLabel)
    }

    @Test
    fun `priority window on a later day says the date`() = runTest {
        val game = nextGameWith(confirmationDeadline = "2026-07-31T10:00:00-03:00", startsAt = "2026-08-01T19:30:00-03:00")

        assertEquals("Prioridade dos mensalistas até 31/07 às 10h00.", game?.deadline)
        assertEquals("Prioridade até 31/07 · 10h00", game?.adminHeroDeadlineLabel)
    }

    @Test
    fun `without the priority the deadline line disappears for athlete and admin`() = runTest {
        val game = nextGameWith(mensalistaPriority = false)

        assertEquals(true, game?.confirmationOpen)
        assertEquals("", game?.deadline)
        assertEquals("", game?.adminHeroDeadlineLabel)
    }

    @Test
    fun `a deadline at the start of the game has no priority window and no line`() = runTest {
        val game = nextGameWith(confirmationDeadline = "2026-07-28T19:30:00-03:00")

        assertEquals("", game?.deadline)
        assertEquals("", game?.adminHeroDeadlineLabel)
    }

    @Test
    fun `waitlisted mensalista derives the reserva kind`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Waitlisted, 1))),
                ),
            ),
        )

        assertEquals(HomeWaitlistKind.Reserva, viewModel.state.value.member?.nextGame?.waitlistKind)
    }

    @Test
    fun `waitlisted avulso with mensalista priority derives the avulso list kind and the self row`() = runTest {
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
    fun `DEB-107 Vou on a game with positions opens the position screen instead of answering`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(
                br.com.saqz.groups.presentation.sampleAttendanceDetail().copy(
                    positions = listOf(br.com.saqz.groups.domain.attendance.PositionSummary(br.com.saqz.groups.domain.athlete.AthletePosition.PONTA, 2, 1, 1, 0)),
                ),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
        )
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertEquals(0, attendance.respondCalls)
        assertTrue(effects.single() is HomeEffect.PickPosition)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `DEB-102 Nao vou on a game with positions answers straight away`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Success(
                br.com.saqz.groups.presentation.sampleAttendanceDetail().copy(
                    positions = listOf(br.com.saqz.groups.domain.attendance.PositionSummary(br.com.saqz.groups.domain.athlete.AthletePosition.PONTA, 2, 1, 1, 0)),
                ),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame())), SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
        )
        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
        advanceUntilIdle()
        assertEquals(1, attendance.respondCalls)
    }

    @Test
    fun `DEB-107 Vou whose attendance read fails sends nothing and shows the response error`() = runTest {
        val attendance = FakeAttendanceGateway(
            detailResult = SaqzResult.Failure(AttendanceError.Data(br.com.saqz.domain.DataError.Connectivity)),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
        )
        val effects = mutableListOf<HomeEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertEquals(0, attendance.respondCalls)
        assertTrue(effects.none { it is HomeEffect.PickPosition })
        assertTrue(viewModel.state.value.responseFailed)
        assertTrue(viewModel.state.value.responseOffline)
        assertFalse(viewModel.state.value.responding)
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
    fun `second Vou or Nao vou while the first is in flight is ignored and answers again once it ends`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
        assertTrue(viewModel.state.value.responding)
        assertEquals(1, attendance.respondCalls)

        response.complete(SaqzResult.Success(br.com.saqz.groups.presentation.sampleVersionedAttendanceMutation()))
        advanceUntilIdle()
        assertFalse(viewModel.state.value.responding)
        assertEquals(1, attendance.respondCalls)

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
        assertEquals(2, attendance.respondCalls)
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

    // ---- Backend v.0.0.17: depois do prazo, "Vou"/"Não vou" voltam 409 ATTENDANCE_DEADLINE_PASSED ----

    /** Prazo das 08h (11h UTC) já passou às 12h UTC; o jogo, às 19h30, ainda não começou. */
    private fun lateGame() = sampleNextGame(confirmationDeadline = "2026-07-28T08:00:00-03:00")

    @Test
    fun `a late answer refused for the deadline closes the answers without saying the game started`() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.DeadlinePassed))
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = lateGame()))),
            attendanceGateway = attendance,
        )
        val before = assertNotNull(viewModel.state.value.member?.nextGame)
        assertTrue(before.canRespond, "backend novo: depois do prazo o Vou entra pela espera")
        assertFalse(before.confirmationOpen)
        assertFalse(before.closedByDeadline)

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        val game = assertNotNull(viewModel.state.value.member?.nextGame)
        assertTrue(game.closedByDeadline)
        assertTrue(game.responseOpen, "o jogo não começou: nada de \"Em andamento\"")
        assertFalse(game.confirmationOpen)
        assertFalse(game.canRespond)
        assertNull(game.ownAttendance, "a previsão otimista volta atrás")
        assertFalse(viewModel.state.value.responseFailed, "sem o erro genérico")
        assertFalse(viewModel.state.value.responding)

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
        advanceUntilIdle()
        assertEquals(1, attendance.respondCalls, "fechado, o toque seguinte nem sai do aparelho")
    }

    @Test
    fun `the deadline closure survives a reload of the same game`() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.DeadlinePassed))
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = lateGame()))),
            attendanceGateway = attendance,
        )
        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.Refresh)
        advanceUntilIdle()

        val game = assertNotNull(viewModel.state.value.member?.nextGame)
        assertTrue(game.closedByDeadline, "a carga reconstrói a tela; o servidor já disse que o prazo fechou")
        assertFalse(game.canRespond)
        assertFalse(game.confirmationOpen)
    }

    @Test
    fun `a game that started is closed as in progress and not as closed by the deadline`() = runTest {
        val attendance = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.Frozen))
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = lateGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        val game = assertNotNull(viewModel.state.value.member?.nextGame)
        assertFalse(game.responseOpen)
        assertFalse(game.closedByDeadline)
        assertFalse(game.canRespond)
        assertFalse(viewModel.state.value.responseFailed)
    }

    @Test
    fun `the current backend takes the late answer into the waitlist and nothing closes`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Success(serverMutation(AttendanceStatus.Waitlisted, 1)),
        )
        val viewModel = viewModel(
            // A recarga por baixo que se segue falha: o que fica na tela é a resposta reconciliada.
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(nextGame = lateGame())),
                SaqzResult.Failure(HomeError.Data(DataError.Server)),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        val game = assertNotNull(viewModel.state.value.member?.nextGame)
        assertEquals(AttendanceStatus.Waitlisted, game.ownAttendance)
        assertFalse(game.closedByDeadline)
        assertTrue(game.responseOpen)
        assertFalse(viewModel.state.value.responseFailed)
    }

    @Test
    fun `athlete answering in a group with a paused plan gets the paused notice and no retry banner`() = runTest {
        val attendance = FakeAttendanceGateway(
            respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.SubscriptionRequired)),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertNull(viewModel.state.value.member?.nextGame?.ownAttendance)
        assertTrue(viewModel.state.value.responsePlanPaused)
        assertFalse(viewModel.state.value.responseFailed)
        assertFalse(viewModel.state.value.responding)
    }

    @Test
    fun `recorded response reports attendance_answered from the app surface and a failed one does not`() = runTest {
        val recorded = mutableListOf<Pair<String, Map<String, String>>>()
        SaqzAnalytics.track = { name, params -> recorded += name to params }
        try {
            val confirmed = viewModel(
                homeGateway = SequenceHomeGateway(
                    SaqzResult.Success(sampleHome(nextGame = sampleNextGame())),
                    SaqzResult.Success(sampleHome(nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Confirmed, null)))),
                ),
                attendanceGateway = FakeAttendanceGateway(),
            )
            confirmed.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
            advanceUntilIdle()
            val failed = viewModel(
                homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
                attendanceGateway = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))),
            )
            failed.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
            advanceUntilIdle()
        } finally {
            SaqzAnalytics.reset()
        }

        assertEquals(
            listOf("attendance_answered" to mapOf("surface" to "app", "answer" to "confirm")),
            recorded.filter { it.first == "attendance_answered" },
        )
    }

    @Test
    fun `recorded response dismisses the attendance window of that game and a failed one keeps it`() = runTest {
        val notifications = br.com.saqz.groups.presentation.FakeNativeNotifications()
        val confirmed = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame())),
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Confirmed, null)))),
            ),
            notifications = notifications,
        )
        confirmed.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()
        val failed = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))),
            notifications = notifications,
        )
        failed.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
        advanceUntilIdle()

        assertEquals(listOf(sampleNextGame().gameId), notifications.dismissed)
    }

    @Test
    fun `recorded response offers the notification prompt to the athlete and a failed one does not`() = runTest {
        val offers = br.com.saqz.groups.presentation.RecordingNotificationPromptOffer()
        val failed = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(nextGame = sampleNextGame()))),
            attendanceGateway = FakeAttendanceGateway(respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Server))),
            notificationPrompt = offers,
        )
        failed.onIntent(HomeIntent.Respond(AttendanceIntent.Decline))
        advanceUntilIdle()
        assertTrue(offers.offered.isEmpty())

        val confirmed = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame())),
                SaqzResult.Success(sampleHome(nextGame = sampleNextGame(HomeOwnAttendance(AttendanceStatus.Confirmed, null)))),
            ),
            notificationPrompt = offers,
        )
        confirmed.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        advanceUntilIdle()

        assertEquals(
            listOf(br.com.saqz.groups.presentation.communication.NotificationPromptAudience.Athlete),
            offers.offered,
        )
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
                            startsAt = "2026-07-28T11:30:00Z",
                        ),
                    ),
                ),
            ),
            attendanceGateway = attendance,
        )

        assertFalse(viewModel.state.value.member?.nextGame?.responseOpen == true)
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
        currentNow = Instant.parse("2026-07-28T23:00:00Z")
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
    fun `manual promotion sends the Vou to the waitlist with the organizer-chooses toast`() = runTest {
        val initial = sampleHome(
            nextGame = sampleNextGame().copy(
                membershipType = AthleteMembershipType.MENSALISTA,
                mensalistaPriority = false,
                promotionMode = br.com.saqz.groups.domain.group.PromotionMode.MANUAL,
            ),
        )
        waitlistedFlow(initial, HomeWaitlistKind.Manual, HomeToast.WaitlistedManual)
    }

    @Test
    fun `after the deadline with the game full the Vou is still sent and lands in the waitlist`() = runTest {
        val initial = sampleHome(
            nextGame = sampleNextGame(confirmationDeadline = "2026-07-28T11:00:00Z").copy(confirmedCount = 12),
        )
        waitlistedFlow(initial, HomeWaitlistKind.AfterDeadline, HomeToast.WaitlistedAfterDeadline)
    }

    @Test
    fun `the offline notice stays until a reload succeeds and then disappears by itself`() = runTest {
        val attendance = FakeAttendanceGateway()
        attendance.respondResult = SaqzResult.Failure(AttendanceError.Data(DataError.Connectivity))
        val initial = sampleHome(nextGame = sampleNextGame(confirmationDeadline = "2099-07-28T11:00:00Z", startsAt = "2099-07-28T19:30:00-03:00"))
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(initial),
                SaqzResult.Failure(HomeError.Data(DataError.Connectivity)),
                SaqzResult.Success(initial),
            ),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        runCurrent()
        assertTrue(viewModel.state.value.responseOffline)
        // A rede ainda não voltou: a recarga automática por baixo falha em silêncio e o aviso fica.
        advanceTimeBy(AutoRetry.DEFAULT_INTERVAL_MILLIS + 1)
        runCurrent()
        assertTrue(viewModel.state.value.responseOffline)
        // A rede voltou: a próxima recarga dá certo e limpa o aviso sozinha.
        advanceTimeBy(AutoRetry.DEFAULT_INTERVAL_MILLIS + 1)
        runCurrent()
        assertFalse(viewModel.state.value.responseOffline)
        assertFalse(viewModel.state.value.responseFailed)
    }

    @Test
    fun `after the deadline in order-of-arrival mode with room the screen never predicts a waitlist`() = runTest {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val initial = sampleHome(nextGame = sampleNextGame(confirmationDeadline = "2026-07-28T11:00:00Z"))
        val refreshed = initial.copy(
            member = initial.member.copy(
                nextGame = initial.member.nextGame?.copy(ownAttendance = HomeOwnAttendance(AttendanceStatus.Confirmed, null)),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(initial), SaqzResult.Success(refreshed)),
            attendanceGateway = attendance,
        )

        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.member?.nextGame?.ownAttendance)
        response.complete(SaqzResult.Success(serverMutation(AttendanceStatus.Confirmed, null)))
        advanceUntilIdle()
        assertEquals(AttendanceStatus.Confirmed, viewModel.state.value.member?.nextGame?.ownAttendance)
    }

    private fun kotlinx.coroutines.test.TestScope.waitlistedFlow(
        initial: HomeReadModel,
        kind: HomeWaitlistKind,
        toast: HomeToast,
    ) {
        val response = CompletableDeferred<SaqzResult<VersionedAttendanceMutation, AttendanceError>>()
        val attendance = FakeAttendanceGateway()
        attendance.respondDeferred = response
        val refreshed = initial.copy(
            member = initial.member.copy(
                nextGame = initial.member.nextGame?.copy(ownAttendance = HomeOwnAttendance(AttendanceStatus.Waitlisted, 1)),
            ),
        )
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(initial), SaqzResult.Success(refreshed)),
            attendanceGateway = attendance,
        )

        assertTrue(viewModel.state.value.member?.nextGame?.responseOpen == true)
        viewModel.onIntent(HomeIntent.Respond(AttendanceIntent.Confirm))
        assertEquals(AttendanceStatus.Waitlisted, viewModel.state.value.member?.nextGame?.ownAttendance)
        assertEquals(kind, viewModel.state.value.member?.nextGame?.waitlistKind)

        response.complete(SaqzResult.Success(serverMutation(AttendanceStatus.Waitlisted, 1)))
        advanceUntilIdle()

        assertEquals(1, attendance.respondCalls)
        assertEquals(toast, viewModel.state.value.toast)
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
        assertEquals("Mensalidade de julho", monthly?.competence)
        assertEquals("R$ 80,00", monthly?.amountLabel)
        assertEquals("Venceu em 05/08", monthly?.dueLabel)
        assertEquals("2 cobranças em aberto", monthly?.countLabel)
        assertEquals("ceret@volei.com.br", monthly?.pix?.key)
        val game = charges?.groups?.last()
        assertEquals("Jogo avulso", game?.competence)
        assertEquals("Vence em 12/08", game?.dueLabel)
        assertNull(game?.countLabel)
        assertNull(game?.pix)
        assertEquals(listOf("Vôlei do CERET"), charges?.overdueGroups?.map { it.groupName })
    }

    @Test
    fun `own charge of a guest is titled with the guest name`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(
                    sampleHome(
                        ownCharges = HomeOwnCharges(
                            groupCount = 1,
                            totalCents = 6000,
                            groups = listOf(
                                sampleOwnChargeGroup(oldest = HomeOwnChargeOldest.Game("Rafa Moreira")),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val game = viewModel.state.value.ownCharges?.groups?.single()
        assertEquals("Convidado: Rafa Moreira", game?.competence)
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
        assertTrue(viewModel.state.value.ownCharges?.overdueGroups?.isEmpty() == true)
    }

    @Test
    fun `home without pending charges leaves the notice off`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        assertNull(viewModel.state.value.ownCharges)
    }

    @Test
    fun `app start does not load twice when the banner resumes over the initial load`() = runTest {
        // Abertura: o `init` carrega e o primeiro resume da faixa chega logo atrás, com a
        // carga ainda no ar. Duas idas à rede na tela de entrada é o que a guarda impede.
        val gate = CompletableDeferred<Unit>()
        val gateway = CountingHomeGateway(
            SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges())),
            gate = gate,
        )
        val viewModel = viewModel(homeGateway = gateway)

        viewModel.onIntent(HomeIntent.Refresh)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, gateway.reads)
        // E a carga que sobrou é a boa: o descarte não pode custar o dado.
        assertEquals(2, viewModel.state.value.ownCharges?.groups?.size)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `refresh with nothing in flight reaches the gateway`() = runTest {
        val gateway = CountingHomeGateway(SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges())))
        val viewModel = viewModel(homeGateway = gateway)
        assertEquals(1, gateway.reads)

        viewModel.onIntent(HomeIntent.Refresh)
        advanceUntilIdle()

        assertEquals(2, gateway.reads)
    }

    @Test
    fun `retry is not discarded by a refresh in flight`() = runTest {
        // A trava é só para a recarga por baixo: quem tocou "tentar novamente" pediu.
        val gate = CompletableDeferred<Unit>()
        val gateway = CountingHomeGateway(SaqzResult.Success(sampleHome()), gate = gate)
        val viewModel = viewModel(homeGateway = gateway)

        viewModel.onIntent(HomeIntent.Retry)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, gateway.reads)
    }

    @Test
    fun `pull refresh keeps the content and ends not refreshing`() = runTest {
        val second = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val viewModel = viewModel(
            homeGateway = DeferredHomeGateway(
                CompletableDeferred(SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges()))),
                second,
            ),
        )
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.PullRefresh)
        advanceUntilIdle()
        // Enquanto atualiza, a tela é a mesma: nada de esqueleto.
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(2, viewModel.state.value.ownCharges?.groups?.size)
        second.complete(SaqzResult.Success(sampleHome()))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertNull(viewModel.state.value.ownCharges)
    }

    @Test
    fun `pull refresh failure keeps the data and warns`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(
                SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges())),
                SaqzResult.Failure(HomeError.Data(DataError.Connectivity)),
            ),
        )
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.PullRefresh)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isRefreshing)
        assertFalse(state.loadFailed)
        assertEquals(2, state.ownCharges?.groups?.size)
        assertEquals(HomeToast.RefreshFailed, state.toast)
    }

    @Test
    fun `pull during a load in flight rides it and warns when it fails`() = runTest {
        val second = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val gateway = DeferredHomeGateway(
            CompletableDeferred(SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges()))),
            second,
        )
        val viewModel = viewModel(homeGateway = gateway)
        advanceUntilIdle()
        viewModel.onIntent(HomeIntent.Refresh)
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.PullRefresh)
        assertTrue(viewModel.state.value.isRefreshing)

        second.complete(SaqzResult.Failure(HomeError.Data(DataError.Connectivity)))
        advanceUntilIdle()
        // Uma busca do init, uma do Refresh; o puxão não abriu uma terceira.
        assertEquals(2, gateway.reads)
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals(HomeToast.RefreshFailed, viewModel.state.value.toast)
        assertEquals(2, viewModel.state.value.ownCharges?.groups?.size)
    }

    @Test
    fun `two pulls in a row fetch once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = HeldAfterFirstHomeGateway(SaqzResult.Success(sampleHome()), gate)
        val viewModel = viewModel(homeGateway = gateway)
        advanceUntilIdle()

        viewModel.onIntent(HomeIntent.PullRefresh)
        viewModel.onIntent(HomeIntent.PullRefresh)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, gateway.reads)
        assertFalse(viewModel.state.value.isRefreshing)
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

    @Test
    fun `copy pix marks the group as copied and toasts then clears after the dwell`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges()))),
        )

        viewModel.onIntent(HomeIntent.CopyPix("group-1"))

        assertEquals("group-1", viewModel.state.value.pixCopiedGroupId)
        assertEquals(HomeToast.PixCopied, viewModel.state.value.toast)
        advanceTimeBy(2_001)
        assertNull(viewModel.state.value.pixCopiedGroupId)
    }

    @Test
    fun `second copy within the dwell keeps the copied state until its own dwell ends`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges()))),
        )

        viewModel.onIntent(HomeIntent.CopyPix("group-1"))
        advanceTimeBy(1_500)
        viewModel.onIntent(HomeIntent.CopyPix("group-1"))
        advanceTimeBy(1_000)

        // O delay do primeiro toque já venceu; o estado é do segundo e continua de pé.
        assertEquals("group-1", viewModel.state.value.pixCopiedGroupId)
        advanceTimeBy(1_001)
        assertNull(viewModel.state.value.pixCopiedGroupId)
    }

    @Test
    fun `copy pix on a group without key leaves the copied state untouched`() = runTest {
        val viewModel = viewModel(
            homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome(ownCharges = sampleOwnCharges()))),
        )

        viewModel.onIntent(HomeIntent.CopyPix("group-2"))

        assertNull(viewModel.state.value.pixCopiedGroupId)
        assertNull(viewModel.state.value.toast)
    }

    @Test
    fun `open notifications intent emits the navigation effect`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        viewModel.onIntent(HomeIntent.OpenNotifications)

        assertEquals(HomeEffect.OpenNotifications, viewModel.effects.first())
    }

    @Test
    fun `member without groups is the first access and a group ends it`() = runTest {
        val empty = sampleHome().let { it.copy(member = it.member.copy(groups = emptyList())) }
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(empty), SaqzResult.Success(sampleHome())))

        assertTrue(viewModel.state.value.member?.firstAccess == true)

        viewModel.onIntent(HomeIntent.Refresh)

        assertFalse(viewModel.state.value.member?.firstAccess == true)
    }

    @Test
    fun `create group from the first access emits the navigation effect`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        viewModel.onIntent(HomeIntent.CreateGroup)

        assertEquals(HomeEffect.OpenCreateGroup, viewModel.effects.first())
    }

    @Test
    fun `submitting a pasted invite link closes the sheet and hands the code to the coordinator`() = runTest {
        val code = "a".repeat(42) + "A"
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        viewModel.onIntent(HomeIntent.OpenInviteSheet)
        viewModel.onIntent(HomeIntent.InviteLinkChanged("https://links.saqz.app/?saqz_invite=$code"))
        viewModel.onIntent(HomeIntent.SubmitInviteLink)

        assertEquals(HomeEffect.AcceptInviteCode(code), viewModel.effects.first())
        assertFalse(viewModel.state.value.inviteSheetOpen)
        assertEquals("", viewModel.state.value.inviteLink)
        assertFalse(viewModel.state.value.inviteLinkInvalid)
    }

    @Test
    fun `a pasted text without an invite code flags the field and keeps the sheet open`() = runTest {
        val viewModel = viewModel(homeGateway = SequenceHomeGateway(SaqzResult.Success(sampleHome())))

        viewModel.onIntent(HomeIntent.OpenInviteSheet)
        viewModel.onIntent(HomeIntent.InviteLinkChanged("https://saqz.app/"))
        viewModel.onIntent(HomeIntent.SubmitInviteLink)

        assertTrue(viewModel.state.value.inviteLinkInvalid)
        assertTrue(viewModel.state.value.inviteSheetOpen)
        assertEquals("https://saqz.app/", viewModel.state.value.inviteLink)

        // Digitar de novo limpa o erro; fechar esvazia a folha.
        viewModel.onIntent(HomeIntent.InviteLinkChanged("x"))
        assertFalse(viewModel.state.value.inviteLinkInvalid)
        viewModel.onIntent(HomeIntent.CloseInviteSheet)
        assertFalse(viewModel.state.value.inviteSheetOpen)
        assertEquals("", viewModel.state.value.inviteLink)
    }

    private fun viewModel(
        homeGateway: HomeGateway,
        attendanceGateway: FakeAttendanceGateway = FakeAttendanceGateway(),
        now: GroupNowPort = this.now,
        notifications: NativeNotificationPort? = null,
        notificationPrompt: br.com.saqz.groups.presentation.communication.NotificationPromptOffer? = null,
        entryRequests: br.com.saqz.groups.domain.membership.GroupEntryRequestGateway? = null,
    ) = HomeViewModel(
        homeGateway = homeGateway,
        athleteGateway = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(OwnAthleteProfile("me", "Bruna Silva", null, emptyList())),
        ),
        attendanceGateway = attendanceGateway,
        now = now,
        notifications = notifications,
        notificationPrompt = notificationPrompt,
        entryRequests = entryRequests,
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

/**
 * Conta as idas ao gateway e, com [gate], segura a resposta para a carga ficar **em voo**
 * enquanto o teste dispara a próxima — é assim que a abertura do app se reproduz.
 */
private class CountingHomeGateway(
    private val result: SaqzResult<HomeReadModel, HomeError>,
    private val gate: CompletableDeferred<Unit>? = null,
) : HomeGateway {
    var reads = 0
        private set

    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> {
        reads++
        gate?.await()
        return result
    }
}

/** A primeira leitura (o `init`) responde na hora; as seguintes esperam [gate]. */
private class HeldAfterFirstHomeGateway(
    private val result: SaqzResult<HomeReadModel, HomeError>,
    private val gate: CompletableDeferred<Unit>,
) : HomeGateway {
    var reads = 0
        private set

    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> {
        if (reads++ > 0) gate.await()
        return result
    }
}

private class DeferredHomeGateway(
    private val first: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
    private val second: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
) : HomeGateway {
    var reads = 0
        private set

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
            oldest = HomeOwnChargeOldest.Game(),
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
    mensalistaPriority: Boolean = false,
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
    mensalistaPriority = mensalistaPriority,
    rosterPreview = HomeRosterPreview(confirmed = listOf(), waitlisted = listOf()),
)
