# Contrato final — operações de recebíveis (F5/F7)

Data de corte: 2026-09-13. Este contrato cobre somente operação administrativa de falhas,
leitura pública de termos já publicados, retorno neutro do checkout e avisos persistentes.
Solicitar, aprovar ou executar saque/reembolso não faz parte desta superfície.

## Pressupostos vinculantes

- O ator administrativo vem exclusivamente de `PlatformAdminLookup`; nenhuma rota aceita ator no JSON.
- `requestId` é obrigatório em mutações, é persistido e identifica uma única intenção. Reuso com operação,
  ação, ator ou motivo diferentes retorna conflito.
- Recuperação consulta primeiro o estado remoto por uma porta somente de leitura. Ausência em uma listagem
  eventualmente consistente e timeout permanecem `UNKNOWN`; nunca disparam criação, saque ou reembolso.
- A fila não devolve credenciais, payload do provedor, dados bancários, CPF/CNPJ, nome, e-mail ou URL de checkout.
- Valores monetários, quando presentes, são inteiros em centavos. Nenhuma taxa é estimada ou inventada.
- Termos públicos são lidos de `receivable_terms`; a aplicação não fornece texto padrão.
- O retorno de navegador é apenas uma tela/representação `PENDING_VERIFICATION`. Pagamento só muda por
  webhook autenticado ou consulta remota conciliada.
- Avisos são persistidos para leitura nas superfícies permitidas; esta entrega não envia e-mail, push ou SMS.

## HTTP/JSON

Todas as respostas abaixo usam `Cache-Control: no-store`.

### `GET /admin/receivables/operations`

Query: `status` opcional (`READY|RUNNING|UNKNOWN|REJECTED`), `kind` opcional, `page` (padrão 1) e
`size` (padrão 25, máximo 100). Retorna página ordenada por `updatedAt DESC, id DESC`:

```json
{
  "items": [{
    "id": "uuid",
    "accountId": "uuid",
    "requestId": "uuid",
    "kind": "ISSUE_ORDER",
    "resourceId": "uuid",
    "status": "UNKNOWN",
    "attempts": 2,
    "failureCode": "PROVIDER_TIMEOUT",
    "createdAt": "2026-09-13T12:00:00Z",
    "updatedAt": "2026-09-13T12:02:00Z",
    "nextAttemptAt": "2026-09-13T12:03:00Z"
  }],
  "page": 1,
  "size": 25,
  "total": 1,
  "hasNext": false
}
```

`200`; `400` para paginação/filtro inválido; `401/403` pela cadeia de autenticação/admin.
Operações `WITHDRAW` e `REFUND` não são recuperáveis por esta API e nunca aparecem como ações habilitadas.

### `GET /admin/receivables/operations/{operationId}`

Retorna o mesmo resumo, `recoverable: boolean` e auditoria append-only com `actorUserId`, `requestId`,
`action`, `reason`, `result`, `createdAt`. `200`, `404`, `401/403`.

### `POST /admin/receivables/operations/{operationId}/recovery`

```json
{ "requestId": "uuid", "reason": "motivo operacional objetivo" }
```

A ação é sempre `RECOVER`: reserva a tentativa uma única vez, audita ator/motivo/resultado e consulta o
provedor antes de qualquer transição. Resposta:

```json
{
  "requestId": "uuid",
  "operationId": "uuid",
  "result": "CONFIRMED|REJECTED|STILL_UNKNOWN|NOT_RECOVERABLE",
  "operationStatus": "SUCCEEDED|REJECTED|UNKNOWN"
}
```

`200` para resultado observado; `400` para corpo/motivo inválido; `404`; `409` para requestId conflitante
ou lease concorrente; `503` com `STILL_UNKNOWN` para timeout/indisponibilidade sem mutação cega.

### `GET /public/receivables/terms/current`

Retorna somente termo já publicado e vigente:

```json
{ "version": "2026-09", "content": "texto publicado", "effectiveAt": "...", "publishedAt": "..." }
```

`200` ou `404`. Nenhuma aceitação é criada.

### `GET /public/receivables/terms/{version}`

Mesmo JSON, somente se a versão estiver publicada e já vigente; `200` ou `404`.

