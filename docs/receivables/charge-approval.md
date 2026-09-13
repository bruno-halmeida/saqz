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
de gateways existente. Assertions :18–20 autenticam/isolam lookup; :23 preserva detalhe/vazio;
:30–35 payload e revisão; :39–49 corpo exato e retry; :54–61 cancelamento pendente; :70–83 identidades/
fingerprint/centavos; :90–101 erros; :107–109 envelope incerto; :115–119 comandos inválidos não enviam.
Android/iOS data + detekt domain/data, exit 0 em /tmp/saqz-charge-gateway.log.
