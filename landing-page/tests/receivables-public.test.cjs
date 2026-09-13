const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

test("checkout return is neutral and never reads query parameters", () => {
  const html = fs.readFileSync(path.join(__dirname, "../recebimentos/retorno/index.html"), "utf8");
  assert.match(html, /não confirma o pagamento/i);
  assert.doesNotMatch(html, /location\.(search|hash)|URLSearchParams|pagamento confirmado/i);
});

test("terms page fetches the configured API across origins and renders published text literally", async () => {
  const vm = require("node:vm");
  const script = fs.readFileSync(path.join(__dirname, "../assets/receivables-terms.js"), "utf8");
  const nodes = { meta: {}, content: {} };
  let request;
  vm.runInNewContext(script, {
    URL, Date,
    document: { querySelector: () => ({ content: "https://api.example.test" }), getElementById: id => nodes[id] },
    fetch: async (url, options) => {
      request = { url, options };
      return { ok: true, json: async () => ({ version: "v1", content: "<b>Texto publicado</b>",
        effectiveAt: "2020-01-01T00:00:00Z", publishedAt: "2020-01-01T00:00:00Z" }) };
    }
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(request.url, "https://api.example.test/public/receivables/terms/current");
  assert.equal(request.options.credentials, "omit");
  assert.equal(nodes.content.textContent, "<b>Texto publicado</b>");
  assert.equal(nodes.content.innerHTML, undefined);
});

test("future or missing terms never produce fallback legal content", async () => {
  const vm = require("node:vm");
  const script = fs.readFileSync(path.join(__dirname, "../assets/receivables-terms.js"), "utf8");
  const nodes = { meta: {}, content: {} };
  vm.runInNewContext(script, {
    URL, Date, document: { querySelector: () => ({ content: "https://api.example.test" }), getElementById: id => nodes[id] },
    fetch: async () => ({ ok: true, json: async () => ({ version: "v2", content: "Futuro", effectiveAt: "2999-01-01", publishedAt: "2999-01-01" }) })
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(nodes.content.textContent, undefined);
  assert.equal(nodes.meta.textContent, "Nenhum termo publicado está disponível agora.");
});
