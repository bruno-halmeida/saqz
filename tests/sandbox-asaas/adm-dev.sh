#!/usr/bin/env bash
# Chama a API do backend de DEV como o administrador de plataforma de teste (owner@saqz.local). Serve para publicar
# termo, comissão e liberação do recebimento pelo app (DEB-138) e ligar as "operações da conta" de um gestor sem
# entrar no adm.
#
# Uso (no Mac, com o ssh saqz-server configurado):
#   tests/sandbox-asaas/adm-dev.sh GET  /admin/receivables/rollout
#   tests/sandbox-asaas/adm-dev.sh PUT  /admin/receivables/rollout '{"requestId":"...", ...}'
#   tests/sandbox-asaas/adm-dev.sh POST /admin/receivables/terms @arquivo.json
#   tests/sandbox-asaas/adm-dev.sh conta gestor@exemplo.com          # situação do gestor no recebimento
#   tests/sandbox-asaas/adm-dev.sh liberar-conta gestor@exemplo.com  # liga as operações da conta financeira dele
#   tests/sandbox-asaas/adm-dev.sh liberar-novas 240                 # por 240 min, liga sozinho as contas novas
#
# Toda conta financeira nasce com as operações desligadas (V49) e o adm liga uma a uma; sem isso o gestor para no
# OPERATIONS_DISABLED ao ativar o grupo.
#
# O token vem de um custom token assinado no servidor com a conta de serviço do saqz-dev
# (.secrets/firebase-dev-service-account.json), trocado no Identity Toolkit pela chave web pública de
# firebase-config.server.js. Chave e token nunca são impressos. Para se o projeto não for o saqz-dev.
set -euo pipefail

