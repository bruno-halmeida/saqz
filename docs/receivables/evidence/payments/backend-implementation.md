# Backend pagamentos avulsos — entrega T08–T10

Data: 2026-09-13. Workspace `/Users/bruno_almeida/Private/saqz`. Propriedade alterada: somente backend/**; relatórios em /tmp. Nenhum staging, branch, commit, push, documento, mobile ou adm foi alterado por este worker.

## Resultado

Implementado fluxo de ordem financeira aprovada ligada à cobrança manual existente, snapshot dos meios/fees e competência original, aceite do pagador, Pix/QR e cartão hospedado via invoiceUrl, persistência cifrada, recuperação de criação incerta, consulta/cancelamento, webhook por subconta, conciliação e efeito único no caixa. Composição por interfaces shared-kernel e bootstrap; receivables não importa groups, nem vice-versa. O bloqueio segue grupo → cobrança → conta → instrumento; nenhuma chamada HTTP é executada com essa transação aberta.

Contrato HTTP completo: `/tmp/saqz-payment-http-contract.md`. Roteiro do coordenador `docs/receivables/payment-operation.md` foi lido e confere com a implementação; não foi editado.

## Contratos finais

- `GroupChargePayments`: snapshot bloqueado da cobrança (inclui owner atual, billingMonth original, valor, pagador, vencimento), reserva, liberação e efeitos PAYMENT/REVERSAL idempotentes. Projeção manual usa group_charges/group_charge_events existentes, inclusive após soft-delete. Reversão cancela, nunca reabre dívida.
- `GroupChargePaymentCancellation`: side effect do cancelamento de jogo, sem HTTP. Sem instrumento vivo, encerra ordem/libera reserva na mesma transação. Com instrumento vivo/incerto, CANCEL_PENDING preserva a reserva até prova remota; recuperação também cancela a cobrança do jogo depois da liberação.
- `PaymentStore`, `PaymentExecution`, `OneOffPaymentProvider`, `PaymentProviderCredentials`, `PaymentEventInbox`, `PaymentWebhookRegistration`: fronteiras de aplicação e composição bootstrap.
- HTTP autenticado: GET configuração grupo; POST charges/{id}/preview e /approve; GET orders/{id}; POST orders/{id}/instruments, /cancel e /reconcile; POST accounts/{id}/webhook. requestId obrigatório em todos POSTs; envelope value/error + requestId; Cache-Control no-store.
- Pagador informa `{payer:{name,cpfCnpj},method,fingerprint,accepted,requestId}`. Payer é sempre identidade autenticada da ordem; documento não é presumido do perfil. Dados cifrados e incluídos no digest idempotente. PIX/CARD/ambos são snapshots da aprovação; seleção vazia/aceite falso/método não permitido são rejeitados sem criação.
- Manutenção de ordens conserva conta/pagador após OFF, corte, saída de membro, troca de owner e exclusão lógica. Novas ordens exigem titular atual do grupo igual ao titular financeiro, vínculo ativo, cadastro/rollout/plano e webhook configurado. Não imposta unicidade histórica nova de vínculo financeiro por grupo.

## Execução/recuperação

Customer tem referência persistida e fence UNKNOWN antes do primeiro POST. Resposta perdida recupera por GET customers externalReference; ausência/duplicidade não autoriza recriar. Operação de instrumento e aceite commitam juntos antes de POST payments, com lease e token que protege resultados tardios. Reenvios usam a mesma operação; recuperação não repete criação. HTTP400 estruturado definitivo fecha instrumento/customer rejeitado e permite nova solicitação com dados corrigidos. Cancelamento incerto pode repetir DELETE apenas da mesma cobrança após consulta autenticada, preservando ator original quando scheduler recupera pedido do delegado. Operação ainda READY pode ser encerrada sem criar pagamento ao cancelar.

Webhook gera token aleatório, cifra/commita antes de POST /webhooks e nunca o expõe na resposta. Setup incerto consulta URL + nome derivado do token + enabled/hasAuthToken; nunca interpreta lista vazia como rejeição. Rejeição400 estruturada de criação é estado REJECTED recuperável após correção da configuração; não exige seed SQL. Evento recebido autentica token por accountId e valida account remoto quando presente; ciphertext deduplicado por conta+eventId commitado antes de200. JSON inválido autenticado retorna400; falha SQL continua5xx. Processador usa evento como sinal para consulta autenticada; query indisponível/mismatch conserva pendência. next_attempt_at/next_reconcile_at garantem backoff/rotação, incluindo AVAILABLE, disputa e itens novos atrás de lotes com falha.

CONFIRMED, SETTLED/AVAILABLE, split e reversão são fatos distintos. RECEIVED marca disponibilidade salvo escrow ativo. Comissão só é movimento efetivo quando split DONE; crédito de split devolvido exige refundedSplits.done/value. Dados faltantes/divergentes geram receivable_payment_occurrences, sem cobrar complemento retroativo. REQUESTED/DISPUTE → DISPUTED; disputa vencida aguardando repasse → RECOVERY_PENDING; depois CONFIRMED/RECEIVED recupera o recibo existente sem duplicar caixa. O fluxo oficial usa REFUNDED para reversão financeira terminal.

## Verificação executada

JDK: `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`. Todos os comandos Gradle abaixo foram executados com JAVA_HOME nesse diretório e --offline, a partir da raiz com `backend/gradlew -p backend` (ou equivalente no cwd backend).

1. Compilação receivables/groups/bootstrap: passou. Log `/tmp/saqz-payment-compile.log`.
2. Gates direcionados PostgreSQL real embarcado + HTTP Spring MockMvc + Asaas HTTP simulado em 127.0.0.1, domínio/concorrência/arquitetura: passaram. Log `/tmp/saqz-payment-gate.log`.
3. Regressão completa: `:bootstrap:test :features:receivables:check :features:groups:check :architecture-tests:test` — BUILD SUCCESSFUL, 2m46s, 1646 testes, zero falhas/erros/skips. Contagens preservadas antes do rerun direcionado em `/tmp/saqz-payment-full-gate-counts.json`; log `/tmp/saqz-payment-full-gate.log`.
   - bootstrap 414; receivables domínio16 + integração28; groups domínio645 + integração523; arquitetura20.
4. Verificação final após a correção pequena do setup webhook400 e adição dos testes composição/concorrência: `:bootstrap:test --tests '*OneOffPaymentsIntegrationTest' --tests '*GroupReceivablesIntegrationTest' :architecture-tests:test`. BUILD SUCCESSFUL, 33s:32 testes bootstrap direcionados +20 arquitetura, zero falhas/erros/skips; log `/tmp/saqz-payment-final-extra.log`.
5. `git diff --check -- backend`: passou.

A suíte nova cobre meios individuais/ambos/vazio, aceite/método inválido, competência distinta do vencimento, autorização após transferência, manutenção OFF/corte/saída/soft-delete, customer/payment response loss, criação concorrente, webhook duplicado/outra conta/malformado, ciphertext antes ACK, consulta com falha, >50 eventos/instrumentos com falhas antigas, confirmação fora de ordem, valor divergente, efeito único/reversão, baixa manual concorrente, cancelamento remoto incerto delegado/scheduler, análise de risco, jogo cancelado com/sem instrumento, disputa vencida, configuração webhook com timeout e rejeição400, e composição Spring com payments-enabled=true. As demais suítes completas cobrem as regressões de groups/HTTP existentes.

O primeiro teste expôs deadlock ao reutilizar o registrador de operação com conexão independente dentro da transação do instrumento. Corrigido gravando operação na mesma conexão/transação e concluindo lease/efeito de forma atômica; o teste e os gates finais passaram. Falhas de fixtures e contagem de migration também foram corrigidas antes dos gates verdes.

## Fontes oficiais consultadas antes de DTOs

- https://docs.asaas.com/reference/criar-nova-cobranca e https://docs.asaas.com/docs/cobrancas-via-cartao-de-credito (fatura hospedada CREDIT_CARD sem PAN/CVV, invoiceUrl).
- https://docs.asaas.com/reference/listar-cobrancas, https://docs.asaas.com/reference/listar-clientes e https://docs.asaas.com/reference/criar-novo-cliente (referência externa e cliente recuperável).
- https://docs.asaas.com/reference/obter-qr-code-para-pagamentos-via-pix e https://docs.asaas.com/reference/excluir-cobranca.
- https://docs.asaas.com/reference/criar-novo-webhook e https://docs.asaas.com/reference/listar-webhooks — schema oficial WebhookConfigGetResponseDTO documenta hasAuthToken boolean, cópia `/tmp/asaas-webhook-list.md:212`.
- https://docs.asaas.com/docs/webhook-para-cobrancas — RECEIVED disponível e fluxo disputa/repasse/REFUNDED; cópia `/tmp/asaas-payment-events.md`.
- Também lidas as referências /checkouts, split checkout e eventos checkout. Decisão coordenada: NÃO usar /checkouts nesta onda, porque consulta por externalReference/idempotência remota não foi documentada; usar payments com método explícito e invoiceUrl. Checkout usa splits, payment usa split; envio aqui sempre fixedValue BRL, nunca percentual remoto presumido.

## Limites e pendências operacionais explícitos

- Somente simulação local: nenhuma chamada de API Asaas real, servidor remoto da aplicação ou chave real. Homologação sandbox integral e produção NÃO atestadas.
- `payments-enabled` é guard de composição inicial OFF; desligá-lo remove inclusive manutenção/webhook/scheduler. Para suspender novos negócios após emissão usar rollout administrativo/grupo, mantendo composição financeira.
- Deployment precisa credenciais de fundação/onboarding, encryption keys, subaccount key válida, platform-wallet-id e webhook origin/email. Setup HTTP evita seed de webhook. Validar configuração na subconta sandbox antes de piloto.
- Pix exige conferir chave Pix da subconta. expiresAt é o valor retornado pelo provedor; GET QR NÃO renova instrumento. Renovação específica de QR vencido foi deixada fora desta onda por orientação do coordenador; não contornar gerando dívida nova.
- Custos do provedor e devolução de split em reversões só ficam finais quando há fatos remotos suficientes. Ausência desses fatos gera ocorrências `REVERSAL_PROVIDER_COST_PENDING` / `REVERSAL_SPLIT_PENDING`, não custo residual inventado nem prova de saldo final.
- Resultado remoto incerto sem recurso encontrado permanece bloqueado. Ausência ou404 não prova autorização para recriar; eventual consistência e recuperação real precisam de homologação.
- Sem saque, reembolso solicitado, recorrência, jornada completa de checkout mobile ou publicação comercial real. Relatório de revisão independente final cabe ao coordenador; esta entrega não substitui esse gate.

## Arquivos alterados/criados

- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/OneOffPaymentsConfiguration.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/OneOffPaymentsIntegrationTest.kt`
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcGroupChargePayments.kt`
- `backend/features/groups/src/main/resources/db/migration/V54__reserve_electronic_group_charges.sql`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/OneOffPaymentsController.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasPayments.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcGroupPaymentCancellation.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentEvents.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentExecution.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentProviderCredentials.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentStore.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentWebhookRegistration.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/OneOffPayments.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/PaymentProvider.kt`
- `backend/features/receivables/src/main/resources/db/migration/V55__one_off_payment_snapshots.sql`
- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/domain/PaymentFactsTest.kt`
- `backend/shared-kernel/src/main/kotlin/br/com/saqz/sharedkernel/group/GroupChargePayments.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/AccessSessionConfiguration.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/GroupReceivablesConfiguration.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/IdentitySecurityConfiguration.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/http/AsaasWebhookBodySizeFilter.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/GroupReceivablesIntegrationTest.kt`
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcChargeManagementRepository.kt`
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcChargeTransactionRepository.kt`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/ReceivablesSchemaIntegrationTest.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/GroupReceivablesController.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/FinancialOperations.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/ManageGroupReceivables.kt`

## Hashes do snapshot entregue

- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/OneOffPaymentsConfiguration.kt` — `c8cdd435928f9fd59bb338bf3dd0f2883ad12adf42ee18934b76daa81373807e`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/OneOffPaymentsIntegrationTest.kt` — `8d5970f3cbf2bdc7b42da8c136b4a8e11ae26122b9a8a242e4f15e55d9cb8bb7`
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcGroupChargePayments.kt` — `c8e32ba947b509ddd69f9df7415080b7c2549743daadb967d3f092d62d8677d8`
- `backend/features/groups/src/main/resources/db/migration/V54__reserve_electronic_group_charges.sql` — `05c2d00401c7895abb64d9dddfaf3b14e160fec3a7163a3a3246ba37efc458a7`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/OneOffPaymentsController.kt` — `3716822be11751d390fae04fc5ff1074f836222cfb4f0fc2e75a97510957f4c2`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasPayments.kt` — `2938dde28e727e9c29ba3207331f2eb1c409b672ced07ad443f845b2d10d90f3`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcGroupPaymentCancellation.kt` — `f526004703771ecefe1414e7b3c9c4fa777737b4132001fd85a74733a423b741`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentEvents.kt` — `cc972c276f2ebc6ddedc838c3dfe5c38f0c7ed99930b814469f2cfd9b965d6f6`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentExecution.kt` — `786e8fc3573f7c8032477b14968952565e47e7faeaf27e853f2a59d5bb00abff`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentProviderCredentials.kt` — `a948439185b43e97e38c8c6d0468fbf77e7367c415f4711bb84e5dfb9bff315e`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentStore.kt` — `e50e4baca485dbd3c25ed58e0cb2688e597efc5cf54bf47e46180921f90c2e75`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentWebhookRegistration.kt` — `075d4ecda16a9a1fc3c0282536bdb660577d6814bc92b99d63dfe8d7d2a7ef72`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/OneOffPayments.kt` — `ef0f779e5a88dd050080bf52675eba6f7092e74d440c621f530f66558e286207`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/PaymentProvider.kt` — `a0267a32ebde487c3b6af6dda1139c613ec7dbfeb18235b496c350fc3c39b2fe`
- `backend/features/receivables/src/main/resources/db/migration/V55__one_off_payment_snapshots.sql` — `03173966c94b3aad154c88f7d39551fd32409ceb2468df95a7fc07e1e5ac8e8e`
- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/domain/PaymentFactsTest.kt` — `d81b568002f9f74449b8ab3abe47cfa44208affae40ca56b17c01cce3c0eb4c4`
- `backend/shared-kernel/src/main/kotlin/br/com/saqz/sharedkernel/group/GroupChargePayments.kt` — `dabee9f32d995133c8c0f391a67d2132f2560614ec74f5e5b5dce21717797784`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/AccessSessionConfiguration.kt` — `8dd5159a1cd3781b3986790a1a0f44ce9f6aa13b2d816d1d8c68e00032d95dc2`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/GroupReceivablesConfiguration.kt` — `118391bad18e5504f8d4296d86dd3b876c20644cf6bd9b9c7de920e726a06d2c`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/IdentitySecurityConfiguration.kt` — `641f0ad54633bb020cca7222ab0681c5ce7632af83526e0b48bf172b42f04bd6`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/http/AsaasWebhookBodySizeFilter.kt` — `745e211bde907e3b6fc41fca35740f1cca269fa03c6db43aee550a1399688b14`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/GroupReceivablesIntegrationTest.kt` — `246a0f149266ef3879c4388b915936b7c99e7cbde432d1ff9d56073af72e50cc`
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcChargeManagementRepository.kt` — `76703ee7b625c2697f2bfad4ad2c94aad0fe470ec799c1ece3848f49b75c530d`
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcChargeTransactionRepository.kt` — `8382f3f2f967cfb2b8936621b413e3521f26b5ceaf1f53721690e3595f3a668a`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/ReceivablesSchemaIntegrationTest.kt` — `c3484c310f6f5929b819e26723bc08d8f14e48c0715870a22ef525cba02cb481`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/GroupReceivablesController.kt` — `12cd65372701b293181c0e9a94ecf542e71dfad0c26a7a892d9e1a491c3649e5`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/FinancialOperations.kt` — `9973427ee474f399c8770b095c83c4ebb19614c956a27750ec092475af2654dd`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/ManageGroupReceivables.kt` — `ddd15a3896948435190fc7dcc228c6b4320eb0eb209066b05eb0c57aa99e4ef3`

## Gate final

Concluído:32 testes direcionados (22 pagamentos +10 configuração) e20 de arquitetura passaram no snapshot final. Regressão completa anterior1646 testes passou; a última mudança funcional foi somente a correção do estado de rejeição definitiva no setup do webhook, exercitada no gate final. Nenhuma alteração de fonte após este resultado.

Nota do coordenador: snapshot commitado em de4707ef, 1824 linhas backend. Revisão independente final em execução; este relatório registra o gate do autor.
