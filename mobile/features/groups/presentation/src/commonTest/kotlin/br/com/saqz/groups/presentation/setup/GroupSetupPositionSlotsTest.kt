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
        var lastSuggestionTotal: Int? = null
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

        override suspend fun suggestion(system: PositionGameSystem, total: Int): SaqzResult<GroupPositionSlots, GroupProfileError> =
            if (failSuggestion.also { lastSuggestionTotal = total }) SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Server)) else SaqzResult.Success(suggestion)
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
    fun `5-1 group with positions shows the card and keeps the total field and the sum matches`() = runTest(mainDispatcher) {
        val viewModel = viewModel(slots = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS))
        runCurrent()
        val state = viewModel.state.value
        assertTrue(state.showsPositionSlots)
        assertEquals(12, state.positionsTotal)
        assertEquals(12, state.positionsTarget)
        assertEquals(0, state.positionsDifference)
        assertFalse(state.positionsBlockSave)
        // Posição com 0 vagas não entra no mapa.
        assertNull(state.positionSlots[AthletePosition.LIBERO])
    }

    @Test
    fun `5-1 group without positions asks the backend for the suggestion by the total`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway()
        val viewModel = viewModel(slots = gateway)
        runCurrent()
        assertEquals(12, gateway.lastSuggestionTotal)
        assertEquals(12, viewModel.state.value.positionsTotal)
        assertFalse(viewModel.state.value.positionsBlockSave)
    }

    @Test
    fun `suggestion failure leaves the positions empty and the save blocked`() = runTest(mainDispatcher) {
        val viewModel = viewModel(slots = FakePositionSlotsGateway(failSuggestion = true))
        runCurrent()
        assertTrue(viewModel.state.value.positionSuggestionFailed)
        assertEquals(emptyMap(), viewModel.state.value.positionSlots)
        assertTrue(viewModel.state.value.positionsBlockSave)
    }

    @Test
    fun `6-0 group and athletes do not get the card nor read positions`() = runTest(mainDispatcher) {
        val sixZero = viewModel(playStyle = GroupPlayStyle.SIX_ZERO)
        runCurrent()
        assertFalse(sixZero.state.value.showsPositionSlots)
        assertFalse(sixZero.state.value.positionsBlockSave)
        val gateway = FakePositionSlotsGateway()
        val athlete = viewModel(role = GroupRole.ATHLETE, slots = gateway)
        runCurrent()
        assertFalse(athlete.state.value.showsPositionSlots)
        assertEquals(0, gateway.reads)
    }

    @Test
    fun `without the gateway the screen behaves as before and never blocks`() = runTest(mainDispatcher) {
        val viewModel = GroupSetupViewModel(
            initialState = GroupSetupState(mode = GroupSetupMode.Edit("group-1"), isLoading = true),
            savedState = SavedStateHandle(),
            groupGateway = FakeGroupGateway(),
            profileGateway = FakeGroupProfileGateway(readResult = SaqzResult.Success(group(GroupPlayStyle.FIVE_ONE, GroupRole.OWNER))),
            timeZonePort = FakeGroupSystemTimeZonePort(),
            athleteGateway = FakeAthleteGateway(),
        )
        runCurrent()
        assertFalse(viewModel.state.value.showsPositionSlots)
        assertFalse(viewModel.state.value.positionsBlockSave)
    }

    @Test
    fun `editing a position shows what is missing or left over`() = runTest(mainDispatcher) {
        val viewModel = viewModel(slots = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS))
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 2))
        assertEquals(-2, viewModel.state.value.positionsDifference)
        assertTrue(viewModel.state.value.positionsBlockSave)
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.LIBERO, 4))
        assertEquals(2, viewModel.state.value.positionsDifference)
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.LIBERO, 2))
        assertEquals(0, viewModel.state.value.positionsDifference)
        assertFalse(viewModel.state.value.positionsBlockSave)
    }

    @Test
    fun `changing the total moves the target and the suggestion follows it`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS)
        val viewModel = viewModel(slots = gateway)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdateCapacity(18))
        assertEquals(-6, viewModel.state.value.positionsDifference)
        viewModel.onIntent(GroupSetupIntent.SuggestPositionSlots)
        runCurrent()
        assertEquals(18, gateway.lastSuggestionTotal)
    }

    @Test
    fun `save with a sum that differs from the total is refused and nothing is written`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS)
        val profile = FakeGroupProfileGateway(readResult = SaqzResult.Success(group(GroupPlayStyle.FIVE_ONE, GroupRole.OWNER)))
        val viewModel = viewModel(slots = gateway, profile = profile)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 2))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertTrue(GroupSetupError.PositionSlotsSumMismatch in viewModel.state.value.errors)
        assertEquals(0, gateway.writes.size)
        assertNull(profile.lastUpdateCommand)
    }

    @Test
    fun `save writes the edited positions after the profile with the version it returned`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS)
        val profile = FakeGroupProfileGateway(
            readResult = SaqzResult.Success(group(GroupPlayStyle.FIVE_ONE, GroupRole.OWNER)),
            updateResult = SaqzResult.Success(group(GroupPlayStyle.FIVE_ONE, GroupRole.OWNER).copy(versionToken = GroupVersionToken("etag-3"))),
        )
        val viewModel = viewModel(slots = gateway, profile = profile)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 2))
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.LIBERO, 2))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        val (token, config) = gateway.writes.single()
        assertEquals(GroupVersionToken("etag-3"), token)
        assertEquals(PositionGameSystem.FIVE_ONE, config.gameSystem)
        assertEquals(12, config.total)
        assertEquals(12, profile.lastUpdateCommand?.form?.defaultCapacity)
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
        val gateway = FakePositionSlotsGateway(stored = FIVE_ONE_TWO_TEAMS, failWrite = true)
        val viewModel = viewModel(slots = gateway)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 2))
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.LIBERO, 2))
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertTrue(viewModel.state.value.saveFailed)
        assertTrue(effects.isEmpty())
    }

    // --- Criação do grupo (DEB-102): o 5-1 já nasce com as vagas por posição ---

    private fun creating(
        slots: FakePositionSlotsGateway? = FakePositionSlotsGateway(),
        profile: FakeGroupProfileGateway = FakeGroupProfileGateway(),
        playStyle: br.com.saqz.groups.model.GroupPlayStyle = br.com.saqz.groups.model.GroupPlayStyle.SIX_ZERO,
    ) = GroupSetupViewModel(
        initialState = GroupSetupState(
            mode = GroupSetupMode.Create,
            form = br.com.saqz.groups.model.GroupSetupForm(
                name = "Vôlei do CERET",
                modality = br.com.saqz.groups.model.GroupModality.COURT_VOLLEYBALL,
                composition = br.com.saqz.groups.model.GroupComposition.MIXED,
                playStyle = playStyle,
                defaultCapacity = 12,
            ),
        ),
        savedState = SavedStateHandle(),
        groupGateway = FakeGroupGateway(),
        profileGateway = profile,
        timeZonePort = FakeGroupSystemTimeZonePort(),
        positionSlotsGateway = slots,
    )

    @Test
    fun `creating a 5-1 group shows the card with the suggestion for the total already filled`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway()
        val viewModel = creating(slots = gateway)
        runCurrent()
        assertFalse(viewModel.state.value.showsPositionSlots)

        viewModel.onIntent(GroupSetupIntent.SelectPlayStyle(br.com.saqz.groups.model.GroupPlayStyle.FIVE_ONE))
        runCurrent()

        assertTrue(viewModel.state.value.showsPositionSlots)
        assertEquals(12, gateway.lastSuggestionTotal)
        assertEquals(12, viewModel.state.value.positionsTotal)
        assertFalse(viewModel.state.value.positionsBlockSave)
    }

    @Test
    fun `creating with 6-0 or without the gateway never shows the card nor blocks`() = runTest(mainDispatcher) {
        val sixZero = creating()
        runCurrent()
        assertFalse(sixZero.state.value.showsPositionSlots)
        val noGateway = creating(slots = null, playStyle = br.com.saqz.groups.model.GroupPlayStyle.FIVE_ONE)
        runCurrent()
        assertFalse(noGateway.state.value.showsPositionSlots)
        assertFalse(noGateway.state.value.positionsBlockSave)
    }

    @Test
    fun `creating is refused while the sum differs from the total and nothing is created`() = runTest(mainDispatcher) {
        val profile = FakeGroupProfileGateway()
        val viewModel = creating(profile = profile, playStyle = br.com.saqz.groups.model.GroupPlayStyle.FIVE_ONE)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.SelectPlayStyle(br.com.saqz.groups.model.GroupPlayStyle.FIVE_ONE))
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.UpdatePositionSlots(AthletePosition.PONTA, 1))
        assertTrue(viewModel.state.value.positionsBlockSave)
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertTrue(GroupSetupError.PositionSlotsSumMismatch in viewModel.state.value.errors)
        assertTrue(profile.createCommands.isEmpty())
    }

    @Test
    fun `after creating the group the positions are written with the total and the version the server returns`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway()
        val profile = FakeGroupProfileGateway()
        val viewModel = creating(slots = gateway, profile = profile)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.SelectPlayStyle(br.com.saqz.groups.model.GroupPlayStyle.FIVE_ONE))
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()

        assertEquals(1, profile.createCommands.size)
        val (token, config) = gateway.writes.single()
        assertEquals(GroupVersionToken("etag-2"), token)
        assertEquals(PositionGameSystem.FIVE_ONE, config.gameSystem)
        assertEquals(12, config.total)
        assertEquals(listOf<GroupSetupEffect>(GroupSetupEffect.Created("group-1")), effects)
    }

    @Test
    fun `creating with 6-0 writes no positions`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway()
        val viewModel = creating(slots = gateway)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertEquals(0, gateway.writes.size)
        assertEquals(listOf<GroupSetupEffect>(GroupSetupEffect.Created("group-1")), effects)
    }

    @Test
    fun `a failed positions write after creating keeps the screen and the retry reuses the same group`() = runTest(mainDispatcher) {
        val gateway = FakePositionSlotsGateway(failWrite = true)
        val profile = FakeGroupProfileGateway()
        val viewModel = creating(slots = gateway, profile = profile)
        val effects = collect(viewModel)
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.SelectPlayStyle(br.com.saqz.groups.model.GroupPlayStyle.FIVE_ONE))
        runCurrent()
        viewModel.onIntent(GroupSetupIntent.Submit)
        runCurrent()
        assertTrue(viewModel.state.value.saveFailed)
        assertTrue(effects.isEmpty())

        gateway.failWrite = false
        viewModel.onIntent(GroupSetupIntent.Retry)
        runCurrent()

        // O mesmo pedido de criação (idempotente pela chave) e agora as posições gravam.
        assertEquals(2, profile.createCommands.size)
        assertEquals(profile.createCommands[0].commandKey, profile.createCommands[1].commandKey)
        assertEquals(listOf<GroupSetupEffect>(GroupSetupEffect.Created("group-1")), effects)
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
