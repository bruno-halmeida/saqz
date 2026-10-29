import path from 'node:path';

// Mesmos nomes de cenário do Android (tests/e2e/android/guard.mjs): a fixture de cada um vem de lá.
const scenarios = {
  'attendance-list': ['GroupListConfirmE2eTest', 1],
  'game-fee': ['GameFeeE2eTest', 1],
  cashbox: ['CashboxE2eTest', 1],
  'uncharged-review': ['UnchargedReviewE2eTest', 1],
  'group-pix': ['GroupPixE2eTest', 1],
  'receivables-off': ['ReceivablesOffE2eTest', 1],
};

/** `--scenario a` ou `--scenario a,b`; sem a opção, todos, na ordem do registro. */
export function selectScenarios(name) {
  const names = name === undefined ? undefined : name.split(',');
  for (const each of names ?? []) {
    if (!Object.hasOwn(scenarios, each)) throw new Error(`Unknown E2E scenario: ${each}`);
  }
  return Object.entries(scenarios).filter(([key]) => names === undefined || names.includes(key))
    .map(([key, [testClass, count]]) => ({ name: key, testClass, count }));
}

export function simulatorUdid(value) {
  if (!/^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$/.test(value ?? '')) {
    throw new Error('Use --udid <UDID> of a booted iOS simulator (xcrun simctl list devices booted).');
  }
  return value;
}

/** `xcresulttool get test-results summary --format json`: contagem exata, nenhuma falha nem skip. */
export function verifySummary(summary, expected) {
  const { totalTestCount, passedTests, failedTests, skippedTests, expectedFailures } = summary;
  if (totalTestCount !== expected || passedTests !== expected || failedTests !== 0 || skippedTests !== 0 || (expectedFailures ?? 0) !== 0) {
    throw new Error(`Expected ${expected} passing journeys, no skips; found total=${totalTestCount} passed=${passedTests} failed=${failedTests} skipped=${skippedTests}. Inspect the xcresult.`);
  }
  return passedTests;
}

/**
 * Anexo `<cenário>-<NN>-<passo>` vira `<NN>-<passo>.png`. O xcresulttool sufixa o nome sugerido com
 * `_<índice>_<UUID>`; anexo de outro cenário ou sem número (ex.: árvore da tela na falha) fica de fora.
 */
export function shotFileName(scenario, suggested) {
  const base = path.basename(suggested).replace(/\.png$/i, '').replace(/_\d+_[0-9A-F-]{36}$/i, '');
  const prefix = `${scenario}-`;
  if (!base.startsWith(prefix)) return undefined;
  const step = base.slice(prefix.length);
  return /^\d{2}-[a-z0-9-]+$/.test(step) ? `${step}.png` : undefined;
}
