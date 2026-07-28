package br.com.saqz.groups.ui.list

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.list.GroupListState
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
class GroupListScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun filled() = capture("group-list-2n-lista-cheia", GroupListSamples.filled)

    @Test
    fun empty() = capture("group-list-2o-primeiro-acesso", GroupListSamples.empty)

    @Test
    fun loading() = capture("group-list-carregando", GroupListSamples.loading)

    @Test
    fun failure() = capture("group-list-falha-de-carga", GroupListSamples.failed)

    // A barra de baixo é do shell: entra só na cena, para o print bater com a célula.
    private fun capture(name: String, state: GroupListState) {
        compose.setContent {
            SaqzTheme {
                Column(
                    modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background),
                ) {
                    GroupListScreen(state = state, onIntent = {}, modifier = Modifier.weight(1f))
                    SaqzBottomNav(items = GroupListSamples.navItems, activeId = "grupos", onSelect = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-67/$name.png")
    }
}
