#!/usr/bin/env python3
"""DEB-138: perguntas do recebimento v1 respondidas no sandbox do Asaas.

Uso: python3 -I tests/sandbox-asaas/deb138.py <etapa>   (etapas: conta, saldo, subconta, chave, qr, divisao, emitida, cartao, pagina, tudo)

A chave da conta principal do sandbox vem de ~/.saqz-multidev/asaas-sandbox.key (uma linha).
Só roda contra api-sandbox.asaas.com e só com chave de homologação ($aact_hmlg_...).
Nenhuma chave é impressa nem gravada no relatório; a da subconta fica em secrets/ (600).
"""
import datetime
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request

BASE = "https://api-sandbox.asaas.com/v3"
HERE = os.path.dirname(os.path.abspath(__file__))
STATE = os.path.join(HERE, "state.json")
SECRETS = os.path.join(HERE, "secrets")
LOG = os.path.join(HERE, "out", "log.jsonl")
KEY_FILE = os.path.expanduser("~/.saqz-multidev/asaas-sandbox.key")
REDACT = {"apiKey", "accessToken", "access_token", "number", "ccv", "creditCardToken"}


def master_key():
    key = open(KEY_FILE).read().strip()
    if not key.startswith("$aact_hmlg_"):
        sys.exit("A chave em ~/.saqz-multidev/asaas-sandbox.key não é de sandbox ($aact_hmlg_...). Parei sem chamar nada.")
    return key


def sub_key():
    return json.load(open(os.path.join(SECRETS, "sub.json")))["apiKey"]


def redact(node):
    if isinstance(node, dict):
        return {k: ("<omitido>" if k in REDACT else redact(v)) for k, v in node.items()}
    if isinstance(node, list):
        return [redact(v) for v in node]
    return node


def call(key, method, path, body=None, label=""):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method, headers={
        "access_token": key, "Content-Type": "application/json", "User-Agent": "saqz-deb138"})
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            status, raw = resp.status, resp.read()
    except urllib.error.HTTPError as err:
        status, raw = err.code, err.read()
    try:
        payload = json.loads(raw) if raw else {}
    except ValueError:
        payload = {"raw": raw[:500].decode(errors="replace")}
    os.makedirs(os.path.dirname(LOG), exist_ok=True)
    with open(LOG, "a") as log:
        log.write(json.dumps({"at": datetime.datetime.now().isoformat(timespec="seconds"), "label": label,
                              "method": method, "path": path, "body": redact(body), "status": status,
                              "response": redact(payload)}, ensure_ascii=False) + "\n")
    print(f"[{status}] {label or method + ' ' + path}")
    if status >= 400:
        print("      ", json.dumps(redact(payload), ensure_ascii=False)[:600])
    return status, payload


def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}


def save(state):
    json.dump(state, open(STATE, "w"), indent=2, ensure_ascii=False)


def cpf():
    digits = [random.randint(0, 9) for _ in range(9)]
    for size in (9, 10):
        total = sum(d * w for d, w in zip(digits, range(size + 1, 1, -1)))
        digits.append((total * 10 % 11) % 10)
    return "".join(map(str, digits))


def today(days=0):
    return (datetime.date.today() + datetime.timedelta(days=days)).isoformat()


def note(state, key, value):
    state.setdefault("achados", {})[key] = value
    save(state)
    print("   =>", key, ":", json.dumps(value, ensure_ascii=False)[:400])


# --- etapas -----------------------------------------------------------------

def conta(state):
    key = master_key()
    _, wallets = call(key, "GET", "/wallets", label="carteira da conta principal")
    state["masterWallet"] = wallets["data"][0]["id"]
    _, status = call(key, "GET", "/myAccount/status", label="situação da conta principal")
    note(state, "conta_principal", {k: status.get(k) for k in ("general", "commercialInfo", "bankAccountInfo", "documentation")})
    _, keys = call(key, "GET", "/pix/addressKeys?status=ACTIVE", label="chaves Pix da conta principal")
    note(state, "chaves_pix_principal", [{"type": k.get("type"), "status": k.get("status")} for k in keys.get("data", [])])


def saldo(state, minimum=300):
    key = master_key()
    _, bal = call(key, "GET", "/finance/balance", label="saldo da conta principal")
    if bal.get("balance", 0) >= minimum:
        return note(state, "saldo_principal", bal.get("balance"))
    _, cust = call(key, "POST", "/customers", {"name": "Saldo DEB-138", "cpfCnpj": cpf(), "notificationDisabled": True},
                   label="cliente para gerar saldo")
    _, pay = call(key, "POST", "/payments", {"customer": cust["id"], "billingType": "PIX", "value": 1000.00,
                                              "dueDate": today(), "description": "Saldo de teste DEB-138"},
                  label="cobrança Pix de saldo")
    call(key, "POST", f"/sandbox/payment/{pay['id']}/confirm", {}, label="confirmar cobrança de saldo (sandbox)")
    time.sleep(5)
    _, bal = call(key, "GET", "/finance/balance", label="saldo depois da confirmação")
    note(state, "saldo_principal", bal.get("balance"))


