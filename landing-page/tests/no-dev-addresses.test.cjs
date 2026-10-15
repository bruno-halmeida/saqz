'use strict';

// Reprova endereço de desenvolvimento em qualquer arquivo publicado do site
// (landing-page, adm-web e links-page). Sem tests/ nem node_modules.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..', '..');
const sites = ['landing-page', 'adm-web', 'links-page'];
const extensions = new Set(['.html', '.js', '.json', '.xml']);
const skipped = new Set(['tests', 'node_modules']);
// Exceção explícita: adm-web/assets/firebase-config.js serve o ambiente local e o painel de staging
// (emulador do Firebase em 127.0.0.1). Não vai para produção: saqz.app/comecar e /assinar são servidos
// pelo nginx da landing com alias para adm-web, e lá /assets/firebase-config.js é o
// landing-page/assets/firebase-config.js (conferido no teste abaixo).
const localOnly = new Set(['adm-web/assets/firebase-config.js']);
const forbidden = ['brunoalmeida.dev', 'localhost', '127.0.0.1', 'staging', '10.0.2.2'];

function publishedFiles(dir) {
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (skipped.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...publishedFiles(full));
    else if (extensions.has(path.extname(entry.name)) || full.includes(`${path.sep}.well-known${path.sep}`)) files.push(full);
  }
  return files;
}

for (const site of sites) {
  test(`${site} não publica endereço de desenvolvimento`, () => {
    const files = publishedFiles(path.join(root, site));
    assert.ok(files.length > 0, `nenhum arquivo publicado encontrado em ${site}`);
    const found = [];
    for (const file of files) {
      if (localOnly.has(path.relative(root, file).split(path.sep).join('/'))) continue;
      fs.readFileSync(file, 'utf8').split('\n').forEach((line, index) => {
        for (const word of forbidden) {
          if (line.toLowerCase().includes(word)) found.push(`${path.relative(root, file)}:${index + 1} → ${word}`);
        }
      });
    }
    assert.deepEqual(found, [], `endereço de desenvolvimento publicado:\n${found.join('\n')}`);
  });
}

test('o firebase-config de produção (landing-page) aponta para a API real e não usa emulador', () => {
  const config = fs.readFileSync(path.join(root, 'landing-page/assets/firebase-config.js'), 'utf8');
  assert.match(config, /apiBaseUrl:\s*"https:\/\/api\.saqz\.app"/);
  assert.doesNotMatch(config, /authEmulatorUrl/);
});
