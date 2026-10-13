// Carregador de medição da landing: só injeta o que tiver ID em analytics-config.js e marca os eventos
// de conversão da página (clique em CTA por posição e abertura de pergunta do FAQ).
(function () {
  var cfg = window.SAQZ_ANALYTICS || {};
  var ga4 = cfg.ga4MeasurementId, clarity = cfg.clarityProjectId, meta = cfg.metaPixelId;

  function script(src) { var s = document.createElement('script'); s.async = true; s.src = src; document.head.appendChild(s); }

  if (ga4) {
    window.dataLayer = window.dataLayer || [];
    window.gtag = function () { window.dataLayer.push(arguments); };
    window.gtag('js', new Date());
    window.gtag('config', ga4, { anonymize_ip: true });
    script('https://www.googletagmanager.com/gtag/js?id=' + encodeURIComponent(ga4));
  }
  if (clarity) {
    window.clarity = window.clarity || function () { (window.clarity.q = window.clarity.q || []).push(arguments); };
    script('https://www.clarity.ms/tag/' + encodeURIComponent(clarity));
  }
  // Pixel da Meta: só depois do aceite no aviso de consentimento (chave saqz_consent_ads).
  var CONSENT_KEY = 'saqz_consent_ads';
  function readConsent() { try { return localStorage.getItem(CONSENT_KEY); } catch (e) { return null; } }
  function writeConsent(v) { try { localStorage.setItem(CONSENT_KEY, v); } catch (e) {} }
  var metaOn = false;
  function startMeta() {
    if (!meta || metaOn) return;
    metaOn = true;
    if (!window.fbq) {
      var n = window.fbq = function () { n.callMethod ? n.callMethod.apply(n, arguments) : n.queue.push(arguments); };
      n.push = n; n.loaded = true; n.version = '2.0'; n.queue = [];
      script('https://connect.facebook.net/en_US/fbevents.js');
    }
    window.fbq('init', meta);
    window.fbq('track', 'PageView');
  }
  if (readConsent() === 'yes') startMeta();
  window.saqzConsent = function (accepted) {
    writeConsent(accepted ? 'yes' : 'no');
    if (accepted) startMeta();
    var bar = document.getElementById('consent'); if (bar) bar.hidden = true;
  };
  document.addEventListener('DOMContentLoaded', function () {
    var bar = document.getElementById('consent');
    if (bar && meta && readConsent() === null) bar.hidden = false;
  });

  function send(name, params) {
    if (ga4 && window.gtag) window.gtag('event', name, params);
    if (clarity && window.clarity) window.clarity('event', name + (params.cta_position ? '_' + params.cta_position : ''));
    if (metaOn && window.fbq) window.fbq('trackCustom', name, params);
  }

  function position(el) {
    if (el.closest('header')) return 'nav';
    var section = el.closest('section');
    if (!section) return 'outro';
    if (section.classList.contains('hero')) return 'hero';
    return section.id || section.className.split(' ')[0] || 'outro';
  }

  document.addEventListener('click', function (ev) {
    var a = ev.target.closest && ev.target.closest('a[href="/comecar/"], a[href="/assinar/"]');
    if (!a) return;
    var card = a.closest('.plan-card');
    send('cta_click', {
      cta_position: position(a),
      cta_label: (a.textContent || '').replace(/\s+/g, ' ').trim(),
      cta_target: a.getAttribute('href') === '/assinar/' ? 'assinar' : 'comecar',
      plan: card ? (card.querySelector('h3') || {}).textContent : undefined,
    });
  });

  document.addEventListener('toggle', function (ev) {
    var d = ev.target;
    if (!d || d.tagName !== 'DETAILS' || !d.open || !d.classList.contains('question')) return;
    var h = d.querySelector('h3');
    send('faq_open', { question: h ? h.textContent.trim() : '' });
  }, true);
})();
