# Recebimento pelo app v1 · plano e contrato (DEB-140, DEB-141, DEB-142)

Escrito pela sessão principal em 26/10/2026. Regras: cofre `~/Documents/Obsidian Vault/Saqz/Regras/Financeiro/FIN-009.md`
a `FIN-014.md` (leia as seis antes de começar). Telas aprovadas pelo dono: canvas
https://claude.ai/artifact/2A6E57nf6yLXkrLYZ1oXdf; cópia local em `~/orca/workspaces/saqz/prototipos-recebimentos/`
(um `.dc.html` por tela e `TEXTOS.md` com todos os textos). **Onde o código de setembro divergir das regras, valem as regras.**

O dono decidiu desenvolver antes do teste no sandbox (DEB-138). A homologação acontece no dev, com o sandbox, depois das
entregas. Por isso o desenho abaixo segue a FIN-011 (QR estático com valor) e deixa o plano B anotado.

## Frentes

| Onda | Frente | Escopo | Depende de |
| --- | --- | --- | --- |
| 1 | F1 backend núcleo | Seções A a H abaixo | — |
| 1 | F2 app | Todas as telas do canvas contra este contrato | contrato (não espera F1) |
| 2 | F3 backend recorrência | Seção I | F1 na main |
| 2 | F4 backend hora do acerto e WhatsApp | Seção J | F1 na main |
| 3 | Dev + sandbox | `tests/sandbox-asaas/deb138.py` e roteiro ponta a ponta | F1–F4 |

Sem flag nova (FIN-009): as travas são `saqz.receivables.launch-enabled`, o controle do adm (V53) e a disponibilidade
`GET /api/receivables/availability`. No app, a constante `StoreLaunchPolicy.receivables = false` dá lugar à disponibilidade.

## O que sai do código de setembro

- A **aprovação por cobrança** (ordem por cobrança, `preview`/`approve`, `electronic_order_id` como reserva) e o
  **formulário de nome e CPF com aceite** do atleta (`orders/{id}/instruments`). Com recebimentos ativos, toda cobrança em
  aberto do grupo pode ser paga pelo app (FIN-009).
- A **recorrência Pix** (assinatura que gera um Pix por mês) e a **renovação de Pix** (`pix-renewal`).
- Rotas, telas e testes desses fluxos saem. **Tabelas e migrações antigas ficam** (nenhuma migração apaga dado).
- Ficam e são reaproveitados: cadastro financeiro e documentos, credenciais cifradas (`FinancialSecrets`), cercas de
  operação (`JdbcFinancialOperationStore`), webhook por conta e caixa de entrada de eventos, tabela de tarifas e comissão
  (adm), carteira, extrato e saque, delegação, elegibilidade e rollout.

## A · Valor do atleta fixado quando a cobrança nasce (FIN-010)

- Grupo com recebimentos ativos tem `fee_policy`: `ABSORB` (padrão ao ativar) ou `PASS`.
- `group_charges` ganha colunas **aditivas**: `payable_cents` (o que o atleta paga; nulo = `amount_cents`),
  `fee_policy`, `fee_schedule_id` (versão usada), `settled_by` (`APP` ou nulo) e `app_method` (`PIX`, `CARD` ou nulo).
- Ao criar cobrança (diária, convidado, mensalidade) num grupo ativo, o módulo de grupos pede o valor a uma porta do
  shared-kernel implementada em recebimentos. `ABSORB`: `payable = amount`. `PASS`: gross-up pela **taxa do cartão**
  (a maior entre os meios) mais a comissão, para o gestor receber `amount` no cartão.
- Cobranças que já existiam ao ativar ficam com o valor que têm (sem repasse). Trocar a política vale para as novas.
- Os DTOs de cobrança que o app já usa ganham `payableCents`, `settledBy` e `appMethod` (aditivo; app antigo ignora).
  `paid_method` continua `PIX` no Pix e `OTHER` no cartão, para não quebrar o app das lojas.

## B · Código de pagamento do atleta (FIN-011)

