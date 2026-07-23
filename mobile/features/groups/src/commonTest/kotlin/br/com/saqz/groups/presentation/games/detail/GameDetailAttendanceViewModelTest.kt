package br.com.saqz.groups.presentation.games.detail

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.attendance.AttendanceCapacity
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceMutation
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceCapacity
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkCode
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkUrl
import br.com.saqz.groups.domain.attendance.share.AttendanceSharePerson
import br.com.saqz.groups.domain.attendance.share.AttendanceShareSnapshot
import br.com.saqz.groups.domain.attendance.share.AttendanceSharingGateway
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
import br.com.saqz.groups.domain.group.GroupRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailAttendanceViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no response exposes authoritative counts and legal actions`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()

        assertEquals(setOf(AttendanceAction.CONFIRM, AttendanceAction.DECLINE), fixture.vm.state.value.attendanceActions)
        assertEquals(2, fixture.vm.state.value.confirmedCount)
        assertEquals(1, fixture.vm.state.value.availableSpots)
    }

    @Test
    fun `confirmed response exposes withdrawal only`() = runTest(mainDispatcher) {
        val fixture = fixture(detail = detail(AttendanceStatus.Confirmed))
        runCurrent()
        assertEquals(setOf(AttendanceAction.WITHDRAW), fixture.vm.state.value.attendanceActions)
    }

    @Test
    fun `waitlisted response exposes position and withdrawal`() = runTest(mainDispatcher) {
        val fixture = fixture(detail = detail(AttendanceStatus.Waitlisted, 4))
        runCurrent()
        assertEquals(4, fixture.vm.state.value.waitlistPosition)
        assertEquals(setOf(AttendanceAction.WITHDRAW), fixture.vm.state.value.attendanceActions)
    }

    @Test
    fun `declined response may confirm again`() = runTest(mainDispatcher) {
        val fixture = fixture(detail = detail(AttendanceStatus.Declined))
        runCurrent()
        assertEquals(setOf(AttendanceAction.CONFIRM), fixture.vm.state.value.attendanceActions)
    }

    @Test
    fun `deadline closes athlete actions`() = runTest(mainDispatcher) {
        val fixture = fixture(game = versioned(deadline = "2026-07-31T10:00:00Z"))
        runCurrent()
        assertTrue(fixture.vm.state.value.attendanceActions.isEmpty())
    }

    @Test
    fun `cancelled game freezes athlete actions`() = runTest(mainDispatcher) {
        val fixture = fixture(game = versioned(status = GameStatus.Cancelled))
        runCurrent()
        assertTrue(fixture.vm.state.value.attendanceActions.isEmpty())
    }

    @Test
    fun `withdrawal confirmation marks pending charge`() = runTest(mainDispatcher) {
        val fixture = fixture(detail = detail(AttendanceStatus.Confirmed))
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.WITHDRAW))
        assertTrue(fixture.vm.state.value.withdrawalKeepsCharge)
        assertTrue(fixture.attendance.respondCalls.isEmpty())
    }

    @Test
    fun `confirmed attendance sends stable command key and exact intent`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.CONFIRM))
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        runCurrent()
        assertEquals(SelfCall(KEY, AttendanceIntent.Confirm), fixture.attendance.respondCalls.single())
    }

    @Test
    fun `double confirm is one logical attendance command`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.attendance.gate = CompletableDeferred()
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.CONFIRM))
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        runCurrent()
        assertEquals(1, fixture.attendance.respondCalls.size)
        fixture.attendance.gate?.complete(Unit)
        runCurrent()
    }

    @Test
    fun `pending response never optimistically claims capacity`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.attendance.gate = CompletableDeferred()
        fixture.attendance.respondResult = successMutation(AttendanceStatus.Waitlisted, position = 3, available = 0)
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.CONFIRM))
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        runCurrent()
        assertNull(fixture.vm.state.value.ownAttendance)
        assertEquals(1, fixture.vm.state.value.availableSpots)
        fixture.attendance.gate?.complete(Unit)
        runCurrent()
        assertEquals(AttendanceStatus.Waitlisted, fixture.vm.state.value.ownAttendance?.status)
        assertEquals(0, fixture.vm.state.value.availableSpots)
    }

    @Test
    fun `conflict retry preserves logical command key`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.attendance.respondResult = SaqzResult.Failure(AttendanceError.Conflict)
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.CONFIRM))
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        runCurrent()
        fixture.attendance.respondResult = successMutation(AttendanceStatus.Confirmed)
        fixture.vm.onIntent(GameDetailIntent.RetryAttendance)
        runCurrent()
        assertEquals(listOf(KEY, KEY), fixture.attendance.respondCalls.map(SelfCall::key))
    }

    @Test
    fun `promotion refresh replaces waitlist with authoritative confirmation`() = runTest(mainDispatcher) {
        val fixture = fixture(detail = detail(AttendanceStatus.Waitlisted, 1))
        runCurrent()
        fixture.attendance.readResult = SaqzResult.Success(detail(AttendanceStatus.Confirmed))
        fixture.vm.onIntent(GameDetailIntent.RefreshAttendance)
        runCurrent()
        assertEquals(AttendanceStatus.Confirmed, fixture.vm.state.value.ownAttendance?.status)
        assertNull(fixture.vm.state.value.waitlistPosition)
    }

    @Test
    fun `attendance success emits charge refresh effect once`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        val effect = async { fixture.vm.effects.first() }
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.CONFIRM))
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        runCurrent()
        assertEquals(GameDetailEffect.AttendanceApplied(AttendanceStatus.Confirmed, 0, true), effect.await())
    }

    @Test
    fun `organizer override validates member and reason locally`() = runTest(mainDispatcher) {
        val fixture = fixture(role = GroupRole.OWNER)
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.OverrideAttendance("", AttendanceIntent.Confirm, "x"))
        assertEquals(GameDetailError.VALIDATION, fixture.vm.state.value.attendanceError)
        assertTrue(fixture.attendance.overrideCalls.isEmpty())
    }

    @Test
    fun `organizer override sends target reason and stable key`() = runTest(mainDispatcher) {
        val fixture = fixture(role = GroupRole.ADMIN)
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.OverrideAttendance("member-2", AttendanceIntent.Decline, "  Correção  "))
        runCurrent()
        assertEquals(
            OverrideCall(KEY, "member-2", AttendanceIntent.Decline, "Correção"),
            fixture.attendance.overrideCalls.single(),
        )
    }

    @Test
    fun `athlete cannot send organizer override`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.OverrideAttendance("member-2", AttendanceIntent.Confirm, "Ajuste"))
        runCurrent()
        assertTrue(fixture.attendance.overrideCalls.isEmpty())
    }

    @Test
    fun `capacity conflict retry preserves value version and key`() = runTest(mainDispatcher) {
        val fixture = fixture(role = GroupRole.OWNER)
        runCurrent()
        fixture.attendance.capacityResult = SaqzResult.Failure(AttendanceError.Conflict)
        fixture.vm.onIntent(GameDetailIntent.ChangeCapacity(30))
        runCurrent()
        fixture.attendance.capacityResult = successCapacity(30)
        fixture.vm.onIntent(GameDetailIntent.RetryAttendance)
        runCurrent()
        assertEquals(
            listOf(
                CapacityCall(KEY, 30, AttendanceVersionToken("\"7\"")),
                CapacityCall(KEY, 30, AttendanceVersionToken("\"7\"")),
            ),
            fixture.attendance.capacityCalls,
        )
    }

    @Test
    fun `capacity success updates version counts and emits promotion`() = runTest(mainDispatcher) {
        val fixture = fixture(role = GroupRole.OWNER)
        runCurrent()
        fixture.attendance.capacityResult = successCapacity(30, promoted = 2)
        val effect = async { fixture.vm.effects.first() }
        fixture.vm.onIntent(GameDetailIntent.ChangeCapacity(30))
        runCurrent()
        assertEquals(GameVersionToken("\"8\""), fixture.vm.state.value.version)
        assertEquals(30, fixture.vm.state.value.game?.capacity)
        assertEquals(GameDetailEffect.CapacityApplied(30, 2), effect.await())
    }

    @Test
    fun `safe validation global message is retained`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.attendance.respondResult = SaqzResult.Failure(
            AttendanceError.Validation(
                DataError.Validation(ValidationDetails(listOf("Mensagem segura"), emptyMap())),
            ),
        )
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.CONFIRM))
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        runCurrent()
        assertEquals("Mensagem segura", fixture.vm.state.value.attendanceErrorMessage)
    }

    @Test
    fun `validation without global message keeps generic fallback`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.attendance.respondResult = SaqzResult.Failure(
            AttendanceError.Validation(
                DataError.Validation(ValidationDetails(emptyList(), mapOf("intent" to listOf("invalid")))),
            ),
        )
        fixture.vm.onIntent(GameDetailIntent.RequestAttendance(AttendanceAction.CONFIRM))
        fixture.vm.onIntent(GameDetailIntent.ConfirmAttendance)
        runCurrent()
        assertEquals(GameDetailError.VALIDATION, fixture.vm.state.value.attendanceError)
        assertNull(fixture.vm.state.value.attendanceErrorMessage)
    }

    @Test
    fun `organizer rotates attendance link and emits share effect`() = runTest(mainDispatcher) {
        val fixture = fixture(role = GroupRole.OWNER)
        runCurrent()
        val effect = async { fixture.vm.effects.first() }
        fixture.vm.onIntent(GameDetailIntent.RequestAttendanceLinkShare)
        runCurrent()
        assertEquals(GameDetailEffect.ShareAttendanceLink(AttendanceLinkUrl(LINK_URL)), effect.await())
        assertEquals(LINK_URL, fixture.vm.state.value.attendanceLinkUrl)
    }

    @Test
    fun `attendance link retry reuses cached url`() = runTest(mainDispatcher) {
        val fixture = fixture(role = GroupRole.OWNER)
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.RequestAttendanceLinkShare)
        runCurrent()
        fixture.share.rotateCalls.clear()
        val effect = async { fixture.vm.effects.first() }
        fixture.vm.onIntent(GameDetailIntent.RetryAttendanceLinkShare)
        runCurrent()
        assertTrue(fixture.share.rotateCalls.isEmpty())
        assertIs<GameDetailEffect.ShareAttendanceLink>(effect.await())
    }

    @Test
    fun `organizer snapshot requires privacy confirmation`() = runTest(mainDispatcher) {
        val fixture = fixture(role = GroupRole.OWNER)
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.RequestAttendanceImageShare)
        runCurrent()
        assertTrue(fixture.vm.state.value.showAttendanceSharePrivacy)
        assertNotNull(fixture.vm.state.value.attendanceShareSnapshot)
    }

    @Test
    fun `athlete never starts organizer share operations`() = runTest(mainDispatcher) {
        val fixture = fixture()
        runCurrent()
        fixture.vm.onIntent(GameDetailIntent.RequestAttendanceLinkShare)
        fixture.vm.onIntent(GameDetailIntent.RequestAttendanceImageShare)
        runCurrent()
        assertTrue(fixture.share.rotateCalls.isEmpty())
        assertTrue(fixture.share.snapshotCalls.isEmpty())
    }

    private fun fixture(
        role: GroupRole = GroupRole.ATHLETE,
        detail: AttendanceDetail = detail(),
        game: VersionedGame = versioned(),
    ): Fixture {
        val attendance = FakeAttendance(SaqzResult.Success(detail))
        val share = FakeAttendanceShare()
        val vm = GameDetailViewModel(
            gateway = FakeGames(game),
            groupId = "group",
            gameId = "game",
            role = role,
            attendanceGateway = attendance,
            attendanceShareGateway = share,
            keys = AttendanceCommandKeyFactory { KEY },
            now = { NOW },
        )
        return Fixture(vm, attendance, share)
    }

    private fun detail(
        status: AttendanceStatus? = null,
        position: Long? = null,
        confirmed: Int = 2,
        available: Int = 1,
    ) = AttendanceDetail(
        ownAttendance = status?.let { AttendanceEntry("member", it, position, 1) },
        confirmedCount = confirmed,
        availableSpots = available,
        waitlistCount = if (status == AttendanceStatus.Waitlisted) 1 else 0,
        capacity = 3,
    )

    private fun successMutation(
        status: AttendanceStatus,
        position: Long? = null,
        available: Int = 0,
    ): SaqzResult<VersionedAttendanceMutation, AttendanceError> = SaqzResult.Success(
        VersionedAttendanceMutation(
            AttendanceMutation(
                attendance = AttendanceEntry("member", status, position, 2),
                promotedCount = 0,
                detail = detail(status, position, confirmed = 3, available = available),
            ),
            AttendanceVersionToken("\"2\""),
        ),
    )

    private fun successCapacity(
        value: Int,
        promoted: Int = 0,
    ): SaqzResult<VersionedAttendanceCapacity, AttendanceError> = SaqzResult.Success(
        VersionedAttendanceCapacity(
            AttendanceCapacity(value, 8, promoted, AttendanceDetail(null, 3, value - 3, 0, value)),
            AttendanceVersionToken("\"8\""),
        ),
    )

    private fun versioned(
        status: GameStatus = GameStatus.Published,
        deadline: String = "2026-08-12T19:00:00Z",
    ) = VersionedGame(
        Game(
            "game",
            GroupId("group"),
            "Treino",
            GameVenue(null, "Arena", "Rua 1"),
            "2026-08-12",
            "19:30:00",
            "America/Sao_Paulo",
            "2026-08-12T22:30:00Z",
            90,
            3,
            deadline,
            2_500,
            "Notas",
            status,
            7,
            2,
            1,
            0,
        ),
        GameVersionToken("\"7\""),
    )

    private data class SelfCall(val key: String, val intent: AttendanceIntent)
    private data class OverrideCall(val key: String, val member: String, val intent: AttendanceIntent, val reason: String)
    private data class CapacityCall(val key: String, val capacity: Int, val version: AttendanceVersionToken)
    private data class ShareCall(val groupId: String, val gameId: String)

    private inner class FakeAttendance(
        var readResult: SaqzResult<AttendanceDetail, AttendanceError>,
    ) : AttendanceGateway {
        val respondCalls = mutableListOf<SelfCall>()
        val overrideCalls = mutableListOf<OverrideCall>()
        val capacityCalls = mutableListOf<CapacityCall>()
        var respondResult: SaqzResult<VersionedAttendanceMutation, AttendanceError> =
            successMutation(AttendanceStatus.Confirmed)
        var overrideResult: SaqzResult<VersionedAttendanceMutation, AttendanceError> =
            successMutation(AttendanceStatus.Confirmed)
        var capacityResult: SaqzResult<VersionedAttendanceCapacity, AttendanceError> = successCapacity(3)
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun read(groupId: GroupId, gameId: String) = readResult

        override suspend fun respond(
            groupId: GroupId,
            gameId: String,
            command: SelfAttendanceCommand,
        ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
            respondCalls += SelfCall(command.requestId, command.intent)
            gate?.await()
            return respondResult
        }

        override suspend fun override(
            groupId: GroupId,
            gameId: String,
            command: OverrideAttendanceCommand,
        ): SaqzResult<VersionedAttendanceMutation, AttendanceError> {
            overrideCalls += OverrideCall(command.requestId, command.memberId, command.intent, command.reason)
            return overrideResult
        }

        override suspend fun capacity(
            groupId: GroupId,
            gameId: String,
            version: AttendanceVersionToken,
            command: AttendanceCapacityCommand,
        ): SaqzResult<VersionedAttendanceCapacity, AttendanceError> {
            capacityCalls += CapacityCall(command.requestId, command.capacity, version)
            return capacityResult
        }
    }

    private class FakeGames(private val initial: VersionedGame) : GameGateway {
        override suspend fun read(groupId: GroupId, gameId: String) = SaqzResult.Success(initial)
        override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> = error("unused")
        override suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> = error("unused")
        override suspend fun edit(groupId: GroupId, gameId: String, version: GameVersionToken, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> = error("unused")
        override suspend fun lifecycle(groupId: GroupId, gameId: String, version: GameVersionToken, action: GameLifecycleAction): SaqzResult<VersionedGame, GameError> = error("unused")
        override suspend fun createSeries(groupId: GroupId, command: WeeklySeriesWriteCommand): SaqzResult<VersionedSeries, GameError> = error("unused")
        override suspend fun readSeries(groupId: GroupId, seriesId: String): SaqzResult<VersionedSeries, GameError> = error("unused")
        override suspend fun boundary(groupId: GroupId, seriesId: String, version: GameVersionToken, command: SeriesBoundaryCommand): SaqzResult<VersionedSeries, GameError> = error("unused")
    }

    private inner class FakeAttendanceShare : AttendanceSharingGateway {
        val rotateCalls = mutableListOf<ShareCall>()
        val snapshotCalls = mutableListOf<ShareCall>()

        override suspend fun rotateLink(groupId: GroupId, gameId: String) =
            SaqzResult.Success(AttendanceLinkUrl(LINK_URL)).also {
                rotateCalls += ShareCall(groupId.value, gameId)
            }

        override suspend fun resolveLink(code: AttendanceLinkCode) = error("unused")

        override suspend fun readSnapshot(groupId: GroupId, gameId: String) =
            SaqzResult.Success(snapshot()).also {
                snapshotCalls += ShareCall(groupId.value, gameId)
            }
    }

    private fun snapshot() = AttendanceShareSnapshot(
        "Treino",
        "2026-08-12T22:30:00Z",
        "America/Sao_Paulo",
        "Arena",
        3,
        listOf(AttendanceSharePerson("Ana")),
        listOf(AttendanceSharePerson("Bruno", 1)),
        listOf(AttendanceSharePerson("Carla")),
    )

    private data class Fixture(
        val vm: GameDetailViewModel,
        val attendance: FakeAttendance,
        val share: FakeAttendanceShare,
    )

    private companion object {
        const val KEY = "stable-attendance-key"
        const val LINK_URL = "https://join.example.test/?saqz_attendance=abc"
        val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z")
    }
}
