# Revisão independente inicial — pagamentos avulsos

Data: 2026-09-13. Checkout `/Users/bruno_almeida/Private/saqz`, base `main 3a9f6b6d`, branch de trabalho `feat/receivables-payments`. Auditoria READ-ONLY sobre fontes mutáveis; não é gate final nem aprovação para produção.

Li payment-wave.md, contrato HTTP /tmp/saqz-payment-http-contract.md e checklist C1–C8. Não encontrei AGENTS.md aplicável ao backend ou nos ancestrais consultados; o AGENTS de mobile está fora do escopo. Li arquivos novos não rastreados além do diff contra a base — o diff sozinho omite grande parte desta implementação. Não rodei builds, testes concorrentes, APIs financeiras reais, nem mutations Git; não alterei fontes. Os reproduzidores abaixo são sequências determinísticas derivadas do código, não execuções de integração. Consultei documentação pública oficial Asaas somente.

## Achados adicionais

### R1 — P1: disputa de chargeback vencida nunca recompõe pagamento/caixa

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasPayments.kt:106`: CHARGEBACK_REQUESTED, CHARGEBACK_DISPUTE e AWAITING_CHARGEBACK_REVERSAL são todos convertidos em CHARGEBACK.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/PaymentProvider.kt:32`: CHARGEBACK é terminal; `next` devolve esse estado para qualquer observação posterior.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentExecution.kt:112`: ordem CHARGEBACK impede reaplicação de pagamento; linhas 125–131 continuam tratando o estado retido como reversão.
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcGroupChargePayments.kt:38`: efeitos PAYMENT/REVERSAL possuem dedup por ordem, sem efeito compensatório para disputa vencida.

Repro mínimo: criar cartão → confirmar → observar CHARGEBACK_DISPUTE → observar AWAITING_CHARGEBACK_REVERSAL → observar RECEIVED por consulta autenticada. O caixa é revertido na disputa e permanece assim após o dinheiro retornar; instrumento e ordem permanecem CHARGEBACK. Mesmo receber primeiro AWAITING_CHARGEBACK_REVERSAL produz reversão definitiva indevida.

