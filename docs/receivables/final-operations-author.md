# Relatório de autoria — frente C/F5/F7

Autor: worker `task_869453e63fc8`, dispatch `ctx_3b35e803f035`. Base isolada: `384b5894`.
Data: 2026-09-13.

## Resultado

Implementei a fila administrativa paginada de operações sem sucesso, detalhe/auditoria, recuperação
read-before-mutation, avisos persistentes, termos públicos já publicados, retorno neutro do checkout e
conciliação local de custo residual observado. A superfície administrativa não oferece nem recupera
`WITHDRAW`/`REFUND`, não cria operações de caixa e não devolve PII, credenciais ou payload bruto.

O wiring novo usa `PlatformAdminLookup`, `JdbcPaymentExecution.reconcile` já existente e
`SubscriptionRepository`, sem editar configurações centrais ou arquivos da frente B. A allowlist GET pública
foi pedida e integrada pelo coordenador em arquivos fora deste ownership.

## Evidência por critério

- Permissão e ausência de poder financeiro: `OperationalReceivables.kt:20`,
  `AdminReceivablesOperationsController.kt:36-110`; HTTP prova 401/403 e saque não recuperável.
- Paginação/sanitização: `JdbcOperationalReceivables.kt:17-45`; DTO contém apenas IDs técnicos, estado,
  tentativas/código e timestamps.
- Request ID, ator, motivo, lease/concorrência e replay: `JdbcOperationalReceivables.kt:59-116`.
- Resultado auditado append-only: `JdbcOperationalReceivables.kt:118-144` e migração V58.
- Read-before-mutation: `ReceivablesOperationsConfiguration.kt:38-48` delega somente a
  `JdbcPaymentExecution.reconcile`; exceção/timeout vira `UNKNOWN`, sem executar criação pela API admin.
- Termos publicados e vigência: `PublicReceivablesController.kt:32-56`; nenhum fallback jurídico.
- Retorno neutro: `PublicReceivablesController.kt:51-54` e `landing-page/recebimentos/retorno/index.html`;
  query string não é lida nem ecoada.
- Avisos sem comunicação: `JdbcOperationalReceivables.kt:169-212`, migração V61 e página adm; não existe
  porta/adaptador de e-mail, SMS ou push neste fluxo.
- Custo residual: `OperationalResidualCosts.kt:6-24` e `JdbcExternalResidualCostLedger.kt:17-32`; aceita
  apenas centavos positivos observados, instrumento já revertido da mesma conta, grava débito exato e
  idempotente.
- Manutenção/corte e limites externos: `final-operations-runbook.md` preserva leitura, histórico e
  recuperação, e separa explicitamente mocks de homologação real.

## Arquivos da entrega

Backend:

- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/OperationalReceivables.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/application/OperationalResidualCosts.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcOperationalReceivables.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/output/jdbc/JdbcExternalResidualCostLedger.kt`
- `backend/features/receivables/src/main/kotlin/br/com/saqz/receivables/adapter/input/http/PublicReceivablesController.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/adminweb/http/AdminReceivablesOperationsController.kt`
- `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/ReceivablesOperationsConfiguration.kt`
- `backend/features/receivables/src/main/resources/db/migration/V58__receivables_operations.sql`
- `backend/features/receivables/src/main/resources/db/migration/V61__receivables_operational_notices.sql`

Testes:

- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/application/OperationalReceivablesTest.kt`
- `backend/features/receivables/src/test/kotlin/br/com/saqz/receivables/adapter/input/http/PublicReceivablesControllerTest.kt`
- `backend/features/receivables/src/integrationTest/kotlin/br/com/saqz/receivables/OperationalReceivablesIntegrationTest.kt`
- `backend/bootstrap/src/test/kotlin/br/com/saqz/bootstrap/ReceivablesOperationsEndpointIntegrationTest.kt`
- `adm-web/tests/operations.test.cjs`
- `landing-page/tests/receivables-public.test.cjs`

Web/docs:

- `adm-web/index.html` (um link para a superfície nova)
- `adm-web/recebimentos/index.html`, `adm-web/recebimentos/operations.js`
- `landing-page/termos/recebimentos/index.html`
- `landing-page/recebimentos/retorno/index.html`
- `docs/receivables/final-operations-contract.md`
- `docs/receivables/final-operations-runbook.md`
- `docs/receivables/final-operations-author.md`
- quatro PNGs `docs/receivables/evidence/final-operations-*.png`

Os arquivos em `docs/` são ignorados globalmente pelo repositório; o coordenador precisará incluí-los
explicitamente no commit (`git add -f`), sem incluir outros artefatos ignorados.

