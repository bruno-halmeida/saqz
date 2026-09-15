const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync(require.resolve('../assinar/assinar.js'), 'utf8');
const render = source.slice(source.indexOf('  function renderizarPlanos()'), source.indexOf('  function mensagemTroca('));
const plans = [
  { id: 'TITULAR', name: 'Titular', maxGroups: 1, maxAthletes: 25, monthlyPriceCents: 3990, annualPriceCents: 35910 },
  { id: 'ORGANIZADOR', name: 'Organizador', maxGroups: 3, maxAthletes: null, monthlyPriceCents: 5990, annualPriceCents: 53910 },
  { id: 'ILIMITADO', name: 'Ilimitado', maxGroups: null, maxAthletes: null, monthlyPriceCents: 8990, annualPriceCents: 80910 },
];
function setup(cycle, subscription = null) {
  const cards = [];
  const selections = [];
  const context = vm.createContext({
    planos: plans, assinaturaAtual: subscription, cicloEscolhido: cycle,
    modoTroca: () => Boolean(subscription), mostrarErro() {}, dataPt: value => value,
    reais: value => `R$ ${value / 100}`,
    abrirDados: (plan, cycle) => selections.push([plan.id, cycle]),
    trocarPlano: plan => selections.push(['change', plan.id]),
    $: () => ({ set innerHTML(value) { cards.length = 0; }, appendChild: card => cards.push(card) }),
    document: { createElement() {
      const card = { className: '', innerHTML: '', title: '', click: null };
      card.querySelector = selector => selector === 'h2'
        ? { set textContent(value) { card.title = value; } }
        : { addEventListener: (_, fn) => { card.click = fn; } };
      return card;
    } },
  });
  vm.runInContext(render + '\nrenderizarPlanos();', context);
  return { cards, selections };
}
test('Organizador is highlighted with continuation while all three plans remain selectable', () => {
  for (const cycle of ['MONTHLY', 'ANNUAL']) {
    const { cards, selections } = setup(cycle);
    assert.equal(cards.length, 3);
    assert.equal(cards.filter(card => card.className.includes('plano--destaque')).length, 1);
    assert.match(cards[1].className, /plano--destaque/);
    assert.match(cards[1].innerHTML, /Continuar com o Organizador/);
    assert.match(cards[1].innerHTML, /3 grupos/);
    assert.match(cards[1].innerHTML, /Atletas ilimitados/);
    cards[0].click(); cards[1].click();
    assert.deepEqual(selections, [['TITULAR', cycle], ['ORGANIZADOR', cycle]]);
    assert.match(cards[0].innerHTML, /Até 25 atletas/);
  }
});
test('paid current plan stays disabled and upgrade actions keep their meaning', () => {
  const { cards, selections } = setup('MONTHLY', { plan: 'ORGANIZADOR' });
  assert.match(cards[1].innerHTML, /Plano atual/);
  assert.equal(cards[1].click, null);
  assert.match(cards[0].innerHTML, /Trocar para este plano/);
  cards[0].click();
  assert.deepEqual(selections, [['change', 'TITULAR']]);
});
