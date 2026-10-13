package br.com.saqz.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import br.com.saqz.composeapp.SaqzApp
import br.com.saqz.composeapp.di.loadSaqzPlatformDependencies
import br.com.saqz.webapp.interop.jsPrefersReducedMotion
import coil3.PlatformContext
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val config = WebConfig.read()
    val composition = WebAppComposition.create(config)
    loadSaqzPlatformDependencies(composition.dependencies, PlatformContext.INSTANCE)
    composition.links.onColdStart(window.location.href)
    document.getElementById("saqz-loading")?.remove()
    ComposeViewport(document.body!!) {
        SaqzApp(reduceMotion = jsPrefersReducedMotion())
    }
}
