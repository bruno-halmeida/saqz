// Contagem própria sem cookie, vídeo do topo e barra do botão no celular.
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

  document.addEventListener('click', function (evento) {
    var alvo = evento.target;
    if (alvo.closest && alvo.closest('[data-saqz-choice]')) conta('aviso');
  });

  // Vídeo do topo (mudo, em loop): conta quando começa a tocar, o que mostra se o navegador deixou tocar sozinho, e
  // quando chega ao fim. Quem pediu menos movimento no aparelho vê a capa parada; o botão pausa e volta a tocar.
  var video = document.querySelector('.hero-video video');
  var pausa = document.querySelector('[data-video-pausa]');
  if (video) {
    var marca = function () { if (pausa) pausa.setAttribute('aria-pressed', String(video.paused)); };
    if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      video.removeAttribute('autoplay');
      video.pause();
    }
    video.addEventListener('playing', function () { conta('video'); marca(); });
    video.addEventListener('pause', marca);
    video.addEventListener('timeupdate', function () {
      if (video.duration && video.currentTime >= video.duration - 1.5) conta('video-fim');
    });
    if (pausa) {
      pausa.addEventListener('click', function () {
        if (video.paused) { video.play().catch(function () {}); } else { video.pause(); }
      });
    }
    marca();
  }

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
