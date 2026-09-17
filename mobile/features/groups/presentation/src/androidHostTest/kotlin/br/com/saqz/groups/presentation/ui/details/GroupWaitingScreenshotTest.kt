package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Os sete estados do bloco "Esperando você". Estado fora da cena é estado não conferido
// (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupWaitingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun idle() = compose.captureDetails("group-details-waiting-idle", GroupWaitingPreviewData.idle, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun notifying() = compose.captureDetails("group-details-waiting-notifying", GroupWaitingPreviewData.notifying, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun notified() = compose.captureDetails("group-details-waiting-notified", GroupWaitingPreviewData.notified, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun failed() = compose.captureDetails("group-details-waiting-failed", GroupWaitingPreviewData.failed, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun closed() = compose.captureDetails("group-details-waiting-closed", GroupWaitingPreviewData.closed, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun quorumOnly() = compose.captureDetails("group-details-waiting-quorum-only", GroupWaitingPreviewData.quorumOnly, "waiting")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun financeFailed() =
        compose.captureDetails("group-details-waiting-finance-failed", GroupWaitingPreviewData.financeFailed, "waiting")
}
