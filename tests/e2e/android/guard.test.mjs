import { test } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { emulatorSerial, localUrl, requireFreePort, verifyReport } from './guard.mjs';

test('ENV: only an explicit emulator may receive the isolated APK', () => {
  assert.equal(emulatorSerial('emulator-5554'), 'emulator-5554');
  for (const input of [undefined, '', 'physical-device', 'emulator-5554;echo unsafe']) {
    assert.throws(() => emulatorSerial(input), /dedicated Android emulator/);
  }
});
test('ENV: no remote/cloud or credential-bearing fixture endpoints', () => {
  assert.equal(localUrl('http://127.0.0.1:18080').origin, 'http://127.0.0.1:18080');
  for (const input of ['https://saqz-api.brunoalmeida.dev', 'http://10.0.2.2', 'http://user@127.0.0.1']) {
    assert.throws(() => localUrl(input), /loopback/);
  }
});
test('ENV: occupied ports fail instead of reusing existing services', async () => {
  const server = net.createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  try { await assert.rejects(requireFreePort(port), /occupied/); }
  finally { await new Promise(resolve => server.close(resolve)); }
  await requireFreePort(port);
});

test('ENV: a green build with missing, skipped or failed journeys is not a pass', () => {
  const xml = '<testsuite><testcase name="one"/><testcase name="two"/></testsuite>';
  assert.equal(verifyReport(xml, 2), 2);
  for (const invalid of ['', xml.replace('<testcase name="two"/>', ''), `${xml}<failure/>`, `${xml}<skipped/>`, `${xml}<error/>`]) {
    assert.throws(() => verifyReport(invalid, 2), /Expected 2 passing/);
  }
});
