// iPhone e computador seguem do "Criar meu grupo" direto para o web app (saqz.app/app/), enquanto
// o app de iPhone não sai na App Store; Android continua no /comecar/ e no Google Play.
// O href não muda (a medição conta os cliques em a[href="/comecar/"]): só o destino do clique.
// E só se o web app responder, para a landing nunca apontar para um /app/ fora do ar.
(function () {
  if (/Android/i.test(navigator.userAgent)) return;
  // Sem WasmGC (iOS antes do 18.2) o web app não roda: fica o /comecar/ de sempre.
  try { if (!(typeof WebAssembly === 'object' && WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 127, 0])))) return; } catch (e) { return; }
  var ready = false;
  fetch('/app/disponivel.json', { cache: 'no-store' })
    .then(function (response) { ready = response.ok; })
    .catch(function () { ready = false; });
  document.addEventListener('click', function (ev) {
    if (!ready || ev.defaultPrevented || ev.button !== 0 || ev.metaKey || ev.ctrlKey || ev.shiftKey || ev.altKey) return;
    var a = ev.target.closest && ev.target.closest('a[href="/comecar/"]');
    if (!a) return;
    ev.preventDefault();
    window.location.assign('/app/');
  });
})();
