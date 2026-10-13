package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.webapp.interop.BrowserOutcome
import br.com.saqz.webapp.interop.BrowserShare
import kotlinx.coroutines.CoroutineScope

/**
 * Folha de compartilhamento do navegador (`navigator.share`); sem ela (Firefox desktop, contexto sem HTTPS),
 * copia o texto. Como no Android, quem fecha a folha não é erro: o `OperationResult` não tem "cancelado" e o
 * Success só diz que a operação foi aberta. Só falha o que o navegador recusou de fato.
 *
 * `scope` não é usado: a chamada ao navegador precisa ser síncrona com o toque (ativação do usuário).
 */
internal class WebShareAdapter(@Suppress("UnusedPrivateProperty") scope: CoroutineScope) : NativeSharePort {
    override fun share(text: String, done: ResultCallback) {
        val finish = { outcome: BrowserOutcome ->
            done.complete(
                if (outcome == BrowserOutcome.FAILED) {
                    OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE)
                } else {
                    OperationResult.Success
                },
            )
        }
        if (BrowserShare.canShare) BrowserShare.shareText(text, finish) else BrowserShare.copyText(text, finish)
    }
}
