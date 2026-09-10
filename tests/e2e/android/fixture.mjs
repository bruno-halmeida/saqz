import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import { localUrl } from './guard.mjs';

const api = 'http://127.0.0.1:18080';
const auth = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
export const password = 'Local-e2e-only-123!';

async function request(url, method, body, token, headers = {}) {
  localUrl(url);
  const response = await fetch(url, {
    method, signal: AbortSignal.timeout(15000),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`Fixture ${method} ${new URL(url).pathname}: HTTP ${response.status}`);
  return response.json();
}

async function user(label, runId) {
  const email = `${label}-${runId}@e2e.saqz.test`;
  const credentials = { email, password, returnSecureToken: true };
  const signup = await request(`${auth}/accounts:signUp?key=fake-saqz-local-api-key`, 'POST', credentials);
  await request(`${auth}/accounts:update?key=fake-saqz-local-api-key`, 'POST', {
    idToken: signup.idToken, displayName: `E2E ${label}`, returnSecureToken: true,
  });
  const session = await request(`${auth}/accounts:signInWithPassword?key=fake-saqz-local-api-key`, 'POST', credentials);
  const bootstrap = await request(`${api}/api/session`, 'PUT', undefined, session.idToken);
  await request(`${api}/api/session/profile`, 'PATCH', { phone: '+5511999990000' }, session.idToken);
  // Tokens remain only in memory; fixture APK contains disposable names/ids, never bearer tokens.
  return { id: bootstrap.user.id, uid: signup.localId, email, token: session.idToken };
}

export async function seed(sql, scenarioNames) {
  const result = { password, scenarios: {} };
  const runId = randomUUID().slice(0, 8);
  for (const name of scenarioNames) {
    const owner = await user(`${name}-owner`, runId);
    const athlete = await user(`${name}-athlete`, runId);
    const other = await user(`${name}-other`, runId);
    const group = randomUUID();
    const secondGroup = randomUUID();
    // Only preconditions use SQL, in this run's disposable DB. Every tested action uses UI.
    // No subscription/provider payment is simulated as a tested scenario.
    await sql(`BEGIN;
      INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,
        default_capacity,default_game_fee_cents,mensalista_priority,created_at,updated_at) VALUES
        ('${group}','${owner.id}','${randomUUID()}','Volei ${name}','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',2,2000,false,now(),now()),
        ('${secondGroup}','${owner.id}','${randomUUID()}','Praia ${name}','America/Sao_Paulo','COMPLETE','BEACH_VOLLEYBALL','MIXED',2,2000,false,now(),now());
      INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at) VALUES
        ('${group}','${owner.id}','ADMIN','AVULSO',now(),now()),
        ('${group}','${athlete.id}','ATHLETE','MENSALISTA',now(),now()),
        ('${secondGroup}','${owner.id}','ADMIN','AVULSO',now(),now()),
        ('${secondGroup}','${athlete.id}','ATHLETE','MENSALISTA',now(),now()),
        ('${secondGroup}','${other.id}','ATHLETE','MENSALISTA',now(),now());
      COMMIT;`);
    const actors = Object.fromEntries(Object.entries({ owner, athlete, other }).map(([key, { token, ...safe }]) => [key, safe]));
    result.scenarios[name] = { ...actors, group, secondGroup };
    if (name === 'leave' || name.startsWith('attendance')) {
      const game = await publishedGame(group, owner.token);
      result.scenarios[name].game = game.id;
      if (name.startsWith('attendance')) {
        await sql(`INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at)
          VALUES ('${group}','${other.id}','ATHLETE','AVULSO',now(),now());`);
        for (const person of [owner, other]) {
          const confirmed = await request(`${api}/api/groups/${group}/games/${game.id}/attendance`, 'PUT',
            { requestId: randomUUID(), intent: 'CONFIRM' }, person.token);
          assert.equal(confirmed.attendance.status, 'CONFIRMED', 'Fixture must fill both real slots before UI confirmation');
        }
        if (name === 'attendance-order') {
          const reserve = await user(`${name}-reserve`, runId);
          const { token, ...safe } = reserve;
          result.scenarios[name].reserve = safe;
          await sql(`INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at)
            VALUES ('${group}','${reserve.id}','ATHLETE','MENSALISTA',now(),now());`);
          for (const [index, person] of [athlete, reserve].entries()) {
            const waiting = await request(`${api}/api/groups/${group}/games/${game.id}/attendance`, 'PUT',
              { requestId: randomUUID(), intent: 'CONFIRM' }, person.token);
            assert.equal(waiting.attendance.status, 'WAITLISTED');
            assert.equal(waiting.attendance.waitlistPosition, index + 1);
          }
        }
      }
    }
  }
  return result;
}

async function publishedGame(group, token) {
  const starts = new Date();
  starts.setUTCDate(starts.getUTCDate() + 1);
  starts.setUTCHours(23, 0, 0, 0); // 20:00 in Sao Paulo; no fixed calendar dependency.
  const created = await request(`${api}/api/groups/${group}/games`, 'POST', {
    requestId: randomUUID(), title: 'Treino E2E', venue: { name: 'Arena QA', address: 'Rua Teste 100' },
    localDate: starts.toISOString().slice(0, 10), localTime: '20:00', zoneId: 'America/Sao_Paulo',
    startsAt: starts.toISOString(), durationMinutes: 90, capacity: 2,
    confirmationDeadline: new Date(starts.getTime() - 7200000).toISOString(), useDefaultGameFee: true,
  }, token);
  const published = await request(`${api}/api/groups/${group}/games/${created.id}/publish`, 'POST',
    undefined, token, { 'If-Match': `"${created.version}"` });
  assert.equal(published.status, 'PUBLISHED');
  return published;
}
