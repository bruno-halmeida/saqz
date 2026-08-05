package br.com.saqz.composeapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertTrue
import platform.Foundation.NSThread

/**
 * Os adapters nativos do iOS são `@MainActor`, e em Swift 6 chamar um deles de fora da main
 * é trap de runtime — não aviso. Como **todo** request autenticado passa por
 * `NativeAuthPort.idToken`, qualquer ViewModel que dispare rede de uma thread de fundo
 * derrubaria o app.
 *
 * Este teste prende esse contrato no dispatcher que as ViewModels usam de fato, para que uma
 * troca de versão do lifecycle que caia no `Dispatchers.Default` apareça aqui, e não numa
 * tela travada no aparelho.
 */
class ViewModelScopeThreadTest {
    private class ProbeViewModel : ViewModel() {
        fun probe(record: (Boolean) -> Unit) {
            viewModelScope.launch { record(NSThread.isMainThread()) }
        }
    }

    @Test
    fun viewModelScopeRunsOnTheMainThread() {
        var onMain: Boolean? = null
        ProbeViewModel().probe { onMain = it }
        assertTrue(
            onMain == true,
            "viewModelScope saiu da main (valor=$onMain): os ports @MainActor vão trapar.",
        )
    }
}