### `GET /public/receivables/checkout-return`

```json
{
  "status": "PENDING_VERIFICATION",
  "message": "O retorno do navegador não confirma o pagamento. Consulte o histórico no Saqz."
}
```

Ignora query string e não ecoa identificadores. Nunca retorna `PAID`/`CONFIRMED`.

### `GET /admin/receivables/notices` e `POST /admin/receivables/notices`

Lista paginada e publicação administrativa de avisos operacionais/planos. A mutação recebe
`requestId`, `audience` (`OPERATIONS|PLAN_OWNERS`), `title`, `message`, `startsAt`, `endsAt` opcional;
persiste autor e conteúdo, sem envio externo. Texto é fornecido pelo administrador, nunca gerado.

### `GET /api/receivables/notices`

Lista avisos ativos `PLAN_OWNERS` do ator autenticado, sem alterar elegibilidade ou executar comunicação.

## Critérios de aceite

1. Não-admin recebe 403 e nenhuma linha de auditoria/reserva é criada.
2. Fila é paginada, determinística e livre de PII/segredos/payload bruto.
3. Recuperações concorrentes para a mesma operação admitem uma reserva; a outra recebe conflito.
4. Repetir o mesmo `requestId` e corpo devolve o resultado gravado; alterar ator/motivo/operação conflita.
5. Timeout/erro de transporte grava resultado `STILL_UNKNOWN`, libera lease e nunca chama criação.
6. `WITHDRAW`/`REFUND` são negados antes da porta remota; o painel não oferece essas ações.
7. Confirmação/rejeição observada é aplicada uma vez e registra ator, motivo e resultado imutáveis.
8. Termos inexistentes/futuros retornam 404; nenhum texto jurídico é inventado.
9. Retorno do checkout, com qualquer query string, permanece pendente e não expõe a query no DOM/JSON.
10. Avisos persistem com autoria e janela temporal; nenhum adaptador de e-mail/push/SMS é chamado.
11. Custo residual externo só é materializado a partir de centavos observados pela porta de conciliação;
    ausência de valor não gera movimento zero/estimado. Integração em `OneOffPayments`/`JdbcPaymentStore`
    depende de patch coordenado da frente B.
12. O runbook separa gates simulados de homologação Asaas real e não atesta credenciais/condições externas.

## Arquivos sob ownership desta frente

- `docs/receivables/final-operations-contract.md`, `final-operations-author.md`, runbook/cenários F7.
- Novos `Operational*`/`Public*` em `backend/features/receivables/src/{main,test,integrationTest}`.
- `OperationalResidualCosts`, `JdbcExternalResidualCostLedger` e testes de centavos/idempotência.
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/ReceivablesOperationsConfiguration.kt`
  e testes HTTP próprios novos no bootstrap.
- `backend/features/receivables/src/main/resources/db/migration/V58__receivables_operations.sql` e
  `V61__receivables_operational_notices.sql`.
- Novos assets/testes de operação em `adm-web/` e páginas/testes públicos em `landing-page/`.

Não serão editados `OneOffPayments`, `PaymentProvider`, `HttpAsaasPayments`, `JdbcPaymentExecution`,
`JdbcPaymentStore`, `IdentitySecurityConfiguration`, nem os arquivos não rastreados de contexto.

## Referências oficiais Asaas verificadas

- Listagem de cobranças aceita `externalReference` e paginação (máximo 100):
  https://docs.asaas.com/reference/list-payments
- Consulta pontual de cobrança existente: https://docs.asaas.com/reference/recuperar-uma-unica-cobranca
- Webhooks são at-least-once, exigem idempotência, podem pausar após falhas e eventos ficam retidos por
  prazo limitado: https://docs.asaas.com/docs/sobre-os-webhooks e https://docs.asaas.com/docs/faq-de-webhooks
- Eventos de checkout indicam que confirmação deve vir do evento `CHECKOUT_PAID`, não do redirect:
  https://docs.asaas.com/docs/eventos-para-checkout
- O extrato é a fonte de lançamentos financeiros efetivos (inclusive taxas) e pode relacioná-los por
  `paymentId`: https://docs.asaas.com/reference/recuperar-extrato
