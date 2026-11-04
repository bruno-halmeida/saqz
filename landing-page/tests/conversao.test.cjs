const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (file) => fs.readFileSync(path.join(root, file), "utf8");
const home = read("index.html");
const counter = read("assets/conversao.js");
const analytics = read("assets/analytics.js");
const policy = read("privacidade/index.html");

test("the page's own count carries no cookie, referrer or click id", () => {
  assert.match(counter, /referrerPolicy: 'no-referrer'/);
  assert.match(counter, /credentials: 'omit'/);
  const code = counter.replace(/^\s*\/\/.*$/gm, "");
  assert.doesNotMatch(code, /fbclid|localStorage|document\.cookie/);
  assert.match(code, /'\/assets\/m\.json\?e=' \+ evento \+ '&a=' \+ encodeURIComponent\(anuncio\)/);
  assert.ok(fs.existsSync(path.join(root, "assets/m.json")));
  assert.match(policy, /Contar o uso da página inicial/);
});

test("the consent notice waits for the first scroll and never records a choice by itself", () => {
  assert.match(home, /<html lang="pt-BR" data-aviso="rolagem">/);
  const waiting = analytics.slice(analytics.indexOf("function askIfNeeded"), analytics.indexOf("document.addEventListener('click'"));
  assert.match(waiting, /addEventListener\('scroll'/);
  assert.doesNotMatch(waiting, /writeConsent|saqzConsent/);
});

test("every ad keeps its own top and the top plays the silent video with a pause button", () => {
  for (const ad of ["f-lista-espera", "v1-editor", "v1-designer", "a-cobrar", "c-antes-depois", "b-quem-vai"]) {
    assert.ok(home.includes(`'${ad}':`), ad);
  }
  const start = home.indexOf('<figure class="hero-video">');
  assert.ok(start > 0);
  const figure = home.slice(start, home.indexOf("</figure>", start));
  for (const attr of [" muted", " autoplay", " loop", " playsinline", 'poster="assets/video/lista-whatsapp-capa.jpg"', "data-video-pausa"]) {
    assert.ok(figure.includes(attr), attr);
  }
  for (const file of ["assets/video/lista-whatsapp.mp4", "assets/video/lista-whatsapp.webm", "assets/video/lista-whatsapp-capa.jpg"]) {
    assert.ok(figure.includes(file), file);
    assert.ok(fs.existsSync(path.join(root, file)), file);
  }
  assert.doesNotMatch(home, /data-resposta|data-recebi|card-espera|card-caixa/);
  assert.match(counter, /conta\('video'\)/);
  assert.match(counter, /conta\('video-fim'\)/);
  assert.match(counter, /prefers-reduced-motion: reduce/);
});

test("the top says the group uses the app and the testimonials name real groups", () => {
  assert.ok(home.includes("Você e a galera usam o app. Cada um instala uma vez."));
  assert.ok(home.includes("Isabelle, organiza o Vôlei de quinta"));
  assert.ok(home.includes("Alexandre, organiza o MBJR"));
});
