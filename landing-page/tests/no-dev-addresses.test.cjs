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
      fs.readFileSync(file, 'utf8').split('\n').forEach((line, index) => {
        for (const word of forbidden) {
          if (line.toLowerCase().includes(word)) found.push(`${path.relative(root, file)}:${index + 1} → ${word}`);
        }
      });
    }
    assert.deepEqual(found, [], `endereço de desenvolvimento publicado:\n${found.join('\n')}`);
  });
}
