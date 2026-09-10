import { spawn } from 'node:child_process';

export function runCommand(binary, args, options = {}) {
  const { input, onStderr = chunk => process.stderr.write(chunk), ...spawnOptions } = options;
  return new Promise((resolve, reject) => {
    const child = spawn(binary, args, spawnOptions);
    let stdout = '';
    let stderr = '';
    child.stdout?.on('data', chunk => { stdout += chunk; });
    child.stderr?.on('data', chunk => { stderr += chunk; onStderr(chunk); });
    child.on('error', reject);
    child.on('close', code => code === 0 ? resolve(stdout.trim())
      : reject(new Error(`${binary} exited ${code}: ${(stdout + stderr).slice(-3000)}`)));
    if (input !== undefined) child.stdin.end(input);
  });
}

export async function stopResources({ testStarted, cancelled, serial, services, database, command, timeoutMs = 5000 }) {
  const failures = [];
  const attempt = async (label, action) => {
    try { await action(); }
    catch (error) { failures.push(new Error(`${label}: ${error.message}`, { cause: error })); }
  };
  if (testStarted && cancelled) {
    await attempt('Stop test APK', () => command('adb', ['-s', serial, 'shell', 'am', 'force-stop', 'app.saqz.e2e'], { signal: undefined, timeout: 10000 }));
  }
  for (const child of [...services].reverse()) {
    await attempt(`Stop service ${child.pid ?? 'process'}`, async () => {
      if (child.exitCode !== null || child.signalCode != null) return;
      if (!await signalAndWait(child, 'SIGTERM', timeoutMs) && !await signalAndWait(child, 'SIGKILL', timeoutMs)) {
        throw new Error('Process did not close after SIGKILL');
      }
    });
  }
  // Only the id returned by this run's docker create; never names, globs or dev volumes.
  if (database) await attempt('Remove disposable database', () => command('docker', ['rm', '-f', database], { signal: undefined, timeout: 15000 }));
  if (failures.length) throw new AggregateError(failures, failures.map(error => error.message).join('\n'));
}

export async function finishCleanup(resources, artifacts) {
  try {
    await stopResources(resources);
    console.log(`Only this run's temporary services/data were removed. Logs retained: ${artifacts}`);
  } catch (error) {
    process.exitCode = 1;
    console.error(`Cleanup incomplete: ${error.message}\nLogs retained: ${artifacts}`);
  }
}

function signalAndWait(child, signal, timeoutMs) {
  return new Promise((resolve, reject) => {
    const finish = closed => {
      clearTimeout(timer);
      child.removeListener('close', onClose);
      resolve(closed);
    };
    const onClose = () => finish(true);
    const timer = setTimeout(() => finish(false), timeoutMs);
    child.once('close', onClose);
    try { child.kill(signal); }
    catch (error) {
      clearTimeout(timer);
      child.removeListener('close', onClose);
      reject(error);
    }
  });
}
