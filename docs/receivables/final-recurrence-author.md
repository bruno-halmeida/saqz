# Relatório do autor — recorrência mensal e renovação de Pix

Data da verificação: 2026-09-13. Base de isolamento solicitada: `384b5894`; o gate foi executado em `/tmp/saqz-recurrence-LWwR4y`, criado por arquivo Git e contendo a base, os arquivos desta frente e somente a porta/ledger/configuração compartilhados já publicados por C e pelo coordenador, sem segredos. Nenhuma chamada real ao Asaas, mutação Git, push, deploy ou alteração de `context.md`/`direcionamento.md` foi feita.

## Resultado entregue

- Contrato mobile completo em `docs/receivables/final-recurrence-contract.md`: preview, aceite, consulta, cancelamento, retomada e renovação de Pix, incluindo JSON, códigos, idempotência e estados assíncronos.
- Recuperação mobile sem repetir mutações: recorrência atual, authorize/resume pelo `requestId` original e renovação Pix pelo par order/request, com isolamento estrito por pagador.
- Recorrência mensal por Pix através de assinatura Asaas e por cartão exclusivamente através do Checkout Asaas hospedado. Nenhum PAN, CVV ou token de cartão é recebido ou persistido pelo Saqz.
- Aceite durável vinculado ao pagador, `requestId`, versão dos termos, meio, centavos e fingerprint da cotação; unicidade concorrente de uma recorrência viva por grupo/pagador.
- Materialização idempotente das cobranças retornadas pelo provedor, uma competência por mês, com snapshot imutável da cotação e reconciliação no fluxo financeiro existente.
- Corte durável e recuperável: deixa de gerar novas cobranças, exclui remotamente apenas cobranças ainda não vencidas, inativa a assinatura e cancela localmente somente competências futuras. Cobranças vencidas e cobranças avulsas permanecem intactas.
- Retomada cria nova recorrência, novo aceite e novo `requestId`; não reabre a autorização anterior.
- Renovação de Pix expirado altera a obrigação Asaas existente com `PUT /payments/{id}`, mantendo payment/order/instrument e o snapshot financeiro. Timeout vira estado `UNKNOWN`; a retomada consulta o pagamento antes de repetir a atualização.
- Jobs reais: recuperação de recorrências e Pix a cada 60 s, sincronização a cada 300 s e corte de elegibilidade a cada 60 s, todos condicionados a `saqz.receivables.payments-enabled=true`.
- Conciliação de custo residual: o extrato Asaas é paginado e correlacionado por `paymentId`; somente débito exato `REFUND_REQUEST_FEE` é entregue à porta de C depois de `REFUNDED`/`CHARGEBACK`. O principal de chargeback nunca é tratado como custo.
- `JdbcPaymentExecution.reconcileObserved` oferece ao recovery administrativo um caminho estritamente read-only no provedor: apenas GET/recover, sem create, cancel ou renew.

## Arquivos da frente

Novos:

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/RecurrencePayments.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/RecurrencePaymentsController.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/RecurrencePixRenewalController.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasRecurrenceProvider.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcRecurrenceStore.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcRecurrenceExecution.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/ReceivablesRecurrenceConfiguration.kt`
- `backend/features/receivables/src/main/resources/db/migration/V57__recurrence_execution.sql`
- `backend/features/receivables/src/main/resources/db/migration/V60__pix_payment_renewals.sql`
- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/application/RecurrencePaymentsTest.kt`
- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/adapter/HttpAsaasRecurrenceProviderTest.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/ReceivablesRecurrenceIntegrationTest.kt`
- `docs/receivables/final-recurrence-contract.md`
- `docs/receivables/final-recurrence-author.md`

Alterados dentro do ownership:

- `OneOffPayments.kt`, `PaymentProvider.kt`
- `HttpAsaasPayments.kt`, `JdbcPaymentStore.kt`, `JdbcPaymentExecution.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/OneOffPaymentsIntegrationTest.kt` (somente injeção explícita da porta compartilhada, autorizada pelo coordenador)

## Evidências de aceite

- `RecurrencePaymentsTest`: 7 testes de centavos/fingerprint, aceite e ator, replay/tamper, permissões, cancelamento, retomada e recuperação mobile isolada.
- `HttpAsaasRecurrenceProviderTest`: 7 testes de payload Pix exato, Checkout hospedado sem dados de cartão, corte sem janela e preservando vencida, renovação Pix no mesmo pagamento e extrato de refund/chargeback em centavos exatos.
- `ReceivablesRecurrenceIntegrationTest`: 7 testes PostgreSQL/HTTP de contrato no-store, concorrência e unicidade, snapshot/materialização única, corte local, timeout/recovery Pix, custo residual idempotente e probe administrativo sem escrita remota.
- O teste de renovação confirma IDs únicos de dívida/instrumento, valor imutável, `RESULT_PENDING` após resposta perdida e recuperação por GET antes de nova escrita.
- O teste de corte confirma `CANCELLED` apenas para instrumento/order/group charge futuro e preserva `ACTIVE`/`ISSUED`/`PENDING` vencido, além do evento de auditoria.

## Gates e logs

Gate isolado aprovado:

```text
JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home \
  /tmp/saqz-recurrence-LWwR4y/backend/gradlew -p /tmp/saqz-recurrence-LWwR4y/backend \
  :features:receivables:test :bootstrap:test \
  --tests br.com.saqz.bootstrap.ReceivablesRecurrenceIntegrationTest \
  --tests br.com.saqz.bootstrap.OneOffPaymentsIntegrationTest --no-daemon

