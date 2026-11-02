// A área de conteúdo do adm é uma grade com altura fixa: a linha precisa seguir a altura do cartão para a tela rolar.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const html = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8');

test('cartão com overflow:hidden não é espremido na grade da tela', () => {
  assert.match(html, /\.adm-body\{grid-auto-rows:max-content\}/);
});

test('membros do grupo seguem em cartão com a paginação dentro da área que rola', () => {
  const ficha = html.slice(html.indexOf('<strong style="font-size:15px;font-weight:700">Membros</strong>'));
  assert.ok(ficha.indexOf('Paginação de membros') > 0);
  assert.ok(ficha.indexOf('Paginação de membros') < ficha.indexOf('Últimos jogos'));
});