- `payment_codes`: conta, grupo, atleta pagador, método (`PIX`/`CARD`), valor (soma dos `payable_cents`), estado
  (`PENDING`, `PAID`, `EXPIRED`, `CANCELLED`), ids do provedor, comissão calculada, criado/pago em.
  `payment_code_charges`: as cobranças cobertas.
- Só cobranças `PENDING` do próprio atleta no grupo (as dos convidados dele também, `member_user_id` = ele).
- **Pix:** QR estático da subconta com `value`, `allowsMultiplePayments=false`, `externalReference` = id do código, sem
  expiração remota curta (o copia e cola vai no WhatsApp e pode ser pago dias depois). Reaproveita o código `PENDING` do
  mesmo atleta para o mesmo conjunto de cobranças.
- **Cartão:** cliente do Asaas por (conta, atleta) criado sob demanda com nome do perfil e CPF; cobrança `CREDIT_CARD`
  com `externalReference` = id do código e divisão da comissão (`split[].fixedValue`); devolve o `invoiceUrl`.
- **CPF uma vez:** guardado cifrado por usuário (vale para todos os grupos). Sem CPF, criar código de cartão responde
  `409 DOCUMENT_REQUIRED`.
- **Expira** quando qualquer cobrança dele deixa de estar `PENDING` (conferido ao ler e ao receber; tentar apagar o QR no
  Asaas é opcional). O app pede um novo.
- **Chave da subconta:** ao ativar (conta aprovada), garantir uma chave aleatória (EVP) pela API e guardar. Enquanto não
  existir, código Pix responde `503 PIX_NOT_READY` e o app oferece só cartão.
- Plano B (se o sandbox reprovar o QR estático): Pix vira cobrança emitida para o cliente, com CPF pedido uma vez também
  no Pix. Manter o código isolado no adaptador do provedor para a troca ser local.

## C · Baixa automática (FIN-014)

- Evento do Asaas (webhook da conta) com pagamento recebido/confirmado:
  - `pixQrCodeId` de um código do Saqz, ou `externalReference` = id de código → baixa das cobranças do código:
    `status=PAID`, `settled_by=APP`, `app_method`, evento em `group_charge_events`, idempotente pelo id do pagamento.
  - Cobrança do código já resolvida (gestor deu "Recebi" no meio): o valor quita as cobranças `PENDING` mais antigas do
    mesmo atleta no grupo (vencimento, depois criação), enquanto o valor cobrir cobranças inteiras; sobra ou nenhuma → revisão.
  - Pix sem código do Saqz → revisão (seção D). Pagamento de assinatura → F3.
- Cobrança paga pelo app não desfaz (o "Desfazer" do gestor recusa `settled_by=APP`).
- Estorno e contestação: manter o tratamento de setembro (reversão marca `review_required`, sem reabrir dívida).
- Travas: cobrança e código bloqueados (`FOR UPDATE`) na baixa, para não brigar com o "Recebi" do gestor.

## D · Revisão (FIN-014)

- `payment_reviews`: conta, grupo, id do pagamento, nome do pagador (vem do Asaas), valor, recebido em, estado
  (`OPEN`, `ASSIGNED`, `NOT_CHARGE`), quem resolveu e quando.
- Grupo da revisão: o da conta que recebeu; se a conta tiver mais de um grupo ativo, a revisão aparece em todos os grupos
  dessa conta e some de todos quando resolvida.
- "É de alguém do grupo": sugestão = cobranças `PENDING` mais antigas do membro que o valor cobre; o gestor confirma a
  lista (pode desmarcar). Baixa como `settled_by=APP`, `app_method=PIX`. Sobra vira entrada no caixa com o nome do pagador.
- "Não é cobrança": entrada no caixa (`group_expenses` `IN`) com o nome do pagador.
- O Saqz não aprende o pagador.

## E · Comissão e caixa (FIN-010, FIN-014)

- Comissão = tabela vigente (adm; `commissionRate` e `commissionFixedCents`, **0 permitido**) sobre o valor pago, gravada
  no código. Cartão: divisão na criação. Pix do QR: transferência da subconta para a carteira do Saqz
  (`POST /transfers` com `walletId`) logo depois da baixa, como operação durável e idempotente. Comissão 0 → nada.
