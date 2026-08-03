package br.com.saqz.groups.presentation.list

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleGroup
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class GroupListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    // Sem plano entitulador: o "+" cai no Fluxo 8, como antes da porta existir.
    private val noPlan = GroupCreationEntitlement { false }

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads one card from own memberships and group read`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                br.com.saqz.groups.domain.athlete.OwnAthleteProfile(
                    userId = "me",
                    displayName = "Bruno",
                    phone = null,
                    memberships = listOf(
                        OwnAthleteMembership(
                            groupId = br.com.saqz.domain.GroupId("group-1"),
                            groupName = "Old name",
                            role = GroupRole.ADMIN,
                            position = null,
                            membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.MENSALISTA,
                            active = true,
                        ),
                    ),
                ),
            ),
        )
        val viewModel = GroupListViewModel(athlete, FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())), noPlan)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })
        assertEquals("Vôlei do CERET", viewModel.state.value.groups.single().name)
        assertEquals("Quadra · São Paulo", viewModel.state.value.groups.single().meta)
        assertNull(viewModel.state.value.groups.single().nextGame)
    }

    @Test
    fun `empty own profile becomes the first access state`() = runTest {
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), noPlan)

        assertTrue(viewModel.state.value.isEmpty)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `gateway failure is visible and maps forbidden`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Failure(
                br.com.saqz.groups.domain.athlete.AthleteError.DataFailure(DataError.Forbidden),
            ),
        )
        val viewModel = GroupListViewModel(athlete, FakeGroupGateway(), noPlan)

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `group read failure is visible and does not leak the domain error`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                br.com.saqz.groups.domain.athlete.OwnAthleteProfile(
                    "me",
                    "Bruno",
                    null,
                    listOf(
                        OwnAthleteMembership(
                            br.com.saqz.domain.GroupId("group-1"),
                            "Group",
                            GroupRole.ATHLETE,
                            null,
                            br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO,
                            true,
                        ),
                    ),
                ),
            ),
        )
        val gateway = FakeGroupGateway(
            readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.NotFound)),
        )
        val viewModel = GroupListViewModel(athlete, gateway, noPlan)

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.NotFound, viewModel.state.value.error)
    }

    @Test
    fun `navigation effects remain available after loading`() = runTest {
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), noPlan)

        viewModel.onIntent(GroupListIntent.OpenGroup("group-1"))
        assertEquals(GroupListEffect.OpenGroup("group-1"), viewModel.effects.first())

        viewModel.onIntent(GroupListIntent.CreateGroup)
        assertEquals(GroupListEffect.OpenPlans, viewModel.effects.first())
    }

    // Com plano ativo e vaga, o "+" de 2n atalha para o formulário 2a.
    @Test
    fun `entitled member creating a group goes straight to the form`() = runTest {
        val viewModel = GroupListViewModel(FakeAthleteGateway(), FakeGroupGateway(), GroupCreationEntitlement { true })

        viewModel.onIntent(GroupListIntent.CreateGroup)

        assertEquals(GroupListEffect.OpenCreateGroup, viewModel.effects.first())
    }

    // O segundo toque enquanto a checagem está em voo é descartado — uma consulta, um efeito.
    @Test
    fun `tapping create again while the check is in flight emits exactly one effect`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        var calls = 0
        val viewModel = GroupListViewModel(
            FakeAthleteGateway(),
            FakeGroupGateway(),
            GroupCreationEntitlement { calls++; gate.await() },
        )

        viewModel.onIntent(GroupListIntent.CreateGroup)
        viewModel.onIntent(GroupListIntent.CreateGroup)
        gate.complete(true)

        assertEquals(1, calls)
        assertEquals(GroupListEffect.OpenCreateGroup, viewModel.effects.first())
    }

    @Test
    fun `a pending invite is not first access`() {
        val state = GroupListState(isLoading = false, invite = GroupInviteUi("invite", "Grupo", "Admin"))
        assertFalse(state.isEmpty)
    }
}