## Gates executados

Scratch criado por `git archive 384b5894` mais somente arquivos desta frente:
`/tmp/saqz-operations.cOxbD4`. JDK: 21.0.12.1. Nenhum secret foi copiado.

1. `./gradlew :features:receivables:test :features:receivables:integrationTest --tests
   br.com.saqz.receivables.OperationalReceivablesIntegrationTest :bootstrap:test --tests
   br.com.saqz.bootstrap.ReceivablesOperationsEndpointIntegrationTest --no-daemon`
   — `BUILD SUCCESSFUL in 13s`.
2. Testes relevantes: 4 `OperationalReceivablesTest`, 3 `PublicReceivablesControllerTest`,
   7 `OperationalReceivablesIntegrationTest`, 3 `ReceivablesOperationsEndpointIntegrationTest`;
   17/17, zero falhas/skips/errors.
3. `node --test adm-web/tests/operations.test.cjs landing-page/tests/receivables-public.test.cjs`
   — 6/6, zero falhas/skips.
4. Playwright visível contra servidor estático local, API/fila simulada: desktop 1280×900, mobile 390×844,
   termo publicado e retorno com `?payment=secret&status=PAID`; query não apareceu no DOM. Quatro capturas
   foram abertas e inspecionadas.

Uma primeira execução completa encontrou e permitiu corrigir um binding JDBC `NULL` de timestamp. O único
outro erro foi o teste legado `ReceivablesSchemaIntegrationTest:25`, que codifica literalmente seis migrações
e naturalmente passou a observar oito com V58/V61; o ajuste desse arquivo compartilhado foi solicitado ao
coordenador. O gate seletivo posterior passou integralmente.

## Capturas

- `docs/receivables/evidence/final-operations-admin-desktop.png` (87.607 bytes)
- `docs/receivables/evidence/final-operations-admin-mobile.png` (86.225 bytes)
- `docs/receivables/evidence/final-operations-public-terms.png` (21.920 bytes)
- `docs/receivables/evidence/final-operations-checkout-return.png` (29.461 bytes)

## Fontes oficiais verificadas

- Cobranças por `externalReference` e paginação: https://docs.asaas.com/reference/list-payments
- Consulta pontual de cobrança: https://docs.asaas.com/reference/recuperar-uma-unica-cobranca
- Webhook at-least-once/retenção: https://docs.asaas.com/docs/sobre-os-webhooks e
  https://docs.asaas.com/docs/faq-de-webhooks
- Eventos de checkout: https://docs.asaas.com/docs/eventos-para-checkout
- Extrato/lançamentos efetivos: https://docs.asaas.com/reference/recuperar-extrato
- Taxas não necessariamente devolvidas: https://docs.asaas.com/reference/refund-payment
- Estorno concluído somente em `DONE`: https://docs.asaas.com/docs/estornos

## Limites reais e integração pendente

- Não houve chamada Asaas real, sandbox real, publicação externa, push/deploy ou teste com credenciais.
- Homologação real, piloto, condições comerciais e aprovação jurídica permanecem externos e não foram
  declarados concluídos.
- A frente B ainda precisa correlacionar no extrato Asaas o lançamento de taxa pelo `paymentId` e chamar
  `ReconcileExternalResidualCost`; a porta não aceita estimativa nem valor ausente.
- O coordenador integra a allowlist pública e corrige o teste de contagem fixa de migrações.
- Não foram tocados os dois arquivos não rastreados de contexto, nem foi executado git mutante.

## Integração posterior pelo coordenador

A revisão encontrou que `reconcile` podia executar comandos remotos. O wiring final usa `reconcileObserved`,
que somente consulta o provedor, aplica fatos correlacionados e resolve CREATE/CANCEL incertos localmente,
sem interromper lease ativo. `ReceivablesRecurrenceIntegrationTest` agora atravessa a configuração real e
prova resultado positivo, estado incerto, 3 consultas e zero criações/cancelamentos/renovações.
Gate focal final: `/tmp/saqz-final-admin-probe2.log`, 7 testes, PASS.

A página estática de termos consulta a origem HTTPS da API configurada, sem credenciais. O teste Node
verifica origem, vigência e conteúdo literal; Playwright foi repetido com a nova consulta e capturas atualizadas.
Segurança pública: GETs exatos permitidos, POST/DELETE/caminhos irmãos negados e CORS público sem credenciais.
O runbook descreve o deploy separado de site/API. A conciliação de taxa observada foi conectada pela frente B.