- Caixa: a entrada é a cobrança paga. As taxas daquele pagamento (valor − líquido do Asaas + comissão) entram como saída
  automática em `group_expenses` (`OUT`, descrição "Taxas de serviço e pagamento", nova coluna `origin` =
  `RECEIVABLES_FEES` para o app marcar "automático" e impedir edição).

## F · Avisos do F1 (FIN-013, FIN-014)

Por push, usando a fila de push do módulo de grupos por uma porta no shared-kernel:
- Gestor, um por pagamento: título "Felipe Nunes pagou R$ 20,00", corpo "Jogo de terça · Vôlei do CERET" (mensalidade:
  "Mensalidade de agosto · Vôlei do CERET"). Mesmo `thread`/grupo de notificação por grupo, para o celular agrupar.
- Gestor, revisão: "Pix para revisar" · "R$ 20,00 de João P. Silva caiu fora de uma cobrança. Quem é?"
- Atleta: "Pagamento confirmado" · "R$ 40,00 · jogo de terça, Vôlei do CERET."
Destinatários "gestor": dono e admins do grupo com permissão de revisar (FIN-009).

## G · Ativar, desligar, excluir (FIN-009, DEB-029)

- Ativar: v1 sempre com Pix e cartão; `fee_policy=ABSORB`; garante a chave EVP e o webhook.
- Desligar ou perder o direito: param códigos novos; códigos `PENDING` viram `CANCELLED`; pagamentos que chegarem continuam
  dando baixa; saque e carteira continuam. A chave Pix pessoal volta a aparecer.
- Excluir o grupo (DEB-029): cancela códigos e cobranças de cartão pendentes no Asaas; as recorrências são cortadas (F3).
- O DTO do grupo que o app já usa ganha `receivablesActive` (aditivo), para esconder a chave pessoal e mostrar "Pagar".

## H · Contrato HTTP do F1

Padrão das rotas de setembro: `{value,requestId}` ou `{error,requestId}`, `Cache-Control: no-store`, `requestId` UUID em
todo POST/PUT, valores em centavos.

Gestor (dono ou admin delegado):
- `GET /api/receivables/groups/{groupId}?accountId=` (existe) — `state` ganha `feePolicy`, `pixReady`, `openReviews`.
- `POST /api/receivables/groups/{groupId}/activate` (existe) — v1 manda `methods=["PIX","CARD"]`.
- `PUT /api/receivables/groups/{groupId}/fee-policy` `{requestId,accountId,feePolicy}` → `state`.
- `GET /api/receivables/groups/{groupId}/quote?accountId=&baseCents=` →
  `{absorb:{athletePaysCents,receivesPixCents,receivesCardCents},pass:{...},pixFeeLabel,cardFeeLabel}`.
- `GET /api/receivables/groups/{groupId}/reviews?accountId=` → `{reviews:[{id,payerName,amountCents,receivedAt}]}`.
- `GET /api/receivables/reviews/{reviewId}/suggestion?memberUserId=` → `{charges:[{chargeId,label,dueDate,payableCents,overdue}],coveredCents}`.
- `POST /api/receivables/reviews/{reviewId}/assign` `{requestId,memberUserId,chargeIds}` → `{settledChargeIds,leftoverCents}`.
- `POST /api/receivables/reviews/{reviewId}/not-charge` `{requestId}` → `{expenseId}`.

Atleta (o próprio, logado):
- `GET /api/receivables/groups/{groupId}/me` → `{active,payeeName,methods,pixReady,hasDocument,openCharges:[{chargeId,kind,
  gameId,billingMonth,guestName,dueDate,payableCents,overdue}],totalCents,recurrence}` (`recurrence` nulo até a F3).
- `POST /api/receivables/groups/{groupId}/payment-codes` `{requestId,method,chargeIds}` →
  `{codeId,method,amountCents,chargeIds,status,pix:{payload,image}|null,card:{checkoutUrl}|null}`.
  Erros: `409 DOCUMENT_REQUIRED`, `409 CHARGE_NOT_PAYABLE`, `503 PIX_NOT_READY`, `403` sem recebimentos.
