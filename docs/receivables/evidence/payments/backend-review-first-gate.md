# Gate final independente — backend de pagamentos avulsos

Data: 2026-09-13. **Gate não aprovado: um P1 financeiro e um P2 de cancelamento reproduzidos.** A revisão solicitada foi concluída; não houve correção de produto neste passe.

Snapshot: `de4707ef3ff1b864bc4693f445c460a3959db7d0`, branch `feat/receivables-payments`, base de comparação `45d10d0d`. Revisei o diff completo de backend: 28 arquivos, 1.812 inserções e 12 exclusões, incluindo composição, security/filter, grupos, migrations, aplicação, persistência, provedor e testes. Li `/tmp/saqz-payment-backend.md`, `/tmp/saqz-payment-http-contract.md`, `/tmp/saqz-payment-backend-review-initial.md`, `docs/receivables/payment-review-checklist.md` e `docs/receivables/payment-operation.md`. Não havia AGENTS aplicável ao backend nos ancestrais ou na árvore examinada; `mobile/AGENTS.md` está fora do escopo. A skill orchestration foi lida e usada para comunicação com o coordenador.

## Evidência e integridade

Criei `/tmp/saqz-payment-final-snapshot` por `git archive de4707ef backend | tar -x -C /tmp/saqz-payment-final-snapshot`. Isso inclui todos os 665 arquivos backend rastreados, inclusive os arquivos novos do lote e os módulos auxiliares de PostgreSQL e build. Não copiei apenas os arquivos que apareciam no diff. Todos os blobs conferem com o commit, exceto a conversão LF/CRLF de `backend/gradlew.bat` feita no archive; sua igualdade após normalização foi verificada. Nenhuma fonte de produto da cópia foi alterada. Acrescentei somente uma classe de testes independente à cópia.

- Diff integral: `/tmp/saqz-payment-final-backend.diff`.
- Manifesto SHA-256 de todos os arquivos originais do snapshot: `/tmp/saqz-final-backend-manifest.json`.
- Fonte dos 11 probes e fixture completa: `/tmp/saqz-payment-final-snapshot/backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/IndependentPaymentGateProbeTest.kt`.
- Scripts que geraram essa classe: `/tmp/saqz-build-review-probes.py`, `/tmp/saqz-add-review-probes.py`.
- XMLs preservados: `/tmp/saqz-final-gate-xml/`; primeira contagem detalhada: `/tmp/saqz-final-gate-counts.json`.

Não alterei arquivos no checkout compartilhado, não executei mutations Git e não chamei API Asaas real, sandbox ou produção. A atividade externa foi somente leitura da documentação pública oficial. Os HTTPs de provedor nos testes apontam para `127.0.0.1` com credenciais sintéticas. Mudanças concorrentes em `.specs/STATE.md` e documentos não pertencem a este worker e não entram no snapshot.

## F1 — P1: reversão observada primeiro credita comissão sem o débito correspondente

**Resultado executado:** o probe `probe first observation refunded must not create unearned commission credit` falhou com `expected: <0> but was: <300>` no somatório de movimentos `COMMISSION`.

Locais no produto:

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasPayments.kt:127`: `splitSettled` só é verdadeiro com `split.status == DONE`.
- Mesmo arquivo, linha 130: lê corretamente o crédito devolvido de `refunds` com status DONE e `refundedSplits.done/value`.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentExecution.kt:150`: só lança o débito de comissão se a observação atual disser split liquidado.
- Mesmo arquivo, linha 151: lança o crédito da comissão devolvida mesmo quando nenhum débito anterior foi registrado.

Repro em PostgreSQL real e HTTP local:

1. Aprovar e criar cartão com comissão fixa R$3; observar inicialmente PENDING com split PENDING, sem movimento de comissão.
2. Perder as notificações intermediárias. Na próxima consulta autenticada receber pagamento REFUNDED, split REFUNDED e `refunds:[{status:"DONE", refundedSplits:[{id:"split_local",done:true,value:3.00}]}]`.
3. Conciliar. Ordem vira REFUNDED e os efeitos pagamento/reversão do caixa são gravados, mas o ledger contém somente `COMMISSION +300`, sem `COMMISSION -300` anterior.
4. O total líquido da comissão fica positivo em 300 centavos, embora a comissão tenha apenas sido devolvida.

