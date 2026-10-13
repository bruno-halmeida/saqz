package br.com.saqz.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import br.com.saqz.composeapp.SaqzApp
import br.com.saqz.composeapp.di.installSaqzKoinModules
import br.com.saqz.composeapp.di.loadSaqzPlatformDependencies
import br.com.saqz.webapp.interop.awaitAuthBridge
import br.com.saqz.webapp.interop.jsPrefersReducedMotion
import br.com.saqz.webapp.navigation.BrowserBackBridge
import coil3.PlatformContext
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        // O bridge do Firebase é módulo ES e carrega depois do wasm; sem ele não há login.
        if (!awaitAuthBridge()) {
            document.getElementById("saqz-loading")?.textContent =
                "Não foi possível carregar o Saqz. Confira sua conexão e recarregue a página."
            return@launch
        }
        val config = WebConfig.read()
        // Mesma ordem do SaqzApplication do Android: Koin, módulos comuns, depois as portas da plataforma.
        startKoin { }
        installSaqzKoinModules()
        val composition = WebAppComposition.create(config)
        loadSaqzPlatformDependencies(composition.dependencies, PlatformContext.INSTANCE)
        composition.links.onColdStart(window.location.href)
        document.getElementById("saqz-loading")?.remove()
        ComposeViewport(document.body!!) {
            BrowserBackBridge()
            SaqzApp(reduceMotion = jsPrefersReducedMotion())
        }
    }
}
