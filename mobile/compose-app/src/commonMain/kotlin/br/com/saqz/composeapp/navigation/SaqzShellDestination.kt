package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.composeapp.shell.SaqzShellGroupsTab
import br.com.saqz.composeapp.shell.SaqzShellHomeTab
import kotlinx.serialization.Serializable

/**
 * C1: o único destino próprio do app — o shell autenticado. O lado de acesso do back
 * stack é da feature ([br.com.saqz.access.navigation.AccessRoute]); juntos formam o
 * único stack acesso→shell que o gate de sessão reconcilia.
 *
 * [initialTab] escolhe a aba que o shell abre: `null` (default) cai na aba Início
 * (VUL-193 — o login cai na Home do Fluxo 6); `SaqzShellGroupsTab` leva à lista de
 * grupos. Os callbacks do invite landing prometem a lista de grupos, então passam
 * [SaqzShellGroupsTab] explicitamente em vez de depender do default.
 *
 * ponytail: um destino, então sem sealed hierarchy. Rotas de produto voltam conforme
 * suas telas, e uma sealed interface é barata de reintroduzir então.
 */
@Serializable
data class SaqzShellDestination(
    val initialTab: String? = null,
) : NavKey {

    /**
     * A aba que o shell deve abrir. `null` significa "default do shell" — hoje a aba
     * Início (VUL-193). Centralizar o resolve aqui evita espalhar `?: SaqzShellHomeTab`
     * entre o [br.com.saqz.composeapp.shell.SaqzAppShell] e os testes.
     */
    fun resolvedTab(): String = initialTab ?: SaqzShellHomeTab

    companion object {
        /** O destino que o login alcança — aba Início, default do shell. */
        val Home: SaqzShellDestination = SaqzShellDestination()

        /** O destino que promete a lista de grupos — aba Grupos explícita. */
        val Groups: SaqzShellDestination = SaqzShellDestination(initialTab = SaqzShellGroupsTab)
    }
}
