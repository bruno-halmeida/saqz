// Contagem própria sem cookie, demonstração do cartão do topo e barra do botão no celular.
(function () {
  'use strict';

  // Soma por anúncio, lida no log do servidor: sem cookie, sem identificador de pessoa e sem o fbclid.
  var anuncio = new URLSearchParams(window.location.search).get('utm_content') || 'direto';
  var contados = {};
  function conta(evento) {
    if (contados[evento]) return;
    contados[evento] = true;
    if (!window.fetch) return;
    window.fetch('/assets/m.json?e=' + evento + '&a=' + encodeURIComponent(anuncio), {
      keepalive: true,
      referrerPolicy: 'no-referrer',
      credentials: 'omit',
      cache: 'no-store',
    }).catch(function () {});
  }

  var segundos = 0;
  var relogio = window.setInterval(function () {
    if (document.visibilityState !== 'visible') return;
    segundos += 1;
    if (segundos < 10) return;
    window.clearInterval(relogio);
    conta('10s');
  }, 1000);

  function olhaRolagem() {
    if (window.scrollY + window.innerHeight < document.documentElement.scrollHeight / 2) return;
    window.removeEventListener('scroll', olhaRolagem);
    conta('metade');
  }
  window.addEventListener('scroll', olhaRolagem, { passive: true });

  function avisa(cartao, texto) {
    var nota = cartao.querySelector('.demo-nota');
    nota.textContent = texto;
    nota.classList.add('is-ok');
    conta('demo');
  }

  function responde(botao) {
    var cartao = botao.closest('.wa-card');
    var vou = botao.getAttribute('data-resposta') === 'vou';
    cartao.querySelectorAll('[data-resposta]').forEach(function (opcao) {
      opcao.setAttribute('aria-pressed', String(opcao === botao));
    });
    cartao.querySelector('.wa-vagas').textContent = 'Vagas: ' + (vou ? 11 : 10) + '/12';
    avisa(cartao, vou ? 'Pronto: 11 de 12, sem ninguém recontar a lista.' : 'Pronto: a galera já sabe, sem ninguém perguntar de novo.');
  }

  function recebe(botao) {
    var cartao = botao.closest('.wa-card');
    var placar = cartao.querySelector('.wa-vagas');
    var pagos = parseInt(placar.textContent, 10) + 1;
    placar.textContent = pagos + ' de 12 pagaram';
    botao.disabled = true;
    botao.textContent = 'Pago ✓';
    avisa(cartao, 'Pronto: ' + pagos + ' de 12, sem planilha.');
  }

  document.addEventListener('click', function (evento) {
    var alvo = evento.target;
    if (!alvo.closest) return;
    var resposta = alvo.closest('[data-resposta]');
    if (resposta) { responde(resposta); return; }
    var recebi = alvo.closest('[data-recebi]');
    if (recebi) { recebe(recebi); return; }
    if (alvo.closest('[data-saqz-choice]')) conta('aviso');
  });

  // Barra do botão: só aparece quando nenhum botão grande está na tela.
  var barra = document.querySelector('.barra-cta');
  if (barra && 'IntersectionObserver' in window) {
    var naTela = [];
    var observador = new IntersectionObserver(function (itens) {
      itens.forEach(function (item) {
        var posicao = naTela.indexOf(item.target);
        if (item.isIntersecting && posicao < 0) naTela.push(item.target);
        if (!item.isIntersecting && posicao >= 0) naTela.splice(posicao, 1);
      });
      barra.classList.toggle('is-on', naTela.length === 0);
    });
    document.querySelectorAll('main a.primary').forEach(function (botao) { observador.observe(botao); });
  }
})();
