const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '..');
const page = fs.readFileSync(path.join(root, 'excluir-conta/index.html'), 'utf8');

test('public deletion page provides a direct request and in-app steps without login', () => {
  assert.match(page, /Perfil → Excluir conta/);
  assert.match(page, /href="mailto:contato@egysis.com\?subject=Excluir%20minha%20conta%20Saqz"/);
  assert.match(page, /confirmará sua identidade/);
  assert.match(page, /Registros financeiros e fiscais/);
  assert.match(page, /prazo aplicável/);
  assert.match(page, /https:\/\/saqz.app\/excluir-conta\//);
  assert.match(fs.readFileSync(path.join(root, 'index.html'), 'utf8'), /href="\/excluir-conta\/"/);
});
