package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupOnboarding
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
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7, application = android.app.Application::class)
class GroupOnboardingScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Test fun create() = capture(GroupOnboarding.CreateGame, "create")
    @Test fun invite() = capture(GroupOnboarding.InviteAthletes("game"), "invite")
    @Test fun finance() = capture(GroupOnboarding.ReviewFinances("game"), "finance")

    private fun capture(guide: GroupOnboarding, name: String) {
        compose.setContent {
            SaqzTheme {
                GroupDetailsScreen(
                    GroupDetailsPreviewData.admin.copy(
                        onboarding = guide,
                        nextGame = if (guide is GroupOnboarding.InviteAthletes) GroupDetailsPreviewData.member.nextGame else null,
                        attendance = if (guide is GroupOnboarding.InviteAthletes) GroupDetailsPreviewData.admin.attendance else null,
                        cashbox = null,
                    ), {}, {},
                )
            }
        }
        compose.onRoot().captureRoboImage("screenshots/onboarding/$name.png")
    }
}
