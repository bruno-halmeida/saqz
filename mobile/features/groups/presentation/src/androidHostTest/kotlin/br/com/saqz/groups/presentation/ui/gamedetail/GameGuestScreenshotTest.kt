package br.com.saqz.groups.presentation.ui.gamedetail

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
@Suppress("TooManyFunctions")
class GameGuestScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h1400dp")
    fun empty() = capture("game-guest-empty", GameGuestPreviewData.empty)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun sheetEmpty() = capture("game-guest-sheet-empty", GameGuestPreviewData.sheetEmpty)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun sheetFilled() = capture("game-guest-sheet-filled", GameGuestPreviewData.sheetFilled)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun sheetAdding() = capture("game-guest-sheet-adding", GameGuestPreviewData.sheetAdding)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun sheetFailed() = capture("game-guest-sheet-failed", GameGuestPreviewData.sheetFailed)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun inQueue() = capture("game-guest-in-queue", GameGuestPreviewData.inQueue)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun twoGuests() = capture("game-guest-two-guests", GameGuestPreviewData.twoGuests)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun promoted() = capture("game-guest-promoted", GameGuestPreviewData.promoted)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun removeWaitlisted() = capture("game-guest-remove-waitlisted", GameGuestPreviewData.removeWaitlisted)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun removeConfirmed() = capture("game-guest-remove-confirmed", GameGuestPreviewData.removeConfirmed)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun othersView() = capture("game-guest-others-view", GameGuestPreviewData.othersView)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun closed() = capture("game-guest-closed", GameGuestPreviewData.closed)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun needAnswer() = capture("game-guest-need-answer", GameGuestPreviewData.needAnswer)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun noFee() = capture("game-guest-no-fee", GameGuestPreviewData.noFee)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun organizerQueue() = capture("game-guest-organizer-queue", GameGuestPreviewData.organizerQueue)

    private fun capture(name: String, state: GameDetailState) = capture(name) {
        GameDetailScreen(state = state, onBack = {}, onIntent = {})
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background),
                ) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/game-guest/$name.png")
    }
}
