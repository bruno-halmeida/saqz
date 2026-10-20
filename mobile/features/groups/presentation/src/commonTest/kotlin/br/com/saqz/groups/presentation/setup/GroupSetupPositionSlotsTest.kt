package br.com.saqz.groups.presentation.setup

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.GroupPlayStyle
import br.com.saqz.groups.domain.group.GroupPositionSlots
import br.com.saqz.groups.domain.group.GroupPositionSlotsGateway
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupVersionToken
import br.com.saqz.groups.domain.group.PositionGameSystem
import br.com.saqz.groups.domain.group.PositionSlot
import br.com.saqz.groups.domain.group.VersionedGroupPositionSlots
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
import br.com.saqz.groups.presentation.FakeGroupSystemTimeZonePort
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** DEB-102 (a): configuração por posição no formulário do grupo. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSetupPositionSlotsTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FakePositionSlotsGateway(
        var stored: GroupPositionSlots = GroupPositionSlots(null, emptyList()),
        var suggestion: GroupPositionSlots = FIVE_ONE_TWO_TEAMS,
        var failWrite: Boolean = false,
        var failSuggestion: Boolean = false,
    ) : GroupPositionSlotsGateway {
        var reads = 0
        val writes = mutableListOf<Pair<GroupVersionToken, GroupPositionSlots>>()

        override suspend fun read(groupId: GroupId): SaqzResult<VersionedGroupPositionSlots, GroupProfileError> {
            reads++
            return SaqzResult.Success(VersionedGroupPositionSlots(stored, GroupVersionToken("etag-2")))
        }

        override suspend fun write(
            groupId: GroupId,
            versionToken: GroupVersionToken,
            config: GroupPositionSlots,
        ): SaqzResult<VersionedGroupPositionSlots, GroupProfileError> {
            writes += versionToken to config
            if (failWrite) return SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Server))
            stored = config
            return SaqzResult.Success(VersionedGroupPositionSlots(config, GroupVersionToken("etag-after-slots")))
        }

        override suspend fun suggestion(system: PositionGameSystem, teams: Int): SaqzResult<GroupPositionSlots, GroupProfileError> =
            if (failSuggestion) SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Server)) else SaqzResult.Success(suggestion)
    }

    private fun group(playStyle: GroupPlayStyle, role: GroupRole = GroupRole.OWNER) = sampleVersionedGroup(
        sampleGroup(role = role, profile = sampleGroup().profile!!.copy(playStyle = playStyle)).let {
            it.copy(profile = it.profile!!.copy(playStyle = playStyle))
        },
    )

    private fun viewModel(
        playStyle: GroupPlayStyle = GroupPlayStyle.FIVE_ONE,
        role: GroupRole = GroupRole.OWNER,
        slots: FakePositionSlotsGateway = FakePositionSlotsGateway(),
        profile: FakeGroupProfileGateway = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(group(playStyle, role)),
            updateResult = SaqzResult.Success(group(playStyle, role).copy(versionToken = GroupVersionToken("etag-3"))),
        ),
    ) = GroupSetupViewModel(
        initialState = GroupSetupState(mode = GroupSetupMode.Edit("group-1"), isLoading = true),
        savedState = SavedStateHandle(),
        groupGateway = FakeGroupGateway(),
        profileGateway = profile,
        timeZonePort = FakeGroupSystemTimeZonePort(),
        athleteGateway = FakeAthleteGateway(),
        positionSlotsGateway = slots,
    )

    private fun TestScope.collect(viewModel: GroupSetupViewModel): List<GroupSetupEffect> {
        val received = mutableListOf<GroupSetupEffect>()
        backgroundScope.launch(mainDispatcher) { viewModel.effects.toList(received) }
        return received
    }

    @Test
    fun `5-1 group shows the position card and total stays while no position is filled`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        runCurrent()
        val state = viewModel.state.value
        assertTrue(state.showsPositionSlots)
        assertFalse(state.usesPositionSlots)
        assertEquals(emptyMap(), state.positionSlots)
    }

    @Test
    fun `6-0 group and athletes do not get the card nor read positions`() = runTest(mainDispatcher) {
        val sixZero = viewModel(playStyle = GroupPlayStyle.SIX_ZERO)
        runCurrent()
        assertFalse(sixZero.state.value.showsPositionSlots)
        val gateway = FakePositionSlotsGateway()
        val athlete = viewModel(role = GroupRole.ATHLETE, slots = gateway)
        runCurrent()
        assertFalse(athlete.state.value.showsPositionSlots)
        assertEquals(0, gateway.reads)
    }

    @Test
    fun `loaded positions replace the total field`() = runTest(mainDispatcher) {
        val viewModel = viewModel(slots = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS))
        runCurrent()
        val state = viewModel.state.value
        assertTrue(state.usesPositionSlots)
        assertEquals(12, state.positionsTotal)
        // Posição com 0 vagas não entra no mapa.
        assertNull(state.positionSlots[AthletePosition.LIBERO])
    }

    @Test
    fun `suggestion fills the positions from the backend table`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.SuggestPositionSlots)
        runCurrent()
        assertEquals(12, viewModel.state.value.positionsTotal)
        assertFalse(viewModel.state.value.isSuggestingPositions)
    }

    @Test
    fun `suggestion failure keeps the positions and says so`() = runTest(mainDispatcher) {
        val viewModel = viewModel(slots = FakePositionSlotsGateway(failSuggestion = true))
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.SuggestPositionSlots)
        runCurrent()
        assertTrue(viewModel.state.value.positionSuggestionFailed)
        assertEquals(emptyMap(), viewModel.state.value.positionSlots)
    }

    @Test
    fun `stepper edits a position and zero removes it`() = runTest(mainDispatcher) {
        val viewModel = viewModel()
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 4))
        assertEquals(mapOf(AthletePosition.PONTA to 4), viewModel.state.value.positionSlots)
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 0))
        assertEquals(emptyMap(), viewModel.state.value.positionSlots)
    }

    @Test
    fun `save writes the positions with the version the profile save returned`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway()
        val profile = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(group(GroupPlayStyle.FIVE_ONE)),
            updateResult = SaqzResult.Success(group(GroupPlayStyle.FIVE_ONE).copy(versionToken = GroupVersionToken("etag-3"))),
        )
        val viewModel = viewModel(slots = gateway, profile = profile)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 4))
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.CENTRAL, 4))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val (token, config) = gateway.writes.single()
        assertEquals(GroupVersionToken("etag-3"), token)
        assertEquals(PositionGameSystem.FIVE_ONE, config.gameSystem)
        assertEquals(listOf(AthletePosition.CENTRAL to 4, AthletePosition.PONTA to 4).sortedBy { it.first.ordinal }, config.slots.map { it.position to it.slots })
        // O perfil leva o total como soma das posições.
        assertEquals(8, profile.lastUpdateCommand?.form?.defaultCapacity)
        assertEquals(listOf<GroupSetupEffect>(GroupSetupEffect.Saved), effects)
    }

    @Test
    fun `save without changing the positions does not write them`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS)
        val viewModel = viewModel(slots = gateway)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertEquals(0, gateway.writes.size)
        assertEquals(listOf<GroupSetupEffect>(GroupSetupEffect.Saved), effects)
    }

    @Test
    fun `switching to 6-0 clears the positions on save`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS)
        val viewModel = viewModel(slots = gateway)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.SelectPlayStyle(br.com.saqz.groups.model.GroupPlayStyle.SIX_ZERO))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        val config = gateway.writes.single().second
        assertEquals(emptyList(), config.slots)
        assertEquals(PositionGameSystem.SIX_ZERO, config.gameSystem)
    }

    @Test
    fun `a failed positions write keeps the screen open with the error and no Saved effect`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway(failWrite = true)
        val viewModel = viewModel(slots = gateway)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 4))
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.CENTRAL, 4))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertTrue(viewModel.state.value.saveFailed)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `a total under four is refused before saving`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway()
        val viewModel = viewModel(slots = gateway)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 2))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertTrue(GroupSetupError.PositionSlotsTotalOutOfRange in viewModel.state.value.errors)
        assertEquals(0, gateway.writes.size)
    }

    private companion object {
        val FIVE_ONE_TWO_TEAMS = GroupPositionSlots(
            PositionGameSystem.FIVE_ONE,
            listOf(
                PositionSlot(AthletePosition.PONTA, 4), PositionSlot(AthletePosition.CENTRAL, 4),
                PositionSlot(AthletePosition.OPOSTO, 2), PositionSlot(AthletePosition.LEVANTADOR, 2),
                PositionSlot(AthletePosition.LIBERO, 0),
            ),
        )
    }
}
