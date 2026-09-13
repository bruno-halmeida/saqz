# Contrato mobile final — recorrência, renovação Pix e comprovante

**Data:** 2026-09-13\
**Escopo:** F3 / F4 / T16\
**Contrato servidor:** `docs/receivables/final-recurrence-contract.md`\
**Estado:** fixado antes do código; rotas de descoberta/recovery confirmadas pela frente backend B

## Fronteira e ownership

Esta frente entrega no código compartilhado KMP a jornada do próprio pagador para revisar,
autorizar, consultar, cancelar e retomar recorrência mensal; renovar o Pix vencido da mesma
obrigação; e compartilhar um comprovante textual somente quando o estado remoto observado permite.
Android e iOS consomem a mesma UI e a mesma máquina de estados.

O worker mobile possui somente novos arquivos `Recurrence*`, `PixRenewal*`, `ReceiptExport*`,
recursos/testes correspondentes e os arquivos existentes `MemberPayment*` dentro de
`mobile/features/receivables/{domain,data,presentation}`. O coordenador possui `ReceivablesModule`,
`SaqzNavHost`, configuração de navegação local e a bridge de plataforma. Arquivos Wallet,
FinancialOnboarding, Management, NativeAuth, bootstrap e plataformas não pertencem a esta frente.

Assinatura publicada para a bridge do coordenador:

```kotlin
sealed interface ReceiptExportResult {
    data object Shared : ReceiptExportResult
    data object Failed : ReceiptExportResult
}

fun interface ReceiptExportPort {
    fun export(text: String, done: (ReceiptExportResult) -> Unit)
}
```

A implementação no composition root deve delegar ao `NativeSharePort` já real e mapear o callback
`OperationResult.Success/Failure` para `ReceiptExportResult.Shared/Failed`. O callback relata o
resultado real do launcher; a UI não anuncia sucesso antes dele.

## Pressupostos vinculantes

- Todo dinheiro é `Long` em centavos BRL vindo do servidor e exibido com o formatador compartilhado;
  o cliente não recalcula tarifa, comissão, líquido ou total.
- Pix recorrente é uma assinatura mensal com cobrança Pix manual. A UI nunca usa o texto “Pix
  Automático”. Cartão abre somente checkout HTTPS hospedado no domínio Asaas e o app nunca recebe
  PAN, CVV, validade ou token.
- Revisão e aceite formam um conjunto indivisível de `accountId`, `groupId`, membro autenticado,
  método, valores, termos, primeiro vencimento e `fingerprint`. Trocar método, revisão ou retomada
  limpa o aceite anterior.
- Retomada parte apenas de recorrência `STOPPED`, repete preview e exige novo `requestId`, novo
  fingerprint e novo aceite. A linha anterior continua histórica; callback do navegador não ativa.
- Cancelamento mostra `STOP_PENDING` enquanto a prova remota não existe. Fatos financeiros já
  ocorridos, vencidas e avulsas permanecem visíveis; não há ação de pedir, aprovar ou executar
  reembolso.
- Perda de plano/elegibilidade impede nova autorização/retomada, mas não impede consulta,
  cancelamento, comprovante nem renovação do Pix vencido de obrigação já existente.
- Renovação Pix usa somente `POST /api/receivables/orders/{orderId}/pix-renewal` e mantém order,
  instrument, provider payment, quote, aceite e snapshots. O mobile nunca cria outra ordem ou
  instrumento para renovar.
- PII do pagador (nome/documento) existe somente no estado e comando em memória. Persistência de
  tentativa contém apenas ator autenticado, conta, grupo, recurso, operação e `requestId`.
- Antes de qualquer mutação de rede, o marcador não sensível está integralmente salvo. Resposta de
  geração/sessão antiga é descartada; logout limpa formulário e marcadores que não pertencem ao
  ator atual.
- `202 RESULT_PENDING`, timeout, perda de conexão e envelope malformado/correlacionado incorretamente
  preservam incerteza. Recuperação nunca dispara uma segunda criação.

## Dependências contratuais do backend B

O mobile recebe inicialmente somente `accountId/groupId`. B confirmou descoberta autenticada em
`GET /api/receivables/recurrences/current?accountId=...&groupId=...`, que devolve a recorrência
própria relevante ou `value:null`, incluindo a `STOPPED` mais recente quando não há recorrência
viva. B confirmou recovery de authorize/resume/cancel em
`GET /api/receivables/recurrences/by-request/{requestId}` e de renovação em
`GET /api/receivables/orders/{orderId}/pix-renewal/{requestId}`. Essas leituras não criam nem
repetem operação; 404 durante recovery é ausência ainda pendente e não autoriza novo POST.

O GET por id é recovery suficiente somente depois que esse id já foi observado e persistido no
marcador não sensível. O retorno `202` continua pendente mesmo que contenha snapshot parcial.

## Critérios de aceite rastreáveis

- **RM-01 — descoberta:** ao abrir com `accountId/groupId`, a jornada consulta somente a recorrência
  do ator autenticado e distingue ausência, viva e `STOPPED` mais recente; recurso estrangeiro falha
  fechado.
- **RM-02 — revisão literal:** preview exibe método, primeiro vencimento, ciclo mensal, termos,
  base/tarifas/total/comissão/taxa estimada/líquido em centavos exatamente como recebidos.
- **RM-03 — aceite explícito:** autorizar só habilita com termos reais carregados, pagador válido e
  aceite marcado; o comando leva o fingerprint exato e não persiste PII.
- **RM-04 — Pix/cartão honestos:** Pix é descrito como pagamento manual mensal; cartão abre somente
  checkout Asaas validado. Retorno do navegador apenas força leitura remota, nunca confirmação.
