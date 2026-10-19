// Máscara do celular do /comecar/: (11) 99999-9999 ao digitar e ao colar, só dígitos, até 11, sem +55 no campo;
// para a API vão só os dígitos (o +55 entra no envio, no backend).
const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

const comecar = fs.readFileSync(require.resolve('../comecar/comecar.js'), 'utf8');
const comecarHtml = fs.readFileSync(require.resolve('../comecar/index.html'), 'utf8');

function setup() {
  const elements = new Map([...comecarHtml.matchAll(/id="([^"]+)"/g)].map(([, id]) => [id, {
    value: '', hidden: false, disabled: false, textContent: '', dataset: {},
    classList: { toggle() {}, contains() { return false; } }, reset() {}, removeAttribute() {}, setAttribute() {},
  }]));
  const requests = [];
  const document = { readyState: 'loading', activeElement: null, addEventListener() {}, getElementById: id => elements.get(id) };
  const context = vm.createContext({ window: {}, URL, URLSearchParams, document,
    fetch: (path, options) => new Promise(resolve => requests.push({ path, options, resolve })) });
  vm.runInContext(comecar.replace('  if (document.readyState', `
    window.phoneTest = {
      phoneDigits, formatPhone, maskPhone, saveProfile,
      signIn(user) { auth = { currentUser: user }; currentUser = user; session = {}; generation++; },
    };
    if (document.readyState`), context);
  return { app: context.window.phoneTest, elements, document, requests };
}

function typedInput(document) {
  const input = { value: '', selectionStart: 0, dataset: {},
    setSelectionRange(start) { this.selectionStart = start; } };
  document.activeElement = input;
  return input;
}

function type(app, input, text) {
  for (const char of text) {
    const caret = input.selectionStart;
    input.value = input.value.slice(0, caret) + char + input.value.slice(caret);
    input.selectionStart = caret + 1;
    app.maskPhone(input, { inputType: 'insertText' });
  }
}

test('formata enquanto digita, com 10 e com 11 dígitos', () => {
  const { app } = setup();
  const steps = { '': '', 1: '(1', 11: '(11', 119: '(11) 9', 119999: '(11) 9999', 1199999: '(11) 9999-9',
    1133334444: '(11) 3333-4444', 11987654321: '(11) 98765-4321' };
  for (const [digits, formatted] of Object.entries(steps)) assert.equal(app.formatPhone(digits), formatted, digits);
});

test('aceita só dígitos, no máximo 11, e tira o +55 de quem cola o número completo', () => {
  const { app } = setup();
  assert.equal(app.phoneDigits('+55 (11) 98765-4321'), '11987654321');
  assert.equal(app.phoneDigits('5511987654321'), '11987654321');
  assert.equal(app.phoneDigits('11 9876a5-4321 ramal 2'), '11987654321');
  assert.equal(app.phoneDigits('119876543210000'), '11987654321');
});

test('digitar número a número mantém o cursor depois do último dígito', () => {
  const { app, document } = setup();
  const input = typedInput(document);
  type(app, input, '11987654321');
  assert.equal(input.value, '(11) 98765-4321');
  assert.equal(input.selectionStart, input.value.length);
  type(app, input, '9');
  assert.equal(input.value, '(11) 98765-4321', 'o 12º dígito não entra');
});

test('colar o número com +55 vira (11) 99999-9999 no campo', () => {
  const { app, document } = setup();
  const input = typedInput(document);
  input.value = '+55 11 98765-4321';
  input.selectionStart = input.value.length;
  app.maskPhone(input, { inputType: 'insertFromPaste' });
  assert.equal(input.value, '(11) 98765-4321');
  assert.equal(input.selectionStart, input.value.length);
});

test('apagar o traço apaga o dígito antes dele, e o cursor fica no lugar', () => {
  const { app, document } = setup();
  const input = typedInput(document);
  type(app, input, '11987654321');
  input.value = '(11) 987654321';
  input.selectionStart = 10;
  app.maskPhone(input, { inputType: 'deleteContentBackward' });
  assert.equal(input.value, '(11) 9876-4321');
  assert.equal(input.selectionStart, 9);
});

test('editar no meio não joga o cursor para o fim', () => {
  const { app, document } = setup();
  const input = typedInput(document);
  type(app, input, '1133334444');
  input.selectionStart = 5;
  type(app, input, '9');
  assert.equal(input.value, '(11) 93333-4444');
  assert.equal(input.selectionStart, 6);
});

test('o perfil manda à API só os dígitos, sem a máscara', async () => {
  const { app, elements, requests } = setup();
  app.signIn({ uid: 'fb-1', getIdToken: async () => 'token' });
  elements.get('phone').value = '(11) 98765-4321';
  elements.get('profile-name-field').hidden = true;
  app.saveProfile({ preventDefault() {} });
  for (let i = 0; i < 50 && !requests.length; i++) await new Promise(resolve => setImmediate(resolve));
  assert.equal(requests.length, 1);
  assert.match(requests[0].path, /\/api\/session\/profile$/);
  assert.deepEqual(JSON.parse(requests[0].options.body), { phone: '11987654321' });
});
