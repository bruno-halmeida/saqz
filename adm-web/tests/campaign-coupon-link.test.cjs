const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync(require.resolve('../assinar/assinar.js'), 'utf8');
const pick = (from, to) => source.slice(source.indexOf(from), source.indexOf(to));
const link = pick('  // Link de campanha (?cupom=CODIGO)', '\n  function limparTokenDaUrl');
const abrir = pick('  function abrirDados(', '\n  function precoLista(');
const validarFn = source.slice(source.indexOf('  function validarCupom()'), source.indexOf('\n  }\n', source.indexOf('  function validarCupom()')) + 4);

function setup(search, resposta) {
  const el = {}; const erros = []; const pedidos = [];
  const $ = id => (el[id] = el[id] || { value: '', textContent: '', classList: { add() {}, remove() {} } });
  const ctx = vm.createContext({
    window: { location: { search } }, URLSearchParams, $, track() {}, limparFormCartao() {}, selecionarMetodo() {},
    atualizarTotal() {}, mostrar() {}, mostrarErro: (id, t) => { if (t) erros.push(t); }, reais: v => String(v),
    planoEscolhido: null, cupomAplicado: null,
    apiJson: (path, body) => { pedidos.push({ path, body }); return Promise.resolve({ ok: true, json: async () => resposta }); },
  });
  vm.runInContext(`var cupomDaUrl; ${link.replace('var cupomDaUrl =', 'cupomDaUrl =')}\n${abrir}\n${validarFn}\n` +
    'abrirDados({ id: "ORGANIZADOR", name: "Organizador", monthlyPriceCents: 5990, annualPriceCents: 53910 }, "MONTHLY", "Assinar");', ctx);
  return { el, erros, pedidos };
}
const flush = () => new Promise(setImmediate);

test('?cupom= da campanha preenche e valida o cupom ao escolher o plano', async () => {
  const { el, pedidos } = setup('?cupom=volei30', { status: 'APPLIED', discountPercent: 30, finalPriceCents: 4193 });
  assert.equal(el['dados-cupom'].value, 'VOLEI30');
  assert.deepEqual(JSON.parse(JSON.stringify(pedidos[0])), { path: '/coupons/validate', body: { code: 'VOLEI30', planId: 'ORGANIZADOR', cycle: 'MONTHLY' } });
});

test('status novos têm mensagem própria', async () => {
  for (const [status, texto] of [['NOT_ELIGIBLE', /não vale para a sua conta/], ['EXHAUSTED', /limite de usos/], ['INVALID_CYCLE', /não vale para este plano/]]) {
    const { erros } = setup('?cupom=X1', { status });
    await flush(); await flush();
    assert.match(erros.join(' '), texto);
  }
});

test('sem ?cupom= nada é validado', () => {
  assert.equal(setup('', {}).pedidos.length, 0);
});
