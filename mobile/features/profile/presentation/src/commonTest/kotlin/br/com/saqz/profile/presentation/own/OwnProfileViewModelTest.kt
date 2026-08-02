package br.com.saqz.profile.presentation.own

import br.com.saqz.domain.DataError
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.fake.FakeProfileGateway
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
    fun `null attendance hides the attendance stat`() = runTest(dispatcher) {
        val gateway = FakeProfileGateway().apply { stats = stats.copy(attendanceRate = null) }
        val viewModel = OwnProfileViewModel(gateway)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.showAttendance)
        assertNull(viewModel.state.value.stats?.attendanceLabel)
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
