package br.com.saqz.webapp.subscriptions

import br.com.saqz.subscriptions.domain.port.WebCheckoutPort

/**
 * Assinar no web app: abre a página de assinatura do site (saqz.app/assinar/, Asaas) numa aba nova.
 * É o mesmo domínio e o mesmo projeto do Firebase, então a pessoa já chega logada. Quando ela volta
 * para a aba do app, [done] avisa a tela para reconsultar o plano e o acesso.
 */
internal class WebCheckoutAdapter(private val url: String) : WebCheckoutPort {
    override fun open(done: () -> Unit) = jsOpenCheckout(url, done)
}

// Sem aba nova (bloqueio de pop-up, app instalado na tela inicial) segue na mesma aba: a volta
// pelo "voltar" recarrega o app, que lê o plano de novo de qualquer jeito.
@JsFun(
    """(url, done) => {
  const tab = window.open(url, '_blank');
  if (!tab) { window.location.assign(url); return; }
  let finished = false;
  const finish = () => {
    if (finished || document.visibilityState !== 'visible') return;
    finished = true;
    document.removeEventListener('visibilitychange', finish);
    window.removeEventListener('focus', finish);
    done();
  };
  // Só escuta a volta depois que a aba nova assumiu: o foco desta aba ainda pode chegar agora.
  setTimeout(() => {
    document.addEventListener('visibilitychange', finish);
    window.addEventListener('focus', finish);
  }, 800);
}""",
)
private external fun jsOpenCheckout(url: String, done: () -> Unit)
