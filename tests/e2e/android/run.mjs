import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, writeFile, readdir, readFile, stat, cp } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { emulatorSerial, requireFreePort, verifyReport, selectScenarios } from './guard.mjs';
import { seed } from './fixture.mjs';
import { runCommand, finishCleanup } from './process.mjs';

const root = fileURLToPath(new URL('../../../', import.meta.url));
const serial = emulatorSerial(process.argv[process.argv.indexOf('--serial') + 1]);
const scenarioIndex = process.argv.indexOf('--scenario');
const selected = selectScenarios(scenarioIndex < 0 ? undefined : process.argv[scenarioIndex + 1] ?? '');
const expectedCount = selected.reduce((sum, scenario) => sum + scenario.count, 0);
const artifacts = await mkdtemp(path.join(tmpdir(), 'saqz-e2e-'));
const services = [];
const cancellation = new AbortController();
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => cancellation.abort());
const reports = path.join(root, 'mobile/android-app/build/outputs/androidTest-results/connected');
let database;
let testStarted = false;
console.log(`E2E artifacts: ${artifacts}`);

function command(binary, args, options = {}) {
  return runCommand(binary, args, {
    cwd: root, env: process.env, signal: cancellation.signal, timeout: 900000, ...options,
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

try {
  for (const port of [18080, 9099, 4400, 4500]) await requireFreePort(port);
  const booted = await command('adb', ['-s', serial, 'shell', 'getprop', 'sys.boot_completed']);
  if (booted !== '1') throw new Error('Selected emulator is not booted.');
  await command('java', ['-version']);
  const firebaseBin = process.env.SAQZ_E2E_FIREBASE_BIN;
  if (!firebaseBin) throw new Error('Set SAQZ_E2E_FIREBASE_BIN to firebase-tools/lib/bin/firebase.js (15.25.1).');
  const firebaseVersion = await command('node', [firebaseBin, '--version']);
  if (firebaseVersion !== '15.25.1') throw new Error('This runner requires firebase-tools 15.25.1.');
  await command('docker', ['info', '--format', '{{.ServerVersion}}']);
  await command('node', ['--test', 'tests/e2e/android/guard.test.mjs', 'tests/e2e/android/process.test.mjs']);
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
  // Resolve pinned CLI without a shell/process group: the process we stop IS the auth emulator.
  service('node', [firebaseBin, 'emulators:start', '--only', 'auth', '--project', 'saqz-local', '--config', firebaseConfig], 'firebase');
  await until(() => healthy('http://127.0.0.1:9099/emulator/v1/projects/saqz-local/config'), 'Firebase Auth');
  const jars = (await readdir(path.join(root, 'backend/bootstrap/build/libs'))).filter(name => name.endsWith('.jar') && !name.endsWith('-plain.jar'));
  if (jars.length !== 1) throw new Error('Expected exactly one bootJar.');
  service('java', ['-jar', path.join(root, 'backend/bootstrap/build/libs', jars[0])], 'backend', {
    ...process.env, SPRING_PROFILES_ACTIVE: 'local', SERVER_PORT: '18080', SERVER_ADDRESS: '127.0.0.1',
    FIREBASE_AUTH_EMULATOR_HOST: '127.0.0.1:9099',
    SAQZ_LINKS_DOMAIN: 'https://links.saqz.app',
    SPRING_DATASOURCE_URL: `jdbc:postgresql://127.0.0.1:${databasePort}/saqz_e2e`,
    SPRING_DATASOURCE_USERNAME: 'saqz', SPRING_DATASOURCE_PASSWORD: 'e2e-local-only',
    SAQZ_PASSWORD_RESET_SECRET: 'disposable-local-e2e-password-reset-secret',
    SAQZ_MONTHLY_CHARGES_CRON: '-', SAQZ_ASAAS_API_KEY: '', SAQZ_ASAAS_WEBHOOK_TOKEN: '',
    SAQZ_NOTIFICATIONS_REMINDER_ENABLED: 'false',
    SAQZ_MAIL_HOST: '127.0.0.1', SAQZ_MAIL_PORT: '1', SAQZ_MAIL_STARTTLS: 'false',
  });
  await until(() => healthy('http://127.0.0.1:18080/actuator/health'), 'Spring/Flyway');
  console.log('Seeding disposable preconditions...');
  const fixture = await seed(sql => command('docker', ['exec', '-i', database, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'saqz', '-d', 'saqz_e2e'], { input: sql }), selected.map(scenario => scenario.name));
  const assetDir = path.join(root, 'mobile/android-app/build/e2e-assets');
  await mkdir(assetDir, { recursive: true });
  await writeFile(path.join(assetDir, 'e2e-fixture.json'), JSON.stringify(fixture));
  console.log('Running installed Android journeys...');
  const started = Date.now();
  testStarted = true;
  const classes = selected.map(scenario => `br.com.saqz.androidapp.${scenario.testClass}`).join(',');
  await command('./mobile/gradlew', ['-p', 'mobile', ':android-app:connectedDevDebugAndroidTest',
    '-Psaqz.e2e=true', `-Pandroid.testInstrumentationRunnerArguments.class=${classes}`,
    '--console=plain'], { stdio: 'inherit', env: { ...process.env, ANDROID_SERIAL: serial } });
  const entries = await readdir(reports, { recursive: true });
  const fresh = [];
  for (const entry of entries.filter(entry => entry.endsWith('.xml'))) {
    const file = path.join(reports, entry);
    if ((await stat(file)).mtimeMs >= started) fresh.push(await readFile(file, 'utf8'));
  }
  verifyReport(fresh.join('\n'), expectedCount);
  console.log(`PASS: ${selected.map(scenario => scenario.name).join(', ')} (${expectedCount} installed tests). JUnit/HTML: mobile/android-app/build/reports/androidTests/connected/`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  // Keep diagnostics even when instrumentation fails; the next Gradle run replaces its reports.
  if (testStarted) await cp(reports, path.join(artifacts, 'junit'), { recursive: true }).catch(error => console.warn(error.message));
  await finishCleanup({ testStarted, cancelled: cancellation.signal.aborted, serial, services, database, command }, artifacts);
}
