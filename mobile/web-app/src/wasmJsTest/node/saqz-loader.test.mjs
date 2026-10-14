// Testes do saqz-loader.js (tela leve de carregamento). Rodar: node --test src/wasmJsTest/node
import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const loader = require("../../wasmJsMain/resources/saqz-loader.js");

const CODE = "A".repeat(42) + "A";

test("reconhece convite e presença válidos e ignora o resto", () => {
  assert.deepEqual(loader.linkContext(`?saqz_invite=${CODE}`), { kind: "invite", code: CODE });
  assert.deepEqual(loader.linkContext(`?x=1&saqz_attendance=${CODE}`), { kind: "attendance" });
  assert.equal(loader.linkContext("?saqz_invite=curto"), null);
  assert.equal(loader.linkContext(""), null);
  assert.equal(loader.linkContext("?saqz_invite=%E0%A4%A"), null);
});

test("linha do convite com e sem dono, e sem grupo não mostra nada", () => {
  assert.equal(
    loader.inviteLine({ groupName: "Vôlei de Quinta", inviterName: "Bruno" }),
    "Entrando no Vôlei de Quinta · convite de Bruno",
  );
  assert.equal(loader.inviteLine({ groupName: " Quinta  ", inviterName: null }), "Entrando no Quinta");
  assert.equal(loader.inviteLine({ inviterName: "Bruno" }), null);
  assert.equal(loader.inviteLine(null), null);
});

test("progresso nunca passa de 97% antes de terminar", () => {
  assert.equal(loader.progressFraction({ received: 50, total: 100 }), 0.5);
  assert.equal(loader.progressFraction({ received: 500, total: 100 }), 0.97);
  assert.equal(loader.progressFraction({ received: 5, total: 0 }), 0);
});

test("tamanho esperado: lembrado, direto ou estimado quando vem comprimido", () => {
  assert.equal(loader.expectedBytes(100, true, 900), 900);
  assert.equal(loader.expectedBytes(100, false, 0), 100);
  assert.equal(loader.expectedBytes(100, true, 0), 260);
  assert.equal(loader.expectedBytes(NaN, false, 0), 0);
});

function fakeWindow({ origin = "https://saqz.app", search = "", wasmBody, preview, appVersion }) {
  const storage = new Map();
  const calls = [];
  return {
    ReadableStream,
    Response,
    AbortController,
    localStorage: { getItem: (k) => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v) },
    location: { origin, search },
    SAQZ_WEB_CONFIG: { apiBaseUrl: origin + "/app/api", appVersion },
    setTimeout: () => 0,
    clearTimeout: () => {},
    storage,
    calls,
    fetch: async (url, init) => {
      calls.push({ url, init });
      if (url.endsWith(".wasm")) {
        return new Response(wasmBody, {
          headers: { "content-type": "application/wasm", "content-length": String(wasmBody.length) },
        });
      }
      if (preview === "falha") throw new Error("rede");
      return new Response(JSON.stringify(preview), { status: 200 });
    },
  };
}

function fakeDocument() {
  const bar = { style: {} };
  const track = { classList: { toggle() {} }, setAttribute() {} };
  const contextNode = { hidden: true, textContent: "" };
  const host = {
    isConnected: true,
    querySelector: (sel) => ({ ".sq-bar > i": bar, ".sq-bar": track, ".sq-context": contextNode })[sel],
  };
  return { contextNode, doc: { getElementById: (id) => (id === "saqz-loading" ? host : null) } };
}

test("conta os bytes do .wasm, entrega o corpo intacto e lembra o tamanho", async () => {
  const body = new Uint8Array(1000).fill(7);
  const win = fakeWindow({ wasmBody: body });
  const seen = [];
  loader.trackWasm(win, (f) => seen.push(f));
  const response = await win.fetch("https://saqz.app/app/abcdef0123456789abcd.wasm");
  const delivered = new Uint8Array(await response.arrayBuffer());
  assert.equal(delivered.length, 1000);
  assert.equal(response.headers.get("content-type"), "application/wasm");
  assert.equal(seen.at(-1), 0.97);
  assert.ok(seen.every((f) => f >= 0 && f <= 1));
  assert.equal(win.storage.get("saqz.wasmBytes.abcdef0123456789abcd.wasm"), "1000");
});

test("dois .wasm em paralelo somam e a barra não anda para trás", async () => {
  const win = fakeWindow({ wasmBody: new Uint8Array(1000) });
  const seen = [];
  loader.trackWasm(win, (f) => seen.push(f));
  const [a, b] = await Promise.all([
    win.fetch("https://saqz.app/app/aaaaaaaaaaaaaaaaaaaa.wasm"),
    win.fetch("https://saqz.app/app/bbbbbbbbbbbbbbbbbbbb.wasm"),
  ]);
  await Promise.all([a.arrayBuffer(), b.arrayBuffer()]);
  for (let i = 1; i < seen.length; i++) assert.ok(seen[i] >= seen[i - 1]);
  assert.equal(seen.at(-1), 0.97);
});

test("fetch de outras URLs passa direto", async () => {
  const win = fakeWindow({ wasmBody: new Uint8Array(1), preview: {} });
  loader.trackWasm(win, () => {});
  const response = await win.fetch("https://saqz.app/app/api/x");
  assert.equal(response.status, 200);
});