[[ $# -ge 2 ]] || { sed -n 2,17p "$0" >&2; exit 2; }
cmd=$1; arg=$2; body=${3:-}
if [[ "$body" == @* ]]; then body=$(cat "${body#@}"); fi
body_b64=$(printf '%s' "$body" | base64 | tr -d '\n')

ssh -o BatchMode=yes saqz-server "bash -s -- '$cmd' '$arg' '$body_b64'" <<'REMOTE'
set -euo pipefail
cd /root/applications/saqz
url=$(grep -E 'SPRING_DATASOURCE_URL' compose.server.yaml | head -1 | sed -E 's/.*jdbc:postgresql:\/\///; s/[" ]//g')
user=$(grep -E 'SPRING_DATASOURCE_USERNAME' compose.server.yaml | head -1 | sed -E 's/.*:\s*//; s/[" ]//g')
host=${url%%:*}; rest=${url#*:}; port=${rest%%/*}; db=${rest#*/}; db=${db%%\?*}
export PGPASSWORD=$(grep -E '^SAQZ_DB_PASSWORD=' .env | cut -d= -f2- | tr -d "'\"")
export DB_ARGS="$host $port $user $db"
python3 - "$@" <<'PY'
import base64, json, os, re, subprocess, sys, tempfile, time, urllib.error, urllib.request, uuid

cmd, arg, body_b64 = sys.argv[1], sys.argv[2], sys.argv[3]
body = base64.b64decode(body_b64).decode() if body_b64 else ""

def sql(query):
    host, port, user, db = os.environ["DB_ARGS"].split()
    out = subprocess.run(["docker", "run", "--rm", "-e", "PGPASSWORD", "--network", "host", "postgres:16-alpine",
                          "psql", "-h", host, "-p", port, "-U", user, "-d", db, "-X", "-q", "-A", "-t", "-c", query],
                         capture_output=True, text=True, check=True).stdout.strip()
    return out

def b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=")

def admin_token():
    sa = json.load(open(".secrets/firebase-dev-service-account.json"))
    config = open("firebase-config.server.js").read()
    if sa.get("project_id") != "saqz-dev" or 'projectId: "saqz-dev"' not in config:
        sys.exit("O projeto Firebase não é o saqz-dev. Parei sem assinar nada.")
    api_key = re.search(r'apiKey:\s*"([^"]+)"', config).group(1)
    uid = sql("SELECT firebase_subject FROM access_users WHERE platform_admin AND email='owner@saqz.local' "
              "AND deleted_at IS NULL AND suspended_at IS NULL")
    if not uid:
        sys.exit("Administrador de teste owner@saqz.local não encontrado no banco de dev.")
    now = int(time.time())
    header = b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = b64url(json.dumps({
        "iss": sa["client_email"], "sub": sa["client_email"], "uid": uid, "iat": now, "exp": now + 3600,
        "aud": "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit"}).encode())
    signing = header + b"." + claims
    with tempfile.NamedTemporaryFile("w", delete=False) as key_file:
        key_file.write(sa["private_key"])
    try:
        signature = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key_file.name], input=signing,
                                   capture_output=True, check=True).stdout
    finally:
        os.unlink(key_file.name)
    request = urllib.request.Request(
        "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=" + api_key,
        data=json.dumps({"token": (signing + b"." + b64url(signature)).decode(), "returnSecureToken": True}).encode(),
        headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(request, timeout=20))["idToken"]

TOKEN = admin_token()

def call(method, path, payload=None):
    data = payload.encode() if payload else None
    request = urllib.request.Request("http://127.0.0.1:8080" + path, data=data, method=method,
                                     headers={"Authorization": "Bearer " + TOKEN, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            status, raw = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, raw = error.code, error.read()
    try:
        parsed = json.loads(raw) if raw else None
    except ValueError:
        parsed = raw.decode(errors="replace")[:500]
    return status, parsed

def user_id(email):
    found = sql("SELECT id FROM access_users WHERE lower(email)=lower('%s') AND deleted_at IS NULL "
                "ORDER BY created_at DESC LIMIT 1" % email.replace("'", "''"))
    if not found:
        sys.exit("Nenhuma conta ativa com o e-mail %s no dev." % email)
    return found

def release(uid, label):
    status, state = call("GET", "/admin/receivables/rollout/users/" + uid)
    if status != 200:
        return "Falha ao ler a liberação de %s: %s %s" % (label, status, state)
    if state.get("accountId") is None:
        return "%s ainda não tem conta financeira. Faça o cadastro financeiro no app e rode de novo." % label
    if state.get("accountOperationsEnabled"):
        return "As operações da conta de %s já estavam ligadas." % label
    change = {"requestId": str(uuid.uuid4()), "expectedVersion": state["version"],
              "reason": "dev: liberar operações da conta para o teste do recebimento no sandbox",
              "overrides": [{"system": o["system"], "decision": o["decision"]} for o in state["overrides"]],
              "accountOperationsEnabled": True}
    status, after = call("PUT", "/admin/receivables/rollout/users/" + uid, json.dumps(change))
    if status == 200 and after.get("accountOperationsEnabled"):
        return "Operações da conta de %s ligadas." % label
    return "Falha ao ligar as operações de %s: %s %s" % (label, status, after)

if cmd in ("GET", "POST", "PUT", "PATCH", "DELETE"):
    status, parsed = call(cmd, arg, body or None)
    print(status)
    print(json.dumps(parsed, ensure_ascii=False, indent=2) if not isinstance(parsed, str) else parsed)
elif cmd == "conta":
    uid = user_id(arg)
    status, state = call("GET", "/admin/receivables/rollout/users/" + uid)
    print(status, json.dumps(state, ensure_ascii=False, indent=2))
    # Mesma regra do CentralReceivablesEligibility: plano ativo que não seja o Titular, ou teste grátis em curso.
    print(sql("SELECT 'plano: ' || coalesce(s.plan::text || ' ' || s.status::text, 'nenhum') || ' | teste grátis até: ' || "
              "coalesce(t.ends_at::date::text, 'nunca teve') || ' | apto a receber: ' || CASE WHEN (s.status::text='ACTIVE' "
              "AND s.plan::text<>'TITULAR') OR t.ends_at > now() THEN 'sim' ELSE 'NÃO' END FROM access_users u "
              "LEFT JOIN subscriptions s ON s.owner_user_id=u.id LEFT JOIN organizer_trials t ON t.owner_user_id=u.id "
              "WHERE u.id='%s'" % uid))
    print(sql("SELECT 'conta financeira: ' || coalesce(string_agg(registration || ' | operações ' || "
              "CASE WHEN new_operations_enabled THEN 'ligadas' ELSE 'desligadas' END || ' | pix pronto ' || pix_key_ready, '; '), 'nenhuma') "
              "FROM receivable_accounts WHERE owner_user_id='%s'" % uid))
elif cmd == "liberar-conta":
    print(release(user_id(arg), arg))
elif cmd == "liberar-novas":
    # Liga as operações de toda conta financeira nova do dev enquanto durar (minutos em `arg`), para o teste não
    # parar no OPERATIONS_DISABLED logo depois do cadastro financeiro.
    deadline = time.time() + int(arg) * 60
    print("Vigiando contas financeiras novas no dev por %s min." % arg, flush=True)
    while time.time() < deadline:
        pending = sql("SELECT a.owner_user_id || ' ' || u.email FROM receivable_accounts a JOIN access_users u "
                      "ON u.id=a.owner_user_id WHERE NOT a.new_operations_enabled")
        for line in filter(None, pending.splitlines()):
            owner, email = line.split(" ", 1)
            TOKEN = admin_token()
            print(time.strftime("%H:%M:%S"), release(owner, email), flush=True)
        time.sleep(20)
    print("Fim da vigia.")
else:
    sys.exit("Comando desconhecido: %s" % cmd)
PY
REMOTE
