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

test("every ad keeps its own top and the card answers to a real button", () => {
  for (const ad of ["f-lista-espera", "v1-editor", "v1-designer", "a-cobrar", "c-antes-depois", "b-quem-vai"]) {
    assert.ok(home.includes(`'${ad}':`), ad);
  }
  assert.ok(home.includes('<button type="button" class="wa-btn" data-resposta="vou"'));
  assert.ok(home.includes("<button type=\"button\" data-recebi>Recebi</button>"));
});

test("the top says the group uses the app and the testimonials name real groups", () => {
  assert.ok(home.includes("Você e a galera usam o app. Cada um instala uma vez."));
  assert.ok(home.includes("Isabelle, organiza o Vôlei de quinta"));
  assert.ok(home.includes("Alexandre, organiza o MBJR"));
});
