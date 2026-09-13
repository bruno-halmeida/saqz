# Liberação de cobranças pelo gestor no mobile

Base f765e19d. Continuação autorizada para fechar recebimentos. Primeiro grupo: T10/T14/T16,
selecionar uma cobrança existente no caixa, revisar todos os meios configurados, liberar ao pagador
original, recuperar resultado incerto e cancelar ordem existente. Não libera produção.

## Passos e arquivos

1. Consulta local GET charges/{chargeId}/order?accountId no OneOffPayments/Controller e teste
   OneOffPaymentsIntegrationTest; contrato HTTP. Gate bootstrap pagamentos + arquitetura.
2. ChargeApprovalGateway e transporte Ktor no domain/data de receivables; testes Android/iOS,
   envelopes, identidade/snapshot, erros, requestId e recuperação idempotente; detekt.
3. ChargeApprovalContract/ViewModel/Screen/Root e strings em receivables presentation, DI/rotas
   compose-app, callback do caixa de grupos. Gates VM/UI Android+iOS, navegação/DI, compilações,
   detekt e capturas inspecionadas. Entrada manual permanece funcionando.
4. Verificação independente fresca com >=5 mutações em cópia temporária, evidências e commits.

## Critérios

- CA1: consulta pela cobrança é somente leitura, exige titular financeiro/delegado válido e conta
  exata; pagador/membro/terceiro não acessa. Sem ordem retorna {detail:null} apenas para conta titular
  do grupo atual; ordem existente permite manutenção após corte/rollout OFF/transferência/exclusão.
  Não cria operações nem consulta o provedor. Sem sessão e UUID inválido mantêm erros HTTP existentes.
- CA2: gateway autentica e valida IDs charge/account/group, fingerprint, métodos e centavos; nenhum
  dado de outra conta/cobrança vira sucesso. Preview, approve e cancel usam requestId; retry de escrita
  mantém corpo exato. Resposta inválida/timeout/5xx de escrita é UNCERTAIN; 400/409 exige nova revisão;
  401 encerra sessão, 403/404 oculta recurso. Consulta vazia não é erro.
- CA3: gestor seleciona cobrança explicitamente no caixa e conta da lista autorizada. Consulta ordem
  existente antes do preview; revisão exibe vencimento, base/taxas/total por meio e todos os termos
  das versões exatas; aceite explícito habilita liberação. Troca de conta/refresh invalida aceite.
  Nenhuma adesão automática de pendências nem coleta de dados do cartão/pagador pelo gestor.
- CA4: antes de approve/cancel salva marker não sensível por usuário/grupo/cobrança/conta/comando.
  Resultado incerto trava edição/novo comando; recuperação consulta primeiro e permite replay explícito
  idêntico, também após process death. Consulta sem ordem preserva aprovação incerta; erro preserva marker.
  Aprovação encontrada encerra pendência e mostra status; cancelamento só é concluído com CANCELLED,
  CANCEL_PENDING mantém reserva/acompanhar. Cancelar requer confirmação explícita, não indica reembolso.
- CA5: geração e chave de sessão descartam respostas/efeitos antigos; logout limpa dados/marker,
  restauração de outro usuário é descartada. Ação em fila revalida o contexto no consumo.
- CA6: telas acessíveis usam DS/strings/tags/previews, empty/loading/erro/retry e cobrança liberada,
  conflito, incerto, cancelamento pendente/final capturados. Rotas usam somente IDs e restauram;
  retorno recarrega caixa. Autorização definitiva no backend; entrada não substitui a permissão.

## Limites de fechamento

Este grupo fecha emissão manual e manutenção de ordens pelo gestor. Carteira, cadastro financeiro,
recorrência, renovação Pix, operação e homologação seguem no plano maior e serão tratados na sequência.
Termos/tarifas reais e validação integrada de produção não serão inventados.

## Gate CA1

