package br.com.saqz.webapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

/**
 * Liga o "voltar" do navegador (botão, gesto de borda do Safari, botão do Android) ao "voltar" do
 * app. Sem isto o app inteiro é uma entrada só do histórico e o voltar sai do Saqz de uma vez.
 *
 * Enquanto o app tem para onde voltar (algum handler de voltar ativo: tela empilhada, folha,
 * diálogo), mantemos uma entrada "guarda" no topo do histórico. O voltar do navegador consome a
 * guarda (popstate) e vira um voltar do app; se ainda houver para onde voltar, a guarda volta.
 * Na tela raiz o voltar consome a guarda e o seguinte sai do app, como num app nativo.
 */
@Composable
internal fun BrowserBackBridge() {
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return
    DisposableEffect(dispatcher) {
        val input = BrowserHistoryInput()
        dispatcher.addInput(input)
        val stop = jsOnPopState { input.onBrowserBack() }
        onDispose {
            stop()
            dispatcher.removeInput(input)
        }
    }
}

private class BrowserHistoryInput : NavigationEventInput() {
    private var canGoBack = false

    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        canGoBack = hasEnabledHandlers
        if (hasEnabledHandlers) jsPushGuard()
    }

    fun onBrowserBack() {
        if (canGoBack) {
            dispatchOnBackCompleted()
            // Se a tela de baixo também tem voltar, a guarda precisa estar lá para o próximo toque.
            if (canGoBack) jsPushGuard()
        } else {
            // A guarda já foi consumida e o app está na raiz: este voltar é para sair do Saqz.
            jsHistoryBack()
        }
    }
}

@JsFun("() => { if (!(history.state && history.state.saqzGuard)) history.pushState({ saqzGuard: true }, ''); }")
private external fun jsPushGuard()

@JsFun("() => history.back()")
private external fun jsHistoryBack()

/** Registra o popstate e devolve a função que o remove. */
@JsFun("(callback) => { const handler = () => callback(); window.addEventListener('popstate', handler); return () => window.removeEventListener('popstate', handler); }")
private external fun jsOnPopState(callback: () -> Unit): () -> Unit
