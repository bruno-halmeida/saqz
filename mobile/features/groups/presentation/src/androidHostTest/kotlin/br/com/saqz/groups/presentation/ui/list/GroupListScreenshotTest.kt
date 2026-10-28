package br.com.saqz.groups.presentation.ui.list

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
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
    fun filled() = capture("group-list-2n-lista-cheia", GroupListSamples.filled, withPhoto = true)

    /** Em andamento sem outro jogo (AGORA, lotado), acima da lotação, na espera e não vai; jogo de 18 vagas. */
    @Test
    fun states() = capture("group-list-2n-estados", GroupListSamples.states, withPhoto = true)

    @Test
    fun confirmSheet() = capture("group-list-2n-folha-confirmar", GroupListSamples.confirming, withPhoto = true)

    @Test
    fun confirmSheetOffline() = capture(
        "group-list-2n-folha-sem-conexao",
        GroupListSamples.confirming.copy(responseFailed = true, responseOffline = true),
    )

    @Test
    fun empty() = capture("group-list-2o-primeiro-acesso", GroupListSamples.empty)

    @Test
    fun emptyOwner() = capture("group-list-2o-primeiro-acesso-dono", GroupListSamples.empty, isPlanOwner = true)

    @Test
    fun emptyInviteSheet() = capture(
        "group-list-2o-tenho-um-convite",
        GroupListSamples.empty.copy(inviteSheetOpen = true, inviteLink = "https://links.saqz.app/?saqz_invite=…"),
    )

    @Test
    fun loading() = capture("group-list-carregando", GroupListSamples.loading)

    @Test
    fun failure() = capture("group-list-falha-de-carga", GroupListSamples.failed)

    private fun capture(name: String, state: GroupListState, isPlanOwner: Boolean = false, withPhoto: Boolean = false) =
        compose.captureGroupList(name, state, isPlanOwner, withPhoto = withPhoto)
}

// A barra de baixo é do shell: entra só na cena, para o print bater com a célula.
internal fun ComposeContentTestRule.captureGroupList(
    name: String,
    state: GroupListState,
    isPlanOwner: Boolean = false,
    fontScale: Float = 1f,
    withPhoto: Boolean = false,
) {
    try {
        captureGroupListScene(name, state, isPlanOwner, fontScale, withPhoto)
    } finally {
        if (withPhoto) stopKoin()
    }
}

private fun ComposeContentTestRule.captureGroupListScene(
    name: String,
    state: GroupListState,
    isPlanOwner: Boolean,
    fontScale: Float,
    withPhoto: Boolean,
) {
    setContent {
        if (withPhoto) {
            // O `GroupRemotePhotoRoot` pega o loader do Koin: registrado antes da primeira composição da tela.
            val context = LocalPlatformContext.current
            remember(context) {
                groupListPhotoLoader(context).also { loader -> startKoin { modules(module { single<ImageLoader> { loader } }) } }
            }
        }
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) { SaqzTheme {
            Column(
                modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background),
            ) {
                GroupListScreen(state = state, onIntent = {}, modifier = Modifier.weight(1f), isPlanOwner = isPlanOwner)
                SaqzBottomNav(items = GroupListSamples.navItems, activeId = "grupos", onSelect = {})
            }
        } }
    }
    onRoot().captureRoboImage("screenshots/deb-145/$name.png")
}
