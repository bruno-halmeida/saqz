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
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseStatus
import br.com.saqz.groups.presentation.gamedetail.GameDetailResponseUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
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
class GameDetailScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h1400dp")
    fun admin() = capture("game-detail-admin", GameDetailPreviewData.admin, directory = "vul-158")

    @Test
    @Config(qualifiers = "+h1400dp")
    fun capacitySheet() = capture(
        "game-detail-capacity",
        GameDetailPreviewData.admin.copy(capacitySheetOpen = true, capacityDraft = 14),
        directory = "vul-158",
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun member() = capture("game-detail-member", GameDetailPreviewData.admin.copy(isAdmin = false))

    @Test
    @Config(qualifiers = "+h1400dp")
    fun draft() = capture(
        "game-detail-draft",
        GameDetailPreviewData.admin.copy(
            header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Draft),
        ),
    )

    @Test
    fun loading() = capture("game-detail-loading", GameDetailState())

    @Test
    fun failed() = capture(
        "game-detail-failed",
        GameDetailState(isLoading = false, loadFailed = true, error = GroupUiError.AccessDenied),
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun cancelled() = capture(
        "game-detail-cancelled",
        GameDetailPreviewData.admin.copy(
            header = GameDetailPreviewData.header.copy(statusTone = GameDetailStatusTone.Cancelled),
        ),
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun cancelling() = capture(
        "game-detail-cancelling",
        GameDetailPreviewData.admin.copy(cancelDialogOpen = true, cancelling = true),
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun cancelFailed() = capture(
        "game-detail-cancel-failed",
        GameDetailPreviewData.admin.copy(cancelDialogOpen = true, cancelFailed = true),
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseConfirmedWithAutoConfirmation() = capture(
        "game-detail-response-confirmed-auto",
        GameDetailPreviewData.admin.copy(
            isAdmin = false,
            memberResponse = GameDetailResponseUi(GameDetailResponseStatus.Confirmed),
            membershipType = AthleteMembershipType.MENSALISTA,
            autoConfirmationVisible = true,
            autoConfirmationEnabled = true,
        ),
        directory = "vul-159",
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseWaitlisted() = capture(
        "game-detail-response-waitlisted",
        GameDetailPreviewData.admin.copy(
            isAdmin = false,
            memberResponse = GameDetailResponseUi(GameDetailResponseStatus.Waitlisted, 3),
            membershipType = AthleteMembershipType.AVULSO,
        ),
        directory = "vul-159",
    )

    @Test
    @Config(qualifiers = "+h1400dp")
    fun responseClosed() = capture(
        "game-detail-response-closed",
        GameDetailPreviewData.admin.copy(
            isAdmin = false,
            header = GameDetailPreviewData.header.copy(confirmationOpen = false),
            memberResponse = GameDetailResponseUi(GameDetailResponseStatus.Confirmed),
        ),
        directory = "vul-159",
    )

    private fun capture(name: String, state: GameDetailState, directory: String = "vul-154") = capture(name, directory) {
        GameDetailScreen(state = state, onBack = {}, onIntent = {})
    }

    private fun capture(name: String, directory: String, content: @Composable () -> Unit) {
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
        compose.onRoot().captureRoboImage("screenshots/$directory/$name.png")
    }
}
