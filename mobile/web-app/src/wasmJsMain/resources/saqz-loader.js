// Tela leve de carregamento do web app: JS puro, roda antes do .wasm (~6 MB) subir.
// Mostra a marca, o progresso real do download do .wasm e, em link de convite, para onde a pessoa está entrando.
// Some quando o Kotlin remove #saqz-loading (Main.kt). Os testes em src/wasmJsTest/node/saqz-loader.test.mjs
// exercitam as funções exportadas no fim do arquivo.
(function (root) {
  "use strict";

  var INVITE_CODE = /^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$/;
  var WASM_URL = /\.wasm(\?|$)/;
  // O .wasm sai do servidor comprimido (gzip) e o fetch entrega os bytes já descomprimidos: o tamanho de
  // referência é o que a própria pessoa baixou na visita anterior; sem ele, estima-se pela razão típica.
  var TYPICAL_GZIP_RATIO = 2.6;
  var STORAGE_PREFIX = "saqz.wasmBytes.";
  // Passado este tempo na tela de carregamento, aparece "Recarregar".
  var RELOAD_AFTER_MS = 20000;

  function queryParam(search, name) {
    var entries = String(search || "").replace(/^\?/, "").split("&");
    for (var i = 0; i < entries.length; i++) {
      var separator = entries[i].indexOf("=");
      var key = separator < 0 ? entries[i] : entries[i].slice(0, separator);
      if (key !== name) continue;
      try {
        return decodeURIComponent((separator < 0 ? "" : entries[i].slice(separator + 1)).replace(/\+/g, " "));
      } catch (e) {
        return null;
      }
    }
    return null;
  }

  /** O que a URL pede: {kind: "invite", code}, {kind: "attendance"} ou null. */
  function linkContext(search) {
    var invite = queryParam(search, "saqz_invite");
    if (invite !== null && INVITE_CODE.test(invite)) return { kind: "invite", code: invite };
    var attendance = queryParam(search, "saqz_attendance");
    if (attendance !== null && INVITE_CODE.test(attendance)) return { kind: "attendance" };
    return null;
  }

  function clean(value) {
    return typeof value === "string" ? value.replace(/\s+/g, " ").trim() : "";
  }

  /** "Entrando no <grupo> · convite de <dono>"; sem grupo na resposta, não há o que mostrar. */
  function inviteLine(preview) {
    var group = clean(preview && preview.groupName);
    if (!group) return null;
    var inviter = clean(preview && preview.inviterName);
    return "Entrando no " + group + (inviter ? " · convite de " + inviter : "");
  }

  function attendanceLine() {
    return "Abrindo a lista de presença";
  }

  /** Fração 0..1 do download. Nunca passa de 0,97: o 100% é a tela do app aparecendo. */
  function progressFraction(state) {
    var total = state.total > 0 ? state.total : 0;
    if (!total) return 0;
    return Math.max(0, Math.min(0.97, state.received / total));
  }

  /** Tamanho esperado do corpo já descomprimido. */
  function expectedBytes(contentLength, encoded, remembered) {
    if (remembered > 0) return remembered;
    if (!(contentLength > 0)) return 0;
    return encoded ? Math.round(contentLength * TYPICAL_GZIP_RATIO) : contentLength;
  }

  function mount(doc) {
    var host = doc.getElementById("saqz-loading");
    if (!host) return null;
    var bar = host.querySelector(".sq-bar > i");
    var track = host.querySelector(".sq-bar");
    var context = host.querySelector(".sq-context");
    var reload = host.querySelector(".sq-reload");
    return {
      /** Depois de ~20 s ainda aqui: a 1ª carga travou (rede lenta, servidor sem compressão) e recarregar costuma resolver. */
      showReload: function () {
        if (!host.isConnected || !reload) return;
        reload.hidden = false;
      },
      setProgress: function (fraction) {
        if (!host.isConnected || !bar) return;
        bar.style.transform = "scaleX(" + fraction.toFixed(3) + ")";
        track.classList.toggle("sq-indeterminate", fraction <= 0);
        track.setAttribute("aria-valuenow", String(Math.round(fraction * 100)));
      },
      setContext: function (text) {
        if (!host.isConnected || !context || !text) return;
        context.textContent = text;
        context.hidden = false;
      },
    };
  }

  /** Soma os downloads em andamento (o app baixa o saqz-web.wasm e o skiko.wasm em paralelo); nunca anda para trás. */
  function createAggregate(onProgress) {
    var files = {};
    var best = 0;
    return function update(name, received, total) {
      files[name] = { received: received, total: total };
      var sumReceived = 0;
      var sumTotal = 0;
      for (var key in files) {
        sumReceived += files[key].received;
        sumTotal += files[key].total;
      }
      var fraction = progressFraction({ received: sumReceived, total: sumTotal });
      if (fraction > best) best = fraction;
      onProgress(best);
    };
  }

  /** Embrulha o fetch para contar os bytes dos .wasm sem mudar a resposta que o loader do Kotlin recebe. */
  function trackWasm(win, onProgress) {
    var original = win.fetch;
    if (typeof original !== "function" || typeof win.ReadableStream !== "function") return;
    var update = createAggregate(onProgress);
    win.fetch = function (input) {
      var url = typeof input === "string" ? input : (input && input.url) || "";
      var pending = original.apply(this, arguments);
      if (!WASM_URL.test(url)) return pending;
      return pending.then(function (response) {
        if (!response.ok || !response.body) return response;
        var name = url.split("?")[0].split("/").pop();
        var key = STORAGE_PREFIX + name;
        var remembered = 0;
        try { remembered = Number(win.localStorage.getItem(key)) || 0; } catch (e) { remembered = 0; }
        var total = expectedBytes(
          Number(response.headers.get("content-length")),
          !!response.headers.get("content-encoding"),
          remembered,
        );
        var received = 0;
        update(name, 0, total);
        var reader = response.body.getReader();
        var stream = new win.ReadableStream({
          pull: function (controller) {
            return reader.read().then(function (step) {
              if (step.done) {
                try { win.localStorage.setItem(key, String(received)); } catch (e) { /* sem storage: segue */ }
                update(name, received, received);
                controller.close();
                return;
              }
              received += step.value.byteLength;
              update(name, received, Math.max(total, received));
              controller.enqueue(step.value);
            });
          },
          cancel: function (reason) { return reader.cancel(reason); },
        });
        return new win.Response(stream, {
          status: response.status,
          statusText: response.statusText,
          headers: response.headers,
        });
      });
    };
  }

  /**
   * Os cabeçalhos X-Saqz-* que o web app manda em toda chamada à API (o mesmo que o HttpTransport faz depois que o
   * Kotlin sobe): a plataforma "web" e, se o deploy gravou uma versão X.Y.Z no saqz-config.js, a versão. Sem isso
   * o backend leria esta chamada como de um app antigo. Valor fora do padrão é omitido, nunca quebra a chamada.
   */
  function appHeaders(config) {
    var headers = { "X-Saqz-Platform": "web" };
    var version = config && config.appVersion;
    if (typeof version === "string" && version.length <= 32 && /^[0-9]+\.[0-9]+\.[0-9]+$/.test(version)) {
      headers["X-Saqz-App-Version"] = version;
    }
    return headers;
  }

  function loadContext(win, ui) {
    var link = linkContext(win.location.search);
    if (!link) return;
    if (link.kind === "attendance") {
      // A prévia de presença exige login (POST /api/attendance-links/resolve): só dá para dizer o que está abrindo.
      ui.setContext(attendanceLine());
      return;
    }
    var config = win.SAQZ_WEB_CONFIG || {};
    var base = String(config.apiBaseUrl || win.location.origin).replace(/\/+$/, "");
    var controller = typeof win.AbortController === "function" ? new win.AbortController() : null;
    var timer = controller ? win.setTimeout(function () { controller.abort(); }, 5000) : null;
    // Chamada anônima e sem efeito: só inválidos entram no limite de tentativas do backend.
    var headers = appHeaders(config);
    headers["Content-Type"] = "application/json";
    win.fetch(base + "/api/invites/preview", {
      method: "POST",
      headers: headers,
      body: JSON.stringify({ code: link.code }),
      signal: controller ? controller.signal : undefined,
    }).then(function (response) {
      return response.ok ? response.json() : null;
    }).then(function (preview) {
      ui.setContext(inviteLine(preview));
    }).catch(function () {
      /* Prévia falhou: fica só a marca. */
    }).then(function () {
      if (timer) win.clearTimeout(timer);
    });
  }

  function start(win, doc) {
    var ui = mount(doc);
    if (!ui) return;
    ui.setProgress(0);
    var button = typeof doc.querySelector === "function" ? doc.querySelector("#saqz-loading .sq-reload button") : null;
    if (button && typeof button.addEventListener === "function") {
      button.addEventListener("click", function () { win.location.reload(); });
    }
    if (typeof win.setTimeout === "function") win.setTimeout(ui.showReload, RELOAD_AFTER_MS);
    trackWasm(win, ui.setProgress);
    loadContext(win, ui);
  }

  var api = {
    linkContext: linkContext,
    inviteLine: inviteLine,
    attendanceLine: attendanceLine,
    progressFraction: progressFraction,
    expectedBytes: expectedBytes,
    appHeaders: appHeaders,
    RELOAD_AFTER_MS: RELOAD_AFTER_MS,
    trackWasm: trackWasm,
    start: start,
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  if (root && root.document) start(root, root.document);
})(typeof window !== "undefined" ? window : null);
