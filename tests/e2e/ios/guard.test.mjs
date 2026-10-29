import { test } from 'node:test';
import assert from 'node:assert/strict';
import { selectScenarios, simulatorUdid, verifySummary, shotFileName } from './guard.mjs';

test('ENV: escolha explícita de cenário com classe e contagem exatas; nome desconhecido ou vazio é erro', () => {
  assert.deepEqual(selectScenarios('attendance-list'), [{ name: 'attendance-list', testClass: 'GroupListConfirmE2eTest', count: 1 }]);
  assert.ok(selectScenarios().length >= 1);
  assert.deepEqual(selectScenarios('cashbox,game-fee').map(item => item.name), ['game-fee', 'cashbox']);
  for (const input of ['', 'missing', '__proto__', 'cashbox,']) assert.throws(() => selectScenarios(input), /Unknown E2E scenario/);
});

test('ENV: só um UDID de simulador explícito', () => {
  assert.equal(simulatorUdid('BAB08A28-4E8C-4AA3-BF6A-54904566BF0F'), 'BAB08A28-4E8C-4AA3-BF6A-54904566BF0F');
  for (const input of [undefined, '', 'booted', 'BAB08A28-4E8C-4AA3-BF6A-54904566BF0F;rm']) {
    assert.throws(() => simulatorUdid(input), /booted iOS simulator/);
  }
});

test('RESULT: contagem exata sem falha nem skip', () => {
  const ok = { totalTestCount: 2, passedTests: 2, failedTests: 0, skippedTests: 0, expectedFailures: 0 };
  assert.equal(verifySummary(ok, 2), 2);
  assert.throws(() => verifySummary(ok, 3), /Expected 3/);
  assert.throws(() => verifySummary({ ...ok, passedTests: 1, failedTests: 1 }, 2), /failed=1/);
  assert.throws(() => verifySummary({ ...ok, passedTests: 1, skippedTests: 1 }, 2), /skipped=1/);
  assert.throws(() => verifySummary({ totalTestCount: 0, passedTests: 0, failedTests: 0, skippedTests: 0 }, 1), /Expected 1/);
});

test('SHOTS: nome do anexo vira <NN>-<passo>.png só para o cenário certo', () => {
  assert.equal(shotFileName('cashbox', 'cashbox-03-saldo-inicial_0_5E1C0D3A-1B2C-4D5E-8F90-A1B2C3D4E5F6.png'), '03-saldo-inicial.png');
  assert.equal(shotFileName('attendance-list', 'attendance-list-01-grupos.png'), '01-grupos.png');
  assert.equal(shotFileName('cashbox', 'game-fee-01-grupo_0_5E1C0D3A-1B2C-4D5E-8F90-A1B2C3D4E5F6.png'), undefined);
  assert.equal(shotFileName('cashbox', 'cashbox-arvore-da-tela.txt'), undefined);
  assert.equal(shotFileName('cashbox', 'Screenshot_0_5E1C0D3A-1B2C-4D5E-8F90-A1B2C3D4E5F6.png'), undefined);
});

test('ENV: cenários que copiam o Pix dizem qual campo da fixture o runner confere', () => {
  assert.equal(selectScenarios('group-pix')[0].clipboard, 'pixKey');
  assert.equal(selectScenarios('cashbox')[0].clipboard, undefined);
});
