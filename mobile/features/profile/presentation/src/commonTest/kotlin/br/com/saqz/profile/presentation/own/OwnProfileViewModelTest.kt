package br.com.saqz.profile.presentation.own

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.profile.domain.Profile
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.fake.FakeProfileGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class OwnProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `athlete profile and monthly payments open their real destinations`() = runTest(dispatcher) {
        val vm = OwnProfileViewModel(FakeProfileGateway())
        vm.onIntent(OwnProfileIntent.OpenGroup("my-group"))
        advanceUntilIdle()
        assertEquals(OwnProfileEffect.OpenAthleteProfile("my-group"), vm.effects.first())
        vm.onIntent(OwnProfileIntent.OpenReceipts)
        assertEquals(OwnProfileEffect.OpenReceipts, vm.effects.first())
        vm.onIntent(OwnProfileIntent.OpenMonthlyPayments)
        advanceUntilIdle()
        assertEquals(OwnProfileEffect.OpenMonthlyPayments, vm.effects.first())
        vm.onIntent(OwnProfileIntent.OpenSettings)
        advanceUntilIdle()
        assertEquals(OwnProfileEffect.OpenSettings, vm.effects.first())
        vm.onIntent(OwnProfileIntent.OpenNotifications)
        advanceUntilIdle()
        assertEquals(OwnProfileEffect.OpenNotifications, vm.effects.first())
        vm.onIntent(OwnProfileIntent.OpenHelp)
        assertEquals(OwnProfileEffect.OpenHelp, vm.effects.first())
        vm.onIntent(OwnProfileIntent.OpenTerms)
        assertEquals(OwnProfileEffect.OpenTerms, vm.effects.first())
    }

    @Test
    fun `null attendance hides the attendance stat`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply { stats = stats.copy(attendanceRate = null) }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.showAttendance)
        assertNull(viewModel.state.value.stats?.attendanceLabel)
    }

    @Test
    fun `games played come from the server number without local counting`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply { stats = stats.copy(games = 0) }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertEquals("0", viewModel.state.value.stats?.gamesLabel)
    }

    @Test
    fun `empty memberships expose the empty groups state`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply {
            athleteProfile = athleteProfile.copy(memberships = emptyList())
        }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isEmpty)
        assertTrue(viewModel.state.value.groups.isEmpty())
    }

    @Test
    fun `gateway failure exposes the error state without leaking data error`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply {
            statsError = ProfileError.DataFailure(DataError.Connectivity)
        }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.loadError)
        assertNull(viewModel.state.value.stats)
    }

    @Test
    fun `refresh reloads the profile data after returning from the editor`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()
        gateway.profile = gateway.profile.copy(
            user = gateway.profile.user.copy(
                displayName = "Novo nome",
                nickname = "Novo apelido",
                city = "Rio de Janeiro",
                photoUrl = "/api/session/photo?v=novo",
            ),
        )

        viewModel.onIntent(OwnProfileIntent.Refresh)
        advanceUntilIdle()

        assertEquals("Novo nome", viewModel.state.value.user?.displayName)
        assertEquals("Novo apelido · Rio de Janeiro", viewModel.state.value.user?.subtitle)
        assertEquals("/api/session/photo?v=novo", viewModel.state.value.user?.photoUrl)
    }

    @Test
    fun `a refresh requested during a load runs when it finishes and shows the newer data`() = runTest(dispatcher) {
        val fake = FakeProfileGateway()
        val firstLoad = CompletableDeferred<Unit>()
        var bootstrapCalls = 0
        val gateway = object : ProfileGateway by fake {
            override suspend fun bootstrap(): SaqzResult<Profile, ProfileError> {
                // Só a primeira carga espera o teste mandar; ela devolve o que a ViewModel leu ANTES da edição.
                if (++bootstrapCalls == 1) {
                    val stale = fake.profile
                    firstLoad.await()
                    return SaqzResult.Success(stale)
                }
                return fake.bootstrap()
            }
        }
        val viewModel = OwnProfileViewModel(gateway)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isLoading)

        // Primeiro "apareceu" é o do Root logo após o `init` (descartado); o segundo é a volta do editor salvo.
        viewModel.onIntent(OwnProfileIntent.Appeared)
        fake.profile = fake.profile.copy(user = fake.profile.user.copy(displayName = "Novo nome"))
        viewModel.onIntent(OwnProfileIntent.Appeared)
        advanceUntilIdle()
        assertEquals(1, bootstrapCalls)

        firstLoad.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, bootstrapCalls)
        assertEquals("Novo nome", viewModel.state.value.user?.displayName)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `appearing again with nothing in flight reloads once and does not loop`() = runTest(dispatcher) {
        var bootstrapCalls = 0
        val fake = FakeProfileGateway()
        val gateway = object : ProfileGateway by fake {
            override suspend fun bootstrap(): SaqzResult<Profile, ProfileError> {
                bootstrapCalls++
                return fake.bootstrap()
            }
        }
        val viewModel = OwnProfileViewModel(gateway)
        advanceUntilIdle()
        viewModel.onIntent(OwnProfileIntent.Appeared)
        viewModel.onIntent(OwnProfileIntent.Appeared)
        advanceUntilIdle()

        assertEquals(2, bootstrapCalls)
    }

    @Test
    fun `pull refresh keeps the profile and ends not refreshing`() = runTest(dispatcher) {
        val fake = FakeProfileGateway()
        val held = CompletableDeferred<Unit>()
        var bootstrapCalls = 0
        val gateway = object : ProfileGateway by fake {
            override suspend fun bootstrap(): SaqzResult<Profile, ProfileError> {
                if (++bootstrapCalls > 1) held.await()
                return fake.bootstrap()
            }
        }
        val viewModel = OwnProfileViewModel(gateway)
        advanceUntilIdle()
        fake.profile = fake.profile.copy(user = fake.profile.user.copy(displayName = "Novo nome"))

        viewModel.onIntent(OwnProfileIntent.PullRefresh)
        viewModel.onIntent(OwnProfileIntent.PullRefresh)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isRefreshing)
        assertFalse(viewModel.state.value.isLoading)
        assertNotNull(viewModel.state.value.user)

        held.complete(Unit)
        advanceUntilIdle()
        // Dois puxões seguidos = uma busca.
        assertEquals(2, bootstrapCalls)
        assertFalse(viewModel.state.value.isRefreshing)
        assertEquals("Novo nome", viewModel.state.value.user?.displayName)
    }

    @Test
    fun `pull refresh failure keeps the profile and warns`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = OwnProfileViewModel(gateway)
        advanceUntilIdle()
        gateway.statsError = ProfileError.DataFailure(DataError.Connectivity)

        viewModel.onIntent(OwnProfileIntent.PullRefresh)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isRefreshing)
        assertFalse(state.loadError)
        assertTrue(state.refreshFailed)
        assertNotNull(state.user)
        assertNotNull(state.stats)
        viewModel.onIntent(OwnProfileIntent.DismissRefreshFailed)
        assertFalse(viewModel.state.value.refreshFailed)
    }

    @Test
    fun `subtitle keeps only the nickname when city is null`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply {
            profile = profile.copy(user = profile.user.copy(nickname = "Rafa", city = null))
        }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertEquals("Rafa", viewModel.state.value.user?.subtitle)
    }

    @Test
    fun `subtitle keeps only the city when nickname is null`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply {
            profile = profile.copy(user = profile.user.copy(nickname = null, city = "Santos"))
        }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertEquals("Santos", viewModel.state.value.user?.subtitle)
    }

    @Test
    fun `subtitle is absent when nickname and city are null`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply {
            profile = profile.copy(user = profile.user.copy(nickname = null, city = null))
        }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertNull(viewModel.state.value.user?.subtitle)
    }

    @Test
    fun `edit data emits the editor exit`() = runTest(dispatcher) {
        val viewModel = OwnProfileViewModel(FakeProfileGateway())

        viewModel.onIntent(OwnProfileIntent.EditData)

        assertEquals(OwnProfileEffect.OpenEditor, viewModel.effects.first())
    }

    @Test
    fun `opening my plan emits the subscription exit`() = runTest(dispatcher) {
        val viewModel = OwnProfileViewModel(FakeProfileGateway())

        viewModel.onIntent(OwnProfileIntent.OpenMyPlan)

        assertEquals(OwnProfileEffect.OpenMyPlan, viewModel.effects.first())
    }

    @Test
    fun `change password emits the password recovery exit`() = runTest(dispatcher) {
        val viewModel = OwnProfileViewModel(FakeProfileGateway())

        viewModel.onIntent(OwnProfileIntent.ChangePassword)

        assertEquals(OwnProfileEffect.OpenPasswordRecovery, viewModel.effects.first())
    }

    @Test
    fun `sign out emits the logout exit`() = runTest(dispatcher) {
        val viewModel = OwnProfileViewModel(FakeProfileGateway())

        viewModel.onIntent(OwnProfileIntent.SignOut)

        assertEquals(OwnProfileEffect.SignedOut, viewModel.effects.first())
    }
}
