package br.com.saqz.groups.presentation.athleteregistration

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

private const val GROUP_ID = "group-1"

@OptIn(ExperimentalCoroutinesApi::class)
class AthleteRegistrationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `quadra loads group header and prefilled own attributes`() = runTest(dispatcher) {
        val viewModel = viewModel(
            membership = membership(
                nickname = "Bruninho",
                position = AthletePosition.LEVANTADOR,
                secondaryPosition = AthletePosition.PONTA,
                level = AthleteLevel.INTERMEDIARIO,
                heightCm = 184,
            ),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isCourt)
        assertEquals("Vôlei do CERET", viewModel.state.value.groupName)
        assertEquals("São Paulo", viewModel.state.value.city)
        assertEquals("Bruninho", viewModel.state.value.nickname)
        assertEquals(AthletePosition.LEVANTADOR, viewModel.state.value.position)
        assertEquals(AthletePosition.PONTA, viewModel.state.value.secondaryPosition)
        assertEquals(AthleteLevel.INTERMEDIARIO, viewModel.state.value.level)
        assertEquals("184", viewModel.state.value.heightText)
    }

    @Test
    fun `beach loads side and does not expose court-only command fields`() = runTest(dispatcher) {
        val group = sampleGroup(
            profile = sampleGroup().profile!!.copy(
                modality = GroupModality.BEACH_VOLLEYBALL,
                composition = GroupComposition.WOMEN,
            ),
        )
        val athleteGateway = FakeAthleteGateway(
            ownProfileResult = ownProfile(
                membership(
                    position = AthletePosition.CENTRAL,
                    secondaryPosition = AthletePosition.PONTA,
                    preferredSide = AthletePreferredSide.DIREITA,
                    heightCm = 182,
                ),
            ),
        )
        val viewModel = viewModel(
            group = group,
            athleteGateway = athleteGateway,
        )

        assertFalse(viewModel.state.value.isCourt)
        assertEquals(AthletePreferredSide.DIREITA, viewModel.state.value.preferredSide)

        viewModel.onIntent(AthleteRegistrationIntent.Save)

        assertNull(viewModel.state.value.error)
        assertNull(athleteGateway.lastUpdateOwnProfileCommand?.position)
        assertNull(athleteGateway.lastUpdateOwnProfileCommand?.secondaryPosition)
        assertEquals(AthletePreferredSide.DIREITA, athleteGateway.lastUpdateOwnProfileCommand?.preferredSide)
        assertNull(athleteGateway.lastUpdateOwnProfileCommand?.heightCm)
    }

    @Test
    fun `secondary position excludes the principal and toggles off`() = runTest(dispatcher) {
        val viewModel = viewModel(membership = membership(position = AthletePosition.PONTA))

        viewModel.onIntent(AthleteRegistrationIntent.SecondaryPositionSelected(AthletePosition.CENTRAL))
        assertEquals(AthletePosition.CENTRAL, viewModel.state.value.secondaryPosition)

        viewModel.onIntent(AthleteRegistrationIntent.SecondaryPositionSelected(AthletePosition.CENTRAL))
        assertNull(viewModel.state.value.secondaryPosition)

        viewModel.onIntent(AthleteRegistrationIntent.SecondaryPositionSelected(AthletePosition.PONTA))
        assertNull(viewModel.state.value.secondaryPosition)

        viewModel.onIntent(AthleteRegistrationIntent.PositionSelected(AthletePosition.CENTRAL))
        assertEquals(AthletePosition.CENTRAL, viewModel.state.value.position)
        assertNull(viewModel.state.value.secondaryPosition)
    }

    @Test
    fun `successful save sends normalized profile and emits completion`() = runTest(dispatcher) {
        val athleteGateway = FakeAthleteGateway(
            ownProfileResult = ownProfile(membership()),
        )
        val viewModel = viewModel(athleteGateway = athleteGateway)
        val effects = viewModel.effects

        viewModel.onIntent(AthleteRegistrationIntent.NicknameChanged("  Rafinha  "))
        viewModel.onIntent(AthleteRegistrationIntent.PositionSelected(AthletePosition.LEVANTADOR))
        viewModel.onIntent(AthleteRegistrationIntent.SecondaryPositionSelected(AthletePosition.PONTA))
        viewModel.onIntent(AthleteRegistrationIntent.LevelSelected(AthleteLevel.AVANCADO))
        viewModel.onIntent(AthleteRegistrationIntent.HeightChanged("190"))
        viewModel.onIntent(AthleteRegistrationIntent.Save)

        assertFalse(viewModel.state.value.isSaving)
        assertEquals(AthleteRegistrationEffect.Saved, effects.first())
        assertEquals(
            "Rafinha",
            athleteGateway.lastUpdateOwnProfileCommand?.nickname,
        )
        assertEquals(AthletePosition.LEVANTADOR, athleteGateway.lastUpdateOwnProfileCommand?.position)
        assertEquals(AthletePosition.PONTA, athleteGateway.lastUpdateOwnProfileCommand?.secondaryPosition)
        assertEquals(AthleteLevel.AVANCADO, athleteGateway.lastUpdateOwnProfileCommand?.level)
        assertEquals(190, athleteGateway.lastUpdateOwnProfileCommand?.heightCm)
        assertNull(athleteGateway.lastUpdateOwnProfileCommand?.preferredSide)
    }

    @Test
    fun `patch failure remains visible and does not emit completion`() = runTest(dispatcher) {
        val viewModel = viewModel(
            athleteGateway = FakeAthleteGateway(
                ownProfileResult = ownProfile(membership()),
                updateOwnProfileResult = SaqzResult.Failure(
                    AthleteError.DataFailure(DataError.Server),
                ),
            ),
        )

        viewModel.onIntent(AthleteRegistrationIntent.Save)

        assertFalse(viewModel.state.value.isSaving)
        assertEquals(GroupUiError.Network, viewModel.state.value.error)
    }

    @Test
    fun `typed patch validation failure remains visible as generic validation error`() = runTest(dispatcher) {
        val viewModel = viewModel(
            athleteGateway = FakeAthleteGateway(
                ownProfileResult = ownProfile(membership()),
                updateOwnProfileResult = SaqzResult.Failure(
                    AthleteError.Validation(
                        ValidationDetails(
                            globalMessages = listOf("invalid"),
                            fieldMessages = mapOf("position" to listOf("invalid")),
                        ),
                    ),
                ),
            ),
        )

        viewModel.onIntent(AthleteRegistrationIntent.Save)

        assertEquals(GroupUiError.Validation, viewModel.state.value.error)
    }

    private fun viewModel(
        group: br.com.saqz.groups.domain.group.Group = sampleGroup(),
        membership: OwnAthleteMembership = membership(),
        athleteGateway: FakeAthleteGateway = FakeAthleteGateway(ownProfileResult = ownProfile(membership)),
    ) = AthleteRegistrationViewModel(
        groupId = GROUP_ID,
        savedState = SavedStateHandle(),
        groupGateway = FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup(group))),
        athleteGateway = athleteGateway,
    )

    private fun membership(
        nickname: String? = null,
        position: AthletePosition? = AthletePosition.PONTA,
        secondaryPosition: AthletePosition? = null,
        level: AthleteLevel? = AthleteLevel.INICIANTE,
        preferredSide: AthletePreferredSide? = null,
        heightCm: Int? = null,
    ) = OwnAthleteMembership(
        groupId = GroupId(GROUP_ID),
        groupName = "Vôlei do CERET",
        role = br.com.saqz.groups.domain.group.GroupRole.ATHLETE,
        position = position,
        membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO,
        active = true,
        nickname = nickname,
        secondaryPosition = secondaryPosition,
        level = level,
        preferredSide = preferredSide,
        heightCm = heightCm,
    )

    private fun ownProfile(membership: OwnAthleteMembership) =
        SaqzResult.Success(OwnAthleteProfile("me", "Bruno", null, listOf(membership)))
}