def subconta(state):
    if os.path.exists(os.path.join(SECRETS, "sub.json")):
        return print("subconta já criada:", state.get("subAccountId"))
    key = master_key()
    stamp = datetime.datetime.now().strftime("%m%d%H%M%S")
    # Nome sem número nem caractere especial: no sandbox isso deixa o Pix da subconta desligado mesmo com a conta
    # aprovada ("A sua conta ainda não está totalmente aprovada para utilizar o Pix").
    letters = "".join("abcdefghij"[int(c)] for c in stamp)
    body = {"name": f"Gestor Teste {letters.capitalize()}", "email": f"sandbox+deb138-{stamp}@saqz.app", "cpfCnpj": cpf(),
            "birthDate": "1990-05-20", "mobilePhone": "11981234567", "incomeValue": 5000.00,
            "address": "Avenida Paulista", "addressNumber": "1000", "province": "Bela Vista", "postalCode": "01310100"}
    status, acc = call(key, "POST", "/accounts", body, label="criar subconta do gestor")
    if status >= 400:
        return note(state, "subconta", {"erro": acc})
    os.makedirs(SECRETS, exist_ok=True)
    path = os.path.join(SECRETS, "sub.json")
    with open(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as f:
        json.dump({"apiKey": acc["apiKey"]}, f)
    state.update(subAccountId=acc["id"], subWallet=acc["walletId"])
    save(state)
    time.sleep(15)
    _, st = call(sub_key(), "GET", "/myAccount/status", label="situação da subconta")
    note(state, "subconta", {k: st.get(k) for k in ("general", "commercialInfo", "bankAccountInfo", "documentation")})


def chave(state):
    key = sub_key()
    status, created = call(key, "POST", "/pix/addressKeys", {"type": "EVP"}, label="(6) chave aleatória da subconta pela API")
    note(state, "6_chave_aleatoria_api", {"status": status, "chave": created.get("status"), "erro": created.get("errors")})
    for _ in range(6):
        _, keys = call(key, "GET", "/pix/addressKeys", label="chaves Pix da subconta")
        active = [k for k in keys.get("data", []) if k.get("status") == "ACTIVE"]
        if active:
            state["subPixKey"] = active[0]["key"]
            save(state)
            return note(state, "6_chave_ativa", {"tipo": active[0].get("type"), "status": "ACTIVE"})
        time.sleep(10)
    note(state, "6_chave_ativa", [{"type": k.get("type"), "status": k.get("status")} for k in keys.get("data", [])])


def qr(state):
    key, payer = sub_key(), master_key()
    _, fixed = call(key, "POST", "/pix/qrCodes/static", {
        "addressKey": state.get("subPixKey"), "description": "Jogo 25/10 · Fulano", "value": 15.00, "format": "PAYLOAD",
        "allowsMultiplePayments": False, "expirationSeconds": 3600, "externalReference": "deb138-fixo"},
        label="(1) QR estático com valor R$ 15")
    state["qrFixed"] = fixed.get("id")
    save(state)
    payload = fixed.get("payload")
    s1, r1 = call(payer, "POST", "/pix/qrCodes/pay", {"qrCode": {"payload": payload}, "value": 10.00},
                  label="(1) pagar R$ 10 num QR de R$ 15")
    s2, r2 = call(payer, "POST", "/pix/qrCodes/pay", {"qrCode": {"payload": payload}, "value": 15.00},
                  label="(1) pagar R$ 15 no QR de R$ 15")
    time.sleep(5)
    s3, r3 = call(payer, "POST", "/pix/qrCodes/pay", {"qrCode": {"payload": payload}, "value": 15.00},
                  label="(1) pagar de novo o QR de uso único")
    note(state, "1_valor_diferente", {"status": s1, "resposta": r1.get("errors") or r1.get("status")})
    note(state, "1_valor_certo", {"status": s2, "resposta": r2.get("errors") or r2.get("status")})
    note(state, "1_segundo_pagamento", {"status": s3, "resposta": r3.get("errors") or r3.get("status")})
    for _ in range(12):
        time.sleep(10)
        _, found = call(key, "GET", f"/payments?pixQrCodeId={state['qrFixed']}", label="(1) cobrança criada pelo QR")
        if found.get("data"):
            break
    pays = found.get("data", [])
    if not pays:
        return note(state, "1_cobranca_criada", "nenhuma cobrança apareceu em 2 min")
    pay = pays[0]
    state["qrPayment"] = pay["id"]
    save(state)
    note(state, "1_cobranca_criada", {k: pay.get(k) for k in (
        "status", "billingType", "value", "netValue", "pixQrCodeId", "pixTransaction", "externalReference",
        "description", "customer")})
    note(state, "1_quantas_cobrancas", len(pays))
    _, cust = call(key, "GET", f"/customers/{pay['customer']}", label="(1) cliente criado sozinho")
    note(state, "1_dados_do_pagador", {k: ("preenchido" if cust.get(k) else "vazio") for k in (
        "name", "cpfCnpj", "email", "mobilePhone", "phone", "address", "postalCode")})
    if pay.get("pixTransaction"):
        _, tx = call(key, "GET", f"/pix/transactions/{pay['pixTransaction']}", label="(1) transação Pix")
        note(state, "1_transacao_pix", sorted(tx.keys()))


def divisao(state):
    key = sub_key()
    pay = state.get("qrPayment")
    status, upd = call(key, "PUT", f"/payments/{pay}", {"split": [{"walletId": state["masterWallet"], "fixedValue": 1.00}]},
                       label="(2) pôr divisão numa cobrança de QR já recebida")
    note(state, "2_divisao_depois", {"status": status, "erro": upd.get("errors"), "split": upd.get("split")})
    _, before = call(key, "GET", "/finance/balance", label="(2) saldo da subconta antes")
    status, tr = call(key, "POST", "/transfers", {"value": 1.00, "walletId": state["masterWallet"],
                                                  "description": "Comissão Saqz · DEB-138"},
                      label="(2) transferir a comissão para a conta do Saqz")
    note(state, "2_transferencia", {"status": status, "erro": tr.get("errors"), "situacao": tr.get("status"),
                                     "taxa": tr.get("transferFee"), "tipo": tr.get("type")})
    time.sleep(5)
    _, after = call(key, "GET", "/finance/balance", label="(2) saldo da subconta depois")
    note(state, "2_saldo_subconta", {"antes": before.get("balance"), "depois": after.get("balance")})


def emitida(state):
    """Plano B do (1)/(2): cobrança Pix emitida para o cliente, com a divisão na criação."""
    key, payer = sub_key(), master_key()
    status, nocpf = call(key, "POST", "/customers", {"name": "Atleta Sem CPF", "notificationDisabled": True},
                         label="cliente sem CPF")
    note(state, "B_cliente_sem_cpf", {"status": status, "erro": nocpf.get("errors")})
    customer = nocpf.get("id")
    if not customer:
        _, cust = call(key, "POST", "/customers", {"name": "Atleta Com CPF", "cpfCnpj": cpf(), "notificationDisabled": True},
                       label="cliente com CPF")
        customer = cust["id"]
    _, pay = call(key, "POST", "/payments", {"customer": customer, "billingType": "PIX", "value": 20.00, "dueDate": today(),
                                              "description": "Jogo 25/10", "externalReference": "deb138-emitida",
                                              "split": [{"walletId": state["masterWallet"], "fixedValue": 1.00}]},
                  label="cobrança Pix emitida com divisão")
    _, code = call(key, "GET", f"/payments/{pay['id']}/pixQrCode", label="código da cobrança emitida")
    s1, r1 = call(payer, "POST", "/pix/qrCodes/pay", {"qrCode": {"payload": code.get("payload")}, "value": 19.00},
                  label="pagar R$ 19 numa cobrança de R$ 20")
    s2, r2 = call(payer, "POST", "/pix/qrCodes/pay", {"qrCode": {"payload": code.get("payload")}, "value": 20.00},
                  label="pagar R$ 20 na cobrança de R$ 20")
    note(state, "B_valor_diferente", {"status": s1, "resposta": r1.get("errors") or r1.get("status")})
    note(state, "B_valor_certo", {"status": s2, "resposta": r2.get("errors") or r2.get("status")})
    for _ in range(12):
        time.sleep(10)
        _, got = call(key, "GET", f"/payments/{pay['id']}", label="cobrança emitida depois do pagamento")
        if got.get("status") in ("RECEIVED", "CONFIRMED"):
            break
    note(state, "B_cobranca_paga", {k: got.get(k) for k in ("status", "value", "netValue", "split")})


def cartao(state):
    key = sub_key()
    holder = {"name": "Atleta Cartao", "email": "sandbox+deb138-atleta@saqz.app", "cpfCnpj": cpf(),
              "postalCode": "01310100", "addressNumber": "1000", "phone": "11981234567"}
    _, cust = call(key, "POST", "/customers", {"name": holder["name"], "cpfCnpj": holder["cpfCnpj"],
                                               "notificationDisabled": True}, label="cliente do cartão")
    state["cardCustomer"] = cust["id"]
    save(state)

    def card(number):
        return {"holderName": "ATLETA CARTAO", "number": number, "expiryMonth": "12", "expiryYear": "2030", "ccv": "123"}

    split = [{"walletId": state["masterWallet"], "fixedValue": 2.00}]
    base = {"customer": cust["id"], "billingType": "CREDIT_CARD", "value": 100.00, "cycle": "MONTHLY",
            "description": "Mensalidade · DEB-138", "creditCardHolderInfo": holder, "remoteIp": "200.200.200.200",
            "split": split}
    _, now = call(key, "POST", "/subscriptions", {**base, "nextDueDate": today(), "creditCard": card("4111111111111111")},
                  label="(3) assinatura no cartão cobrada hoje")
    _, later = call(key, "POST", "/subscriptions", {**base, "nextDueDate": today(5), "creditCard": card("4111111111111111")},
                    label="(3) assinatura no cartão com vencimento em 5 dias")
    state.update(subNow=now.get("id"), subLater=later.get("id"))
    save(state)
    time.sleep(10)
    _, first = call(key, "GET", f"/subscriptions/{now['id']}/payments", label="(3) cobranças da assinatura de hoje")
    note(state, "3_primeira_cobranca", [{k: p.get(k) for k in ("status", "value", "dueDate")} for p in first.get("data", [])])
    status, upd = call(key, "PUT", f"/subscriptions/{now['id']}", {"value": 120.00, "updatePendingPayments": True},
                       label="(3) mudar o valor da assinatura ativa")
    note(state, "3_mudar_valor", {"status": status, "erro": upd.get("errors"), "valor": upd.get("value")})
    _, pend = call(key, "GET", f"/subscriptions/{later['id']}/payments", label="(3) cobranças da assinatura de 5 dias")
    pending = [p for p in pend.get("data", []) if p.get("status") == "PENDING"]
    note(state, "3_cobranca_futura", [{k: p.get(k) for k in ("status", "value", "dueDate")} for p in pend.get("data", [])])
    if pending:
        status, gone = call(key, "DELETE", f"/payments/{pending[0]['id']}", label="(3) cancelar só a cobrança futura")
        _, sub = call(key, "GET", f"/subscriptions/{later['id']}", label="(3) assinatura depois de cancelar a cobrança")
        note(state, "3_cancelar_cobranca", {"status": status, "apagada": gone.get("deleted"), "assinatura": sub.get("status"),
                                            "proximo_vencimento": sub.get("nextDueDate")})
    status, refused = call(key, "POST", "/subscriptions", {**base, "nextDueDate": today(), "creditCard": card("5184019740373151")},
                           label="(4) assinatura com cartão que recusa")
    note(state, "4_recusado_na_criacao", {"status": status, "erro": refused.get("errors"), "assinatura": refused.get("status")})
    if refused.get("id"):
        time.sleep(10)
        _, rp = call(key, "GET", f"/subscriptions/{refused['id']}/payments", label="(4) cobranças da assinatura recusada")
        note(state, "4_cobranca_recusada", [{k: p.get(k) for k in ("status", "value", "dueDate")} for p in rp.get("data", [])])


def pagina(state):
    key = sub_key()
    customer = state.get("cardCustomer")
    _, inv = call(key, "POST", "/payments", {"customer": customer, "billingType": "CREDIT_CARD", "value": 15.00,
                                              "dueDate": today(), "description": "Jogo 25/10 · cartão"},
                  label="(5) cobrança no cartão com página do Asaas")
    _, chk = call(key, "POST", "/checkouts", {"billingTypes": ["CREDIT_CARD"], "chargeTypes": ["RECURRENT"],
                                              "minutesToExpire": 60, "customer": customer,
                                              "callback": {"successUrl": "https://saqz.app/ok", "cancelUrl": "https://saqz.app/cancelado",
                                                           "expiredUrl": "https://saqz.app/expirado"},
                                              "items": [{"name": "Mensalidade", "description": "Mensalidade recorrente",
                                                         "quantity": 1, "value": 100.00}],
                                              "subscription": {"cycle": "MONTHLY", "nextDueDate": today()},
                                              "splits": [{"walletId": state["masterWallet"], "fixedValue": 2.00}]},
                  label="(5) checkout recorrente no cartão")
    note(state, "5_links", {"fatura": inv.get("invoiceUrl"), "checkout": chk.get("link") or chk.get("url"),
                            "campos_checkout": sorted(chk.keys())})


STEPS = {"conta": conta, "saldo": saldo, "subconta": subconta, "chave": chave, "qr": qr, "divisao": divisao,
         "emitida": emitida, "cartao": cartao, "pagina": pagina}

if __name__ == "__main__":
    wanted = sys.argv[1:] or ["tudo"]
    order = list(STEPS) if wanted == ["tudo"] else wanted
    st = load()
    for name in order:
        print(f"\n== {name}")
        STEPS[name](st)
