package br.com.saqz.groups.presentation.details

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.GroupProfileError
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupProfileGateway
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
import kotlin.test.assertTrue

private const val GROUP_ID = "group-1"

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads the group header and profile details`() = runTest {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertEquals("Misto · Intermediário", viewModel.state.value.header?.subtitle)
        assertEquals("CERET", viewModel.state.value.venue?.name)
        assertTrue(viewModel.state.value.header?.summaryChips?.isNotEmpty() == true)
    }

    @Test
    fun `empty profile still renders a usable header`() = runTest {
        val empty = sampleVersionedGroup(sampleGroup(profile = null))
        val viewModel = GroupDetailsViewModel(
            GROUP_ID,
            FakeGroupGateway(readResult = SaqzResult.Success(empty)),
            FakeGroupProfileGateway(readResult = SaqzResult.Success(empty)),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("Vôlei do CERET", viewModel.state.value.header?.name)
        assertTrue(viewModel.state.value.header?.summaryChips.isNullOrEmpty())
        assertEquals(null, viewModel.state.value.venue)
    }

    @Test
    fun `gateway failure is visible and typed`() = runTest {
        val viewModel = GroupDetailsViewModel(
            GROUP_ID,
            FakeGroupGateway(
                readResult = SaqzResult.Failure(GroupProfileError.DataFailure(DataError.Forbidden)),
            ),
            FakeGroupProfileGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `navigation effects remain available`() = runTest {
        val viewModel = viewModel()

        viewModel.onIntent(GroupDetailsIntent.ManageMembers)
        assertEquals(GroupDetailsEffect.OpenMembers(GROUP_ID), viewModel.effects.first())

        viewModel.onIntent(GroupDetailsIntent.OpenSchedule)
        assertEquals(GroupDetailsEffect.OpenSchedule(GROUP_ID), viewModel.effects.first())
    }

    private fun viewModel() = GroupDetailsViewModel(
        GROUP_ID,
        FakeGroupGateway(),
        FakeGroupProfileGateway(),
    )
}