Backend: 36 OneOffPaymentsIntegrationTest + 20 architecture tests passed, exit 0
in /tmp/saqz-charge-lookup.log. Four new tests assert exact empty/detail response, unchanged
operations/charge status, owner/delegate/revocation/account isolation, transfer/deletion, HTTP
200/400/404 and no-store. Source assertions are in OneOffPaymentsIntegrationTest from line560;
final independent matrix will preserve exact expressions and line numbers.

## Gate CA2

Oito casos novos KtorChargeApprovalGatewayTest aprovados nas duas plataformas, junto da regressão
de gateways existente. Assertions KtorChargeApprovalGatewayTest :17–21 autenticam/isolam lookup; :27–31 payload/revisão;
:41–44 ordem/corpo exato/retry; :54–55 cancelamento pendente; :66–76 identidades/fingerprint/centavos;
:82–89 erros; :94–95 envelope incerto; :100–104 comandos inválidos não enviam.
Android/iOS data + detekt domain/data, exit 0 em /tmp/saqz-charge-gateway.log.

## Gate UI e matriz do autor

53 testes presentation Android + 56 iOS (10 VMs novos e 1 UI iOS nova), DI6 Android +6 iOS,
navegação1 iOS, UI3 Android, entrada1 Android passaram. Compilações Android/iOS e detekt dos
módulos alterados passaram; logs /tmp/saqz-charge-ui-build.log e /tmp/saqz-charge-ios-final.log.
Somando backend56 e data60, são 242 execuções no escopo (não são 242 casos novos).
Capturas em mobile/build/reports/charge-approval: revisão com dois preços/termos, aceite,
incerteza, confirmação/cancelamento e caixa inspecionados. Dados e nomes nas capturas são fixtures.

VT = mobile/features/receivables/presentation/src/commonTest/kotlin/br/com/saqz/receivables/presentation/ChargeApprovalViewModelTest.kt.
ST = mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/ChargeApprovalScreenshotTest.kt.
IT = mobile/features/receivables/presentation/src/iosTest/kotlin/br/com/saqz/receivables/presentation/ChargeApprovalScreenTest.kt.

| Critério | Asserção e resultado esperado |
|---|---|
| CA3 escolha, termos, aceite | VT:24 `assertNull(accountId)`, :27 `assertEquals(listOf("v1", "v2"), termRequests)`, :28 revisão inteira/sem aceite, :32–35 target/fingerprint/accepted e ordem exatos; :41 sem termos não libera; :45–47 refresh/troca invalidam |
| CA3 ordem existente | VT:51 ordem exata e `assertEquals(0, previews)`; :52 `approvals.isEmpty()` |
| CA4 restauração/replay | VT:57 marker antes da rede; :60 objeto inteiro; :65/67 marker igual após ausência/erro; :70 não escreve ao restaurar; :71 comando inteiro igual; :73 marker removido ao confirmar; :80 consulta resolve sem novo write |
| CA4 cancelamento | VT:85 `cancels.isEmpty()` sem confirmar; :89 orderId salvo antes da rede; :91 status `CANCEL_PENDING` e attempt presente; :94 replay igual; :96–97 marker ausente somente após `CANCELLED` |
| CA4 terminais/conflito | VT:104 não cancela/libera terminais; :110–113 STALE remove revisão/aceite e obriga revisar |
| CA5 contexto | VT:121 `REFUNDED` vence resposta antiga; :123 sessão limpa; :127 efeito obsoleto inválido; :133 marker estrangeiro descartado; :137–140 duplicata bloqueada e logout descarta criação |
| CA6 interface | ST verifica valores literais Pix100/6,58/106,58 e cartão100/9/109, versões e ações; IT:17 submit desabilitado, :20 ação Approve, :22 ausência de confirmação inicial, :24/27 ações exatas e :29 cancelamento pendente |

Todos os testes novos mapeiam aos critérios acima: VT10→CA3/4/5; ST3+IT1+entrada1+rotas/DI→CA6;
gateway8→CA2; backend4→CA1. Verificação independente ainda pendente. Nenhum teste existente
foi removido, ignorado ou teve asserções enfraquecidas. UAT humano e sandbox real não executados.
