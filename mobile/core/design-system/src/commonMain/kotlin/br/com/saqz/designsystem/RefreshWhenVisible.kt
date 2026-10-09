package br.com.saqz.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Chama [onRefresh] toda vez que a tela passa a estar visível — "toda tela busca os dados de novo
 * sempre que é aberta ou volta a aparecer". Cobre os três caminhos que um `init { load() }` não
 * cobre, porque neles a ViewModel sobrevive:
 *
 * 1. **troca de aba no shell** — o Root da aba sai da composição e reentra com o owner já `RESUMED`;
 * 2. **volta de uma tela filha** — a entrada que ficou no fundo da pilha é recomposta;
 * 3. **app trazido de volta ao primeiro plano** — o owner passa por `ON_RESUME`.
 *
 * Dispara também na primeira composição, logo depois do `init`: é a ViewModel que ignora esse
 * primeiro pedido com a guarda de carga em voo — nunca um `remember` aqui.
 *
 * [trigger] é opcional: o contador externo de "mudou" (os `*RefreshVersion` do `SaqzNavHost`).
 * Ele pode mudar com a tela ainda montada, e aí o efeito dispara de novo. A ViewModel não entra
 * aqui de propósito: o Root a resolve uma vez e só a usa dentro de [onRefresh].
 */
@Composable
fun RefreshWhenVisible(trigger: Any? = null, onRefresh: () -> Unit) {
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    LifecycleResumeEffect(trigger) {
        currentOnRefresh()
        onPauseOrDispose { }
    }
}