test("start busca a prévia do convite e mostra o contexto", async () => {
  const win = fakeWindow({
    search: `?saqz_invite=${CODE}`,
    wasmBody: new Uint8Array(1),
    preview: { groupName: "Vôlei de Quinta", inviterName: "Bruno" },
  });
  const { doc, contextNode } = fakeDocument();
  loader.start(win, doc);
  await new Promise((r) => setTimeout(r, 10));
  const call = win.calls.find((c) => c.url.endsWith("/api/invites/preview"));
  assert.equal(call.url, "https://saqz.app/app/api/api/invites/preview");
  assert.deepEqual(JSON.parse(call.init.body), { code: CODE });
  assert.equal(contextNode.textContent, "Entrando no Vôlei de Quinta · convite de Bruno");
  assert.equal(contextNode.hidden, false);
});

test("a prévia se identifica como o web app, com a versão que o deploy gravou", async () => {
  const win = fakeWindow({
    search: `?saqz_invite=${CODE}`,
    wasmBody: new Uint8Array(1),
    preview: { groupName: "Vôlei de Quinta" },
    appVersion: "1.0.3",
  });
  loader.start(win, fakeDocument().doc);
  await new Promise((r) => setTimeout(r, 10));
  const call = win.calls.find((c) => c.url.endsWith("/api/invites/preview"));
  assert.deepEqual(call.init.headers, {
    "X-Saqz-Platform": "web",
    "X-Saqz-App-Version": "1.0.3",
    "Content-Type": "application/json",
  });
});

test("sem versão no config a prévia manda só a plataforma", async () => {
  for (const appVersion of [undefined, "web", "web (dev)"]) {
    const win = fakeWindow({ search: `?saqz_invite=${CODE}`, wasmBody: new Uint8Array(1), preview: {}, appVersion });
    loader.start(win, fakeDocument().doc);
    await new Promise((r) => setTimeout(r, 10));
    const call = win.calls.find((c) => c.url.endsWith("/api/invites/preview"));
    assert.deepEqual(call.init.headers, { "X-Saqz-Platform": "web", "Content-Type": "application/json" }, String(appVersion));
  }
});

test("appHeaders só aceita X.Y.Z de até 32 caracteres e nunca lança", () => {
  assert.deepEqual(loader.appHeaders({ appVersion: "10.20.30" }), { "X-Saqz-Platform": "web", "X-Saqz-App-Version": "10.20.30" });
  const limit = "1." + "0".repeat(28) + ".3";
  assert.equal(limit.length, 32);
  assert.equal(loader.appHeaders({ appVersion: limit })["X-Saqz-App-Version"], limit);
  const invalid = ["", "1.0", "1.0.3.4", "v1.0.3", "1.0.3-beta", " 1.0.3", "1.0.3 ", "1.0.3\n", "1." + "0".repeat(29) + ".3", 103, null, {}];
  for (const appVersion of invalid) {
    assert.deepEqual(loader.appHeaders({ appVersion }), { "X-Saqz-Platform": "web" }, JSON.stringify(appVersion));
  }
  assert.deepEqual(loader.appHeaders(undefined), { "X-Saqz-Platform": "web" });
  assert.deepEqual(loader.appHeaders({}), { "X-Saqz-Platform": "web" });
});

test("prévia que falha deixa só a marca", async () => {
  const win = fakeWindow({ search: `?saqz_invite=${CODE}`, wasmBody: new Uint8Array(1), preview: "falha" });
  const { doc, contextNode } = fakeDocument();
  loader.start(win, doc);
  await new Promise((r) => setTimeout(r, 10));
  assert.equal(contextNode.hidden, true);
});

test("presença mostra só o que está abrindo, sem chamar a API", () => {
  const win = fakeWindow({ search: `?saqz_attendance=${CODE}`, wasmBody: new Uint8Array(1) });
  const { doc, contextNode } = fakeDocument();
  loader.start(win, doc);
  assert.equal(contextNode.textContent, "Abrindo a lista de presença");
  assert.equal(win.calls.length, 0);
});

test("depois de 20 s na tela de carregamento aparece o Recarregar, que recarrega a página", () => {
  const win = fakeWindow({ wasmBody: new Uint8Array(1) });
  const timers = [];
  win.setTimeout = (fn, ms) => { timers.push({ fn, ms }); return 0; };
  let reloaded = false;
  win.location.reload = () => { reloaded = true; };
  let click = null;
  const reload = { hidden: true };
  const button = { addEventListener: (_, fn) => { click = fn; } };
  const bar = { style: {} };
  const track = { classList: { toggle() {} }, setAttribute() {} };
  const host = {
    isConnected: true,
    querySelector: (sel) => ({ ".sq-bar > i": bar, ".sq-bar": track, ".sq-reload": reload })[sel] ?? null,
  };
  const doc = {
    getElementById: () => host,
    querySelector: (sel) => (sel === "#saqz-loading .sq-reload button" ? button : null),
  };
  loader.start(win, doc);
  const timer = timers.find((t) => t.ms === loader.RELOAD_AFTER_MS);
  assert.ok(timer, "agenda o aviso");
  assert.equal(reload.hidden, true);
  timer.fn();
  assert.equal(reload.hidden, false);
  click();
  assert.equal(reloaded, true);
});
