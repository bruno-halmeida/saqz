const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const html = require('node:fs').readFileSync(require.resolve('../index.html'), 'utf8');
const source = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
const flush = async () => { for (let i = 0; i < 6; i++) await new Promise(setImmediate); };

function setup() {
  let sessao, saiu = 0;
  const ctx = vm.createContext({
    DCLogic: class { props = {}; setState(p, done) { Object.assign(this.state, typeof p === 'function' ? p(this.state) : p); done?.(); } },
    window: { saqzAdmin: { me: {}, onSession(fn) { sessao = fn; }, signOut() { saiu++; return Promise.resolve(); }, fetchAdmin: () => new Promise(() => {}) } },
    setTimeout, clearTimeout, console, Date, Promise, Math, JSON, Set, Map, encodeURIComponent,
  });
  const App = vm.runInContext(source + '\nComponent', ctx);
  const app = new App(); app.componentDidMount();
  return { app, sessao: me => sessao(me), saiu: () => saiu, vals: () => app.renderVals() };
}
const destaque = estilo => /background:var\(--saqz-ice\)/.test(estilo);

test('painel Mais abre, fecha pelo fundo e fecha ao escolher uma tela', () => {
  const s = setup();
  assert.equal(s.vals().navMaisAberto, 'false');
  s.vals().onNavMais();
  assert.equal(s.vals().navMaisAberto, 'true');
  s.vals().onNavFechar();
  assert.equal(s.vals().navMaisAberto, 'false');
  s.vals().onNavMais();
  s.app.go('flags');
  assert.equal(s.vals().navMaisAberto, 'false');
  assert.equal(s.app.state.page, 'flags');
});

test('Mais fica em destaque nas telas que moram nele, inclusive nos detalhes', () => {
  const s = setup();
  for (const page of ['visao', 'usuarios', 'usuario', 'grupos', 'grupo', 'mensagens']) {
    s.app.setState({ page });
    assert.equal(destaque(s.vals().sMais), false, page);
  }
  for (const page of ['flags', 'recebimentos', 'receita', 'assinatura', 'cupons', 'parceiros', 'parceiro', 'suporte', 'denuncia']) {
    s.app.setState({ page });
    assert.equal(destaque(s.vals().sMais), true, page);
  }
});

test('Sair do painel encerra a sessão, e o logout fecha o painel', async () => {
  const s = setup();
  s.vals().onSair();
  assert.equal(s.saiu(), 1);
  s.vals().onNavMais();
  s.sessao(null); await flush();
  assert.equal(s.app.state.navMais, false);
});

test('marcação: quatro telas na barra e as outras no painel, com rótulo visível', () => {
  const aside = html.match(/<aside[\s\S]*?<\/aside>/)[0];
  const extra = aside.match(/<div class="adm-nav-extra">[\s\S]*?<\/div>/)[0];
  const rotulos = t => [...t.matchAll(/class="adm-nav-rotulo">([^<]+)</g)].map(m => m[1]);
  assert.deepEqual(rotulos(extra), ['Flags', 'Recebimentos', 'Receita', 'Cupons', 'Parceiros', 'Suporte', 'Sair']);
  assert.deepEqual(rotulos(aside.replace(extra, '')), ['Visão geral', 'Usuários', 'Grupos', 'Mensagens', 'Mais']);
});

test('CSS do celular não casa com style inline escrito sem espaço: o navegador reescreve como "prop: valor"', () => {
  const css = [...html.matchAll(/<style>([\s\S]*?)<\/style>/g)].map(m => m[1]).join('\n');
  assert.doesNotMatch(css, /\[style\*="[a-z-]+:/);
});

test('cada linha de tabela que vira cartão mantém o nome da coluna', () => {
  const linhas = [...html.matchAll(/class="adm-lin(?: (?!adm-flex)[^"]*)?"[^>]*>(.*)/g)].map(m => m[1]);
  assert.ok(linhas.length >= 10);
  for (const linha of linhas.filter(l => l.includes('{{'))) assert.match(linha, /data-l="/, linha.slice(0, 80));
});
