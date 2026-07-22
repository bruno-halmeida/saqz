package br.com.saqz.composeapp.di

import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.CreateGroupProfileCommand
import br.com.saqz.groups.domain.group.UpdateGroupProfileCommand
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkUrl
import br.com.saqz.groups.domain.attendance.share.AttendanceShareSnapshot
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingError
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryCommand
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import br.com.saqz.groups.domain.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.port.GroupDraftReadResult
import br.com.saqz.groups.port.GroupDraftStorePort
import br.com.saqz.groups.port.GroupDraftWriteResult
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import br.com.saqz.groups.presentation.games.detail.GameDetailEffect
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailViewModel
import br.com.saqz.groups.presentation.setup.GroupCommandKeyFactory
import br.com.saqz.groups.presentation.setup.GroupSetupInput
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ComposePresentationModuleTest {
    @Test
    fun groupSetupResolvesDraftAndTimeZonePortsFromKoin() = runTest {
        val drafts = RecordingDraftStore()
        val timeZones = RecordingTimeZonePort()
        val app = koinApplication {
            modules(
                module {
                    single<GroupProfileGateway> { UnusedGroupProfileGateway }
                    single<GroupDraftStorePort> { drafts }
                    single<GroupSystemTimeZonePort> { timeZones }
                },
                composePresentationModule,
            )
        }
        try {
            app.koin.get<GroupSetupViewModel> {
                parametersOf(
                    GroupSetupViewModelParameters(
                        input = GroupSetupInput(),
                        commandKeys = GroupCommandKeyFactory { "command" },
                        testScope = this,
                    ),
                )
            }
            runCurrent()

            assertEquals(1, drafts.reads)
            assertEquals(1, timeZones.detections)
        } finally {
            app.close()
        }
    }

    @Test
    fun gameDetailResolvesAttendanceShareGatewayFromKoin() = runTest {
        val share = RecordingAttendanceShareGateway()
        val app = koinApplication {
            modules(
                module {
                    single<GameGateway> { PublishedGameGateway }
                    single<AttendanceGateway> { EmptyAttendanceGateway }
                    single<AttendanceSharingGateway> { share }
                },
                composePresentationModule,
            )
        }
        try {
            val viewModel = app.koin.get<GameDetailViewModel> {
                parametersOf(
                    GameDetailViewModelParameters(
                        groupId = "group",
                        gameId = "game",
                        role = GroupRole.OWNER,
                        testScope = this,
                    ),
                )
            }
            runCurrent()
            val effect = async { viewModel.effects.first() }

            viewModel.onIntent(GameDetailIntent.RequestAttendanceLinkShare)
            runCurrent()

            assertEquals(listOf("group" to "game"), share.rotations)
            assertEquals(GameDetailEffect.ShareAttendanceLink(AttendanceLinkUrl(LINK_URL)), effect.await())
        } finally {
            app.close()
        }
    }
}

private object UnusedGroupProfileGateway : GroupProfileGateway {
    override suspend fun createProfile(command: CreateGroupProfileCommand) = error("unused")
    override suspend fun readProfile(groupId: GroupId) = error("unused")
    override suspend fun updateProfile(command: UpdateGroupProfileCommand) = error("unused")
}

private class RecordingDraftStore : GroupDraftStorePort {
    var reads = 0

    override fun read(key: GroupDraftKey, done: (GroupDraftReadResult) -> Unit) {
        reads++
        done(GroupDraftReadResult.Success(null))
    }

    override fun write(draft: GroupSetupDraft, done: (GroupDraftWriteResult) -> Unit) =
        done(GroupDraftWriteResult.Success)

    override fun clear(key: GroupDraftKey, commandKey: String, done: (GroupDraftWriteResult) -> Unit) =
        done(GroupDraftWriteResult.Success)
}

private class RecordingTimeZonePort : GroupSystemTimeZonePort {
    var detections = 0

    override fun detect(done: (GroupSystemTimeZoneResult) -> Unit) {
        detections++
        done(GroupSystemTimeZoneResult.Unavailable)
    }
}

private object PublishedGameGateway : GameGateway {
    override suspend fun read(groupId: GroupId, gameId: String) = SaqzResult.Success(PUBLISHED_GAME)
    override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> = error("unused")
    override suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> = error("unused")
    override suspend fun edit(groupId: GroupId, gameId: String, version: GameVersionToken, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> = error("unused")
    override suspend fun lifecycle(groupId: GroupId, gameId: String, version: GameVersionToken, action: GameLifecycleAction): SaqzResult<VersionedGame, GameError> = error("unused")
    override suspend fun createSeries(groupId: GroupId, command: WeeklySeriesWriteCommand): SaqzResult<VersionedSeries, GameError> = error("unused")
    override suspend fun readSeries(groupId: GroupId, seriesId: String): SaqzResult<VersionedSeries, GameError> = error("unused")
    override suspend fun boundary(groupId: GroupId, seriesId: String, version: GameVersionToken, command: SeriesBoundaryCommand): SaqzResult<VersionedSeries, GameError> = error("unused")
}

private object EmptyAttendanceGateway : AttendanceGateway {
    override suspend fun read(groupId: GroupId, gameId: String) = SaqzResult.Success(
        AttendanceDetail(null, confirmedCount = 2, availableSpots = 1, waitlistCount = 0, capacity = 3),
    )
    override suspend fun respond(groupId: GroupId, gameId: String, command: SelfAttendanceCommand): SaqzResult<VersionedAttendanceMutation, AttendanceError> = error("unused")
    override suspend fun override(groupId: GroupId, gameId: String, command: OverrideAttendanceCommand): SaqzResult<VersionedAttendanceMutation, AttendanceError> = error("unused")
    override suspend fun capacity(groupId: GroupId, gameId: String, version: AttendanceVersionToken, command: AttendanceCapacityCommand): SaqzResult<VersionedAttendanceCapacity, AttendanceError> = error("unused")
}

private class RecordingAttendanceShareGateway : AttendanceSharingGateway {
    val rotations = mutableListOf<Pair<String, String>>()

    override suspend fun rotateLink(groupId: GroupId, gameId: String): SaqzResult<AttendanceLinkUrl, AttendanceSharingError> {
        rotations += groupId.value to gameId
        return SaqzResult.Success(AttendanceLinkUrl(LINK_URL))
    }

    override suspend fun resolveLink(code: br.com.saqz.groups.domain.attendance.share.AttendanceLinkCode): SaqzResult<br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination, AttendanceSharingError> = error("unused")
    override suspend fun readSnapshot(groupId: GroupId, gameId: String): SaqzResult<AttendanceShareSnapshot, AttendanceSharingError> = error("unused")
}

private val PUBLISHED_GAME = VersionedGame(
    Game(
        id = "game",
        groupId = GroupId("group"),
        title = "Treino",
        venue = GameVenue(null, "Arena", "Rua 1"),
        localDate = "2026-08-12",
        localTime = "19:30:00",
        zoneId = "America/Sao_Paulo",
        startsAt = "2026-08-12T22:30:00Z",
        durationMinutes = 90,
        capacity = 3,
        confirmationDeadline = "2026-08-12T19:00:00Z",
        gameFeeCents = 2500,
        notes = "Notas",
        status = GameStatus.Published,
        version = 7,
        confirmedCount = 2,
        availableSpots = 1,
        waitlistCount = 0,
        financeReviewRequired = false,
    ),
    version = GameVersionToken("\"7\""),
)

private const val LINK_URL = "https://join.example.test/?saqz_attendance=abc"
