package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupDetailsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // A tela rola: no telefone os dois estados carregados passam bem da dobra. A altura
    // sobe só na captura, para o print do PR mostrar a pilha inteira.
    @Test
    @Config(qualifiers = "+h1400dp")
    fun admin() = capture("group-details-admin", GroupDetailsPreviewData.admin)

    @Test
    @Config(qualifiers = "+h2000dp")
    fun member() = capture("group-details-member", GroupDetailsPreviewData.member)

    @Test
    @Config(qualifiers = "+h2000dp")
    fun memberResponse() = capture(
        "group-details-response-confirmed-auto",
        GroupDetailsPreviewData.member,
        directory = "vul-159",
    )

    @Test
    fun loading() = capture("group-details-loading", GroupDetailsState())

    @Test
    @Config(qualifiers = "+h1400dp")
    fun financeShortcut() = capture(
        name = "group-details-finance-shortcut",
        state = GroupDetailsPreviewData.admin,
        directory = "vul-178",
    )

    // VUL-203 — os quatro estados da seção de cobranças: com pendência (e Pix), quitada,
    // carregando e com falha. Estado fora da cena é estado não conferido (AGENTS.md §11).
    @Test
    @Config(qualifiers = "+h2400dp")
    fun ownCharges() = capture(
        name = "group-details-own-charges",
        state = GroupDetailsPreviewData.member,
        directory = "vul-203",
    )

    @Test
    @Config(qualifiers = "+h2400dp")
    fun ownChargesSettled() = capture(
        name = "group-details-own-charges-settled",
        state = GroupDetailsPreviewData.memberOwnChargesSettled,
        directory = "vul-203",
    )

    @Test
    @Config(qualifiers = "+h2000dp")
    fun ownChargesLoading() = capture(
        name = "group-details-own-charges-loading",
        state = GroupDetailsPreviewData.memberOwnChargesLoading,
        directory = "vul-203",
    )

    @Test
    @Config(qualifiers = "+h2000dp")
    fun ownChargesFailed() = capture(
        name = "group-details-own-charges-failed",
        state = GroupDetailsPreviewData.memberOwnChargesFailed,
        directory = "vul-203",
    )

    private fun capture(name: String, state: GroupDetailsState, directory: String = "vul-69") = capture(name, directory) {
        GroupDetailsScreen(state = state, onBack = {}, onIntent = {})
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
