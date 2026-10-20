// Uazapi falso para o stack local: responde como uma instância conectada e grava tudo o que o
// backend mandaria ao WhatsApp em ~/.saqz-multidev/logs/uazapi-mock.jsonl. Nada sai da máquina.
// O backend só aceita HTTPS (NotificationWhatsAppConfiguration), então, uma vez:
//   D=~/.saqz-multidev/uazapi; mkdir -p $D && cd $D
//   openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 30 -subj "/CN=localhost" \
//     -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"
//   cp "$JDK21/lib/security/cacerts" truststore.jks && chmod u+w truststore.jks
//   "$JDK21/bin/keytool" -importcert -noprompt -alias uazapi-falso -file cert.pem -keystore truststore.jks -storepass changeit
// Depois: `node tests/multi-aparelhos/ferramentas/uazapi-falso.mjs` e suba o backend (stack.sh down só do backend) com
//   SAQZ_NOTIFICATIONS_WHATSAPP_ENABLED=true SAQZ_NOTIFICATIONS_WHATSAPP_BASE_URL=https://localhost:18443
//   SAQZ_NOTIFICATIONS_WHATSAPP_TOKEN=falso SAQZ_PUBLIC_API_URL=http://127.0.0.1:18080
//   JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$HOME/.saqz-multidev/uazapi/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
// O código de convite do WhatsApp precisa ter 10 a 50 caracteres (o backend recusa antes de chamar o Uazapi).
// Grupo conhecido: código de convite "QAGRUPO0001" → 120363000000000001@g.us ("Vôlei QA WhatsApp").
// Convite de mensalistas (CONV-012), para testar sem WhatsApp de verdade:
//   - `/chat/check` responde `[{"query":"<n>","isInWhatsapp":<bool>}]`, com `false` para números terminados em 90
//     ("Sem WhatsApp") e `true` para o resto;
//   - `/send/text` responde 500 para números terminados em 91 a 99 (3 tentativas → "Falhou"; três seguidas pausam a fila).
import https from "node:https";
import fs from "node:fs";
import os from "node:os";
const LOG = `${os.homedir()}/.saqz-multidev/logs/uazapi-mock.jsonl`;
const SELF = "5511999990000";
const ADMIN = process.env.MOCK_GROUP_ADMIN || "5511900000011";
const groups = {
  QAGRUPO0001: { JID: "120363000000000001@g.us", Name: "Vôlei QA WhatsApp" },
  QAGRUPO0002: { JID: "120363000000000002@g.us", Name: "Vôlei QA WhatsApp 2" },
};
const participants = () => [
  { PhoneNumber: `${ADMIN}@s.whatsapp.net`, IsAdmin: true, IsSuperAdmin: true },
  { PhoneNumber: `${SELF}@s.whatsapp.net`, IsAdmin: false },
];
let seq = 0;
const send = (res, code, obj) => { res.writeHead(code, { "Content-Type": "application/json" }); res.end(JSON.stringify(obj)); };
https.createServer({ key: fs.readFileSync(`${os.homedir()}/.saqz-multidev/uazapi/key.pem`), cert: fs.readFileSync(`${os.homedir()}/.saqz-multidev/uazapi/cert.pem`) }, (req, res) => {
  let raw = "";
  req.on("data", (c) => (raw += c));
  req.on("end", () => {
    let body = null; try { body = raw ? JSON.parse(raw) : null; } catch { body = raw; }
    const path = req.url.split("?")[0];
    fs.appendFileSync(LOG, JSON.stringify({ at: new Date().toISOString(), method: req.method, path, body }) + "\n");
    if (path === "/instance/status") return send(res, 200, {
      instance: { id: "mock", name: "saqz-mock", status: "connected", profileName: "Saqz", token: "mock" },
      status: { connected: true, loggedIn: true, jid: `${SELF}:7@s.whatsapp.net` } });
    const code = body && (body.invitecode || body.inviteCode || body.code || body.invite_code);
    const jid = body && (body.groupjid || body.groupJid || body.jid || body.id);
    const byCode = code && groups[String(code).replace(/^.*chat\.whatsapp\.com\//, "")];
    const byJid = jid && Object.values(groups).find((g) => g.JID === jid);
    if (path === "/group/inviteInfo") return byCode ? send(res, 200, { group: { ...byCode, Participants: participants() } })
      : send(res, 500, { error: "that group does not exist" });
    if (path === "/group/join") return byCode ? send(res, 200, { group: {} }) : send(res, 500, { error: "that group does not exist" });
    if (path === "/group/info") return byJid ? send(res, 200, { ...byJid, Participants: participants() })
      : send(res, 500, { error: "that group does not exist" });
    if (path === "/chat/check") {
      const numbers = (body && (body.numbers || body.number || body.phones)) || [];
      return send(res, 200, [].concat(numbers).map((n) => ({ query: String(n), isInWhatsapp: !String(n).endsWith("90") })));
    }
    const target = String((body && body.number) || "");
    if (path === "/send/text" && /9[1-9]$/.test(target)) return send(res, 500, { error: `mock: falha simulada para ${target}` });
    if (path === "/send/text" || path === "/send/menu") return send(res, 200, { id: `mock-${++seq}`, messageid: `mock-${seq}`, status: "sent" });
    return send(res, 404, { error: `mock sem rota ${path}` });
  });
}).listen(18443, "127.0.0.1", () => console.log("uazapi mock em https://localhost:18443, log em " + LOG));
