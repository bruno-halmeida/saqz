package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import kotlinx.coroutines.CoroutineScope

// STUB: substituir por navigator.share com fallback para a área de transferência.
internal class WebShareAdapter(private val scope: CoroutineScope) : NativeSharePort {
    override fun share(text: String, done: ResultCallback) =
        done.complete(OperationResult.Failure(NativeFailureCode.PROVIDER_UNAVAILABLE))
}
