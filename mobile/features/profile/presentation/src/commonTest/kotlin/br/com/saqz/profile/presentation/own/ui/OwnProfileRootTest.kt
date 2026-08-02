package br.com.saqz.profile.presentation.own.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OwnProfileRootTest {
    @Test
    fun `refresh version reloads the retained profile after an editor save`() = runComposeUiTest {
        val gateway = FakeProfileGateway()
        val viewModel = OwnProfileViewModel(gateway)
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            SaqzTheme {
                OwnProfileRoot(
                    onOpenEditor = {},
                    onOpenPasswordRecovery = {},
                    onSignOut = {},
                    refreshVersion = refreshVersion,
                    viewModel = viewModel,
                    imageLoader = imageLoader,
                )
            }
        }

        waitForIdle()
        gateway.profile = gateway.profile.copy(
            user = gateway.profile.user.copy(
                displayName = "Novo nome",
                nickname = "Novo apelido",
                city = "Rio de Janeiro",
                photoUrl = "/api/session/photo?v=novo",
            ),
        )
        runOnIdle { refreshVersion = 1 }
        waitForIdle()

        onNodeWithText("Novo nome").assertExists()
        onNodeWithText("Novo apelido · Rio de Janeiro").assertExists()
        assertEquals("/api/session/photo?v=novo", viewModel.state.value.user?.photoUrl)
    }
}
