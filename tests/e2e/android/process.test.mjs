import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { spawnSync } from 'node:child_process';
import { runCommand, stopResources } from './process.mjs';

test('ENV: stdout metadata stays exact while successful stderr remains visible', async () => {
  let diagnostic = '';
  const id = 'a'.repeat(64);
  const result = await runCommand(process.execPath, ['-e',
    `process.stdout.write('${id}\\n'); process.stderr.write('image pull notice\\n')`],
  { onStderr: chunk => { diagnostic += chunk; } });
  assert.equal(result, id);
  assert.equal(diagnostic, 'image pull notice\n');
});

test('ENV: nonzero subprocess rejects with both output streams and exit code', async () => {
  await assert.rejects(runCommand(process.execPath, ['-e',
    "process.stdout.write('out-detail'); process.stderr.write('err-detail'); process.exitCode = 7"],
  { onStderr: () => {} }), error => {
    assert.match(error.message, /exited 7/);
    assert.match(error.message, /out-detail/);
    assert.match(error.message, /err-detail/);
    return true;
  });
});

test('ENV: subprocess still receives input and spawn failures reject', async () => {
  assert.equal(await runCommand(process.execPath, ['-e', 'process.stdin.pipe(process.stdout)'], { input: 'fixture SQL' }), 'fixture SQL');
  await assert.rejects(runCommand('/saqz-e2e-nonexistent-command', []), { code: 'ENOENT' });
});

function environment({ adbFails = false, serviceFails = false, databaseFails = false } = {}) {
  const calls = [];
  const service = name => {
    const child = new EventEmitter();
    child.exitCode = null;
    child.signalCode = null;
    child.kill = signal => {
      calls.push([name, signal]);
      if (serviceFails && name === 'backend') throw new Error('backend stop failed');
      queueMicrotask(() => { child.exitCode = 0; child.emit('close', 0); });
      return true;
    };
    return child;
  };
  const command = async (binary, args, options) => {
    calls.push([binary, ...args]);
    assert.equal(options.signal, undefined, 'cleanup must not inherit the cancelled signal');
    if (adbFails && binary === 'adb') throw new Error('device disconnected');
    if (databaseFails && binary === 'docker') throw new Error('docker unavailable');
  };
  const options = {
    testStarted: true, cancelled: true, serial: 'emulator-5554',
    services: [service('firebase'), service('backend')], database: 'owned-id', command, timeoutMs: 10,
  };
  return { calls, options };
}

test('ENV: cleanup targets only the selected APK, owned processes and exact container', async () => {
  const { calls, options } = environment();
  await stopResources(options);
  assert.deepEqual(calls, [
    ['adb', '-s', 'emulator-5554', 'shell', 'am', 'force-stop', 'app.saqz.e2e'],
    ['backend', 'SIGTERM'], ['firebase', 'SIGTERM'], ['docker', 'rm', '-f', 'owned-id'],
  ]);
  assert.equal(options.services.length, 2);
});

test('ENV: a disconnected emulator cannot prevent process or database cleanup', async () => {
  const { calls, options } = environment({ adbFails: true });
  await assert.rejects(stopResources(options), /device disconnected/);
  assert.deepEqual(calls.slice(1), [
    ['backend', 'SIGTERM'], ['firebase', 'SIGTERM'], ['docker', 'rm', '-f', 'owned-id'],
  ]);
});

test('ENV: a failed service stop does not prevent remaining cleanup or hide failure', async () => {
  const { calls, options } = environment({ serviceFails: true, databaseFails: true });
  await assert.rejects(stopResources(options), error => {
    assert.match(error.message, /backend stop failed/);
    assert.match(error.message, /docker unavailable/);
    return true;
  });
  assert.deepEqual(calls.slice(-2), [['firebase', 'SIGTERM'], ['docker', 'rm', '-f', 'owned-id']]);
});

test('ENV: absent resources and ordinary completion do not force-stop another APK', async () => {
  const { calls, options } = environment();
  await stopResources({ ...options, cancelled: false, services: [], database: undefined });
  assert.deepEqual(calls, []);
});

test('ENV: cleanup waits for forced termination when a service ignores SIGTERM', async () => {
  const { calls, options } = environment();
  const stubborn = options.services[1];
  stubborn.kill = signal => {
    calls.push(['backend', signal]);
    if (signal === 'SIGKILL') queueMicrotask(() => { stubborn.signalCode = signal; stubborn.emit('close', null); });
    return true;
  };
  await stopResources(options);
  assert.deepEqual(calls.slice(1), [
    ['backend', 'SIGTERM'], ['backend', 'SIGKILL'], ['firebase', 'SIGTERM'], ['docker', 'rm', '-f', 'owned-id'],
  ]);
  assert.equal(stubborn.listenerCount('close'), 0);
});

test('ENV: unconfirmed termination stays a failure while other owned resources are cleaned', async () => {
  const { calls, options } = environment();
  const stubborn = options.services[1];
  stubborn.kill = signal => { calls.push(['backend', signal]); return true; };
  await assert.rejects(stopResources(options), /Process did not close after SIGKILL/);
  assert.deepEqual(calls.slice(-2), [['firebase', 'SIGTERM'], ['docker', 'rm', '-f', 'owned-id']]);
  assert.equal(stubborn.listenerCount('close'), 0);
});

test('ENV: the stdout-only container identifier is the exact target of start and cleanup', async () => {
  const id = 'b'.repeat(64);
  const database = await runCommand(process.execPath, ['-e',
    `process.stdout.write('${id}\\n'); process.stderr.write('pull diagnostic\\n')`], { onStderr: () => {} });
  const { calls, options } = environment();
  await options.command('docker', ['start', database], { signal: undefined });
  await stopResources({ ...options, cancelled: false, services: [], database });
  assert.deepEqual(calls, [['docker', 'start', id], ['docker', 'rm', '-f', id]]);
});

test('ENV: cleanup failure exits nonzero without claiming all resources were removed', () => {
  const script = `
    import { finishCleanup } from ${JSON.stringify(new URL('./process.mjs', import.meta.url).href)};
    await finishCleanup({ testStarted: true, cancelled: true, serial: 'emulator-5554',
      services: [], database: 'owned-id', command: async (binary, args) => {
        if (binary === 'adb') throw new Error('device disconnected');
        console.log(JSON.stringify([binary, ...args]));
      } }, '/tmp/inert-e2e-artifacts');
  `;
  const result = spawnSync(process.execPath, ['--input-type=module', '-e', script], { encoding: 'utf8', timeout: 5000 });
  assert.equal(result.status, 1);
  assert.equal(result.stdout.trim(), '["docker","rm","-f","owned-id"]');
  assert.match(result.stderr, /Cleanup incomplete: Stop test APK: device disconnected/);
  assert.match(result.stderr, /Logs retained: \/tmp\/inert-e2e-artifacts/);
  assert.doesNotMatch(result.stdout + result.stderr, /services\/data were removed/);
});
