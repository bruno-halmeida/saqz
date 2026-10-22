// Marca e publica um jogo pela API real com a conta indicada (precondição de roteiro, não ação sob teste).
// Uso: node marcar-jogo.mjs <email> <groupId> <dias a partir de hoje> <HH:MM> <quadra> [vagas]
import { randomUUID } from 'node:crypto';
const [email, group, days, time, venue, capacity = '6'] = process.argv.slice(2);
const auth = 'http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1';
const api = 'http://127.0.0.1:18080';
async function call(url, method, body, token, headers = {}) {
  const r = await fetch(url, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers }, body: body && JSON.stringify(body) });
  if (!r.ok) throw new Error(`${method} ${url}: ${r.status} ${await r.text()}`);
  return r.json();
}
const { idToken } = await call(`${auth}/accounts:signInWithPassword?key=fake`, 'POST', { email, password: 'SenhaQA2026', returnSecureToken: true });
const local = new Date(Date.now() - 3 * 3600e3); local.setUTCDate(local.getUTCDate() + Number(days));
const localDate = local.toISOString().slice(0, 10);
const [h, m] = time.split(':').map(Number);
const starts = new Date(`${localDate}T${time}:00-03:00`);
const created = await call(`${api}/api/groups/${group}/games`, 'POST', {
  requestId: randomUUID(), title: 'Jogo', venue: { name: venue, address: '' }, localDate, localTime: time, zoneId: 'America/Sao_Paulo',
  startsAt: starts.toISOString(), durationMinutes: 90, capacity: Number(capacity),
  confirmationDeadline: new Date(starts.getTime() - 7200e3).toISOString(), useDefaultGameFee: true,
}, idToken);
const pub = await call(`${api}/api/groups/${group}/games/${created.id}/publish`, 'POST', undefined, idToken, { 'If-Match': `"${created.version}"` });
console.log(venue, pub.id, pub.status);
