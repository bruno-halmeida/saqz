# Contrato final — recorrência mensal e renovação Pix

**Base:** `384b5894`
**Frente:** B / F3 / F4
**Estado:** contrato de implementação fixado antes do código

## Limites e pressupostos

- Valores monetários são inteiros em centavos de BRL. Nenhuma rota aceita decimal.
- Toda mutação exige `requestId` UUID estável e usa o usuário autenticado como ator; o cliente não informa `actorUserId`.
- Só o próprio membro/pagador aceita, retoma ou renova. Titular, delegado, administrador e operação não aceitam em seu nome.
- Uma recorrência ativa/autorizando/encerrando por `(groupId, memberUserId)` e uma ordem por competência mensal.
- A revisão fixa conta, grupo, membro, meio, valor-base, tarifa, total, termos e primeiro vencimento em um `fingerprint` SHA-256. Aprovação divergente retorna conflito.
- Pix recorrente significa assinatura mensal que gera cobranças Pix para pagamento manual; não usa nem promete Pix Automático.
- Cartão é coletado apenas pelo Checkout hospedado do Asaas (`CREDIT_CARD` + `RECURRENT`). O Saqz não recebe PAN, CVV, validade ou token de cartão.
- Callback/retorno de navegador nunca ativa recorrência nem confirma pagamento. A criação do checkout deixa `AUTHORIZING`; recuperação autenticada associa a subscription.
- Corte por pedido, perda de elegibilidade, desativação do grupo ou saída do membro é assíncrono. Enquanto o resultado remoto for incerto, o estado é `STOP_PENDING` e novas competências locais são bloqueadas. A assinatura é comprovadamente inativada antes da limpeza, fechando a janela de novas emissões; uma listagem final precisa provar que não restou futura aberta.
- A remoção de subscription no Asaas pode remover cobranças pendentes ou vencidas da própria subscription. Para cumprir a preservação de vencidas, o corte não usa remoção em lote até separar as obrigações vencidas; cancela somente cobranças futuras antecipadas e inativa a subscription por atualização quando suportado. Falha ou ambiguidade não é convertida em sucesso.
- Retomada cria uma nova recorrência e uma nova aceitação `RECURRENCE`; a anterior permanece `STOPPED` para auditoria.
- Renovação Pix atualiza a cobrança Asaas já vinculada ao instrumento vencido (`PUT /payments/{id}`), preservando order, instrument, quote, acceptance, base/fees/total/split e `provider_payment_id`. Nunca cria uma dívida, ordem, instrumento ou payment novo.
- Conciliação de reversão consulta o extrato Asaas e lança custo residual somente para débito `REFUND_REQUEST_FEE` com `paymentId` exatamente igual ao pagamento conciliado. Principal revertido, tarifa estimada, item ambíguo ou tipo desconhecido não vira custo. Isso não solicita nem executa reembolso.

## Envelope comum

Sucesso:

```json
{"value": {}, "requestId": "8fcd89da-1480-4d11-9ed9-0f0327d95ae5"}
```

Falha:

```json
{"error": "CONFLICT", "requestId": "8fcd89da-1480-4d11-9ed9-0f0327d95ae5"}
```

Mapeamento HTTP: `400 INVALID_INPUT`; `404 NOT_FOUND/UNAUTHORIZED`; `409 CONFLICT`; `202 RESULT_PENDING`; `403 INELIGIBLE_PLAN/REGISTRATION_RESTRICTED/OPERATIONS_DISABLED`; `503 CONFIGURATION_UNAVAILABLE/PROVIDER_UNAVAILABLE`. Todas as respostas usam `Cache-Control: no-store`.

## 1. Revisar recorrência

`POST /api/receivables/recurrences/preview`

```json
{
  "requestId": "8fcd89da-1480-4d11-9ed9-0f0327d95ae5",
  "accountId": "bc2f4649-76d4-4655-8046-f7b49a531be9",
  "groupId": "ce89eb8c-0446-4357-b73d-9ca51dd286d7",
  "method": "PIX",
  "firstDueDate": "2026-10-10"
}
```

`200`:

```json
{
  "value": {
    "accountId": "bc2f4649-76d4-4655-8046-f7b49a531be9",
    "groupId": "ce89eb8c-0446-4357-b73d-9ca51dd286d7",
    "memberUserId": "d83869cb-7d88-443e-baa8-b592def5f353",
    "method": "PIX",
    "baseCents": 10000,
    "feesCents": 490,
    "totalCents": 10490,
    "commissionCents": 300,
    "expectedProviderFeeCents": 190,
    "expectedNetCents": 10000,
    "feeScheduleId": "a369bb09-c4af-47bb-8448-9cb9f88510d7",
    "termsVersion": "receivables-2026-09",
    "firstDueDate": "2026-10-10",
    "cycle": "MONTHLY",
    "fingerprint": "64-lowercase-hex"
  },
  "requestId": "8fcd89da-1480-4d11-9ed9-0f0327d95ae5"
}
```

