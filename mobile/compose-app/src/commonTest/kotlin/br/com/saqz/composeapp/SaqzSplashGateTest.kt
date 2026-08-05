package br.com.saqz.composeapp

import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.composeapp.navigation.AccessUiState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaqzSplashGateTest {
    @Test
    fun holdsBeforeTheProviderAnswers() {
        assertFalse(AccessUiState().settledForOpening)
    }

    @Test
    fun holdsWhileBootstrapping() {
        // Quem abre o app já autenticado passa por aqui: parar a splash agora entregaria a
        // tela de bootstrap, que é a segunda tela cheia que o fluxo 9 proíbe na abertura.
        assertFalse(
            AccessUiState(
                authObserved = true,
                session = SessionAccessState.Bootstrapping,
            ).settledForOpening,
        )
    }

    @Test
    fun opensOnSignedOut() {
        assertTrue(
            AccessUiState(authObserved = true, session = SessionAccessState.SignedOut)
                .settledForOpening,
        )
    }

    @Test
    fun opensOnBootstrapError() {
        // Erro é resposta: a tela de bootstrap tem o "tentar de novo" e precisa aparecer.
        assertTrue(
            AccessUiState(authObserved = true, session = SessionAccessState.BootstrapError)
                .settledForOpening,
        )
    }
}
