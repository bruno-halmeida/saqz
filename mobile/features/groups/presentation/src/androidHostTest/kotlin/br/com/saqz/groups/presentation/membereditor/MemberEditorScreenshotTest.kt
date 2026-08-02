package br.com.saqz.groups.presentation.membereditor

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
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
class MemberEditorScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun monthly() = capture("3g-mensalista") {
        MemberEditorScreen(memberEditorPreviewState, {}, {})
    }

    @Test
    fun singleGame() = capture("3g-avulso") {
        MemberEditorScreen(memberEditorPreviewState.copy(membershipType = AthleteMembershipType.AVULSO), {}, {})
    }

    @Test
    fun removeSheet() = capture("3h-remover") {
        MemberEditorScreen(memberEditorPreviewState.copy(removeSheetOpen = true), {}, {})
    }

    @Test
    fun billingSheet() = capture("3i-mensalista") {
        MemberEditorScreen(
            memberEditorPreviewState.copy(
                membershipType = AthleteMembershipType.AVULSO,
                billingSheetOpen = true,
                billingAmountText = "85,00",
                billingDueDay = 10,
            ),
            {},
            {},
        )
    }

    @Test
    fun lowerHalf() = capture("3g-baixo", scrollToBottom = true) {
        MemberEditorScreen(memberEditorPreviewState, {}, {})
    }

    private fun capture(name: String, scrollToBottom: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    content()
                }
            }
        }
        if (scrollToBottom) {
            compose.onNodeWithTag(MemberEditorTags.Screen).performTouchInput { swipeUp() }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-144/$name.png")
    }
}
