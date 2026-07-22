package br.com.saqz.composeapp.di

import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.attendance.AttendanceDetailDto
import br.com.saqz.groups.data.attendance.AttendanceGateway
import br.com.saqz.groups.data.attendance.CapacityCommand
import br.com.saqz.groups.data.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.data.attendance.SelfAttendanceCommand
import br.com.saqz.groups.data.attendance.VersionedAttendanceMutationDto
import br.com.saqz.groups.data.attendance.VersionedCapacityDto
import br.com.saqz.groups.data.attendance.share.AttendanceLinkUrlDto
import br.com.saqz.groups.data.attendance.share.AttendanceShareGateway
import br.com.saqz.groups.data.attendance.share.AttendanceShareSnapshotDto
import br.com.saqz.groups.data.attendance.share.ResolvedAttendanceLinkDto
import br.com.saqz.groups.data.game.GameDto
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.data.game.GameStatusDto
import br.com.saqz.groups.data.game.GameVenueDto
import br.com.saqz.groups.data.game.GameWriteCommand
import br.com.saqz.groups.data.game.SeriesBoundaryCommand
import br.com.saqz.groups.data.game.VersionedGameDto
import br.com.saqz.groups.data.game.VersionedSeriesDto
import br.com.saqz.groups.data.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.model.GroupCreateCommand
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.GroupUpdateCommand
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
import br.com.saqz.network.NetworkResult
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
                    single<AttendanceShareGateway> { share }
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
                        role = GroupRoleDto.OWNER,
                        testScope = this,
                    ),
                )
            }
            runCurrent()
            val effect = async { viewModel.effects.first() }

            viewModel.onIntent(GameDetailIntent.RequestAttendanceLinkShare)
            runCurrent()

            assertEquals(listOf("group" to "game"), share.rotations)
            assertEquals(GameDetailEffect.ShareAttendanceLink(LINK_URL), effect.await())
        } finally {
            app.close()
        }
    }
}

private object UnusedGroupProfileGateway : GroupProfileGateway {
    override suspend fun createProfile(command: GroupCreateCommand) = error("unused")
    override suspend fun readProfile(groupId: String) = error("unused")
    override suspend fun updateProfile(command: GroupUpdateCommand) = error("unused")
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
    override suspend fun read(groupId: String, gameId: String) = NetworkResult.Success(PUBLISHED_GAME)
    override suspend fun list(groupId: String) = error("unused")
    override suspend fun create(groupId: String, command: GameWriteCommand) = error("unused")
    override suspend fun edit(groupId: String, gameId: String, etag: String, command: GameWriteCommand) = error("unused")
    override suspend fun lifecycle(groupId: String, gameId: String, etag: String, mutation: String) = error("unused")
    override suspend fun createSeries(groupId: String, command: WeeklySeriesWriteCommand): NetworkResult<VersionedSeriesDto> = error("unused")
    override suspend fun readSeries(groupId: String, seriesId: String): NetworkResult<VersionedSeriesDto> = error("unused")
    override suspend fun boundary(groupId: String, seriesId: String, etag: String, command: SeriesBoundaryCommand): NetworkResult<VersionedSeriesDto> = error("unused")
}

private object EmptyAttendanceGateway : AttendanceGateway {
    override suspend fun read(groupId: String, gameId: String) = NetworkResult.Success(
        AttendanceDetailDto(null, confirmedCount = 2, availableSpots = 1, waitlistCount = 0, capacity = 3),
    )
    override suspend fun respond(groupId: String, gameId: String, command: SelfAttendanceCommand): NetworkResult<VersionedAttendanceMutationDto> = error("unused")
    override suspend fun override(groupId: String, gameId: String, command: OverrideAttendanceCommand): NetworkResult<VersionedAttendanceMutationDto> = error("unused")
    override suspend fun capacity(groupId: String, gameId: String, etag: String, command: CapacityCommand): NetworkResult<VersionedCapacityDto> = error("unused")
}

private class RecordingAttendanceShareGateway : AttendanceShareGateway {
    val rotations = mutableListOf<Pair<String, String>>()

    override suspend fun rotateLink(groupId: String, gameId: String): NetworkResult<AttendanceLinkUrlDto> {
        rotations += groupId to gameId
        return NetworkResult.Success(AttendanceLinkUrlDto(LINK_URL))
    }

    override suspend fun resolveLink(code: String): NetworkResult<ResolvedAttendanceLinkDto> = error("unused")
    override suspend fun readSnapshot(groupId: String, gameId: String): NetworkResult<AttendanceShareSnapshotDto> = error("unused")
}

private val PUBLISHED_GAME = VersionedGameDto(
    GameDto(
        id = "game",
        groupId = "group",
        title = "Treino",
        venue = GameVenueDto(null, "Arena", "Rua 1"),
        localDate = "2026-08-12",
        localTime = "19:30:00",
        zoneId = "America/Sao_Paulo",
        startsAt = "2026-08-12T22:30:00Z",
        durationMinutes = 90,
        capacity = 3,
        confirmationDeadline = "2026-08-12T19:00:00Z",
        gameFeeCents = 2500,
        notes = "Notas",
        status = GameStatusDto.PUBLISHED,
        version = 7,
        confirmedCount = 2,
        availableSpots = 1,
        waitlistCount = 0,
        financeReviewRequired = false,
    ),
    etag = "\"7\"",
)

private const val LINK_URL = "https://join.example.test/?saqz_attendance=abc"
