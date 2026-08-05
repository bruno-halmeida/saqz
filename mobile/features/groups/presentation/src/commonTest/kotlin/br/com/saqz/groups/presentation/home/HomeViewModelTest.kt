package br.com.saqz.groups.presentation.home

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.home.HomeError
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeMemberGroup
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.GroupUiError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success keeps the home aggregate and greeting profile name`() = runTest {
        val home = sampleHome()
        val viewModel = HomeViewModel(
            homeGateway = FakeHomeGateway(SaqzResult.Success(home)),
            athleteGateway = FakeAthleteGateway(
                ownProfileResult = SaqzResult.Success(OwnAthleteProfile("me", "Bruna", null, emptyList())),
            ),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Bruna", viewModel.state.value.displayName)
        assertEquals(home, viewModel.state.value.home)
        assertFalse(viewModel.state.value.loadFailed)
    }

    @Test
    fun `home failure exposes the standard retry error`() = runTest {
        val viewModel = HomeViewModel(
            homeGateway = FakeHomeGateway(SaqzResult.Failure(HomeError.Data(DataError.Server))),
            athleteGateway = FakeAthleteGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.Network, viewModel.state.value.error)
    }

    @Test
    fun `retry ignores a slower response from the previous generation`() = runTest {
        val first = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val second = CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>()
        val viewModel = HomeViewModel(
            homeGateway = FakeHomeGateway(first, second),
            athleteGateway = FakeAthleteGateway(),
        )

        viewModel.onIntent(HomeIntent.Retry)
        second.complete(SaqzResult.Success(sampleHome("second")))
        first.complete(SaqzResult.Success(sampleHome("first")))

        assertEquals("second", viewModel.state.value.home?.member?.groups?.singleOrNull()?.id?.value)
    }

    private fun sampleHome(id: String = "group-1") = HomeReadModel(
        member = HomeMemberReadModel(
            nextGame = null,
            lastCompletedGame = null,
            groups = listOf(
                HomeMemberGroup(
                    id = GroupId(id),
                    name = "Vôlei do CERET",
                    role = GroupRole.ATHLETE,
                    memberCount = 12,
                    gamesPlayed = 3,
                ),
            ),
        ),
        admin = null,
    )
}

private class FakeHomeGateway(
    private var immediateResult: SaqzResult<HomeReadModel, HomeError>? = null,
    private vararg val deferredResults: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
) : HomeGateway {
    override suspend fun read(): SaqzResult<HomeReadModel, HomeError> =
        deferredResults.getOrNull(reads++)?.await() ?: checkNotNull(immediateResult)

    private var reads = 0

    constructor(result: SaqzResult<HomeReadModel, HomeError>) : this(immediateResult = result)

    constructor(
        first: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
        second: CompletableDeferred<SaqzResult<HomeReadModel, HomeError>>,
    ) : this(deferredResults = arrayOf(first, second))
}
