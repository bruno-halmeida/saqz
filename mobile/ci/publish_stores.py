#!/usr/bin/env python3
"""Publica no Play e na App Store o build de uma RC já enviado às lojas (mobile-release.yml).

  publish_stores.py --version 1.0.5 --build 16 --lojas ambas --notas "Texto público" [--simular]

Não rebuilda nada. Play: copia o build da trilha interna para produção (100%) com as notas
públicas. App Store: cria (ou reaproveita) a versão, grava "Novidades", anexa o build processado
e envia à revisão com liberação automática. Com --simular lê tudo e valida (no Play abre um edit,
valida e descarta), mas não faz commit no Play nem cria/envia nada na Apple.

Credenciais por ambiente (as mesmas do Environment "mobile"):
  PLAY_SERVICE_ACCOUNT_JSON            conteúdo do JSON da conta de serviço do Play
  ASC_ISSUER_ID, ASC_KEY_ID, ASC_KEY_P8  chave da App Store Connect API (.p8 inteiro em ASC_KEY_P8)
Para uso local, PLAY_SA_JSON_PATH / ASC_KEY_P8_PATH apontam para os arquivos.

Dependências: PyJWT e cryptography (mobile/ci/requirements.txt).
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

PACKAGE = "app.saqz"
LOCALE = "pt-BR"
PLAY_NOTES_LIMIT = 500
PLAY_API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PACKAGE
ASC_API = "https://api.appstoreconnect.apple.com/v1"

# Estados de versão que não ocupam a fila: a versão já saiu (ou foi retirada).
ASC_FINISHED = {"READY_FOR_SALE", "REPLACED_WITH_NEW_VERSION", "REMOVED_FROM_SALE", "DEVELOPER_REMOVED_FROM_SALE"}


class Abort(Exception):
    """Falha esperada, com mensagem para o operador."""


def log(msg):
    print(msg, flush=True)


# ---------------------------------------------------------------- decisões puras (testadas)

def decide_ios_version(versions, tag_version):
    """versions: lista de (versionString, appStoreState). Devolve 'create' ou 'reuse'.

    Para com mensagem clara quando há outra versão na fila ou a da tag já saiu/está em andamento.
    """
    for string, state in versions:
        if string == tag_version:
            if state == "PREPARE_FOR_SUBMISSION":
                return "reuse"
            if state in ASC_FINISHED:
                raise Abort(f"A versão {tag_version} já está na App Store ({state}); nada a publicar.")
            raise Abort(f"A versão {tag_version} já está em andamento na App Store ({state}); não mexo nela.")
    for string, state in versions:
        if state not in ASC_FINISHED:
            raise Abort(
                f"A versão {string} está ocupando a fila da App Store ({state}) e não é a da tag ({tag_version}). "
                "Espere sair, ou cancele/remova a submissão no App Store Connect, e rode de novo. Não renomeio versão."
            )
    if not any(state == "READY_FOR_SALE" for _, state in versions):
        raise Abort("Nenhuma versão anterior liberada (READY_FOR_SALE): a primeira publicação é manual.")
    return "create"


def play_release_for(track_releases, build):
    """Release da trilha que contém o build (versionCodes são strings no JSON do Play)."""
    for release in track_releases or []:
        if str(build) in [str(c) for c in release.get("versionCodes", [])]:
            return release
    return None


def validate_notes(notes):
    notes = notes.strip()
    if not notes:
        raise Abort("As notas públicas estão vazias.")
    if len(notes) > PLAY_NOTES_LIMIT:
        raise Abort(f"Notas públicas com {len(notes)} caracteres; o Play aceita até {PLAY_NOTES_LIMIT}.")
    return notes


# ---------------------------------------------------------------- HTTP

def http(method, url, token=None, body=None, form=None, retries=5):
    data, headers = None, {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    for attempt in range(1, retries + 1):
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as err:
            detail = err.read().decode(errors="replace")
            if err.code >= 500 and attempt < retries:
                log(f"  {method} {url.split('?')[0]} -> {err.code}, tentando de novo ({attempt}/{retries})")
                time.sleep(2 * attempt)
                continue
            raise Abort(f"{method} {url.split('?')[0]} -> HTTP {err.code}: {detail[:600]}")
        except urllib.error.URLError as err:
            if attempt < retries:
                time.sleep(2 * attempt)
                continue
            raise Abort(f"{method} {url.split('?')[0]} falhou: {err}")


# ---------------------------------------------------------------- Play

def play_token():
    import jwt  # PyJWT

    raw = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw and os.environ.get("PLAY_SA_JSON_PATH"):
        raw = open(os.environ["PLAY_SA_JSON_PATH"]).read()
    if not raw:
        raise Abort("Falta PLAY_SERVICE_ACCOUNT_JSON.")
    sa = json.loads(raw)
    now = int(time.time())
    assertion = jwt.encode(
        {"iss": sa["client_email"], "scope": "https://www.googleapis.com/auth/androidpublisher",
         "aud": "https://oauth2.googleapis.com/token", "iat": now, "exp": now + 3000},
        sa["private_key"], algorithm="RS256")
    resp = http("POST", "https://oauth2.googleapis.com/token", form={
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion})
    return resp["access_token"]


def play_promote(version, build, notes, simular):
    log(f"== Play: {version} (build {build}) -> produção")
    token = play_token()
    edit = http("POST", f"{PLAY_API}/edits", token, body={})["id"]
    committed = False
    try:
        internal = http("GET", f"{PLAY_API}/edits/{edit}/tracks/internal", token)
        if not play_release_for(internal.get("releases"), build):
            raise Abort(f"O build {build} não está na trilha interna do Play; suba a RC antes.")
        log(f"  build {build} está na trilha interna")
        prod = http("GET", f"{PLAY_API}/edits/{edit}/tracks/production", token)
        current = play_release_for(prod.get("releases"), build)
        if current and current.get("status") == "completed":
            log(f"  build {build} já está em produção (completed); nada a fazer no Play.")
            return
        log(f"  produção hoje: {[(r.get('status'), r.get('versionCodes')) for r in prod.get('releases', [])]}")
        http("PUT", f"{PLAY_API}/edits/{edit}/tracks/production", token, body={
            "track": "production",
            "releases": [{
                "versionCodes": [str(build)],
                "status": "completed",
                "releaseNotes": [{"language": LOCALE, "text": notes}],
            }]})
        http("POST", f"{PLAY_API}/edits/{edit}:validate", token, body={})
        log("  edit validado")
        if simular:
            log("  [simular] edit descartado, nada publicado no Play")
            return
        http("POST", f"{PLAY_API}/edits/{edit}:commit", token, body={})
        committed = True
        log("  commit feito: sai em produção quando o Google aprovar")
    finally:
        if not committed:
            try:
                http("DELETE", f"{PLAY_API}/edits/{edit}", token, retries=2)
            except Abort:
                pass


# ---------------------------------------------------------------- App Store

def asc_token():
    import jwt

    key = os.environ.get("ASC_KEY_P8", "")
    if not key and os.environ.get("ASC_KEY_P8_PATH"):
        key = open(os.path.expanduser(os.environ["ASC_KEY_P8_PATH"])).read()
    key_id, issuer = os.environ.get("ASC_KEY_ID", ""), os.environ.get("ASC_ISSUER_ID", "")
    if not (key and key_id and issuer):
        raise Abort("Faltam ASC_KEY_P8, ASC_KEY_ID ou ASC_ISSUER_ID.")
    now = int(time.time())
    return jwt.encode({"iss": issuer, "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"},
                      key, algorithm="ES256", headers={"kid": key_id})


def asc_get(path, token, **params):
    q = ("?" + urllib.parse.urlencode(params)) if params else ""
    return http("GET", f"{ASC_API}{path}{q}", token)


def ios_release(version, build, notes, simular):
    log(f"== App Store: {version} (build {build}) -> revisão")
    token = asc_token()
    app = asc_get("/apps", token, **{"filter[bundleId]": PACKAGE})["data"]
    if not app:
        raise Abort(f"App {PACKAGE} não encontrado no App Store Connect.")
    app_id = app[0]["id"]

    versions = asc_get(f"/apps/{app_id}/appStoreVersions", token, **{"filter[platform]": "IOS", "limit": 50})["data"]
    seen = [(v["attributes"]["versionString"], v["attributes"]["appStoreState"]) for v in versions]
    log(f"  versões na loja: {seen}")
    action = decide_ios_version(seen, version)
    log(f"  decisão: {'reaproveitar a versão em PREPARE_FOR_SUBMISSION' if action == 'reuse' else 'criar a versão'}")

    build_id = wait_ios_build(token, app_id, version, build)

    if simular:
        log("  [simular] nada criado nem enviado na Apple. Faria: "
            f"{'usar' if action == 'reuse' else 'criar'} a versão {version} (AFTER_APPROVAL), gravar whatsNew ({LOCALE}), "
            f"anexar o build {build}, criar a submissão e enviar.")
        return

    if action == "reuse":
        version_id = next(v["id"] for v in versions if v["attributes"]["versionString"] == version)
    else:
        version_id = http("POST", f"{ASC_API}/appStoreVersions", token, body={"data": {
            "type": "appStoreVersions",
            "attributes": {"versionString": version, "platform": "IOS", "releaseType": "AFTER_APPROVAL"},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]["id"]
        log(f"  versão criada: {version_id}")

    locs = asc_get(f"/appStoreVersions/{version_id}/appStoreVersionLocalizations", token)["data"]
    loc = next((l for l in locs if l["attributes"]["locale"] == LOCALE), None)
    if loc:
        http("PATCH", f"{ASC_API}/appStoreVersionLocalizations/{loc['id']}", token, body={"data": {
            "type": "appStoreVersionLocalizations", "id": loc["id"], "attributes": {"whatsNew": notes}}})
    else:
        http("POST", f"{ASC_API}/appStoreVersionLocalizations", token, body={"data": {
            "type": "appStoreVersionLocalizations", "attributes": {"locale": LOCALE, "whatsNew": notes},
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version_id}}}}})
    log("  novidades gravadas")

    http("PATCH", f"{ASC_API}/appStoreVersions/{version_id}/relationships/build", token,
         body={"data": {"type": "builds", "id": build_id}})
    log("  build anexado")

    subs = asc_get(f"/apps/{app_id}/reviewSubmissions", token,
                   **{"filter[platform]": "IOS", "filter[state]": "READY_FOR_REVIEW"})["data"]
    if subs:
        sub_id = subs[0]["id"]
    else:
        sub_id = http("POST", f"{ASC_API}/reviewSubmissions", token, body={"data": {
            "type": "reviewSubmissions", "attributes": {"platform": "IOS"},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]["id"]
    items = asc_get(f"/reviewSubmissions/{sub_id}/items", token)["data"]
    if not items:
        http("POST", f"{ASC_API}/reviewSubmissionItems", token, body={"data": {
            "type": "reviewSubmissionItems", "relationships": {
                "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": sub_id}},
                "appStoreVersion": {"data": {"type": "appStoreVersions", "id": version_id}}}}})
    http("PATCH", f"{ASC_API}/reviewSubmissions/{sub_id}", token, body={"data": {
        "type": "reviewSubmissions", "id": sub_id, "attributes": {"submitted": True}}})
    log("  enviado à revisão (liberação automática após aprovação)")


def wait_ios_build(token, app_id, version, build, timeout=1800):
    deadline = time.time() + timeout
    while True:
        found = asc_get("/builds", token, **{
            "filter[app]": app_id, "filter[version]": str(build),
            "filter[preReleaseVersion.version]": version, "limit": 5})["data"]
        if found:
            state = found[0]["attributes"]["processingState"]
            if state == "VALID":
                log(f"  build {build} processado (VALID)")
                if found[0]["attributes"].get("usesNonExemptEncryption") is None and not DRY[0]:
                    http("PATCH", f"{ASC_API}/builds/{found[0]['id']}", token, body={"data": {
                        "type": "builds", "id": found[0]["id"], "attributes": {"usesNonExemptEncryption": False}}})
                    log("  criptografia declarada como isenta")
                return found[0]["id"]
            if state in ("FAILED", "INVALID"):
                raise Abort(f"O build {build} da Apple terminou em {state}.")
            log(f"  build {build}: {state}, aguardando")
        else:
            log(f"  build {build} (versão {version}) ainda não apareceu no App Store Connect")
        if time.time() > deadline:
            raise Abort(f"O build {build} não ficou VALID em {timeout // 60} min.")
        time.sleep(30)


DRY = [False]


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--version", required=True)
    p.add_argument("--build", required=True, type=int)
    p.add_argument("--lojas", choices=["android", "ios", "ambas"], default="ambas")
    p.add_argument("--notas", required=True)
    p.add_argument("--simular", action="store_true")
    args = p.parse_args(argv)
    DRY[0] = args.simular
    try:
        notes = validate_notes(args.notas)
        failures = []
        for loja, fn in (("android", play_promote), ("ios", ios_release)):
            if args.lojas in (loja, "ambas"):
                try:
                    fn(args.version, args.build, notes, args.simular)
                except Abort as err:
                    log(f"ERRO ({loja}): {err}")
                    failures.append(loja)
        if failures:
            return 1
        log("Pronto." + (" (simulação: nada foi publicado)" if args.simular else ""))
        return 0
    except Abort as err:
        log(f"ERRO: {err}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
