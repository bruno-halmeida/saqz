package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.appaccess.OnboardingError
import br.com.saqz.access.domain.appaccess.OnboardingGateway
import br.com.saqz.access.domain.appaccess.OnboardingStatus
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppOnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `skip saves completion and opens the form without creating a group`() = runTest {
        val gateway = FakeGateway()
        val viewModel = AppOnboardingViewModel(gateway) { "owner-a" }
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()
        gateway.statusResults.single().complete(SaqzResult.Success(OnboardingStatus(false)))
        runCurrent()
        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(AppOnboardingIntent.Skip)
        runCurrent()
        gateway.completeResult.complete(SaqzResult.Success(OnboardingStatus(true)))
        runCurrent()
        assertEquals(AppOnboardingEffect.OpenFirstGroupForm, effect.await())
        assertEquals(true, viewModel.state.value.completed)
        assertEquals(1, gateway.completeCalls)
    }

    @Test
    fun `already completed account closes intro without another completion write`() = runTest {
        val gateway = FakeGateway()
        val viewModel = AppOnboardingViewModel(gateway) { "owner-a" }
        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()
        gateway.statusResults.single().complete(SaqzResult.Success(OnboardingStatus(true)))
        runCurrent()
        assertEquals(AppOnboardingEffect.CloseIntro, effect.await())
        assertEquals(true, viewModel.state.value.completed)
        assertEquals(0, gateway.completeCalls)
    }

    @Test
    fun `logout discards completion and emits no navigation`() = runTest {
        var owner: String? = "owner-a"
        val gateway = FakeGateway()
        val viewModel = AppOnboardingViewModel(gateway) { owner }
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()
        gateway.statusResults.single().complete(SaqzResult.Success(OnboardingStatus(false)))
        runCurrent()
        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(AppOnboardingIntent.Continue)
        runCurrent()
        owner = null
        gateway.completeResult.complete(SaqzResult.Success(OnboardingStatus(true)))
        runCurrent()
        assertEquals(false, viewModel.state.value.completed)
        assertEquals(false, effect.isCompleted)
        effect.cancel()
    }

    @Test
    fun `incoherent completion response does not open form`() = runTest {
        val gateway = FakeGateway()
        val viewModel = AppOnboardingViewModel(gateway) { "owner-a" }
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()
        gateway.statusResults.single().complete(SaqzResult.Success(OnboardingStatus(false)))
        runCurrent()
        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(AppOnboardingIntent.Continue)
        runCurrent()
        gateway.completeResult.complete(SaqzResult.Success(OnboardingStatus(false)))
        runCurrent()
        assertEquals(OnboardingError.Data(br.com.saqz.domain.DataError.InvalidResponse), viewModel.state.value.failure)
        assertEquals(false, effect.isCompleted)
        effect.cancel()
    }

    @Test
    fun `does not request intro before verified owner is available`() = runTest {
        var owner: String? = null
        val gateway = FakeGateway()
        val viewModel = AppOnboardingViewModel(gateway) { owner }

        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()

        assertEquals(0, gateway.statusCalls)
        owner = "owner-a"
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()

        assertEquals(1, gateway.statusCalls)
    }

    @Test
    fun `late get from previous account cannot publish into the new owner`() = runTest {
        var owner = "owner-a"
        val gateway = FakeGateway()
        val viewModel = AppOnboardingViewModel(gateway) { owner }
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()

        owner = "owner-b"
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()
        gateway.statusResults[0].complete(SaqzResult.Success(OnboardingStatus(completed = true)))
        runCurrent()

        assertEquals(2, gateway.statusCalls)
        assertEquals(false, viewModel.state.value.completed)
        assertEquals(true, viewModel.state.value.isLoading)
    }

    @Test
    fun `continue completes once and emits one first-group navigation effect`() = runTest {
        val gateway = FakeGateway()
        val viewModel = AppOnboardingViewModel(gateway) { "owner-a" }
        viewModel.onIntent(AppOnboardingIntent.Opened)
        runCurrent()
        gateway.statusResults.single().complete(SaqzResult.Success(OnboardingStatus(false)))
        runCurrent()

        val effect = async { viewModel.effects.first() }
        viewModel.onIntent(AppOnboardingIntent.Continue)
        viewModel.onIntent(AppOnboardingIntent.Continue)
        runCurrent()
        gateway.completeResult.complete(SaqzResult.Success(OnboardingStatus(true)))
        runCurrent()

        assertEquals(1, gateway.completeCalls)
        assertEquals(AppOnboardingEffect.OpenFirstGroupForm, effect.await())
        assertEquals(true, viewModel.state.value.completed)
    }

    private class FakeGateway : OnboardingGateway {
        var statusCalls = 0
        var completeCalls = 0
        val statusResults = mutableListOf<CompletableDeferred<SaqzResult<OnboardingStatus, OnboardingError>>>()
        val completeResult = CompletableDeferred<SaqzResult<OnboardingStatus, OnboardingError>>()

        override suspend fun status(): SaqzResult<OnboardingStatus, OnboardingError> {
            statusCalls++
            return CompletableDeferred<SaqzResult<OnboardingStatus, OnboardingError>>().also {
                statusResults += it
            }.await()
        }

        override suspend fun complete(): SaqzResult<OnboardingStatus, OnboardingError> {
            completeCalls++
            return completeResult.await()
        }
    }
}
