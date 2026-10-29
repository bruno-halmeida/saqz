import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, writeFile, readdir, readFile, rm, cp } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { requireFreePort } from '../android/guard.mjs';
import { seed } from '../android/fixture.mjs';
import { runCommand, finishCleanup } from '../android/process.mjs';
import { simulatorUdid, selectScenarios, verifySummary, shotFileName } from './guard.mjs';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const option = name => { const index = process.argv.indexOf(name); return index < 0 ? undefined : process.argv[index + 1] ?? ''; };
const udid = simulatorUdid(option('--udid'));
const selected = selectScenarios(option('--scenario'));
const artifacts = await mkdtemp(path.join(tmpdir(), 'saqz-e2e-ios-'));
const services = [];
const cancellation = new AbortController();
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => cancellation.abort());
const iosApp = path.join(root, 'mobile/ios-app');
const derived = path.join(iosApp, 'build/e2e-derived');
const results = path.join(iosApp, 'build/e2e-results');
const shots = path.join(iosApp, 'build/e2e-shots');
const apiUrl = 'http://127.0.0.1:18080';
// Trava compartilhada com a suíte Android: as duas usam 18080/9099/4400/4500.
const lock = '/tmp/saqz-e2e.lock';
const developerDir = process.env.DEVELOPER_DIR ?? '/Applications/Xcode.app/Contents/Developer';
const mobileJava = process.env.SAQZ_E2E_MOBILE_JAVA_HOME ?? '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const xcodeEnv = { ...process.env, DEVELOPER_DIR: developerDir, JAVA_HOME: mobileJava, 'ORG_GRADLE_PROJECT_saqz.api.devBaseUrl': apiUrl };
const project = ['-project', path.join(iosApp, 'SaqzIOS.xcodeproj'), '-scheme', 'SaqzDev', '-configuration', 'Debug',
  '-destination', `id=${udid}`, '-derivedDataPath', derived];
let database;
let locked = false;
console.log(`E2E iOS artifacts: ${artifacts}`);

function command(binary, args, options = {}) {
  return runCommand(binary, args, {
    cwd: root, env: process.env, signal: cancellation.signal, timeout: 1800000, ...options,
  });
}

