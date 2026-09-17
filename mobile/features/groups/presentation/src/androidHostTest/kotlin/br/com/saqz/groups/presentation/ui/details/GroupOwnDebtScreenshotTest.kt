package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val Directory = "own-debt"

// Os dez estados de "Minhas cobranças" na tela inteira. Estado fora da cena é estado não
// conferido (AGENTS.md §11). A altura de 2400dp deixa a tela toda sem rolagem: o toque do
// histórico não desloca a captura.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupOwnDebtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun owesTwo() = compose.captureDetails("own-debt-owes-two", GroupOwnDebtPreviewData.owesTwo, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun owesOne() = compose.captureDetails("own-debt-owes-one", GroupOwnDebtPreviewData.owesOne, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun pixCopied() = compose.captureDetails("own-debt-pix-copied", GroupOwnDebtPreviewData.pixCopied, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun noPix() = compose.captureDetails("own-debt-no-pix", GroupOwnDebtPreviewData.noPix, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun historyOpen() = compose.captureDetailsAfterTap(
        name = "own-debt-history-open",
        state = GroupOwnDebtPreviewData.owesTwo,
        tag = GroupDetailsTags.OwnChargesHistoryToggle,
    )

    @Test
    @Config(qualifiers = "+h2400dp")
    fun settled() = compose.captureDetails("own-debt-settled", GroupOwnDebtPreviewData.settled, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun settledHistoryOpen() = compose.captureDetailsAfterTap(
        name = "own-debt-settled-history-open",
        state = GroupOwnDebtPreviewData.settled,
        tag = GroupDetailsTags.OwnChargesSettled,
    )

    @Test
    @Config(qualifiers = "+h2400dp")
    fun loading() = compose.captureDetails("own-debt-loading", GroupOwnDebtPreviewData.loading, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun failed() = compose.captureDetails("own-debt-failed", GroupOwnDebtPreviewData.failed, Directory)

    @Test
    @Config(qualifiers = "+h2400dp")
    fun adminOwes() = compose.captureDetails("own-debt-admin-owes", GroupOwnDebtPreviewData.adminOwes, Directory)
}

/** `captureDetails` com um toque antes da captura: o histórico é estado visual, não do ViewModel. */
private fun ComposeContentTestRule.captureDetailsAfterTap(name: String, state: GroupDetailsState, tag: String) {
    setContent {
        SaqzTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SaqzTheme.colors.background),
            ) {
                GroupDetailsScreen(state = state, onBack = {}, onIntent = {}, photoFailed = false)
            }
        }
    }
    onNodeWithTag(tag).performClick()
    onRoot().captureRoboImage("screenshots/$Directory/$name.png")
}
