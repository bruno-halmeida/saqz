package br.com.saqz.profile.presentation.own.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import coil3.ImageLoader
import coil3.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import coil3.compose.LocalPlatformContext
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import br.com.saqz.profile.presentation.own.OwnProfileIntent
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// O `runComposeUiTest` compõe num despachante de teste sem fila: a foto, que o Coil busca em outra thread, voltava
// escrevendo o estado do painter nessa thread, às vezes no meio da recomposição que trocou a URL ("Unsupported
// concurrent change during composition", RC 1.0.10). Sem troca de contexto, a requisição roda inteira na thread que a
// começou. No app a composição despacha para a Main, então o caso é só do teste.
private fun testImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context).coroutineContext(EmptyCoroutineContext).build()

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class OwnProfileRootTest {
    // A ViewModel lança na Main. Com a Main real do iOS, a carga muda o estado numa fila que o relógio do
    // Compose não controla e, às vezes, no meio de uma recomposição ("Unsupported concurrent change").
    // Com a Main de teste, ela corre na mesma thread que o teste, em ordem.
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun resetMain() = Dispatchers.resetMain()

    @Test
    fun helpAndTermsOpenThePublishedContactAndTerms() = runComposeUiTest {
        val viewModel = OwnProfileViewModel(FakeProfileGateway())
        val opened = mutableListOf<String>()
        val uriHandler = object : UriHandler {
            override fun openUri(uri: String) {
                opened += uri
            }
        }
        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { testImageLoader(context) }
            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                SaqzTheme {
                    OwnProfileRoot(onOpenEditor = {}, onOpenPasswordRecovery = {}, onSignOut = {},
                        viewModel = viewModel, imageLoader = imageLoader)
                }
            }
        }
        onNodeWithText("Ajuda e contato").performScrollTo().performClick()
        onNodeWithText("Termos de uso").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("mailto:contato@egysis.com?subject=Saqz%20-%20Ajuda", "https://saqz.app/termos/"), opened)
    }

    @Test
    fun appVersionClosesTheProfileAndIsAbsentWithoutOne() = runComposeUiTest {
        var version by mutableStateOf<br.com.saqz.domain.AppVersion?>(br.com.saqz.domain.AppVersion("0.0.5 (7)"))
        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { testImageLoader(context) }
            SaqzTheme {
                OwnProfileRoot(onOpenEditor = {}, onOpenPasswordRecovery = {}, onSignOut = {},
                    viewModel = OwnProfileViewModel(FakeProfileGateway()), imageLoader = imageLoader, appVersion = version)
            }
        }
        // Último item da lista: só existe depois que a lista rola até ele.
        onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(OwnProfileTags.AppVersion))
        onNodeWithText("Versão 0.0.5 (7)").assertExists()
        version = null
        waitForIdle()
        onNodeWithTag(OwnProfileTags.AppVersion).assertDoesNotExist()
    }

    @Test
    fun receiptsEntryRoutesWhenAuthorizedEvenWithoutPlanOwnership() = runComposeUiTest {
        val gateway = FakeProfileGateway().apply { profile = profile.copy(memberships = emptyList()) }
        val viewModel = OwnProfileViewModel(gateway)
        var opens = 0
        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { testImageLoader(context) }
            SaqzTheme {
                OwnProfileRoot(onOpenEditor = {}, onOpenPasswordRecovery = {}, onSignOut = {},
                    onOpenReceipts = { opens++ }, isPlanOwner = false, viewModel = viewModel, imageLoader = imageLoader)
            }
        }
        onNodeWithTag(OwnProfileTags.Receipts).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, opens)
    }

    @Test
    fun receiptsEntryDisappearsAndStopsRoutingWhenAuthorizationIsRemoved() = runComposeUiTest {
        val gateway = FakeProfileGateway().apply { profile = profile.copy(memberships = emptyList()) }
        val viewModel = OwnProfileViewModel(gateway)
        var opens = 0
        var onOpenReceipts by mutableStateOf<(() -> Unit)?>({ opens++ })
        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { testImageLoader(context) }
            SaqzTheme {
                OwnProfileRoot(
                    onOpenEditor = {}, onOpenPasswordRecovery = {}, onSignOut = {},
                    onOpenReceipts = onOpenReceipts, viewModel = viewModel, imageLoader = imageLoader,
                )
            }
        }
        onNodeWithTag(OwnProfileTags.Receipts).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, opens)

        runOnIdle { onOpenReceipts = null }
        onNodeWithTag(OwnProfileTags.MonthlyPayments).performScrollTo().assertExists()
        onNodeWithTag(OwnProfileTags.Receipts).assertDoesNotExist()
        runOnIdle { viewModel.onIntent(OwnProfileIntent.OpenReceipts) }
        waitForIdle()
        assertEquals(1, opens)
    }

    @Test
    fun `refresh version reloads the retained profile after an editor save`() = runComposeUiTest {
        val gateway = FakeProfileGateway()
        val viewModel = OwnProfileViewModel(gateway)
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { testImageLoader(context) }
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

        // A carga do `init` roda na ViewModel, fora do relógio do Compose: sem esperar por ela, o pedido de
        // recarga cai junto dela (é ignorado) ou o estado muda no meio de uma recomposição.
        waitUntil(timeoutMillis = 10_000) { viewModel.state.value.user != null }
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
        waitUntil(timeoutMillis = 10_000) { viewModel.state.value.user?.displayName == "Novo nome" }
        waitForIdle()

        onNodeWithText("Novo nome").assertExists()
        onNodeWithText("Novo apelido · Rio de Janeiro").assertExists()
        assertEquals("/api/session/photo?v=novo", viewModel.state.value.user?.photoUrl)
    }
}
