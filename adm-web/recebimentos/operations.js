(function (root, factory) {
  "use strict";
  var api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else { root.SaqzReceivablesOperations = api; api.mount(root.document, root.saqzAdmin, root.crypto); }
})(typeof window === "undefined" ? globalThis : window, function () {
  "use strict";
  var forbiddenKinds = new Set(["WITHDRAW", "REFUND"]);
  function normalizeOperation(value) {
    var allowed = ["id", "accountId", "requestId", "kind", "resourceId", "status", "attempts", "failureCode", "createdAt", "updatedAt", "nextAttemptAt", "recoverable"];
    var clean = {};
    allowed.forEach(function (key) { if (Object.prototype.hasOwnProperty.call(value, key)) clean[key] = value[key]; });
    clean.recoverable = clean.recoverable === true && !forbiddenKinds.has(clean.kind);
    return clean;
  }
  function recoveryBody(requestId, reason) {
    var trimmed = String(reason || "").trim();
    if (trimmed.length < 3 || trimmed.length > 500) throw new Error("Motivo deve ter entre 3 e 500 caracteres.");
    return { requestId: requestId, reason: trimmed };
  }
  function checkoutReturnStatus() { return "PENDING_VERIFICATION"; }
  function mount(document, session, crypto) {
    if (!document || !session) return;
    var state = { page: 1, size: 25, status: "", busy: false, data: null };
    var list = document.getElementById("operations"), status = document.getElementById("queue-status");
    function text(tag, value, className) { var node = document.createElement(tag); node.textContent = value; if (className) node.className = className; return node; }
    function load() {
      if (state.busy) return;
      state.busy = true; status.textContent = "Carregando…";
      var query = "?page=" + state.page + "&size=" + state.size + (state.status ? "&status=" + encodeURIComponent(state.status) : "");
      session.fetchAdmin("/admin/receivables/operations" + query).then(function (response) {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.json();
      }).then(function (data) { state.data = data; render(); }).catch(function () {
        status.textContent = "Não foi possível carregar a fila."; status.className = "error";
      }).finally(function () { state.busy = false; });
    }
    function recover(operation, button) {
      var reason = rootPrompt("Motivo da recuperação (será auditado):");
      if (reason == null) return;
      var body;
      try { body = recoveryBody(crypto.randomUUID(), reason); } catch (error) { status.textContent = error.message; status.className = "error"; return; }
      button.disabled = true;
      session.fetchAdmin("/admin/receivables/operations/" + encodeURIComponent(operation.id) + "/recovery", {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body)
      }).then(function (response) {
        if (response.status === 503) { status.textContent = "O estado continua incerto; nenhuma nova cobrança foi criada."; return; }
        if (!response.ok) throw new Error("HTTP " + response.status);
        status.textContent = "Consulta concluída e auditada.";
      }).catch(function () { status.textContent = "Não foi possível reservar a recuperação."; status.className = "error"; })
        .finally(function () { button.disabled = false; load(); });
    }
    function rootPrompt(message) { return typeof window !== "undefined" && window.prompt ? window.prompt(message) : null; }
    function render() {
      list.replaceChildren();
      var data = state.data || { items: [], total: 0, hasNext: false };
      data.items.map(normalizeOperation).forEach(function (operation) {
        var row = document.createElement("article"); row.className = "operation";
        var copy = document.createElement("div");
        copy.append(text("div", operation.status + " · " + operation.kind, "status"));
        copy.append(text("div", "Operação " + operation.id + " · conta " + operation.accountId, "meta"));
        copy.append(text("div", "Tentativas: " + operation.attempts + (operation.failureCode ? " · código: " + operation.failureCode : ""), "meta"));
        row.append(copy);
        var button = text("button", "Consultar e recuperar"); button.type = "button"; button.disabled = !operation.recoverable;
        button.addEventListener("click", function () { recover(operation, button); }); row.append(button); list.append(row);
      });
      status.className = ""; status.textContent = data.total ? data.total + " operação(ões) sem sucesso." : "Nenhuma falha na fila.";
      document.getElementById("page").textContent = "Página " + state.page;
      document.getElementById("previous").disabled = state.page <= 1;
      document.getElementById("next").disabled = !data.hasNext;
    }
    document.getElementById("filters").addEventListener("submit", function (event) { event.preventDefault(); state.page = 1; state.status = document.getElementById("status").value; state.size = Number(document.getElementById("size").value); load(); });
    document.getElementById("previous").addEventListener("click", function () { if (!state.busy && state.page > 1) { state.page--; load(); } });
    document.getElementById("next").addEventListener("click", function () { if (!state.busy && state.data && state.data.hasNext) { state.page++; load(); } });
    document.getElementById("notice-form").addEventListener("submit", function (event) {
      event.preventDefault(); var result = document.getElementById("notice-status");
      var value = function (id) { return document.getElementById(id).value; };
      var body = { requestId: crypto.randomUUID(), audience: value("audience"), title: value("title").trim(), message: value("message").trim(), startsAt: new Date(value("starts") + "Z").toISOString() };
      if (value("ends")) body.endsAt = new Date(value("ends") + "Z").toISOString();
      session.fetchAdmin("/admin/receivables/notices", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) })
        .then(function (response) { if (!response.ok) throw new Error(); result.textContent = "Aviso publicado sem envio externo."; result.className = "success"; })
        .catch(function () { result.textContent = "Não foi possível publicar o aviso."; result.className = "error"; });
    });
    session.onSession(function (me) { if (me) load(); else { state.data = null; list.replaceChildren(); status.textContent = "Aguardando sessão administrativa…"; } });
  }
  return { normalizeOperation: normalizeOperation, recoveryBody: recoveryBody, checkoutReturnStatus: checkoutReturnStatus, mount: mount };
});
