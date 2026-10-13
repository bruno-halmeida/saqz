// Carregador de medição do site (landing, /comecar/ e /assinar/): só injeta o que tiver ID em analytics-config.js,
// só depois da escolha no aviso de cookies, e marca os eventos de conversão da página (clique em CTA por posição
// e abertura de pergunta do FAQ).
(function () {
  var cfg = window.SAQZ_ANALYTICS || {};
  var ga4 = cfg.ga4MeasurementId, clarity = cfg.clarityProjectId, meta = cfg.metaPixelId;

  function script(src) { var s = document.createElement('script'); s.async = true; s.src = src; document.head.appendChild(s); }

  // Nada de medição antes da escolha. Valores guardados em saqz_consent_ads:
  // 'all' (estatísticas + Meta), 'stats' (GA4 + Clarity) e 'none' (nenhum). 'yes'/'no' são o formato antigo.
  var CONSENT_KEY = 'saqz_consent_ads';
  function readConsent() {
    var v = null;
    try { v = localStorage.getItem(CONSENT_KEY); } catch (e) {}
    return v === 'yes' ? 'all' : v === 'no' ? 'stats' : (v === 'all' || v === 'stats' || v === 'none') ? v : null;
  }
  function writeConsent(v) { try { localStorage.setItem(CONSENT_KEY, v); } catch (e) {} }
  var statsOn = false, metaOn = false, pendingUser = null;

  function startStats() {
    if (statsOn) return;
    statsOn = true;
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
    if (pendingUser) applyUser(pendingUser.uid);
  }
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
  function apply(choice) {
    if (choice === 'all' || choice === 'stats') startStats();
    if (choice === 'all') startMeta();
  }
  // Apaga os cookies de medição deste domínio (GA, Clarity, Meta) ao reduzir a escolha.
  function clearCookies() {
    try {
      var host = location.hostname.split('.').slice(-2).join('.');
      document.cookie.split(';').forEach(function (c) {
        var name = c.split('=')[0].trim();
        if (!/^(_ga|_gid|_gat|_clck|_clsk|CLID|MUID|_fbp|_fbc)/.test(name)) return;
        [location.hostname, '.' + host].forEach(function (d) {
          document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; domain=' + d;
        });
        document.cookie = name + '=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
      });
    } catch (e) {}
  }
  var RANK = { none: 0, stats: 1, all: 2 };
  window.saqzConsent = function (choice) {
    if (choice === true) choice = 'all'; else if (choice === false) choice = 'stats';
    if (!(choice in RANK)) return;
    var before = statsOn ? (metaOn ? 'all' : 'stats') : 'none';
    writeConsent(choice);
    var bar = document.getElementById('consent'); if (bar) bar.hidden = true;
    if (RANK[choice] < RANK[before]) {
      // Menos do que já está rodando: limpa os cookies e recarrega a página sem os scripts de medição.
      clearCookies();
      try { location.reload(); } catch (e) {}
      return;
    }
    apply(choice);
  };
  window.saqzConsentState = readConsent;

  var BAR_CSS = '.saqz-consent{position:fixed;z-index:2147483000;left:16px;right:16px;bottom:16px;display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px 20px;max-width:860px;margin:0 auto;padding:14px 18px;border:1px solid #d2d2d7;border-radius:14px;background:rgba(255,255,255,.98);box-shadow:0 12px 40px rgba(0,0,0,.18);font-family:Inter,-apple-system,BlinkMacSystemFont,system-ui,sans-serif}' +
    '.saqz-consent[hidden]{display:none}' +
    '.saqz-consent p{flex:1 1 380px;margin:0;color:#1d1d1f;font-size:13px;line-height:1.45}' +
    '.saqz-consent a{color:#0638DF}' +
    '.saqz-consent-actions{display:flex;flex-wrap:wrap;gap:8px}' +
    '.saqz-consent button{padding:9px 14px;border-radius:980px;font:600 13px Inter,-apple-system,BlinkMacSystemFont,system-ui,sans-serif;cursor:pointer;border:1px solid #c8c8cc;background:#fff;color:#1d1d1f}' +
    '.saqz-consent button.saqz-accept{border:0;background:#0638DF;color:#fff}';
  function buildBar() {
    var bar = document.getElementById('consent');
    if (bar) return bar;
    if (!document.body) return null;
    var style = document.createElement('style'); style.textContent = BAR_CSS; document.head.appendChild(style);
    bar = document.createElement('div');
    bar.id = 'consent'; bar.className = 'saqz-consent'; bar.hidden = true;
    bar.setAttribute('role', 'region'); bar.setAttribute('aria-label', 'Aviso de cookies');
    bar.innerHTML = '<p>Usamos cookies para medir o uso do site (Google Analytics e Microsoft Clarity, com campos mascarados) e, se você deixar, para medir campanhas da Meta. Nada disso carrega antes da sua escolha. <a href="/privacidade/">Política de privacidade</a></p>' +
      '<div class="saqz-consent-actions">' +
      '<button type="button" class="saqz-accept" data-saqz-choice="all">Aceitar todos</button>' +
      '<button type="button" data-saqz-choice="stats">Só estatísticas</button>' +
      '<button type="button" data-saqz-choice="none">Recusar</button></div>';
    document.body.appendChild(bar);
    return bar;
  }
  function openConsent() { var bar = buildBar(); if (bar) bar.hidden = false; }
  window.saqzOpenConsent = openConsent;
  function askIfNeeded() { if ((ga4 || clarity || meta) && readConsent() === null) openConsent(); }
  document.addEventListener('click', function (ev) {
    var t = ev.target && ev.target.closest ? ev.target : null;
    if (!t) return;
    var choice = t.closest('[data-saqz-choice]');
    if (choice) { window.saqzConsent(choice.getAttribute('data-saqz-choice')); return; }
    if (t.closest('[data-saqz-cookies]')) { ev.preventDefault(); openConsent(); }
  });
  apply(readConsent());
  document.addEventListener('DOMContentLoaded', askIfNeeded);
  // O script pode rodar depois do DOMContentLoaded (defer em página já carregada).
  if (document.readyState && document.readyState !== 'loading') askIfNeeded();

  // Eventos padrão da Meta para os marcos do funil; o resto vai como evento customizado.
  var META_STANDARD = { sign_up: 'CompleteRegistration', trial_released: 'StartTrial', begin_checkout: 'InitiateCheckout', purchase: 'Purchase' };
  function send(name, params) {
    params = params || {};
    if (!statsOn) return; // sem escolha (ou recusado): nada é medido
    if (ga4 && window.gtag) window.gtag('event', name, params);
    if (clarity && window.clarity) window.clarity('event', name + (params.cta_position ? '_' + params.cta_position : ''));
    if (metaOn && window.fbq) {
      var standard = META_STANDARD[name];
      if (standard) window.fbq('track', standard, { value: params.value, currency: params.currency, content_name: params.plan });
      else window.fbq('trackCustom', name, params);
    }
  }
  // Usados pelas páginas /comecar/ e /assinar/ para marcar o funil (ver deploy/medicao-web.md).
  window.saqzTrack = send;
  // user_id = id interno da conta (o mesmo que o app manda ao Firebase), para o GA4 ligar site e app.
  // O cadastro conhece esse id pela sessão e guarda o par com o uid do Firebase; a assinatura só
  // conhece o uid do Firebase e reaproveita o par quando ele bate. Sem consentimento nada é gravado:
  // o id fica só em memória e é aplicado se a pessoa aceitar depois.
  var USER_KEY = 'saqz_analytics_user';
  function applyUser(uid) {
    pendingUser = { uid: uid };
    if (!statsOn) return;
    if (ga4 && window.gtag) window.gtag('config', ga4, { user_id: uid, send_page_view: false });
    if (clarity && window.clarity) window.clarity('identify', uid);
  }
  window.saqzIdentify = function (uid, firebaseUid) {
    if (!uid) return;
    applyUser(uid);
    if (!statsOn) return;
    try { localStorage.setItem(USER_KEY, JSON.stringify({ uid: uid, fb: firebaseUid || null })); } catch (e) {}
  };
  window.saqzIdentifyFirebase = function (firebaseUid) {
    if (!firebaseUid || !statsOn) return;
    try {
      var saved = JSON.parse(localStorage.getItem(USER_KEY) || 'null');
      if (saved && saved.uid && saved.fb === firebaseUid) applyUser(saved.uid);
    } catch (e) {}
  };

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
