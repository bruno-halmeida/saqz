(function () {
  'use strict';
  // Modo de depuração: ?static desliga toda a coreografia e mostra o estado final.
  if (/[?&]static\b/.test(window.location.search)) return;
  document.documentElement.classList.add('js');

  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  var finePointer = window.matchMedia('(hover: hover) and (pointer: fine)');

  // Preencha o número (só dígitos, DDI+DDD) para o CTA final abrir o WhatsApp.
  var WHATSAPP_NUMBER = '';
  var WHATSAPP_MESSAGE = 'Oi! Quero uma vaga para meu grupo no pré-lançamento do Saqz.';
  if (WHATSAPP_NUMBER) {
    var waUrl = 'https://wa.me/' + WHATSAPP_NUMBER + '?text=' + encodeURIComponent(WHATSAPP_MESSAGE);
    Array.prototype.forEach.call(document.querySelectorAll('.final .primary'), function (link) {
      link.href = waUrl;
      link.target = '_blank';
      link.rel = 'noopener';
    });
  }

  // Header ganha sombra ao rolar.
  var header = document.querySelector('header');
  function elevateHeader() { header.classList.toggle('is-scrolled', window.scrollY > 12); }
  window.addEventListener('scroll', elevateHeader, { passive: true });
  elevateHeader();

  // Libera as animações de entrada do hero para os hovers voltarem ao normal.
  var hero = document.querySelector('.hero');
  window.setTimeout(function () { hero.classList.add('is-settled'); }, 1700);

  // Números que contam ao entrar na tela.
  function animateCount(el) {
    var target = parseInt(el.getAttribute('data-count'), 10);
    if (!isFinite(target)) return;
    if (reduceMotion.matches) { el.textContent = String(target); return; }
    var duration = 1100;
    var start = null;
    function tick(now) {
      if (start === null) start = now;
      var progress = Math.min((now - start) / duration, 1);
      var eased = 1 - Math.pow(1 - progress, 3);
      el.textContent = String(Math.round(eased * target));
      if (progress < 1) window.requestAnimationFrame(tick);
    }
    window.requestAnimationFrame(tick);
  }

  // Revelação por scroll.
  var revealEls = Array.prototype.slice.call(document.querySelectorAll('[data-reveal]'));
  var countEls = Array.prototype.slice.call(document.querySelectorAll('[data-count]'));
  revealEls.forEach(function (el) {
    var delay = el.getAttribute('data-reveal-delay');
    if (delay) el.style.transitionDelay = delay + 'ms';
  });
  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        observer.unobserve(entry.target);
        if (entry.target.hasAttribute('data-reveal')) entry.target.classList.add('is-in');
        if (entry.target.hasAttribute('data-count')) animateCount(entry.target);
      });
    }, { threshold: 0.2 });
    revealEls.concat(countEls).forEach(function (el) { observer.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add('is-in'); });
    countEls.forEach(animateCount);
  }

  // Tilt 3D do celular seguindo o ponteiro.
  var stage = document.querySelector('.device-stage');
  var visual = document.querySelector('.visual');
  if (stage && visual && finePointer.matches) {
    var targetRX = 0, targetRY = 0, currentRX = 0, currentRY = 0, rafId = null;
    var frame = function () {
      currentRX += (targetRX - currentRX) * 0.09;
      currentRY += (targetRY - currentRY) * 0.09;
      stage.style.setProperty('--rx', currentRX.toFixed(2) + 'deg');
      stage.style.setProperty('--ry', currentRY.toFixed(2) + 'deg');
      if (Math.abs(targetRX - currentRX) + Math.abs(targetRY - currentRY) > 0.04) rafId = window.requestAnimationFrame(frame);
      else rafId = null;
    };
    var wake = function () { if (rafId === null) rafId = window.requestAnimationFrame(frame); };
    visual.addEventListener('pointermove', function (event) {
      if (reduceMotion.matches) return;
      var rect = stage.getBoundingClientRect();
      targetRY = Math.max(-7, Math.min(7, ((event.clientX - rect.left - rect.width / 2) / rect.width) * 7));
      targetRX = Math.max(-6, Math.min(6, ((event.clientY - rect.top - rect.height / 2) / rect.height) * -6));
      wake();
    });
    visual.addEventListener('pointerleave', function () {
      targetRX = 0;
      targetRY = 0;
      wake();
    });
  }

  // Brilho que segue o cursor nos painéis com data-glow.
  if (finePointer.matches) {
    Array.prototype.forEach.call(document.querySelectorAll('[data-glow]'), function (panel) {
      panel.addEventListener('pointermove', function (event) {
        var rect = panel.getBoundingClientRect();
        panel.style.setProperty('--gx', (event.clientX - rect.left) + 'px');
        panel.style.setProperty('--gy', (event.clientY - rect.top) + 'px');
      });
    });
  }
})();
