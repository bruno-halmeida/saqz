#!/usr/bin/env python3
"""Paga no sandbox do Asaas o Pix copia e cola que o app mostrou ao atleta (teste do recebimento v1, DEB-138).

Uso: python3 -I tests/sandbox-asaas/pagar-pix.py '<copia e cola>' [valor em reais]

No sandbox nenhum app de banco paga Pix. Quem paga aqui é a conta principal do sandbox, com a chave em
~/.saqz-multidev/asaas-sandbox.key (gravada por `configurar-dev.sh --copiar-chave`). O valor sai do próprio código
(campo 54 do copia e cola); passe o valor só se o código não trouxer. Só chama api-sandbox.asaas.com, só com chave de
homologação, e não imprime chave.
"""
import json
import os
import sys
import urllib.error
import urllib.request

BASE = "https://api-sandbox.asaas.com/v3"
KEY_FILE = os.path.expanduser("~/.saqz-multidev/asaas-sandbox.key")


def fields(payload):
    """Campos de primeiro nível do código Pix (EMV: id de 2 dígitos, tamanho de 2 dígitos, valor)."""
    found, i = {}, 0
    while i + 4 <= len(payload):
        tag, size = payload[i:i + 2], int(payload[i + 2:i + 4])
        found[tag] = payload[i + 4:i + 4 + size]
        i += 4 + size
    return found


def main():
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    payload = sys.argv[1].strip()
    key = open(KEY_FILE).read().strip()
    if not key.startswith("$aact_hmlg_"):
        sys.exit("A chave em %s não é de sandbox. Parei sem chamar nada." % KEY_FILE)
    data = fields(payload)
    if not payload.startswith("000201") or "63" not in data:
        sys.exit("Isso não parece um Pix copia e cola.")
    value = sys.argv[2].replace(",", ".") if len(sys.argv) == 3 else data.get("54")
    if not value:
        sys.exit("O código não traz valor. Passe o valor: pagar-pix.py '<copia e cola>' 20,00")
    print("Recebedor: %s · valor: R$ %s" % (data.get("59", "?"), value.replace(".", ",")))
    request = urllib.request.Request(BASE + "/pix/qrCodes/pay", method="POST",
                                     data=json.dumps({"qrCode": {"payload": payload}, "value": float(value)}).encode(),
                                     headers={"access_token": key, "Content-Type": "application/json",
                                              "User-Agent": "saqz-pagar-pix"})
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            status, body = response.status, json.loads(response.read() or b"{}")
    except urllib.error.HTTPError as error:
        status, body = error.code, json.loads(error.read() or b"{}")
    if status >= 400:
        sys.exit("Asaas recusou (%s): %s" % (status, json.dumps(body.get("errors", body), ensure_ascii=False)))
    print("Pagamento enviado ao sandbox (%s): transação %s, situação %s." % (status, body.get("id"), body.get("status")))
    if body.get("status") == "AWAITING_CRITICAL_ACTION_AUTHORIZATION":
        # A conta principal do sandbox exige token de ação crítica e não há rota de API para autorizar (testado em
        # 28/10/2026). Uma subconta pagadora com saldo não serve: o crédito Pix confirmado no sandbox só cai em D+3.
        print("Falta autorizar: entre no painel do sandbox (https://sandbox.asaas.com), conta Saqz, abra a transação Pix "
              "que aguarda autorização e confirme com o token 000000 (token fixo do sandbox).")
    print("Depois do pagamento, o aviso do Asaas chega ao backend de dev pelo webhook e a cobrança vira paga no app.")


if __name__ == "__main__":
    main()
