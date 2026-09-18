const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const page = fs.readFileSync(path.join(root, "privacidade/index.html"), "utf8");
const home = fs.readFileSync(path.join(root, "index.html"), "utf8");

test("privacy page names the controller, the CNPJ and the contact channel", () => {
  assert.match(page, /Égide Sistemas/);
  assert.match(page, /68\.648\.777\/0001-09/);
  assert.match(page, /mailto:contato@egysis\.com/);
});

test("privacy page declares usage and diagnostics data and the operators behind them", () => {
  assert.match(page, /Dados de uso:/);
  assert.match(page, /Dados de diagnóstico:/);
  assert.match(page, /Google \(Firebase\)/);
  assert.match(page, /Asaas/);
  assert.match(page, /identificador de publicidade/);
});

test("privacy page covers LGPD rights, retention and account deletion", () => {
  assert.match(page, /Lei 13\.709\/2018/);
  assert.match(page, /Seus direitos/);
  assert.match(page, /excluir a conta/);
  assert.match(page, /ANPD/);
});

test("privacy page is static and canonical", () => {
  assert.doesNotMatch(page, /<script/);
  assert.match(page, /rel="canonical" href="https:\/\/saqz\.app\/privacidade\/"/);
  assert.doesNotMatch(page, /\{\{|TODO|PLACEHOLDER/);
});

test("home links to the privacy policy", () => {
  assert.match(home, /href="\/privacidade\/"/);
});