Fonte do reproduzidor: `IndependentPaymentGateProbeTest.kt:109`; fixture/servidor completos estão na mesma classe. O payload usa campos e enums presentes no OpenAPI oficial, inclusive split REFUNDED, refund DONE e refundedSplits.done/value; não depende de um campo inventado pelo mock.

Isso viola C7 e o tratamento implementado de eventos fora de ordem. Não é ausência de recurso para solicitar reembolso, nem pendência de apuração de custo do provedor: há prova explícita da comissão devolvida, mas o histórico líquido fica incorreto. `REVERSAL_PROVIDER_COST_PENDING` não representa nem neutraliza esse crédito sem débito. A correção deve reconstruir o par de fatos comprovados ou impedir crédito desacompanhado de débito com pendência explícita, preservando idempotência quando o split DONE foi observado antes e quando a primeira observação já é REFUNDED.

Referência oficial: [recuperar uma cobrança](https://docs.asaas.com/reference/recuperar-uma-unica-cobranca). Cópia integral `/tmp/saqz-final-payment.md`, schemas `PaymentRefundedSplitResponseDTO`, `LeanPaymentSplitGetResponsePaymentSplitStatus`, `PaymentRefundGetResponsePaymentRefundStatus`.

## F2 — P2: falha exclusiva no QR impede cancelar Pix cuja cobrança já foi consultada

**Resultado executado:** o probe `probe QR outage does not prevent cancellation of authenticated existing Pix` falhou com `expected: <CANCELLED> but was: <CANCEL_PENDING>`.

Locais no produto:

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/asaas/HttpAsaasPayments.kt:87`: cancel chama recover antes do DELETE.
- Mesmo arquivo, linha 117: a construção da observação de um Pix ACTIVE faz obrigatoriamente GET `/payments/{id}/pixQrCode`.
- Mesmo arquivo, linha 90: o DELETE só é alcançado se essa leitura auxiliar de QR também tiver sucesso.
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcPaymentExecution.kt:59`: falha é convertida em resultado incerto, conservando reserva e CANCEL_PENDING.

Repro:

1. Criar Pix normalmente e persistir `paymentId` conhecido.
2. Fazer somente o endpoint `/pixQrCode` responder 503; GET da cobrança continua autenticado e responde PENDING com id/reference/meio/valor corretos, e DELETE está disponível no servidor local.
3. Solicitar cancelamento. A consulta da cobrança é feita, mas a leitura de QR aborta o fluxo antes do DELETE; nenhuma exclusão remota ocorre.
4. A ordem continua CANCEL_PENDING e a baixa manual continua bloqueada. Recuperação percorre a mesma dependência, portanto não resolve enquanto o endpoint de QR falhar.

Fonte do reproduzidor: `IndependentPaymentGateProbeTest.kt:100`. A guarda contra resultado remoto realmente incerto deve permanecer; o defeito é condicionar consulta de estado/cancelamento à obtenção de material de apresentação. Separar fatos da cobrança da hidratação de QR permite cancelar com a prova já obtida, sem novo POST.

É bug na manutenção de instrumento existente, não pedido de renovar QR vencido. O teste não usa expiração, wallet, recorrência ou qualquer dependência de mobile. A duração da falha do QR determina a duração do bloqueio; não alego que o provedor real tenha produzido essa falha nesta revisão.

Referências oficiais: [QR de Pix](https://docs.asaas.com/reference/obter-qr-code-para-pagamentos-via-pix) e [consulta de cobrança](https://docs.asaas.com/reference/recuperar-uma-unica-cobranca). Cópias `/tmp/saqz-final-pix-qr.md` e `/tmp/saqz-final-payment.md`.

## Matriz C1–C11 e riscos solicitados

| Critério | Resultado deste passe |
|---|---|
| C1 — titular atual versus manutenção histórica | Guard compara owner do grupo/conta em emissão nova; teste de transferência passa. Snapshots mantêm pagador/conta após OFF, corte, saída e soft-delete. Vínculos têm chave por conta/grupo; este diff não cria unicidade histórica global por grupo. |
| C2 — competência, atomicidade e locks | Competência vem de billing_month original, testada como agosto com vencimento setembro. Aprovação reserva e grava ordem/quotes na mesma transação. Leitura de locks e probes concorrentes descritos abaixo sem deadlock observado. |
| C3 — análise, vencimento, fatos separados | AWAITING_RISK_ANALYSIS vira UNKNOWN e não autoriza DELETE nem novo instrumento. expiresAt vem do QR. RECEIVED com escrow ACTIVE é SETTLED, sem disponibilidade; com ausência de escrow ativo torna-se AVAILABLE. Split exige DONE. Ressalva documental sobre flags históricas abaixo. |
| C4 — ACK/aplicação, backoff e justiça | Inbox cifrado antes do ACK; sucesso depende de reconcile boolean. Testes de falha da consulta, fila com mais de 50 eventos/instrumentos e probe de valor divergente com paymentId conhecido passaram: evento divergente permanece pendente. |
| C5 — ator do cancelamento | Operação CANCEL_INSTRUMENT recupera ator persistido; teste com delegado e scheduler passou. Revogação antes do replay de approve/cancel também foi exercitada por probe independente e negada. |
| C6 — DELETE incerto seguro | Teste de resposta perdida e retry após consulta passou, sem novo POST e conservando ator. **F2 aberto**: hidratação obrigatória de QR pode impedir alcançar esse DELETE. |
| C7 — fatos monetários e reversão | netValue é líquido após tarifa Asaas no schema; comissão efetiva exige split DONE e faltas/divergências produzem ocorrência. **F1 aberto**: devolução observada antes do débito cria crédito líquido indevido. |
| C8 — customer, webhook e credenciais | Customer400 estruturado fecha instrumento e permite novos dados corrigidos; timeout não autoriza recriar. Webhook tem token aleatório cifrado antes do POST, setup HTTP owner-only,400 REJECTED corrigível, timeout UNKNOWN recuperável por URL/nome/hasAuthToken/enabled. Aprovar exige state SUCCEEDED. Testes passaram; configuração real da subconta continua pendência operacional. |
| C9 — cancelamento sem instrumento | Correção inicial confirmada: tanto teste do autor quanto probe independente passando pelo cancelamento real de jogo encerram ordem, cobrança e reserva atomicamente, sem POST. |
| C10 — disputa vencida | REQUESTED/DISPUTE são DISPUTED; AWAITING_CHARGEBACK_REVERSAL é RECOVERY_PENDING, sem reversão terminal. Sequência até CONFIRMED/RECEIVED passa com um único efeito PAYMENT. O achado R1 inicial está resolvido. |
| C11 — JSON e persistência | Corpo `{` retorna400 no teste do autor; whitespace retorna400 no probe independente. Falha SQL real no inbox lança DataAccessException, não é normalizada para400 nem ACK; controller só captura IllegalArgumentException e handler global preserva erro interno. O achado R4 inicial está resolvido. |

Origem/isolamento: token é validado por accountId, conta remota do corpo é verificada quando presente, recurso é procurado dentro da mesma conta e consulta do provedor usa credencial dessa conta. O probe adicional com token válido da conta B e id/externalReference de instrumento A resultou em UNMATCHED_EVENT pendente e nenhum efeito financeiro em A. Testes existentes também cobrem token errado, conta inexistente e divergência explícita de account remoto. Não atesto funcionamento das chaves reais.

Unknown/idempotência: operação de instrumento/aceite commita antes do POST, lease tem token que impede aplicar resultado tardio de executor vencido. Customer usa fence UNKNOWN independente. Replay do mesmo request retorna mesmo instrumento; payload diferente conflita. Perda de resposta e consulta vazia não autorizam novo POST. O probe independente apagou apenas a resposta visível do customer no servidor local após timeout: três conciliações mantiveram um único customer POST, zero payment POST e instrumento UNKNOWN reservado.

Concorrência: a ordem observada é grupo → cobrança → conta → instrumento/operação no efeito local. As chamadas HTTP ficam fora dessa transação. O registrador/claim com conexão própria só é chamado fora dos locks de domínio; inserção inicial de operação de instrumento participa da transação local. O cancelamento de jogo trava grupo/cobrança antes de delegar ao adaptador financeiro. A baixa manual exige electronic_order_id NULL e versão; seus updates conflitam com a reserva. Os testes existentes de pagamento versus baixa e instrumentos concorrentes passaram. Dois probes independentes controlaram a intercalação com CountDownLatch: consulta ACTIVE atrasada depois de RECEIVED preservou AVAILABLE/PAID e recibo único; cancelamento durante preparação remota de customer encerrou operação READY, permitiu baixa manual e o executor liberado não enviou payment POST. Isso é evidência focada de intercalações relevantes, não exploração exaustiva de todas as escalas de execução.

## Flags financeiras: esclarecimento documental, sem novo defeito demonstrado

`confirmed`, `settled`, `available` e `splitSettled` são persistidos com OR em `JdbcPaymentExecution.kt:129` e nunca voltam a false. Representam marcos históricos observados; available pode continuar true após disputa/refund. Não encontrei consumo desses campos como saldo atual no backend deste lote. Recomendo explicitar isso no contrato e orientar consumidores a interpretar status e conciliação para estado atual. Não classifiquei a ambiguidade como terceiro bug, nem avaliei UI de pagamento mobile ausente deste lote.

## Comandos e resultados efetivamente executados

Todos os Gradles usaram `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`, `--offline` e o backend do snapshot. Nenhuma suíte completa foi repetida. O PostgreSQL embarcado executado é **16.9 real**, em localhost, com Flyway aplicando 55 migrations no bootstrap; o helper desliga fsync/synchronous_commit/full_page_writes para testes descartáveis, portanto isso não prova crash durability em produção.

Preparação/read-only:

```sh
git archive de4707ef backend | tar -x -C /tmp/saqz-payment-final-snapshot
git diff 45d10d0d de4707ef -- backend > /tmp/saqz-payment-final-backend.diff
git diff --check 45d10d0d de4707ef -- backend
```

O primeiro Gradle usou cache e ficou verde; **não o contabilizei como execução independente** (`/tmp/saqz-payment-review-final-gates.log`). Para corrigir essa limitação repeti somente os gates focados com execução forçada:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
/tmp/saqz-payment-final-snapshot/backend/gradlew -p /tmp/saqz-payment-final-snapshot/backend \
--offline --no-build-cache :bootstrap:test \
--tests '*OneOffPaymentsIntegrationTest' --tests '*GroupReceivablesIntegrationTest' \
:features:receivables:test --tests '*PaymentFactsTest' \
:features:receivables:integrationTest --tests '*ReceivablesSchemaIntegrationTest' \
:architecture-tests:test --rerun-tasks
```

Resultado: BUILD SUCCESSFUL em25s, **59 testes, zero falhas/erros/skips**. Log `/tmp/saqz-payment-review-final-gates-executed.log`. A composição Spring payments-enabled=true foi exercitada pela classe do autor com AnnotationConfigApplicationContext e seus colaboradores registrados; não é um boot completo do deployment com serviços externos.

Probes independentes iniciais, depois de preservar os XMLs acima:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
/tmp/saqz-payment-final-snapshot/backend/gradlew -p /tmp/saqz-payment-final-snapshot/backend \
--offline --no-build-cache :bootstrap:test --tests '*IndependentPaymentGateProbeTest'
```

Resultado: BUILD FAILED em9s, **8 testes,6 aprovados,2 falhas de asserção,0 erros/skips**. Log `/tmp/saqz-payment-review-independent-probes.log`; XML inicial preservado em `/tmp/saqz-final-gate-xml/TEST-br.com.saqz.bootstrap.IndependentPaymentGateProbeTest.xml`.

Três probes adicionais de intercalação/isolation/SQL e regressão focada do repositório de grupos alterado:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
/tmp/saqz-payment-final-snapshot/backend/gradlew -p /tmp/saqz-payment-final-snapshot/backend \
--offline --no-build-cache :bootstrap:test \
--tests '*IndependentPaymentGateProbeTest.probe cancellation during*' \
--tests '*IndependentPaymentGateProbeTest.probe valid subaccount*' \
--tests '*IndependentPaymentGateProbeTest.probe webhook SQL*' \
:features:groups:integrationTest --tests '*JdbcChargeTransactionRepositoryIntegrationTest'
```

Resultado: BUILD SUCCESSFUL em10s, **24 testes,zero falhas/erros/skips**. Log `/tmp/saqz-payment-review-extra-probes.log`; XMLs com prefixos `probes_extra-` e `groups-` preservados no diretório de evidência. Depois disso não rodei novos gates.

| Classe/grupo XML | Testes | Falhas | Erros | Skips |
|---|---:|---:|---:|---:|
| OneOffPaymentsIntegrationTest |22|0|0|0|
| GroupReceivablesIntegrationTest |10|0|0|0|
| PaymentFactsTest |1|0|0|0|
| ReceivablesSchemaIntegrationTest |6|0|0|0|
| BackendArchitectureTest |20|0|0|0|
| JdbcChargeTransactionRepositoryIntegrationTest |21|0|0|0|
| IndependentPaymentGateProbeTest, primeira execução |8|2|0|0|
| IndependentPaymentGateProbeTest, apenas3 novos métodos |3|0|0|0|
| **Total único** |**91**|**2**|**0**|**0**|

São **80 testes existentes aprovados +11 probes independentes (9 aprovados,2 falhos)**. Os 1.646 testes da suíte completa citados pelo autor são evidência dele, não foram reexecutados nem somados aqui. A fixture independente reaproveita infraestrutura PostgreSQL/HTTP do autor, porém os cenários, intercalações e asserções foram acrescentados independentemente; os dois defeitos aparecem na integração HTTP/JDBC real, não em mocks de campos financeiros inexistentes.

## Fontes oficiais e limites remanescentes

O web tool recusou content-type text/markdown e urllib sem User-Agent recebeu403. A leitura foi concluída com `curl -L -A 'Mozilla/5.0'` para os `.md` oficiais, com HTTP200. Esses documentos incluem OpenAPI e foram consultados para verificar campos/enums, sem extrapolar contratos de mocks:

- [Consulta de cobrança](https://docs.asaas.com/reference/recuperar-uma-unica-cobranca), `/tmp/saqz-final-payment.md`: netValue, split fixedValue/status, escrow e refunds/refundedSplits.
- [Eventos de cobrança](https://docs.asaas.com/docs/webhook-para-cobrancas), `/tmp/saqz-final-events.md`: disputa vencida e eventos subsequentes CONFIRMED/RECEIVED, REFUNDED e PAYMENT_SPLIT_DONE.
- [Criar cliente](https://docs.asaas.com/reference/criar-novo-cliente), `/tmp/saqz-final-customer.md`: cpfCnpj, externalReference e resposta do customer.
- [Criar webhook](https://docs.asaas.com/reference/criar-novo-webhook), `/tmp/saqz-final-webhook-create.md`: configuração e enum dos eventos enviados.
- [Listar webhooks](https://docs.asaas.com/reference/listar-webhooks), `/tmp/saqz-final-webhook-list.md:212`: hasAuthToken oficial, sem presumir retorno do segredo.
- [QR Pix](https://docs.asaas.com/reference/obter-qr-code-para-pagamentos-via-pix), `/tmp/saqz-final-pix-qr.md`: endpoint e campos de apresentação/expiração.

Pendências aceitas de escopo/operacionais, distintas dos dois bugs reproduzidos:

- Homologar configuração completa em subconta sandbox com credenciais, chaves de cifragem, plataforma wallet, webhook origin/email e chave Pix apropriados. Nenhuma chave ou configuração real foi validada.
- Provisionamento inicial `payments-enabled` OFF remove também manutenção, webhook e scheduler; depois de emitir, suspender apenas novos negócios pelo rollout/grupo. Esta limitação está documentada e não é novo achado.
- Ausência eventual de recurso permanece UNKNOWN e bloqueada; não há prova local de consistência ou comportamento remoto real.
- Custos residuais do provedor e split retornado ainda não comprovados exigem ocorrências de conciliação. Isso não justifica F1, que usa devolução comprovada e lança valor líquido incorreto.
- Wallet/saque, reembolso solicitado, recorrência, UI completa do pagador mobile e renovação automática de Pix estão explicitamente fora deste lote. F2 cancela Pix existente durante falha isolada do QR e não solicita renovação.
- Não foram atestados produção, carga, falhas de máquina, durabilidade contra crash ou todas as possíveis intercalações de concorrência. Não houve pedido de publicação nem ações remotas financeiras.

Próxima ação: implementar as correções F1/F2, incorporar os probes como regressões e submeter o delta e esses cenários ao gate independente. Nenhum outro defeito foi confirmado neste passe; os achados iniciais R1/R2/R4 foram revalidados como resolvidos.
