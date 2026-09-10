import net from 'node:net';

export function emulatorSerial(value) {
  if (!/^emulator-\d+$/.test(value ?? '')) throw new Error('Use --serial emulator-NNNN (dedicated Android emulator).');
  return value;
}

export function localUrl(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.username || url.password) {
    throw new Error('E2E permits only loopback HTTP services.');
  }
  return url;
}

export async function requireFreePort(port) {
  const server = net.createServer();
  await new Promise((resolve, reject) => {
    server.once('error', () => reject(new Error(`Port ${port} occupied; refusing to reuse another environment.`)));
    server.listen(port, '127.0.0.1', resolve);
  });
  await new Promise(resolve => server.close(resolve));
}

export function verifyReport(xml, expected) {
  const count = [...xml.matchAll(/<testcase\s/g)].length;
  if (count !== expected || /<(failure|error|skipped)[\s/>]/.test(xml)) {
    throw new Error(`Expected ${expected} passing journeys, no skips; found ${count} cases. Inspect JUnit report.`);
  }
  return count;
}
