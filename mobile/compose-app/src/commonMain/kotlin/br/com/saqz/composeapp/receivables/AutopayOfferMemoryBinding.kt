package br.com.saqz.composeapp.receivables

import br.com.saqz.groups.port.GroupOnboardingMemoryPort
import br.com.saqz.receivables.domain.AutopayOfferMemory

/**
 * "Quer deixar no automático?" uma vez por mês (FIN-012) fica no aparelho, na mesma memória das folhas já vistas
 * (SharedPreferences / NSUserDefaults): a chave já carrega o grupo e o mês, sem port nativo novo.
 */
internal class AutopayOfferMemoryBinding(private val memory: GroupOnboardingMemoryPort) : AutopayOfferMemory {
    override fun wasOffered(key: String, done: (Boolean) -> Unit) = memory.isSheetSeen(key, done)
    override fun markOffered(key: String) = memory.markSheetSeen(key) { }
}