A [documentação oficial de eventos Asaas](https://docs.asaas.com/docs/webhook-para-cobrancas) distingue disputa vencida aguardando repasse e informa o retorno posterior a CONFIRMED/RECEIVED. Bug de transição financeira, adicional à apuração de custos de C7; não é pedido de implementar reembolso solicitado. Precisa modelar disputa e compensação/recrédito de forma idempotente, sem declarar todo chargeback terminal.

### R2 — P1: cancelamento de jogo deixa ordem sem instrumento presa em CANCEL_PENDING

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcGroupPaymentCancellation.kt:15`: apenas muda ISSUED para CANCEL_PENDING.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentEvents.kt:62`: recuperação inicia em receivable_instruments com JOIN de ordens.
- `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcChargeTransactionRepository.kt:25`: encaminha cancelamento eletrônico; a atualização normal de cobranças pendentes exclui electronic_order_id preenchido.

Repro mínimo: aprovar ordem de cobrança de jogo; não criar instrumento; cancelar jogo; executar scheduler repetidamente. A ordem não participa de nenhuma seleção de recuperação, a cobrança fica PENDING e electronic_order_id permanece preenchido. A baixa manual permanece bloqueada e criação de instrumento falha porque a ordem não está ISSUED. POST cancel explícito pode recuperar este caso, mas o caminho automático prometido pelo cancelamento de jogo não o faz.

É bug no fluxo já implementado, não dependência operacional nem C6: não existe cobrança remota cujo DELETE precise de retry. Processar ordens CANCEL_PENDING sem instrumento (ou encerrá-las atomicamente no próprio pedido) resolve a ausência; deve preservar o lock e não criar instrumento concorrente.

### R3 — retirado na releitura final: seleção AVAILABLE corrigida pelo autor

Durante a revisão, identifiquei que RECEIVED passou a AVAILABLE mas o scheduler ainda não selecionava esse estado. Na última releitura, o autor acrescentou AVAILABLE e next_reconcile_at com rotação por próxima tentativa em JdbcPaymentEvents.kt:63; o reproduzidor de webhook SPLIT_DONE perdido deixou de sustentar o achado. Não contabilizar R3 como defeito aberto. Essa correção não resolve R1 nem R2.

### R4 — P2: JSON sintaticamente inválido no webhook retorna erro interno em vez de 400

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentEvents.kt:25`: readTree é chamado diretamente, após autenticação.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/OneOffPaymentsController.kt:70`: captura apenas IllegalArgumentException.
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/http/SafeExceptionHandler.kt:554`: handler genérico de Exception.

Repro mínimo: POST no webhook de conta configurada com token correto e corpo `{`. O corpo é String, então o erro ocorre dentro do adapter como JsonParseException/JsonProcessingException, não HttpMessageNotReadableException no binding MVC. Ele escapa do catch IllegalArgumentException e chega ao handler genérico 500, contrariando o contrato de malformed=400. Preservar 5xx para falha de persistência e normalizar somente falha de parsing para erro de entrada.

## Switches solicitados pelo coordenador

`OneOffPaymentsConfiguration.kt:22` é guard de bootstrap payments-enabled: false remove controllers de manutenção, webhook e scheduler. Serve ao provisionamento inicial OFF, mas não pode ser usado para suspender apenas novos negócios após emissão; desligá-lo em deployment com ordens vivas interrompe manutenção inteira. Documentar essa limitação operacional expressamente.

Os controles administrativos de rollout BACKEND/MOBILE são separados: `OneOffPayments.kt:143` monta autorização de manutenção com READ; rollout e elegibilidade só são consultados com newBusiness=true. get/instrument/reconcile/cancel não exigem rollout ativo para ordens emitidas, e webhook não passa por rollout. Não encontrei bug que faça o OFF administrativo cortar esses caminhos no código inspecionado. Isto não valida deployment ou composição real do Spring.

## C1–C8 e limites

Não relistei como novos achados o risco de análise como ACTIVE, ausência inicial de expiresAt/available, projeções de comissão/custo, fairness da fila ou ator do cancelamento: o autor estava corrigindo esses pontos e observei mudanças durante as leituras. C6 já tinha caminho provider.cancel que consulta e repete DELETE. C8 continua precisando jornada operacional e validação de provisionamento conforme checklist, não deve ser confundido com sucesso de mocks.

Há detalhe adicional de C4 a validar: `JdbcPaymentExecution.apply` retorna Unit e pode sair apenas com PAYMENT_MISMATCH (linhas 87–89), mas reconcile ainda retorna true; com paymentId já conhecido o evento pode ser marcado processed apesar de não aplicar estado financeiro. Classificar como pendência explícita tratada ou fazer o resultado refletir aplicação; não contei como quinto achado porque é extensão direta de C4.

Observei também `return false` nas funções Unit create/cancel de JdbcPaymentExecution (linhas 21/28), enquanto o autor editava a fonte, e avisei o coordenador. Não rodei compilação para conferir: tratar como inconsistência transitória a resolver pelo autor, não como conclusão de gate desta auditoria.

Requisitos que não atesto como implementados: provisionamento sem seed, aceitação operacional de credenciais/webhook, recuperação real com eventual consistency, e comportamento completo em falhas de rede. Tests verdes existentes não foram usados como prova. Revalidar os achados abertos R1, R2 e R4 no snapshot final, executar os reproduzidores em testes de integração isolados e confirmar que o autor não resolveu o problema depois desta leitura.

## Identificação da última releitura

UTC: 2026-09-13T17:38:33.494723+00:00

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentEvents.kt`: SHA-256 `50c7bed6236753a3336300e90cf80e9ffc3b1dd7120f74a37d4cedd83936ee6a`.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcGroupPaymentCancellation.kt`: SHA-256 `5c7bea68461bc2323c8ddf0066ba2ee5c0bfa096c74e8704e3301b96f207c5ac`.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/PaymentProvider.kt`: SHA-256 `866b76c19fd56f6bbac2e5ad59667167d13fec1fc4a5953f51b4c2db9adaae82`.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasPayments.kt`: SHA-256 `f922064432a06f524e38a348bfe76f6729423db6c634f4bb232ebb8b65f9ce3a`.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/OneOffPaymentsController.kt`: SHA-256 `3519d597f9c50dc1f90433f502a05139a4e7d649b2364fb1917b3814e2572d3a`.
