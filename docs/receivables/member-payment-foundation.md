# Pagamento do membro — descoberta e contrato mobile

Continuação de T14/T16. Este lote entrega a consulta paginada das ordens do próprio pagador
 e o gateway KMP para listar, consultar, criar instrumento e conciliar. A tela de pagamento,
 a aprovação de cobranças pelo gestor, comprovante visual e renovação Pix permanecem nas tarefas existentes.

Premissas: ordens já aprovadas são manutenção, sem nova consulta comercial. Identidade vem da
sessão autenticada; o cliente nunca escolhe o pagador na listagem. O contrato financeiro vigente
em payment-http-contract.md continua autoritativo. Nenhuma chamada ao Asaas real neste lote.

## Passos e arquivos

1. Backend: OneOffPayments.kt, JdbcPaymentStore.kt, OneOffPaymentsController.kt e
   OneOffPaymentsIntegrationTest.kt; consulta paginada sem efeitos externos. Commit próprio após gate.
2. Mobile: MemberPayments.kt (domain), KtorMemberPaymentsGateway.kt e transports (data), teste
   KtorMemberPaymentsGatewayTest.kt, ReceivablesModule.kt e SaqzKoinModulesTest.kt (composition root).
   Gate Android/iOS, DI, compilação e detekt; commit próprio.
3. Verificação independente do delta e registro das evidências; atualização de STATE/tasks.

## Critérios de aceite

- AC1: GET /api/receivables/orders retorna {orders,nextCursor} em envelope no-store.
  Somente ordens cujo payerId é o ator, incluindo terminais. Owner/delegate não recebem ordens de
  outros pagadores nesta listagem. Sem ordens retorna lista vazia e cursor nulo.
- AC2: acesso continua após rollout OFF, plano expirado, saída do pagador e exclusão do grupo.
  Consulta não cria instrumentos, não consulta provedor e não altera caixa.
- AC3: páginas de até 50 ordens por issued_at DESC, id DESC, com after UUID opcional.
  Cursor de outro pagador ou inexistente retorna vazio; UUID malformado retorna 400.
  Página seguinte não repete nem perde ordens do conjunto estático. Dados Pix e CPF não entram na lista.
- AC4: gateway autenticado mapeia página, detalhe, cotações em centavos, termos/fingerprint,
  Pix, checkout hospedado, expiração e status atuais sem confundir marcos históricos com saldo.
- AC5: criar instrumento envia somente requestId, method, fingerprint, accepted e payer{name,cpfCnpj}.
  Conciliação envia somente requestId. Retry de escrita conserva exatamente corpo e requestId;
  gateway nunca cria instrumento como consequência de leitura/conciliação ou de estado UNKNOWN.
- AC6: falhas 401→SIGNED_OUT, 403/404→DENIED, 409→STALE, 400→INVALID.
  Falha de transporte/5xx/JSON inválido/envelope incompatível em escrita→UNCERTAIN;
  leitura sem resposta válida falha sem produzir dados. IDs de ordem/instrumento, fingerprint,
  valores e meio precisam corresponder ao recurso/comando antes de aceitar resposta de escrita.
  Nenhum retorno do checkout é interpretado como confirmação; status REFUNDED permanece REFUNDED
  mesmo com confirmed/available históricos true.

## Gates

- Backend: JAVA_HOME=$(/usr/libexec/java_home -v 21) DOCKER_HOST=unix://$HOME/.colima/default/docker.sock backend/gradlew -p backend :bootstrap:test --tests '*OneOffPaymentsIntegrationTest' :architecture-tests:test
- Mobile: JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:receivables:data:testAndroidHostTest :features:receivables:data:iosSimulatorArm64Test :features:receivables:data:detektAll :features:receivables:domain:detektAll :compose-app:compileKotlinIosSimulatorArm64 :android-app:compileDevDebugKotlin
- DI: comandos focados existentes em SaqzKoinModulesTest, sem afirmar execução de allTests NO-SOURCE.

## Gate do passo 1

2026-09-13: comando backend acima terminou com exit 0; 31 testes OneOffPaymentsIntegrationTest
(28 existentes + 3 novos) e 20 BackendArchitectureTest, sem falhas. Log local /tmp/saqz-member-backend.log.

| Critério | Evidência em OneOffPaymentsIntegrationTest.kt | Resultado esperado |
|---|---|---|
| AC1/AC2 | :484 `assertEquals(listOf(order.copy(status = "REFUNDED")), page.orders)`; :487 `assertEquals(PaymentOrderPage(emptyList(), null), ...)` | histórico próprio inclui terminal após corte, terceiros recebem vazio |
| AC2 sem efeitos | :490–494 `assertEquals(0, ...)`, `assertEquals("PENDING", ...)` | nenhum customer/payment/instrument/caixa alterado |
| AC3 paginação | :507 `assertEquals(listOf(original.id) + ids.reversed().take(49), ...)`; :510 `assertEquals(listOf(ids[1], ids[0]), ...)`; :512 `assertEquals(52, ...)` | 50 + 2 ordens, sem perda/repetição |
| AC3 isolamento cursor | :513–516 `assertEquals(PaymentOrderPage(emptyList(), null), ...)` | cursor estrangeiro/inexistente não revela dados |
| AC1/AC3 HTTP | :530–544 asserts status=200, Cache-Control=no-store, id/payerId exatos, nextCursor nulo, sem cpfCnpj/pixPayload, cursor inválido=400 e outro ator=vazio | envelope e identidade autoritativos |

Mapeamento reverso: os três testes novos :475, :497, :519 cobrem respectivamente AC1/AC2,
AC3 e AC1/AC3 HTTP. Nenhum teste especulativo; os 28 anteriores foram preservados.
Adequação: aprovado para o passo 1; revisão independente final ainda pendente.
