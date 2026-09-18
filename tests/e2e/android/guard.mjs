import net from 'node:net';

const scenarios = {
  access: ['AccessE2eTest', 2],
  leave: ['GroupLeaveE2eTest', 1],
  attendance: ['AttendanceE2eTest', 1],
  'attendance-order': ['AttendanceOrderE2eTest', 1],
  'attendance-guest': ['GameGuestE2eTest', 1],
  communication: ['CommunicationE2eTest', 1],
  finance: ['MonthlyGenerationE2eTest', 1],
  'notification-settings': ['NotificationsE2eTest', 1],
  'message-pagination': ['MessagePaginationE2eTest', 1],
  reminders: ['ReminderE2eTest', 1],
  'sports-profile': ['SportsProfileE2eTest', 1],
  'member-privacy': ['MemberPrivacyE2eTest', 1],
  'monthly-history': ['MonthlyHistoryE2eTest', 1],
  payments: ['PaymentE2eTest', 1],
  'charge-lifecycle': ['ChargeLifecycleE2eTest', 1],
  settlement: ['SettlementE2eTest', 1],
};

export function selectScenarios(name) {
  if (name !== undefined && !Object.hasOwn(scenarios, name)) throw new Error(`Unknown E2E scenario: ${name}`);
  return Object.entries(scenarios).filter(([key]) => name === undefined || key === name)
    .map(([key, [testClass, count]]) => ({ name: key, testClass, count }));
}

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
