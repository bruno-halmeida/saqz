const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const page = fs.readFileSync(path.join(root, "termos/index.html"), "utf8");
const home = fs.readFileSync(path.join(root, "index.html"), "utf8");

test("terms name the operator and link the privacy policy and the receivables terms", () => {
  assert.match(page, /Égide Sistemas/);
  assert.match(page, /68\.648\.777\/0001-09/);
  assert.match(page, /href="\/privacidade\/"/);
  assert.match(page, /href="\/termos\/recebimentos\/"/);
});

test("terms state the owner's decisions: age, cancellation and venue", () => {
  assert.match(page, /18 anos ou mais/);
  assert.match(page, /autorização e o acompanhamento dos responsáveis legais/);
  assert.match(page, /continua até o fim do período já pago/);
  assert.match(page, /Não há reembolso proporcional/);
  assert.match(page, /Comarca de Curitiba, Paraná/);
});

test("terms match the trial rules published on the home page", () => {
  assert.match(page, /14 dias/);
  assert.match(page, /não exige cartão/);
  assert.match(page, /primeiro grupo é criado com sucesso/);
  assert.match(home, /14 dias/);
});

test("terms keep the statutory consumer rights", () => {
  assert.match(page, /7 dias corridos/);
  assert.match(page, /Código de Defesa do Consumidor/);
  assert.match(page, /foro do seu domicílio/);
});

test("terms page is static, canonical and price free", () => {
  assert.doesNotMatch(page, /<script/);
  assert.match(page, /rel="canonical" href="https:\/\/saqz\.app\/termos\/"/);
  assert.doesNotMatch(page, /R\$/);
  assert.doesNotMatch(page, /\{\{|TODO|PLACEHOLDER/);
});

test("home links to the terms of use", () => {
  assert.match(home, /href="\/termos\/"/);
});
