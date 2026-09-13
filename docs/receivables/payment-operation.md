# Preparação operacional dos pagamentos avulsos

Este roteiro prepara homologação. Não registra uma homologação Asaas realizada e não habilita produção.
As condições comerciais reais e os termos devem ser definidos pelo responsável pela operação.

## Configuração do backend

Além das credenciais cifradas e demais pré-requisitos da fundação de recebimentos, o adaptador de
pagamentos usa estas propriedades de deployment:

- `saqz.receivables.payments-enabled=true`: compõe controllers, adaptador e recuperação.
- `saqz.receivables.asaas-base-url`: URL do ambiente Asaas correto.
- `saqz.receivables.platform-wallet-id`: carteira Saqz que recebe o split fixo.
- `saqz.receivables.webhook-base-url`: origem HTTPS pública do backend, sem query/fragmento.
- `saqz.receivables.webhook-email`: contato operacional do webhook.
- `saqz.receivables.recovery-delay-ms`: intervalo do scheduler; padrão de 60 segundos.

O guard de deployment não substitui o rollout administrativo. Após existir uma ordem, não desligar
`payments-enabled` para suspender novos negócios: isso também removeria manutenção, webhook e scheduler.
Usar os controles BACKEND/MOBILE e a liberação operacional da conta, conforme [operação de rollout](rollout-operation.md).
Nunca copiar API keys, tokens, documentos ou dados bancários para logs, screenshots ou tickets.

## Sequência de homologação

1. Publicar termos e condições comerciais de teste autorizadas pelo adm, com vigência apropriada.
   O simulador não publica nem inventa tarifas. Conferir separadamente base, processamento e comissão.
2. Concluir cadastro voluntário da conta financeira na plataforma e confirmar sua aprovação e credenciais.
   A subconta sandbox criada fora da plataforma anteriormente não constitui vínculo automático com o banco Saqz.
3. Configurar webhook pela rota autenticada `POST /api/receivables/accounts/{accountId}/webhook`, com
   `requestId` e titular financeiro. O backend gera e cifra o token antes do envio ao provedor.
4. Conferir a chave Pix da subconta antes do piloto. O fluxo regular e o fallback sem chave possuem
   expirações diferentes; não interpretar a leitura do QR como prova de renovação.
   Referência: [cobranças Pix do Asaas](https://docs.asaas.com/docs/cobrancas-via-pix).
5. Liberar os usuários de teste nos sistemas necessários. Ativar o grupo após escolher Pix, cartão ou
   ambos, revisar condições e aceitar. Nenhum meio selecionado deve impedir a ativação.
6. Selecionar expressamente uma cobrança existente, consultar preview e aprovar a ordem. Conferir
   `chargeId`, pagador, valor base, competência original e snapshot das condições.
7. Como pagador original, consultar a ordem, aceitar meio/total e solicitar instrumento com o mesmo
   `requestId` nos reenvios. Cartão abre `invoiceUrl` do Asaas; nenhum PAN/CVV passa pela plataforma.
8. Confirmar o resultado por webhook e consulta autenticada, inclusive quando o navegador volta antes
   do webhook. Conferir pagamento, disponibilidade, split e reflexo único no caixa como fatos distintos.
9. Exercitar timeout, webhook duplicado/fora de ordem, cancelamento concorrente e desligamento do rollout.
   Uma ordem já emitida deve continuar consultável e pagável; não gerar outra dívida para contornar falha.
10. Encerrar a homologação desligando novos negócios pelos controles administrativos, mantendo
    conciliação e manutenção. Registrar evidências sem dados pessoais nem segredos.

## Limites a conferir antes do piloto

Este lote não entrega a jornada completa de pagamento do membro no mobile, carteira/saque,
reembolso solicitado, recorrência ou corte remoto de ciclos. A configuração do grupo no mobile
é uma entrega separada do checkout do pagador. A renovação de instrumento Pix expirado exige
validação específica; o GET do QR não é uma implementação automática dessa renovação.

Uma consulta simulada ao provedor e testes PostgreSQL locais não substituem o teste integral
na subconta sandbox, nem comprovam condições de produção. Os relatórios da entrega devem registrar
quais cenários foram efetivamente executados e quais dependem dessa homologação.
