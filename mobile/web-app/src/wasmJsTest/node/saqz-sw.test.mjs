// Testes do saqz-sw.js (service worker). Rodar: node --test src/wasmJsTest/node
import { test } from "node:test";
import assert from "node:assert/strict";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const sw = require("../../wasmJsMain/resources/saqz-sw.js");

const SCOPE = "https://saqz.app/app/";
const get = (path, method = "GET") => ({ url: "https://saqz.app" + path, method });

test("o que entra no cache e o que passa direto", () => {
  assert.equal(sw.strategyFor(get("/app/0123456789abcdef0123.wasm"), SCOPE), "immutable");
  assert.equal(sw.strategyFor(get("/app/saqz-web.js?v=abc123"), SCOPE), "immutable");
  assert.equal(sw.strategyFor(get("/app/saqz-bridge.js?v=abc123"), SCOPE), "immutable");
  assert.equal(
    sw.strategyFor(get("/app/composeResources/br.com.saqz.designsystem.resources/font/inter_bold.ttf"), SCOPE),
    "revalidate",
  );
  assert.equal(sw.strategyFor(get("/app/composeResources/x/drawable/a.png"), SCOPE), "revalidate");
  // sempre rede
  assert.equal(sw.strategyFor(get("/app/"), SCOPE), null);
  assert.equal(sw.strategyFor(get("/app/index.html"), SCOPE), null);
  assert.equal(sw.strategyFor(get("/app/saqz-config.js"), SCOPE), null);
  assert.equal(sw.strategyFor(get("/app/saqz-web.js"), SCOPE), null);
  assert.equal(sw.strategyFor(get("/app/saqz-sw.js"), SCOPE), null);
  assert.equal(sw.strategyFor(get("/app/api/api/session"), SCOPE), null);
  assert.equal(sw.strategyFor(get("/__/auth/handler"), SCOPE), null);
  assert.equal(sw.strategyFor(get("/app/0123456789abcdef0123.wasm", "POST"), SCOPE), null);
  assert.equal(
    sw.strategyFor({ url: "https://outro.com/app/0123456789abcdef0123.wasm", method: "GET" }, SCOPE),
    null,
  );
});

function fakeCaches() {
  const stores = new Map();
  const open = async (name) => {
    if (!stores.has(name)) stores.set(name, new Map());
    const map = stores.get(name);
    return {
      match: async (req) => map.get(req.url)?.clone(),
      put: async (req, res) => { map.set(req.url, res); },
      keys: async () => [...map.keys()].map((url) => ({ url })),
      delete: async (req) => map.delete(req.url),
    };
  };
  return { stores, open, keys: async () => [...stores.keys()], delete: async (n) => stores.delete(n) };
}

test("imutável: busca uma vez e depois serve do cache sem rede", async () => {
  const caches = fakeCaches();
  let network = 0;
  const fetcher = async () => { network++; return new Response("corpo"); };
  const request = get("/app/0123456789abcdef0123.wasm");
  const first = await sw.respond("immutable", request, caches, fetcher);
  assert.equal(await (await first.response).text(), "corpo");
  const second = await sw.respond("immutable", request, caches, fetcher);
  assert.equal(await second.response.text(), "corpo");
  assert.equal(network, 1);
});

test("revalida: serve o cache e atualiza em segundo plano", async () => {
  const caches = fakeCaches();
  const request = get("/app/composeResources/a/font/x.ttf");
  await (await sw.respond("revalidate", request, caches, async () => new Response("v1"))).response;
  const second = await sw.respond("revalidate", request, caches, async () => new Response("v2"));
  assert.equal(await second.response.text(), "v1");
  await second.background;
  const third = await sw.respond("revalidate", request, caches, async () => new Response("v3"));
  assert.equal(await third.response.text(), "v2");
});

test("resposta com erro não é guardada", async () => {
  const caches = fakeCaches();
  const request = get("/app/0123456789abcdef0123.wasm");
  await (await sw.respond("immutable", request, caches, async () => new Response("x", { status: 404 }))).response;
  const after = await sw.respond("immutable", request, caches, async () => new Response("ok"));
  assert.equal(await (await after.response).text(), "ok");
});

test("só os dois .wasm mais recentes ficam guardados", async () => {
  const caches = fakeCaches();
  for (const hash of ["a".repeat(20), "b".repeat(20), "c".repeat(20)]) {
    const r = await sw.respond("immutable", get(`/app/${hash}.wasm`), caches, async () => new Response(hash));
    await r.response;
  }
  const keys = [...caches.stores.get(sw.CACHE_NAME).keys()];
  assert.equal(keys.length, 2);
  assert.ok(!keys.some((k) => k.includes("a".repeat(20))));
});

test("a versão nova apaga os caches antigos do Saqz e só eles", async () => {
  const caches = fakeCaches();
  await caches.open("saqz-app-v0");
  await caches.open(sw.CACHE_NAME);
  await caches.open("outro-app");
  await sw.purgeOldCaches(caches);
  assert.deepEqual((await caches.keys()).sort(), ["outro-app", sw.CACHE_NAME].sort());
});
