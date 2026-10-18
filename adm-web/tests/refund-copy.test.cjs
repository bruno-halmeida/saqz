const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const html = fs.readFileSync(require.resolve('../assinar/index.html'), 'utf8');
const copy = /Quer cancelar a compra\? Em até 7 dias da primeira cobrança, escreva para <a href="mailto:contato@egysis\.com">contato@egysis\.com<\/a> e devolvemos o valor\./;
function section(id) {
  const start = html.indexOf(`<section id="${id}"`);
  return html.slice(start, html.indexOf('</section>', start));
}
test('assinar mostra a política de reembolso perto do botão de pagar', () => {
  const dados = section('estado-dados');
  assert.match(dados, copy);
  assert.ok(dados.indexOf('id="botao-pagar"') < dados.indexOf('id="dados-reembolso"'));
});
test('assinar mostra a política de reembolso na tela de sucesso', () => {
  assert.match(section('estado-sucesso'), copy);
});
