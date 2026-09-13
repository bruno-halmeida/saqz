(function () {
  "use strict";
  var meta = document.getElementById("meta");
  function unavailable() {
    meta.className = "meta error";
    meta.textContent = "Nenhum termo publicado está disponível agora.";
  }
  var base;
  try {
    base = new URL(document.querySelector('meta[name="saqz-api-base-url"]').content);
    if (base.protocol !== "https:" || base.username || base.password) throw new Error();
  } catch (_) { unavailable(); return; }
  fetch(new URL("/public/receivables/terms/current", base).href, {
    headers: { Accept: "application/json" }, cache: "no-store", credentials: "omit"
  }).then(function (response) {
    if (!response.ok) throw new Error();
    return response.json();
  }).then(function (terms) {
    var effective = new Date(terms.effectiveAt);
    var published = new Date(terms.publishedAt);
    if (typeof terms.content !== "string" || !terms.content.trim() ||
        typeof terms.version !== "string" || !terms.version.trim() ||
        !Number.isFinite(effective.getTime()) || effective > new Date() ||
        !Number.isFinite(published.getTime()) || published > new Date()) throw new Error();
    meta.textContent = "Versão " + terms.version + " · vigente desde " + effective.toLocaleDateString("pt-BR");
    document.getElementById("content").textContent = terms.content;
  }).catch(unavailable);
})();
