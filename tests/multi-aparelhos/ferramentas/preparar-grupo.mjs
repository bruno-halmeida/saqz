// Atalho da preparação do roteiro 01 (passos 1 a 8) por API e SQL: quatro contas no Auth Emulator, sessão e
// telefone pela API real e o grupo `QA Vôlei <data>` com ORG dono e ATL, ATL2, ATL3 avulsos. Só precondição.
// Uso: node tests/multi-aparelhos/ferramentas/preparar-grupo.mjs DD/MM
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const data = process.argv[2];
if (!/^\d\d\/\d\d$/.test(data ?? '')) throw new Error('Uso: preparar-grupo.mjs DD/MM');
const tag = data.replace('/', '');
const auth = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const api = 'http://127.0.0.1:18080';
const sql = q => execFileSync('docker', ['exec', '-i', 'saqz-multidev-db', 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'saqz', '-d', 'saqz', '-Atq'], { input: q }).toString().trim();

async function call(url, method, body, token) {
  const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body && JSON.stringify(body) });
  if (!r.ok) throw new Error(`${method} ${url}: ${r.status} ${await r.text()}`);
  return r.json();
}

const ids = {};
for (const [i, role] of ['org', 'atl', 'atl2', 'atl3'].entries()) {
  const label = role[0].toUpperCase() + role.slice(1);
  const email = `qa-${role}+${tag}@saqz.local`;
  const creds = { email, password: 'SenhaQA2026', returnSecureToken: true };
  const up = await call(`${auth}/accounts:signUp?key=fake`, 'POST', creds);
  await call(`${auth}/accounts:update?key=fake`, 'POST', { idToken: up.idToken, displayName: `QA ${label} ${data}`, emailVerified: true });
  const s = await call(`${auth}/accounts:signInWithPassword?key=fake`, 'POST', creds);
  const session = await call(`${api}/api/session`, 'PUT', undefined, s.idToken);
  await call(`${api}/api/session/profile`, 'PATCH', { phone: `+551190000000${i + 1}` }, s.idToken);
  ids[role] = session.user.id;
}
const g = randomUUID();
sql(`BEGIN;
INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,default_capacity,mensalista_priority,auto_confirm_enabled,created_at,updated_at)
 VALUES ('${g}','${ids.org}','${randomUUID()}','QA Vôlei ${data}','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',12,true,false,now(),now());
INSERT INTO group_memberships (group_id,user_id,role,membership_type,created_at,updated_at) VALUES
 ('${g}','${ids.org}','ADMIN','AVULSO',now(),now()),('${g}','${ids.atl}','ATHLETE','AVULSO',now(),now()),
 ('${g}','${ids.atl2}','ATHLETE','AVULSO',now(),now()),('${g}','${ids.atl3}','ATHLETE','AVULSO',now(),now());
COMMIT;`);
console.log(JSON.stringify({ group: g, ...ids }));