O valor-base e o dia de vencimento vêm da mensalidade efetiva do membro no grupo, nunca do cliente. Preview exige conta aprovada, link financeiro ativo, meio habilitado, webhook pronto, rollout e elegibilidade atuais, e o ator deve ser membro ativo.

## 2. Autorizar recorrência

`POST /api/receivables/recurrences`

```json
{
  "requestId": "8fcd89da-1480-4d11-9ed9-0f0327d95ae5",
  "accountId": "bc2f4649-76d4-4655-8046-f7b49a531be9",
  "groupId": "ce89eb8c-0446-4357-b73d-9ca51dd286d7",
  "method": "PIX",
  "firstDueDate": "2026-10-10",
  "fingerprint": "64-lowercase-hex",
  "accepted": true,
  "payer": {"name": "Maria Silva", "cpfCnpj": "12345678901"}
}
```

`200` (Pix ativo) ou `202` enquanto remoto incerto:

```json
{
  "value": {
    "id": "b31d64e8-155d-4b59-bfd4-bc8f891d595f",
    "accountId": "bc2f4649-76d4-4655-8046-f7b49a531be9",
    "groupId": "ce89eb8c-0446-4357-b73d-9ca51dd286d7",
    "memberUserId": "d83869cb-7d88-443e-baa8-b592def5f353",
    "method": "PIX",
    "baseCents": 10000,
    "feesCents": 490,
    "totalCents": 10490,
    "firstDueDate": "2026-10-10",
    "status": "ACTIVE",
    "providerSubscriptionId": "sub_123",
    "hostedCheckoutUrl": null,
    "cutoffAt": null
  },
  "requestId": "8fcd89da-1480-4d11-9ed9-0f0327d95ae5"
}
```

Para `CARD`, a resposta normal inicial é `AUTHORIZING`, contém apenas URL HTTPS do domínio Asaas em `hostedCheckoutUrl` e nunca contém dados de cartão. Repetir o mesmo `requestId` e payload retorna o mesmo recurso; mesmo `requestId` com payload/ator diferente retorna `409`.

## 3. Consultar recorrência própria

`GET /api/receivables/recurrences/{recurrenceId}` → `200` com o mesmo objeto acima. Outro usuário recebe `404`. A leitura pode acionar recuperação pontual, mas não confirma por callback.

### 3.1 Descobrir a recorrência do pagador

`GET /api/receivables/recurrences/current?accountId={uuid}&groupId={uuid}`

Retorna `200` e o mesmo objeto de recorrência em `value`. A busca é sempre limitada ao usuário autenticado: prioriza a recorrência viva (`AUTHORIZING`, `ACTIVE` ou `STOP_PENDING`) e, se não houver, retorna a `STOPPED` mais recente. Quando o pagador nunca teve recorrência nesse par conta/grupo, a resposta não revela dados de terceiros:

```json
{"value":null,"requestId":"uuid-gerado-para-a-leitura"}
```

### 3.2 Recuperar autorização/retomada pelo request original

`GET /api/receivables/recurrences/by-request/{requestId}`

Retorna `200` com a recorrência criada pelo `POST` original, ativa ou parada, e usa o próprio `{requestId}` no envelope. Somente o mesmo pagador autenticado pode encontrá-la; outro ator ou request inexistente recebe `404`. A leitura não cria recorrência, aceite, cobrança ou dívida e é o caminho de recuperação quando o cliente perdeu a resposta do authorize/resume.

## 4. Cancelar/cortar

`POST /api/receivables/recurrences/{recurrenceId}/cancel`

```json
{"requestId":"4e5a8702-bb6e-44af-b272-76756597d09a"}
```

`200 STOPPED` quando comprovado; `202 STOP_PENDING` se houver timeout, cobrança remota que precise de conciliação ou outra incerteza. O corte define `cutoffAt`, bloqueia novas competências imediatamente, preserva ordens/instrumentos avulsos e recorrentes já vencidos, e cancela apenas cobranças futuras antecipadas. O job aplica o mesmo corte ao detectar perda de elegibilidade, grupo desativado/excluído ou membro inativo.

## 5. Retomar com novo aceite

`POST /api/receivables/recurrences/{stoppedRecurrenceId}/resume`

Corpo idêntico ao de autorização, inclusive novo `requestId`, `fingerprint`, `accepted=true` e pagador. A rota só aceita recorrência `STOPPED`, revalida todos os requisitos atuais e cria outro `id`/aceitação; nunca reativa a linha anterior.

## 6. Renovar Pix vencido da obrigação existente

`POST /api/receivables/orders/{orderId}/pix-renewal`

```json
{
  "requestId": "0a9efedd-0594-4fb7-8b04-b6bce14a10df",
  "dueDate": "2026-09-20"
}
```

`200`:

