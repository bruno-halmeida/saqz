package br.com.saqz.access.presentation.verification

import br.com.saqz.core.common.mvi.MviViewModel

/**
 * Órfã desde o VUL-84 e sem rota que a alcance: a trava de e-mail saiu do backend no
 * VUL-76 e do coordenador no VUL-84, junto com o estado `AwaitingVerification` e os dois
 * intents que esta classe comandava.
 *
 * Fica aqui, inerte e compilando, porque quem a apaga é o VUL-91 — que entrega a faixa de
 * e-mail não confirmado que a substitui, e é quem tem como conferir que nada ficou para
 * trás. Não há mais o que projetar nem para onde despachar, então o estado é o inicial e
 * os intents não vão a lugar nenhum.
 */
class VerificationViewModel :
    MviViewModel<VerificationState, VerificationIntent, VerificationEffect>(VerificationState()) {

    override fun onIntent(intent: VerificationIntent) {
        when (intent) {
            VerificationIntent.Confirm, VerificationIntent.Resend -> Unit
        }
    }
}