function service(binary, args, name, env = process.env) {
  const log = createWriteStream(path.join(artifacts, `${name}.log`));
  const child = spawn(binary, args, { cwd: artifacts, env, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(log); child.stderr.pipe(log);
  child.on('error', error => log.write(`${error.message}\n`));
  child.on('close', () => log.end());
  services.push(child);
  return child;
}

async function until(check, label, timeout = 90000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    cancellation.signal.throwIfAborted();
    if (services.some(child => child.exitCode !== null)) throw new Error(`A required service stopped. See ${artifacts}`);
    if (await check()) return;
    await delay(500);
  }
  throw new Error(`Timed out: ${label}. See ${artifacts}`);
}

async function healthy(url) {
  try { return (await fetch(url, { signal: AbortSignal.timeout(1500) })).ok; }
  catch { return false; }
}

async function acquireLock() {
  for (;;) {
    cancellation.signal.throwIfAborted();
    try {
      await mkdir(lock);
      locked = true;
      await writeFile(path.join(lock, 'dono'), 'ios\n');
      return;
    } catch (error) {
      if (error.code !== 'EEXIST') throw error;
      console.log(`Waiting for ${lock} (owner: ${(await readFile(path.join(lock, 'dono'), 'utf8').catch(() => '?')).trim()})...`);
      await delay(30000);
    }
  }
}

/** Anexos `<cenário>-<NN>-<passo>` do xcresult viram mobile/ios-app/build/e2e-shots/<cenário>/<NN>-<passo>.png. */
async function exportShots(scenario, bundle) {
  const raw = path.join(artifacts, 'attachments', scenario.name);
  await command('xcrun', ['xcresulttool', 'export', 'attachments', '--path', bundle, '--output-path', raw], { env: xcodeEnv });
  const manifest = JSON.parse(await readFile(path.join(raw, 'manifest.json'), 'utf8'));
  const target = path.join(shots, scenario.name);
  await rm(target, { recursive: true, force: true });
  await mkdir(target, { recursive: true });
  const names = [];
  for (const test of manifest) {
    for (const attachment of test.attachments ?? []) {
      const name = shotFileName(scenario.name, attachment.suggestedHumanReadableName ?? '');
      if (!name) continue;
      await cp(path.join(raw, attachment.exportedFileName), path.join(target, name));
      names.push(name);
    }
  }
  console.log(`Shots ${scenario.name}: ${names.length} → ${path.relative(root, target)}`);
}

try {
  await command('node', ['--test', 'tests/e2e/ios/guard.test.mjs']);
  const firebaseBin = process.env.SAQZ_E2E_FIREBASE_BIN;
  if (!firebaseBin) throw new Error('Set SAQZ_E2E_FIREBASE_BIN to firebase-tools/lib/bin/firebase.js (15.25.1).');
  if (await command('node', [firebaseBin, '--version']) !== '15.25.1') throw new Error('This runner requires firebase-tools 15.25.1.');
  const booted = await command('xcrun', ['simctl', 'list', 'devices', 'booted'], { env: xcodeEnv });
  if (!booted.includes(`(${udid}) (Booted)`)) throw new Error(`Simulator ${udid} is not booted.`);
  await command('java', ['-version']);
  await command('docker', ['info', '--format', '{{.ServerVersion}}']);

  // O build não usa as portas: roda antes da trava.
  console.log('Building SaqzDev for testing (API http://127.0.0.1:18080, Firebase Auth Emulator)...');
  await command('./mobile/gradlew', ['-p', 'mobile', ':compose-app:generateIosApiConfig', `-Psaqz.api.devBaseUrl=${apiUrl}`, '--console=plain', '-q'],
    { stdio: 'inherit', env: xcodeEnv });
  const plist = path.join(derived, 'Build/Products/Debug-iphonesimulator/SaqzIOS.app/Info.plist');
  // Build incremental às vezes deixa o SaqzAPIBaseURL padrão no Info.plist (visto no builds.sh e aqui): um segundo
  // build regrava; se ainda assim errar, para.
  let builtUrl;
  for (let attempt = 1; attempt <= 2 && builtUrl !== apiUrl; attempt += 1) {
    await command('xcodebuild', [...project, 'build-for-testing', 'ARCHS=arm64', 'EXCLUDED_ARCHS=x86_64',
      'CODE_SIGN_IDENTITY=-', 'CODE_SIGNING_REQUIRED=NO', '-quiet'], { stdio: ['ignore', 'inherit', 'inherit'], env: xcodeEnv });
    builtUrl = await command('/usr/libexec/PlistBuddy', ['-c', 'Print :SaqzAPIBaseURL', plist]);
  }
  if (builtUrl !== apiUrl) throw new Error(`SaqzIOS.app points to ${builtUrl}, not ${apiUrl}; remove ${derived} and retry.`);

  await acquireLock();
  for (const port of [18080, 9099, 4400, 4500]) await requireFreePort(port);
  console.log('Building real backend...');
  await command('./backend/gradlew', ['-p', 'backend', ':bootstrap:bootJar', '--console=plain'], { stdio: 'inherit' });
  database = await command('docker', ['create', '--label', 'saqz.e2e=disposable',
    '--publish', '127.0.0.1::5432', '--env', 'POSTGRES_DB=saqz_e2e', '--env', 'POSTGRES_USER=saqz',
    '--env', 'POSTGRES_PASSWORD=e2e-local-only', '--tmpfs', '/var/lib/postgresql/data', 'postgres:16-alpine']);
  await command('docker', ['start', database]);
  await until(async () => {
    try { await command('docker', ['exec', database, 'pg_isready', '-U', 'saqz', '-d', 'saqz_e2e']); return true; }
    catch { return false; }
  }, 'PostgreSQL');
  const databasePort = (await command('docker', ['port', database, '5432/tcp'])).split(':').at(-1);
  const firebaseConfig = path.join(artifacts, 'firebase.json');
  await writeFile(firebaseConfig, JSON.stringify({ emulators: { auth: { host: '127.0.0.1', port: 9099 }, ui: { enabled: false } } }));
  service('node', [firebaseBin, 'emulators:start', '--only', 'auth', '--project', 'saqz-local', '--config', firebaseConfig], 'firebase');
  await until(() => healthy('http://127.0.0.1:9099/emulator/v1/projects/saqz-local/config'), 'Firebase Auth');
  const jars = (await readdir(path.join(root, 'backend/bootstrap/build/libs'))).filter(name => name.endsWith('.jar') && !name.endsWith('-plain.jar'));
  if (jars.length !== 1) throw new Error('Expected exactly one bootJar.');
  // Mesmo ambiente do runner Android (tests/e2e/android/run.mjs).
  service('java', ['-jar', path.join(root, 'backend/bootstrap/build/libs', jars[0])], 'backend', {
    ...process.env, SPRING_PROFILES_ACTIVE: 'local', SERVER_PORT: '18080', SERVER_ADDRESS: '127.0.0.1',
    FIREBASE_AUTH_EMULATOR_HOST: '127.0.0.1:9099',
    SAQZ_LINKS_DOMAIN: 'https://links.saqz.app',
    SPRING_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${databasePort}/saqz_e2e`,
    SPRING_DATASOURCE_USERNAME: 'saqz', SPRING_DATASOURCE_PASSWORD: 'e2e-local-only',
    SAQZ_PASSWORD_RESET_SECRET: 'disposable-local-e2e-password-reset-secret',
    SAQZ_MONTHLY_CHARGES_CRON: '-', SAQZ_ASAAS_API_KEY: '', SAQZ_ASAAS_WEBHOOK_TOKEN: '',
    SAQZ_NOTIFICATIONS_REMINDER_ENABLED: 'false',
    SAQZ_RECEIVABLES_LAUNCH_ENABLED: 'true',
    SAQZ_MAIL_HOST: '127.0.0.1', SAQZ_MAIL_PORT: '1', SAQZ_MAIL_STARTTLS: 'false',
  });
  await until(() => healthy(`${apiUrl}/actuator/health`), 'Spring/Flyway');
  console.log('Seeding disposable preconditions...');
  const fixture = await seed(sql => command('docker', ['exec', '-i', database, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'saqz', '-d', 'saqz_e2e'], { input: sql }), selected.map(scenario => scenario.name));
  // Vai ao processo do XCTest em base64: o prefixo TEST_RUNNER_ é retirado pelo xcodebuild.
  const encodedFixture = Buffer.from(JSON.stringify(fixture)).toString('base64');

  await rm(results, { recursive: true, force: true });
  await mkdir(results, { recursive: true });
  const started = Date.now();
  const failures = [];
  for (const scenario of selected) {
    console.log(`Running installed iOS journey: ${scenario.name} (${scenario.testClass})...`);
    // Sessão do Firebase fica no Keychain e sobrevive à desinstalação: começa sempre deslogado.
    await command('xcrun', ['simctl', 'terminate', udid, 'app.saqz'], { env: xcodeEnv, onStderr: () => {} }).catch(() => {});
    await command('xcrun', ['simctl', 'uninstall', udid, 'app.saqz'], { env: xcodeEnv, onStderr: () => {} }).catch(() => {});
    await command('xcrun', ['simctl', 'keychain', udid, 'reset'], { env: xcodeEnv });
    const bundle = path.join(results, `${scenario.name}.xcresult`);
    try {
      await command('xcodebuild', [...project, 'test-without-building', `-only-testing:SaqzIOSUITests/${scenario.testClass}`,
        '-resultBundlePath', bundle], {
        stdio: ['ignore', 'inherit', 'inherit'],
        env: { ...xcodeEnv, TEST_RUNNER_SAQZ_E2E_FIXTURE: encodedFixture, TEST_RUNNER_SAQZ_E2E_SCENARIO: scenario.name },
      }).catch(error => { failures.push(`${scenario.name}: ${error.message.split('\n')[0]}`); });
      const summary = JSON.parse(await command('xcrun', ['xcresulttool', 'get', 'test-results', 'summary', '--path', bundle, '--format', 'json'], { env: xcodeEnv }));
      try { verifySummary(summary, scenario.count); }
      catch (error) { failures.push(`${scenario.name}: ${error.message}`); }
    } finally {
      await exportShots(scenario, bundle).catch(error => console.warn(`Shots ${scenario.name}: ${error.message}`));
    }
  }
  const expectedCount = selected.reduce((sum, scenario) => sum + scenario.count, 0);
  const minutes = ((Date.now() - started) / 60000).toFixed(1);
  if (failures.length) throw new Error(`FAIL (${minutes} min):\n${failures.join('\n')}\nxcresult: ${path.relative(root, results)}`);
  console.log(`PASS: ${selected.map(scenario => scenario.name).join(', ')} (${expectedCount} installed iOS tests, ${minutes} min). xcresult: ${path.relative(root, results)}; shots: ${path.relative(root, shots)}`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await command('xcrun', ['simctl', 'terminate', udid, 'app.saqz'], { env: xcodeEnv, signal: undefined, onStderr: () => {} }).catch(() => {});
  await finishCleanup({ testStarted: false, cancelled: cancellation.signal.aborted, services, database, command }, artifacts);
  if (locked) await rm(lock, { recursive: true, force: true });
}