BUILD SUCCESSFUL in 36s
29 actionable tasks: 8 executed, 21 up-to-date
```

O gate unitário executou 30 testes no módulo, 14 deles específicos desta frente; o gate bootstrap executou os 7 testes de integração específicos desta frente e os 36 testes de `OneOffPaymentsIntegrationTest`, todos aprovados.

Gate integrado amplo:

```text
:features:receivables:integrationTest
32 tests completed, 1 failed
```

Os 31 testes funcionais passaram. A única falha é a asserção literal preexistente de inventário em `ReceivablesSchemaIntegrationTest` (`6` migrações/`27` tabelas), que agora deve esperar `8`/`29` com V57 e V60; conforme decisão do coordenador, essa atualização será integrada por ele e o arquivo não foi copiado nem alterado por esta frente.

## Fontes oficiais e limites reais

As decisões de integração e seus links oficiais estão registradas no contrato. Em especial: assinatura Pix usa `POST /v3/subscriptions`; cartão usa Checkout `CREDIT_CARD` + `RECURRENT`; corte usa listagem de cobranças, `DELETE /v3/payments/{id}` apenas nas futuras e `PUT /v3/subscriptions/{id}` com `INACTIVE`; renovação Pix usa `PUT /v3/payments/{id}`. A remoção da assinatura não foi usada porque a documentação informa que ela remove cobranças pendentes e vencidas.

Para custos externos, a fonte oficial é `GET /v3/financialTransactions`: a API fornece `paymentId`, `type`, `value` e paginação máxima de 100. Apenas `REFUND_REQUEST_FEE` negativo ligado ao mesmo pagamento é aceito; `CHARGEBACK` comprova a reversão principal, mas não é custo residual. Falha, ausência, valor positivo/malformado, outro payment ou tipo novo são ignorados para lançamento e voltam a ser consultados numa conciliação posterior.

Limite documentado: se a resposta de criação do Checkout de cartão se perder antes de o ID do checkout ser persistido, a API oficial consultada não oferece busca idempotente do Checkout por `externalReference`; portanto o registro permanece `UNKNOWN` para intervenção/reconciliação, sem retry cego que possa criar outra assinatura. Depois que o checkout ID existe, a recuperação é automática via consulta de pagamentos por `checkoutSession`; callbacks são apenas navegação do usuário e nunca são tratados como prova financeira.

## Configuração operacional

- Nova propriedade obrigatória quando pagamentos estão habilitados: `saqz.receivables.checkout-callback-base-url`, HTTPS e sem credenciais/query/fragment.
- Paginação de cobranças limitada explicitamente a 100 páginas de 100 itens; exceder falha fechado e tenta novamente no próximo ciclo.
- Claims remotos usam lease de 90 s; chamadas HTTP têm timeout de 60 s; resultados incertos usam retry/recovery em 60 s.
- Lotes de recuperação, sincronização e cutoff processam no máximo 100 registros por execução.
