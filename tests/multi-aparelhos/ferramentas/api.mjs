// Chamada autenticada à API do stack local com a conta indicada (precondições de roteiro).
// Uso: node api.mjs <email> <MÉTODO> <caminho> [json] [If-Match]
import { randomUUID } from 'node:crypto';
const [email, method, path, body, ifMatch] = process.argv.slice(2);
const r0 = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password: 'SenhaQA2026', returnSecureToken: true }) });
const { idToken } = await r0.json();
const payload = body ? body.replaceAll('$RID', randomUUID()) : undefined;
const r = await fetch(`http://127.0.0.1:18080${path}`, { method, body: payload,
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${idToken}`, ...(ifMatch ? { 'If-Match': `"${ifMatch}"` } : {}) } });
console.log(r.status, (await r.text()).slice(0, 300));