- `GET /api/receivables/payment-codes/{codeId}` → mesmo corpo, `status` atualizado (o app consulta a cada poucos segundos
  na tela de pagamento até `PAID` → "Pagamento confirmado").
- `PUT /api/receivables/me/document` `{requestId,cpf}` → `{hasDocument:true}` (valida dígitos; nunca devolve o CPF).

## I · Recorrência no cartão, "modo Netflix" (F3, FIN-012)

- Só mensalista; uma por pessoa por grupo; só o próprio atleta adere e cancela. Checkout do Asaas (`CREDIT_CARD` +
  `RECURRENT`) com a divisão da comissão, valor = `payable_cents` da mensalidade, ciclo mensal no dia de vencimento do
  grupo; mensalidade do mês já em aberto é cobrada na hora (e a cobrança do mês é dada como paga quando o Asaas confirmar).
- Pagamento da assinatura dá baixa na mensalidade do mês (`settled_by=APP`, `app_method=CARD`).
- Reajuste: novo valor no ciclo seguinte; push e WhatsApp particular 7 dias antes ("A mensalidade muda de R$ 100,00 para
  R$ 120,00 a partir de 10/10..."). Isenção: a cobrança do mês no Asaas é cancelada antes da data. Corte automático:
  virar avulso, sair do grupo, grupo desligar ou perder o direito.
- Cancelar: motivo obrigatório (`STOP_PLAYING`, `PREFER_PIX`, `CARD_PROBLEM`, `TOO_EXPENSIVE`, `OTHER`, `PREFER_NOT_SAY`);
  continua mensalista; push ao gestor "Thiago Melo cancelou o automático" · "As próximas mensalidades dele chegam para
  pagar por Pix."
- Recusado: push e WhatsApp particular ao atleta com o copia e cola; push ao gestor; cobrança fica em aberto.
- Rotas: `GET/POST /api/receivables/groups/{groupId}/recurrence` (`POST` → `{checkoutUrl}`),
  `POST .../recurrence/cancel` `{requestId,reason}`, `POST .../recurrence/card` → `{checkoutUrl}` (trocar o cartão),
  `GET /api/receivables/groups/{groupId}/recurrences?accountId=` (gestor: `[{memberUserId,status}]`, status `ACTIVE`,
  `REFUSED`). `recurrence` no `/me`: `{status,amountCents,nextChargeDate,refusedChargeId,history:[...]}`.

## J · Hora do acerto e WhatsApp (F4, FIN-013)

- Jogo: 30 min depois do fim (início + duração), só com diária e alguém devendo. Mensalistas: no dia de vencimento, 09:00.
- Grupo do WhatsApp (vínculo ativo, NOT-008): imagem + texto e botão de URL no formato do lembrete de presença
  (`/send/menu`). Jogo: "Jogo finalizado, hora do acerto" / botão "Pagar meu jogo". Mensal: "Mensalistas, hora do
  acerto" / "Pagar mensalidade". Nunca diz quem deve.
- Botão: link no padrão do link de presença (mesmo domínio e mesmas regras: sem app → loja; deslogado → login), com um
  código por (grupo, jogo) ou (grupo, mês), no caminho `/acerto/{codigo}`. O app resolve com
  `GET /api/receivables/settle-links/{codigo}` → `{groupId,gameId,billingMonth}` e abre o pagamento do atleta logado com as
  cobranças em aberto daquele jogo (dele e dos convidados) ou daquela mensalidade, filtradas do `/me`. Quem não deve vê
  "Você não tem nada em aberto aqui". Push "Hora do acerto" e "Cobrar" abrem o mesmo destino.
- Push a cada devedor: "Hora do acerto" · "R$ 40,00 do jogo de terça no Vôlei do CERET. Toque para pagar." Quem está no
  cartão não recebe a dos mensalistas.
- WhatsApp particular ("Cobrar", FIN-005/NOT-005) em grupo ativo: sai a chave pessoal; entra o botão "Pagar no app" e,
  **numa segunda mensagem**, só o copia e cola com o valor (código da seção B).
- Sai mesmo depois das 22h (aprovado pelo dono).

## Fora da v1

Pix Automático, recorrência Pix, devolução pelo Saqz, comprovante (DEB-143), tela nova no adm.
