// Imita saqz.app (porta 8787) e links.saqz.app (porta 8788) contra o stack local (stack.sh up).
// Analytics desligado (nada vai para GA4/Clarity/Meta); Auth Emulator e API repassados pelo mesmo origin.
// /app e /app/* redirecionam para links.saqz.app local, como o nginx da landing faz em produção.
//
//   node tests/multi-aparelhos/ferramentas/saqz-app-local.mjs
//   Safari do simulador: http://localhost:8787/comecar/ · convite: http://localhost:8788/?saqz_invite=<código>
import http from "node:http";
import fs from "node:fs";
import path from "node:path";

const ROOT = new URL("../../..", import.meta.url).pathname.replace(/\/$/, "");
const API = { host: "127.0.0.1", port: 18080 };
const AUTH = { host: "127.0.0.1", port: 9099 };
const MIME = { ".html": "text/html; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".css": "text/css",
  ".json": "application/json", ".svg": "image/svg+xml", ".png": "image/png",
  ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".ttf": "font/ttf", ".woff2": "font/woff2", ".webmanifest": "application/manifest+json",
  ".txt": "text/plain", ".xml": "application/xml", ".ico": "image/x-icon", ".map": "application/json" };

const siteConfig = `window.SAQZ_FIREBASE_CONFIG = {
  apiKey: "fake-saqz-local-api-key", authDomain: window.location.host, projectId: "saqz-local",
  authEmulatorUrl: window.location.origin, apiBaseUrl: window.location.origin + "/api",
  iosAppStoreUrl: "", androidPlayStoreUrl: "https://play.google.com/store/apps/details?id=app.saqz",
};`;
const analyticsOff = `window.SAQZ_ANALYTICS = { ga4MeasurementId: "", clarityProjectId: "", metaPixelId: "" };`;

function send(res, code, body, type = "text/plain; charset=utf-8") {
  res.writeHead(code, { "Content-Type": type, "Cache-Control": "no-cache" }); res.end(body);
}
function file(res, p, transform) {
  try {
    let st = fs.statSync(p);
    if (st.isDirectory()) { p = path.join(p, "index.html"); st = fs.statSync(p); }
    const type = MIME[path.extname(p)] || "application/octet-stream";
    if (transform) return send(res, 200, transform(fs.readFileSync(p, "utf8")), type);
    res.writeHead(200, { "Content-Type": type, "Content-Length": st.size, "Cache-Control": "no-cache" });
    fs.createReadStream(p).pipe(res);
  } catch { send(res, 404, "404 " + p.replace(ROOT, "")); }
}
function proxy(req, res, target, newPath) {
  const headers = { ...req.headers, host: `${target.host}:${target.port}` };
  const up = http.request({ ...target, method: req.method, path: newPath, headers }, (r) => {
    res.writeHead(r.statusCode, r.headers); r.pipe(res);
  });
  up.on("error", (e) => send(res, 502, "proxy: " + e.message));
  req.pipe(up);
}
const authPrefixes = ["/emulator/", "/identitytoolkit.googleapis.com/", "/securetoken.googleapis.com/", "/www.googleapis.com/"];

http.createServer((req, res) => {
  const url = new URL(req.url, "http://x");
  const p = decodeURIComponent(url.pathname);
  if (authPrefixes.some((x) => p.startsWith(x))) return proxy(req, res, AUTH, req.url);
  if (p.startsWith("/api/")) return proxy(req, res, API, req.url.slice("/api".length));
  if (p === "/app" || p.startsWith("/app/")) { res.writeHead(301, { Location: "http://localhost:8788/" + url.search }); return res.end(); }
  if (p === "/assets/firebase-config.js") return send(res, 200, siteConfig, MIME[".js"]);
  if (p === "/assets/analytics-config.js") return send(res, 200, analyticsOff, MIME[".js"]);
  const m = p.match(/^\/assets\/(logo\.png|inter-var\.ttf|saqz-volleyball\.svg)$/);
  if (m) return file(res, `${ROOT}/adm-web/assets/${m[1]}`);
  if (p === "/comecar" || p === "/assinar") { res.writeHead(301, { Location: p + "/" + url.search }); return res.end(); }
  if (p.startsWith("/comecar/") || p.startsWith("/assinar/")) return file(res, `${ROOT}/adm-web${p}`);
  return file(res, `${ROOT}/landing-page${p}`);
}).listen(8787, "0.0.0.0", () => console.log("saqz.app local em http://localhost:8787"));

// links.saqz.app local: o "site" da página aponta para o saqz.app local.
http.createServer((req, res) => {
  const p = decodeURIComponent(new URL(req.url, "http://x").pathname);
  if (p.startsWith("/assets/")) return file(res, `${ROOT}/links-page${p}`);
  if (p.startsWith("/.well-known/")) return file(res, `${ROOT}/links-page${p}`);
  return file(res, `${ROOT}/links-page/index.html`, (html) =>
    html.replaceAll('var site = "https://saqz.app"', 'var site = "http://localhost:8787"'));
}).listen(8788, "0.0.0.0", () => console.log("links.saqz.app local em http://localhost:8788"));
