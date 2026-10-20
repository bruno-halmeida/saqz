package br.com.saqz.groups.presentation.ui.monthlyinvite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.membership.MonthlyFeeDefault
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeGroupWhatsAppGateway
import br.com.saqz.groups.presentation.FakeMonthlyInviteGateway
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteIntent
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInvitePersonUi
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteState
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteToast
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** V1.15: o toast com "Desfazer" nunca pode ficar por cima de "Enviar N convites". */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class MonthlyInviteToastPlacementTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun close() { Dispatchers.resetMain() }

    private val removed = MonthlyInviteState(
        isLoading = false,
        groupName = "Vôlei da Quinta",
        senderName = "Lucas",
        defaultFee = MonthlyFeeDefault(8_000, 10),
        people = listOf(MonthlyInvitePersonUi("Diego Farias", "11982223344"), MonthlyInvitePersonUi("Marcos Vieira", "11973334455")),
        toast = MonthlyInviteToast.Removed("Larissa"),
    )

    @Test fun `toast stays above the send button with large font on a narrow screen`() = runComposeUiTest {
        narrow(fontScale = 2f) { MonthlyInviteScreen(state = removed, onIntent = {}, onBack = {}) }

        assertNoOverlap(onNodeWithTag(MonthlyInviteTags.Toast).getBoundsInRoot(), onNodeWithTag(MonthlyInviteTags.Send).getBoundsInRoot())
    }

    @Test fun `toast stays above the send button at normal size`() = runComposeUiTest {
        narrow(fontScale = 1f) { MonthlyInviteScreen(state = removed, onIntent = {}, onBack = {}) }

        assertNoOverlap(onNodeWithTag(MonthlyInviteTags.Toast).getBoundsInRoot(), onNodeWithTag(MonthlyInviteTags.Send).getBoundsInRoot())
    }

    @Test fun `undo brings the person back and never sends`() = runComposeUiTest {
        val gateway = FakeMonthlyInviteGateway()
        val viewModel = MonthlyInviteViewModel("group-1", gateway, FakeGroupGateway(), FakeAthleteGateway(), FakeGroupWhatsAppGateway())
        viewModel.onIntent(MonthlyInviteIntent.OpenSheet)
        viewModel.onIntent(MonthlyInviteIntent.UpdateName("Larissa Prado"))
        viewModel.onIntent(MonthlyInviteIntent.UpdatePhone("11911122233"))
        viewModel.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        viewModel.onIntent(MonthlyInviteIntent.Remove(0))
        narrow(fontScale = 2f) { Bound(viewModel) }

        onNodeWithTag(MonthlyInviteTags.Undo).performClick()
        waitForIdle()

        assertEquals(listOf("Larissa Prado"), viewModel.state.value.people.map { it.name })
        assertTrue(gateway.sends.isEmpty())
    }

    @Test fun `tapping where an expired toast was sends nothing`() = runComposeUiTest {
        val gateway = FakeMonthlyInviteGateway()
        val viewModel = MonthlyInviteViewModel("group-1", gateway, FakeGroupGateway(), FakeAthleteGateway(), FakeGroupWhatsAppGateway())
        viewModel.onIntent(MonthlyInviteIntent.OpenSheet)
        viewModel.onIntent(MonthlyInviteIntent.UpdateName("Larissa Prado"))
        viewModel.onIntent(MonthlyInviteIntent.UpdatePhone("11911122233"))
        viewModel.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        viewModel.onIntent(MonthlyInviteIntent.OpenSheet)
        viewModel.onIntent(MonthlyInviteIntent.UpdateName("Diego Farias"))
        viewModel.onIntent(MonthlyInviteIntent.UpdatePhone("11982223344"))
        viewModel.onIntent(MonthlyInviteIntent.Add(keepOpen = false))
        viewModel.onIntent(MonthlyInviteIntent.Remove(0))
        narrow(fontScale = 2f) { Bound(viewModel) }
        val undo = onNodeWithTag(MonthlyInviteTags.Undo).getBoundsInRoot()

        viewModel.onIntent(MonthlyInviteIntent.DismissToast)
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        onRoot().performTouchInput { click(with(density) { androidx.compose.ui.geometry.Offset(undo.center().x.toPx(), undo.center().y.toPx()) }) }
        waitForIdle()

        assertTrue(gateway.sends.isEmpty())
        assertFalse(viewModel.state.value.sending)
    }

    @Composable
    private fun Bound(viewModel: MonthlyInviteViewModel) {
        val state by viewModel.state.collectAsState()
        MonthlyInviteScreen(state = state, onIntent = viewModel::onIntent, onBack = {})
    }

    private fun ComposeUiTest.narrow(fontScale: Float, content: @Composable () -> Unit) = setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SaqzTheme { Box(Modifier.width(320.dp)) { content() } }
        }
    }

    private fun DpRect.center() = androidx.compose.ui.unit.DpOffset((left + right) / 2, (top + bottom) / 2)

    private fun assertNoOverlap(toast: DpRect, button: DpRect) {
        val a = Rect(toast.left.value, toast.top.value, toast.right.value, toast.bottom.value)
        val b = Rect(button.left.value, button.top.value, button.right.value, button.bottom.value)
        assertTrue(a.height > 0f && b.height > 0f, "toast $a, botão $b")
        assertFalse(a.overlaps(b), "toast $a cobre o botão $b")
        assertTrue(a.bottom <= b.top, "toast $a deveria ficar acima do botão $b")
    }
}
