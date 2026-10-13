// Service worker do web app (escopo /app/): guarda o que pesa para as próximas aberturas ficarem rápidas.
//  - .wasm (nome com hash do conteúdo) e scripts com ?v=: cache primeiro, nunca mudam sob o mesmo nome;
//  - fontes e composeResources (nome sem hash): cache na hora e atualização em segundo plano;
//  - todo o resto (index.html, saqz-config.js, API, /__/auth/…): direto da rede, sem passar pelo cache.
// Troca segura de versão: a versão nova instala ao lado, assume (skipWaiting + claim) e só então apaga os
// caches antigos; como as URLs versionadas não colidem, nenhuma página recebe arquivo de outra versão.
// Os testes ficam em src/wasmJsTest/node/saqz-sw.test.mjs.
(function (root) {
  "use strict";

  var CACHE_PREFIX = "saqz-app-";
  var CACHE_NAME = CACHE_PREFIX + "v1";
  // Cada publicação deixa um .wasm de ~20 MB descomprimido: guardar só os dois mais recentes.
  var MAX_WASM_ENTRIES = 2;

  /** "immutable" | "revalidate" | null (não interceptar). */
  function strategyFor(request, scopeUrl) {
    if (request.method !== "GET") return null;
    var url = new URL(request.url);
    var scope = new URL(scopeUrl);
    if (url.origin !== scope.origin || url.pathname.indexOf(scope.pathname) !== 0) return null;
    var relative = url.pathname.slice(scope.pathname.length);
    if (relative.indexOf("api/") === 0 || relative.indexOf("__/") === 0) return null;
    if (/^[0-9a-f]{16,}\.wasm$/.test(relative)) return "immutable";
    if (/^saqz-(web|bridge|loader)\.js$/.test(relative) && url.searchParams.has("v")) return "immutable";
    if (/\.ttf$/.test(relative) || relative.indexOf("composeResources/") === 0) return "revalidate";
    return null;
  }

  function cacheable(response) {
    return response && response.ok && response.status === 200;
  }

  function store(caches, request, response) {
    return caches.open(CACHE_NAME).then(function (cache) {
      return cache.put(request, response).then(function () {
        if (!/\.wasm$/.test(new URL(request.url).pathname)) return null;
        return cache.keys().then(function (keys) {
          var wasm = keys.filter(function (key) { return /\.wasm$/.test(new URL(key.url).pathname); });
          // keys() devolve na ordem de inserção: os primeiros são os mais antigos.
          return Promise.all(wasm.slice(0, Math.max(0, wasm.length - MAX_WASM_ENTRIES)).map(function (key) {
            return cache.delete(key);
          }));
        });
      });
    });
  }

  function respond(strategy, request, caches, fetcher) {
    return caches.open(CACHE_NAME).then(function (cache) {
      return cache.match(request).then(function (cached) {
        if (cached && strategy === "immutable") return { response: cached, background: null };
        var network = fetcher(request).then(function (response) {
          if (!cacheable(response)) return response;
          var copy = response.clone();
          return store(caches, request, copy).then(function () { return response; }, function () { return response; });
        });
        if (cached) return { response: cached, background: network.catch(function () { return null; }) };
        return { response: network, background: null };
      });
    });
  }

  function purgeOldCaches(caches) {
    return caches.keys().then(function (names) {
      return Promise.all(names
        .filter(function (name) { return name.indexOf(CACHE_PREFIX) === 0 && name !== CACHE_NAME; })
        .map(function (name) { return caches.delete(name); }));
    });
  }

  var api = {
    CACHE_NAME: CACHE_NAME,
    strategyFor: strategyFor,
    respond: respond,
    purgeOldCaches: purgeOldCaches,
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;

  if (root && typeof root.addEventListener === "function" && root.registration) {
    root.addEventListener("install", function () { root.skipWaiting(); });
    root.addEventListener("activate", function (event) {
      event.waitUntil(purgeOldCaches(root.caches).then(function () { return root.clients.claim(); }));
    });
    root.addEventListener("fetch", function (event) {
      var strategy = strategyFor(event.request, root.registration.scope);
      if (!strategy) return;
      var outcome = respond(strategy, event.request, root.caches, function (request) { return root.fetch(request); });
      event.respondWith(outcome.then(function (result) {
        if (result.background) event.waitUntil(result.background);
        return result.response;
      }));
    });
  }
})(typeof self !== "undefined" ? self : null);
