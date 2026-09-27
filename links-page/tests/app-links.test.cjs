const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { association, validateAssociation, normalizeFingerprint, DEBUG_CERTIFICATE } = require('../scripts/configure-app-links.cjs');
// Synthetic public fingerprints only: never written into the deployed association file.
const fixture = '0123456789abcdef'.repeat(4);

test('normalizes a Play fingerprint and deduplicates rotated keys', () => {
  const normalized = normalizeFingerprint(fixture);
  assert.equal(normalized, '01:23:45:67:89:AB:CD:EF:'.repeat(4).slice(0, -1));
  const data = association([fixture, normalized, 'FE'.repeat(32)]);
  assert.equal(data[0].target.package_name, 'app.saqz');
  assert.deepEqual(data[0].target.sha256_cert_fingerprints, [normalized, 'FE:'.repeat(32).slice(0, -1)]);
  validateAssociation(data);
});

test('rejects debug absent placeholder and malformed certificates', () => {
  for (const value of [DEBUG_CERTIFICATE, '', 'SHA-256 DO PLAY', '00'.repeat(32), 'AA'.repeat(31), fixture + ':']) {
    assert.throws(() => normalizeFingerprint(value));
  }
  assert.throws(() => association([]));
});

test('rejects wrong package relation and namespace', () => {
  const wrongPackage = association([fixture]);
  wrongPackage[0].target.package_name = 'app.saqz.debug';
  assert.throws(() => validateAssociation(wrongPackage));
  const wrongRelation = association([fixture]);
  wrongRelation[0].relation = [];
  assert.throws(() => validateAssociation(wrongRelation));
  const wrongNamespace = association([fixture]);
  wrongNamespace[0].target.namespace = 'web';
  assert.throws(() => validateAssociation(wrongNamespace));
});

test('CLI refuses to overwrite on invalid input and checks a configured file', (t) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'saqz-links-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const file = path.join(directory, 'assetlinks.json');
  fs.writeFileSync(file, 'original');
  const run = (...args) => spawnSync(process.execPath, [path.resolve(__dirname, '../scripts/configure-app-links.cjs'), '--file', file, ...args]);
  assert.equal(run('--sha256', DEBUG_CERTIFICATE).status, 1);
  assert.equal(fs.readFileSync(file, 'utf8'), 'original');
  assert.equal(run('--sha256', fixture).status, 0);
  assert.equal(run('--check').status, 0);
  assert.deepEqual(JSON.parse(fs.readFileSync(file)), association([fixture]));
});

test('Apple association identifies production and all supported link paths', () => {
  const data = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../.well-known/apple-app-site-association')));
  const details = data.applinks.details.find(item => item.appIDs.includes('8JG4JP8VMT.app.saqz'));
  assert.ok(details);
  assert.ok(details.components.some(item => item['/'] === '/attendance/*'));
  for (const key of ['saqz_invite', 'saqz_onboarding']) {
    assert.ok(details.components.some(item => item['/'] === '/' && item['?']?.[key] === '*'));
  }
});