- **RM-05 — idempotência e recovery:** authorize, resume, cancel e renewal registram
  ator/conta/grupo/recurso/operação/request antes da rede; timeout bloqueia nova mutação e recupera
  pelo mesmo request sem duplicar cobrança.
- **RM-06 — cancelamento:** cancelar bloqueia interação conflitante; `STOP_PENDING` permanece
  pendente e `STOPPED` só aparece por resposta/leitura remota. Histórico não é apagado.
- **RM-07 — retomada:** somente `STOPPED` oferece retomada; novo preview limpa aceite e a submissão
  cria nova recorrência, mantendo o id anterior como histórico.
- **RM-08 — corte de plano:** erro `INELIGIBLE_PLAN`/403 bloqueia autorizar e retomar, mas a tela de
  obrigação existente continua oferecendo renewal elegível e comprovante elegível.
- **RM-09 — renovação Pix:** somente instrumento Pix vencido de ordem ainda pendente habilita nova
  data; sucesso exige mesmos `orderId`, `instrumentId`, `providerPaymentId`, método e snapshots, com
  alteração apenas de vencimento/artefatos/expiração/status.
- **RM-10 — tempo:** expiração observada pelo relógio desabilita copiar Pix sem depender de reload;
  data de renovação inválida não chega à rede; resposta atrasada de geração antiga não vence.
- **RM-11 — comprovante real:** compartilhar só habilita para estado remoto pago/confirmado/liquidado/
  disponível, usa snapshot real não sensível (referência, ids, método, vencimento, valores e estado)
  e mostra sucesso/falha somente após callback real do port.
- **RM-12 — conteúdo fechado:** payload estrangeiro, dinheiro negativo/inconsistente, fingerprint
  inválido, URL não Asaas, Pix sem payload/expiração ou receipt com conteúdo incorreto falham
  fechados e nunca disparam efeito externo.
- **RM-13 — sessão:** todo carregamento e efeito carrega geração; troca de sessão/ator limpa PII,
  cancela timers e descarta callbacks antigos.
- **RM-14 — UI compartilhada:** telas, estados, strings PT-BR, tags e previews ficam em commonMain e
  são exercitados no iOS; captura Android inclui vazio, revisão, pendente, stopped, Pix expirado,
  renewal e comprovante sucesso/falha.

## Matriz de verificação

| Camada | Prova obrigatória | Gate em scratch |
|---|---|---|
| Domain | validação de snapshots, status elegíveis, receipt e comandos 1:1 com RM-01..RM-13 | `:features:receivables:domain:testAndroidHostTest` e `:features:receivables:domain:iosSimulatorArm64Test` |
| Data | MockEngine: URL/método/body exatos, Bearer, dinheiro literal, envelope/request, foreign ids, 202/4xx/5xx, timeout e retry idempotente | `:features:receivables:data:testAndroidHostTest` e `:features:receivables:data:iosSimulatorArm64Test` |
| ViewModel | fake direto: aceite, cancel/resume, marker-before-network, process death recovery, geração/sessão, plano cortado, expiry e share callback | `:features:receivables:presentation:testAndroidHostTest` (não inclui commonTest) e `:features:receivables:presentation:iosSimulatorArm64Test` |
| UI | tags/conteúdo/interações em iOS e capturas Roborazzi inspecionadas para todos os estados alterados | `:features:receivables:presentation:iosSimulatorArm64Test` e `recordRoborazziAndroidHostTest` filtrado |
| Qualidade | compilação Android+iOS, detekt e `git diff --check`; nenhum teste apagado/enfraquecido | tasks diretas no scratch, serializadas |

Builds são feitas apenas numa cópia temporária contendo a base e os arquivos desta frente, com
`local.properties` copiado apenas para localizar o SDK. Nenhum segredo, chamada real Asaas,
publicação, git mutante ou mudança de configuração local faz parte desta validação. A homologação
real do provedor e a revisão fresca autor != verificador ficam separadas da autoria.

## Wiring solicitado ao coordenador

1. Registrar `RecurrenceGateway`, `PixRenewalGateway` e `ReceiptExportPort` no `ReceivablesModule`,
   sendo o último um adapter sobre `NativeSharePort`.
2. Adicionar rotas escalares serializáveis de recorrência à configuração local e ao `SaqzNavHost`.
3. No `MemberPaymentRoot`, consumir callback `(accountId: String, groupId: String) -> Unit` emitido
   somente a partir do detalhe próprio já validado; empilhar a rota de recorrência no composition root.
4. Não interpretar retorno do checkout como confirmação; no resume da tela, apenas refrescar GET.

## Dimensões implícitas

| Dimensão | Resolução |
|---|---|
| Validação | ids/textos/status/centavos/data/fingerprint/URL/payload validados e falha fechada |
| Falha parcial | estado pendente persistido antes da rede; `202`/timeout recuperável |
| Idempotência | requestId estável por tentativa; mesma operação nunca vira segundo POST |
| Autorização | apenas ator autenticado/pagador; 403/404 não revelam recurso |
| Concorrência | UI serializa intents e backend garante unicidade; resposta stale é descartada |
| Ciclo de vida | PII só memória; marker não sensível vive até desfecho remoto/logout incompatível |
| Observabilidade | marker contém ator/conta/grupo/recurso/operação/request, sem PII |
| Dependência externa | checkout/cobrança incertos permanecem pendentes; sem confirmação por callback |
| Transições | ações habilitadas por matriz explícita de status; unknown falha fechado |

**Questões abertas:** nenhuma. A frente backend B confirmou o contrato executável acima durante a
implementação; nenhuma rota foi inferida pelo mobile.