```json
{
  "value": {
    "orderId": "3ebcf00d-3891-48f6-86fa-ad35ed9b7fbb",
    "instrumentId": "da53ec12-3e04-40b8-8d1f-0c419c1b4371",
    "providerPaymentId": "pay_123",
    "method": "PIX",
    "status": "ACTIVE",
    "baseCents": 10000,
    "feesCents": 490,
    "totalCents": 10490,
    "dueDate": "2026-09-20",
    "pixPayload": "000201...",
    "pixImage": "base64-provider-value",
    "expiresAt": "2026-09-20T23:59:59Z"
  },
  "requestId": "0a9efedd-0594-4fb7-8b04-b6bce14a10df"
}
```

Só o pagador da ordem pode renovar; a ordem precisa continuar pendente, o instrumento precisa ser Pix vencido e possuir `providerPaymentId`. É permitido após o corte porque mantém obrigação existente. Mesmo request/payload é idempotente. Timeout retorna `202 RESULT_PENDING`; reprocessamento consulta o mesmo payment antes de repetir `PUT` e nunca chama `POST /payments`.

Recuperação pelo request original: `GET /api/receivables/orders/{orderId}/pix-renewal/{requestId}`. Retorna o mesmo `200` acima depois da prova remota; enquanto a operação estiver `READY`, `RUNNING` ou `UNKNOWN`, retorna `202` com `{"error":"RESULT_PENDING","requestId":"..."}` e sem `value`; request inexistente, ordem divergente ou outro ator recebe `404`. O GET não executa uma nova atualização nem cria recurso.

## Critérios de aceite rastreáveis

- **AC-R01:** revisão e aceite fixam termos, meio e todos os valores em centavos; adulteração dá conflito.
- **AC-R02:** ator é sempre o pagador autenticado e somente membro ativo aceita; permissões de titular/admin/delegado não substituem consentimento.
- **AC-R03:** no máximo uma recorrência viva e uma ordem por competência, inclusive sob concorrência/retry.
- **AC-R04:** Pix mensal usa subscription comum, não Pix Automático; cartão usa checkout hospedado sem PAN/CVV/token no Saqz.
- **AC-R05:** timeout de create/checkout/cutoff fica recuperável por referência externa estável; não há recriação cega.
- **AC-R06:** corte bloqueia imediatamente novos ciclos, cancela futuros antecipados, preserva vencidas/avulsas e só conclui após prova remota.
- **AC-R07:** perda de plano, link/grupo desabilitado, grupo excluído ou membro inativo aciona o mesmo corte pelo job real.
- **AC-R08:** retomada exige novo fingerprint/aceite/request e cria nova recorrência; a anterior permanece auditável.
- **AC-P01:** renovação Pix mantém IDs e snapshots financeiros/aceite, funciona após corte e muda apenas vencimento/artefatos Pix.
- **AC-P02:** renovação concorrente/idempotente não duplica operação; timeout é `RESULT_PENDING` e recovery consulta antes de novo update.
- **AC-P03:** nenhuma resposta, URL ou log inclui credencial, PAN, CVV ou PII além dos campos explícitos do contrato.
- **AC-M01:** o mobile recupera a recorrência corrente, authorize/resume e renovação Pix apenas com os identificadores persistidos, sem repetir POST nem recriar dívida.
- **AC-C01:** custo de reversão é o centavo debitado e identificado no extrato remoto, registrado uma única vez depois do estado terminal correlacionado; ausência/ambiguidade não gera lançamento.

## Fontes oficiais verificadas em 2026-09-13

- Asaas, “Criar nova assinatura”: `POST /v3/subscriptions`, `cycle=MONTHLY`, `PIX`/cartão, `externalReference` e ciclo independente das cobranças: https://docs.asaas.com/reference/create-new-subscription
- Asaas, “Checkout com Assinatura (recorrente)”: `CREDIT_CARD`, `RECURRENT`, checkout hospedado, criação assíncrona e callback sem valor confirmatório: https://docs.asaas.com/docs/checkout-com-assinatura-recorrente
- Asaas, “Atualizar cobrança existente”: `PUT /v3/payments/{id}` é permitido em cobrança aguardando pagamento ou vencida e aceita `dueDate`: https://docs.asaas.com/reference/update-existing-payment
- Asaas, “Remover assinatura”: remoção para novos ciclos também remove pendentes/vencidas, razão pela qual o corte precisa preservar obrigações antes de usar esse endpoint: https://docs.asaas.com/reference/remove-subscription
- Asaas, “Atualizar assinatura existente”: `PUT /v3/subscriptions/{id}` aceita `INACTIVE`, interrompe novas cobranças e mantém todas as cobranças existentes inalteradas: https://docs.asaas.com/reference/atualizar-assinatura-existente
- Asaas, “Listar cobranças de uma assinatura”: apenas cobranças já geradas aparecem e cada uma tem ciclo próprio: https://docs.asaas.com/reference/listar-cobrancas-de-uma-assinatura
- Asaas, “Recuperar extrato”: `GET /v3/financialTransactions`, paginação máxima 100, `paymentId`, `type`, `value`, e tipos `REFUND_REQUEST_FEE`/`CHARGEBACK`: https://docs.asaas.com/reference/recuperar-extrato
