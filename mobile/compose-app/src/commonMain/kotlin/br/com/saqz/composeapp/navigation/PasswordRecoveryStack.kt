package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState

/**
 * O 1g→1h. A troca **consome** o ticket, então o que sobrou da recuperação sai do stack em
 * vez de ficar embaixo do 1h: o 1g reaberto pediria a senha de novo com um token morto na
 * mão, e o 1e, com um código já gasto. Nos dois casos a pessoa digitaria tudo outra vez
 * para levar um erro que não tem como entender.
 *
 * Sobra a base correta para a sessão atual, que é para onde o "Entrar agora" leva de qualquer
 * forma — o voltar do 1h, visível ou do sistema, passa a dar no mesmo lugar. Empilhar sobre o
 * formulário era o defeito; substituir só ele deixaria o mesmo defeito uma entrada acima.
 */
internal fun MutableList<NavKey>.completePasswordReset(session: SessionAccessState) {
    clear()
    add(session.passwordChangedDestination())
    add(AccessRoute.PasswordChanged)
}

/**
 * PC-04: "Lembrou a senha? Entrar ›" do 1e desiste da recuperação. Deslogado, a base é o login.
 * Logado (Perfil → trocar senha), a base é o que estava embaixo: mandar para o login com a
 * sessão viva prendia a pessoa ali, porque o [reconcileAccessStack] só roda quando a sessão
 * muda — e ela não mudou.
 */
internal fun MutableList<NavKey>.abandonPasswordRecovery(session: SessionAccessState) {
    if (session !is SessionAccessState.Ready) {
        clear()
        add(AccessRoute.Login)
        return
    }
    while (lastOrNull().let { it is AccessRoute.ForgotPassword || it is AccessRoute.ResetCode }) {
        removeLast()
    }
    if (isEmpty()) add(session.passwordChangedDestination())
}

internal fun MutableList<NavKey>.finishPasswordChanged(session: SessionAccessState) {
    clear()
    add(session.passwordChangedDestination())
}

private fun SessionAccessState.passwordChangedDestination(): NavKey = when (this) {
    is SessionAccessState.Ready -> SaqzShellDestination.Home
    SessionAccessState.SignedOut,
    is SessionAccessState.CompletingIdentity,
    SessionAccessState.Bootstrapping,
    SessionAccessState.BootstrapError,
    -> AccessRoute.Login
}
