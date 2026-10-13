package br.com.saqz.subscriptions.domain.port

/**
 * Contratação pela web (saqz.app/assinar/, cobrança pelo Asaas). Só o web app tem: no navegador
 * não existe App Store nem Google Play, e a página de assinatura do site usa a mesma conta (mesmo
 * domínio e mesmo projeto do Firebase). Android e iOS passam `null` e seguem nas lojas.
 */
fun interface WebCheckoutPort {
    /** Abre o checkout; [done] chega quando a pessoa volta ao app, para a tela reconsultar o plano. */
    fun open(done: () -> Unit)
}
