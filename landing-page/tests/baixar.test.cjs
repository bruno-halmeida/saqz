const test = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");

const root = join(__dirname, "..", "..");
const home = readFileSync(join(root, "landing-page", "index.html"), "utf8");
const nginx = readFileSync(join(root, "nginx-landing.conf"), "utf8");
const config = readFileSync(join(root, "landing-page", "assets", "firebase-config.js"), "utf8");
const analytics = readFileSync(join(root, "landing-page", "assets", "analytics.js"), "utf8");

test("os botões da home levam à loja, não ao cadastro na web", () => {
  assert.ok(!home.includes('href="/comecar/"'), "nenhum botão da home aponta para /comecar/");
  assert.ok(home.includes('<a class="primary" href="/baixar">Baixar grátis'));
  assert.ok(home.includes('<a class="nav-cta" href="/baixar">Baixar grátis</a>'));
  assert.ok(home.includes('<a class="primary" href="/baixar">Testar com meu grupo</a>'));
  assert.ok(home.includes('href="/assinar/"'), "assinar pela web continua na tabela de planos");
});

test("/baixar usa os mesmos links de loja do site", () => {
  const ios = config.match(/iosAppStoreUrl:\s*"([^"]+)"/)[1];
  const android = config.match(/androidPlayStoreUrl:\s*"([^"]+)"/)[1];
  assert.ok(nginx.includes(`"${ios}"`), "App Store igual à de firebase-config.js");
  assert.ok(nginx.includes(`"${android}&referrer=`), "Google Play igual ao de firebase-config.js");
  assert.match(nginx, /location = \/baixar \{\s*add_header Cache-Control "no-store" always;\s*return 302 \$saqz_store_url;/);
  assert.match(nginx, /default\s+"https:\/\/links\.saqz\.app\/";/);
});

test("campanha só entra na URL da loja com lista fechada de caracteres", () => {
  for (const field of ["source", "medium", "campaign", "content"]) {
    const rule = new RegExp(`map \\$arg_utm_${field}\\s+\\$saqz_ref_${field}\\s+\\{ "~\\^\\[A-Za-z0-9\\._-\\]\\{1,60\\}\\$" \\$arg_utm_${field};\\s+default [a-z]+; \\}`);
    assert.match(nginx, rule);
    assert.ok(nginx.includes(`utm_${field}%3D$saqz_ref_${field}`));
  }
});

test("/indicar abre o WhatsApp com o texto pronto e a home marca a origem", () => {
  const link = nginx.match(/location = \/indicar \{[^}]*return 302 "([^"]+)";/)[1];
  const text = new URL(link).searchParams.get("text");
  assert.equal(new URL(link).origin, "https://wa.me");
  assert.match(text, /https:\/\/saqz\.app\/\?utm_source=indicacao/);
  assert.ok(home.includes('<a class="share-link" href="/indicar">'));
});

test("a campanha vai ao /baixar sem levar o fbclid", () => {
  assert.ok(analytics.includes("['utm_source', 'utm_medium', 'utm_campaign', 'utm_content']"));
  assert.ok(!/campaign\.get\('fbclid'\)/.test(analytics));
});
