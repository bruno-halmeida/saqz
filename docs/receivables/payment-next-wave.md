# Próxima onda — primeiro pagamento avulso

Documento de preparação do coordenador. Não altera o contrato de rollout nem autoriza produção.

## Dependências e propriedade

Primeiro fechar toggles/segmentação backend, adm e mobile. A onda financeira terá emissão,
idempotência e conciliação sob um mesmo implementador backend. Mobile consome contrato financeiro
publicado; adm acompanha ocorrências depois que houver consultas operacionais. Evitar que dois
agentes alterem estados de pagamento e efeitos no caixa simultaneamente.

## Pontos já encontrados no código

- receivable_orders liga uma única group_charge_id; cobrança mensal tem índice único de competência.
- receivable_instruments tem um único instrumento vivo por ordem; snapshots monetários e referências
  de payment/checkout/split já têm colunas, mas payload Pix/URL ainda requer armazenamento apropriado.
- RunFinancialOperation recupera operações incertas, sem repetir execute. Cliente/customer também
  precisa de criação recuperável; não basta proteger somente POST payments/checkouts.
- ChargeManagement.status e JdbcChargeTransactionRepository.reconcileCanceledGame alteram cobranças
  manuais. Ambos precisam respeitar o fechamento/consulta dos instrumentos eletrônicos. Proteger só
  o botão do app ou só ChargeManagement não cobre cancelamento de jogo.
- Extrato e resumo financeiro de grupos derivam de group_charges e group_charge_events. A conciliação
  deve aplicar efeito no caixa uma vez, pela interface compartilhada composta no bootstrap.
- Nova ordem exige plano/rollout/cadastro/grupo, mas pagar/renovar instrumento de ordem já aprovada
  não cria dívida nova e permanece acessível após corte ou saída do pagador do grupo.

## Critérios da entrega

1. Gestor aprova ordem vinculada a cobrança PENDING e a preços/condições revisados; nenhuma captura
   silenciosa de pendência antiga. Payer só cria instrumento para sua ordem; valor vem do servidor.
2. Aceite do pagador registra versão dos termos, base, taxas agregadas e total por meio. Tarifa e
   comissão fixas/percentuais seguem FeeCalculator; split Saqz enviado como valor fixo em reais.
3. Pix retorna QR e copia-e-cola; cartão usa checkout hospedado, sem PAN/CVV no Saqz.
4. Identificadores externos e operações persistidos antes de chamada; timeout entra em recuperação,
   nunca permite nova cobrança concorrente. Callback web não confirma pagamento.
5. Webhook autenticado por conta, evento cifrado persistido antes do ACK, deduplicação por conta+id;
   processador tolera ordem invertida, valida valores/referências e recupera eventos perdidos por consulta.
6. Confirmação, liquidação, disponibilidade e split são fatos distintos. Divergência gera ocorrência;
   valor adicional nunca cobrado retroativamente. Reversões não reabrem dívida automaticamente.
7. Testes PostgreSQL/HTTP/provedor simulado para concorrência pagamento/baixa/cancelamento, replay,
   isolamento de subconta, callback sem webhook e acesso após corte. Homologação sandbox separada.

## Referências verificadas em 2026-09-13

- https://docs.asaas.com/docs/checkout-asaas
- https://docs.asaas.com/reference/criar-novo-checkout
- https://docs.asaas.com/docs/checkout-com-split-de-pagamento
- https://docs.asaas.com/docs/eventos-para-checkout
- https://docs.asaas.com/docs/link-do-checkout-e-redirecionamento-do-cliente
- https://docs.asaas.com/reference/criar-nova-cobranca

Consultar schema completo e endpoints de recuperação antes de fixar DTOs. Não pressupor que o
checkout aceita a mesma propriedade split das cobranças; não pressupor idempotência remota.
