# Contrato final — carteira e saques (F1/T11)

Data de fixação: 2026-09-13. Base: `384b5894`.

## Pressupostos e fontes remotas

- Todos os valores do contrato Saqz são inteiros em centavos de BRL. A conversão para o decimal
  exigido pelo Asaas ocorre somente no adaptador, com escala 2 e sem arredondamento silencioso.
- A credencial Asaas da subconta é lida do armazenamento cifrado do servidor; o cliente nunca a vê.
- `GET /v3/finance/balance` fornece o saldo remoto e
  `GET /v3/finance/payment/statistics?status=PENDING` fornece os recebíveis ainda pendentes. São
  números distintos e permanecem distintos no JSON.
- `GET /v3/financialTransactions` é paginado por `offset`/`limit` (máximo remoto 100). O cursor Saqz
  é opaco, vinculado à conta e carrega somente o próximo offset; PII ou credenciais não aparecem nele.
- `POST /v3/transfers` aceita `externalReference`; o Saqz usa o UUID estável da operação. Após timeout,
  a recuperação consulta `GET /v3/transfers?externalReference=<operationId>` e não repete o POST.
  Ausência/ambiguidade conserva `UNKNOWN` e responde `RESULT_PENDING`.
- Fontes oficiais verificadas: [saldo](https://docs.asaas.com/reference/retrieve-account-balance),
  [estatísticas](https://docs.asaas.com/reference/billing-statistics),
  [extrato](https://docs.asaas.com/reference/retrieve-extract),
  [criar transferência](https://docs.asaas.com/reference/transfer-to-another-institution-or-pix-key)
  e [listar transferências](https://docs.asaas.com/reference/list-transfers).

## Autorização comum

- Todas as rotas exigem o Bearer normal e resolvem o ator no servidor.
- Leituras aceitam titular ou delegado não revogado que ainda seja administrador do titular. A
  autorização é recalculada em toda chamada; plano/grupo expirado não bloqueia dinheiro existente.
- Cadastro de destino e saque exigem um Bearer reemitido após reautenticação. A camada central valida
  assinatura/revogação e propaga o `auth_time` verificado; a carteira aceita no máximo 5 minutos
  (com 30 segundos de tolerância futura). Um booleano/data enviados pelo cliente nunca bastam.
- Saque requer `explicitlyAuthorized: true`. Não há rota administrativa de saque.
- Falha de autorização/isolamento retorna `404` para não revelar outra conta; autenticação recente
  ausente/inválida retorna `403` com `RECENT_AUTHENTICATION_REQUIRED`.

## HTTP e JSON

### Resumo

`GET /api/receivables/accounts/{accountId}/wallet`

```json
{"value":{"accountId":"uuid","availableBalanceCents":12345,"pendingReceivablesCents":6789,"refreshedAt":"2026-09-13T12:00:00Z"},"requestId":"uuid"}
```

`GET /api/receivables/accounts/{accountId}/wallet/statement?limit=20&cursor=<opaco>`

```json
{"value":{"items":[{"id":"ftn_x","kind":"PAYMENT_RECEIVED","amountCents":1000,"balanceCents":5000,"occurredOn":"2026-09-12","description":"Recebimento"}],"nextCursor":"opaco-ou-null"},"requestId":"uuid"}
```

`GET /api/receivables/accounts/{accountId}/bank-destinations`

```json
{"value":[{"id":"uuid","bankCode":"001","accountType":"CHECKING","ownerName":"M*** S***","cpfCnpjSuffix":"1234","agencySuffix":"1234","accountSuffix":"9876","verified":false}],"requestId":"uuid"}
```

`POST /api/receivables/accounts/{accountId}/bank-destinations`

```json
{"requestId":"uuid","bankCode":"001","accountType":"CHECKING","ownerName":"Maria Silva","cpfCnpj":"12345678901","agency":"1234","account":"98765","accountDigit":"0"}
```

Resposta 200 repete a visão mascarada do destino. Reutilizar o mesmo `requestId` e conteúdo devolve
o mesmo destino; conteúdo/ator diferente retorna 409. Os dados completos ficam cifrados e nunca são
logados. O CPF/CNPJ precisa ser o mesmo da conta financeira; destino de terceiro é rejeitado.

`GET /api/receivables/accounts/{accountId}/bank-destinations/by-request/{requestId}` recupera o
resultado persistido de uma criação cujo retorno tenha sido perdido, sem repetir a escrita.

`POST /api/receivables/accounts/{accountId}/withdrawals`

```json
{"requestId":"uuid","destinationId":"uuid","amountCents":12345,"explicitlyAuthorized":true}
```

```json
{"value":{"id":"uuid","requestId":"uuid","destinationId":"uuid","amountCents":12345,"feeCents":0,"status":"PROCESSING","providerTransferId":"uuid-ou-null"},"requestId":"uuid"}
```

O Saqz não supõe taxa: `feeCents` começa em zero e só muda por fato remoto observado. Repetição do
mesmo `requestId` nunca cria outro saque; conteúdo/ator divergente retorna 409. Saldo insuficiente
retorna 422, restrição remota 409, e timeout/resultado incerto retorna 202 com `RESULT_PENDING`.

`GET /api/receivables/accounts/{accountId}/withdrawals/by-request/{requestId}` é a recuperação primária
do cliente: localiza a operação persistida antes do I/O e devolve a mesma representação. Em estado
`REQUESTED`, executa a primeira tentativa; em `UNKNOWN`, consulta o provedor antes de qualquer replay.
`GET /api/receivables/accounts/{accountId}/withdrawals/{withdrawalId}` oferece a mesma recuperação por
identificador do recurso para manutenção autenticada.

## Critérios de aceite e ownership

- AC-W1: saldo disponível e recebíveis pendentes vêm de endpoints remotos distintos e convertem
  valores exatos em centavos; resposta sem cache e sem credenciais/PII.
- AC-W2: extrato respeita `1..100`, cursor opaco vinculado à conta e nunca permite ler outra conta.
- AC-W3: destino completo é cifrado, retorno é mascarado, identidade legal precisa coincidir e
  requestId é idempotente.
- AC-W4: titular/delegado atual acessam somente a conta autorizada; revogação/remoção tem efeito na
  chamada seguinte. Corte de plano/grupo não bloqueia leitura nem saque de dinheiro existente.
- AC-W5: autenticação recente é validada pelo servidor e saque exige autorização explícita.
- AC-W6: saque é persistido e reservado sob lock antes do I/O; concorrência não supera o saldo
  observado e requestId igual nunca duplica.
- AC-W7: timeout entra em `UNKNOWN`; recuperação por `externalReference` não refaz POST. Restrição,
  saldo insuficiente e estados Asaas são mapeados sem inventar sucesso.
- AC-W8: não existe endpoint de saque administrativo nem de reembolso.

Arquivos próprios: novos `Wallet*`, `Bank*`, `Withdrawal*` em `backend/features/receivables` (main,
test e integrationTest), `HttpAsaasWallet`, `JdbcWalletStore`, `WalletController`, migrações V56/V59,
`backend/bootstrap/.../ReceivablesWalletConfiguration.kt`, este contrato e
`docs/receivables/evidence/final-wallet-author.md`. Nenhum arquivo reservado às outras frentes será
editado.
