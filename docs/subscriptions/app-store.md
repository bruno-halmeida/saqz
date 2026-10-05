# Assinatura pela App Store (In-App Purchase)

O iOS vende a assinatura do Saqz pela App Store (StoreKit 2). A web continua
vendendo pelo Asaas, e as cobranças dos grupos (mensalidade da quadra) continuam
no Asaas: são serviço presencial, fora do In-App Purchase.

O backend é a fonte da verdade do acesso. O app só compra e entrega a transação
assinada; quem decide se a conta tem plano é o backend, a partir da transação
verificada e das notificações da App Store.

## Produtos

Um único grupo de assinatura, **Saqz**, com seis assinaturas auto-renováveis. O
nível define upgrade e downgrade dentro do grupo: a Apple aplica upgrade na hora
(com reembolso proporcional) e downgrade só na renovação.

| Product ID | Plano | Ciclo | Nível no grupo |
|---|---|---|---|
| `app.saqz.ilimitado.mensal` | `ILIMITADO` | `MONTHLY` | 1 |
| `app.saqz.ilimitado.anual` | `ILIMITADO` | `ANNUAL` | 1 |
| `app.saqz.organizador.mensal` | `ORGANIZADOR` | `MONTHLY` | 2 |
| `app.saqz.organizador.anual` | `ORGANIZADOR` | `ANNUAL` | 2 |
| `app.saqz.titular.mensal` | `TITULAR` | `MONTHLY` | 3 |
| `app.saqz.titular.anual` | `TITULAR` | `ANNUAL` | 3 |

O mapeamento product ID → plano/ciclo existe só no backend e chega ao app por
`GET /plans`. O preço exibido no app vem sempre do StoreKit (`displayPrice`),
nunca do backend: é a App Store que cobra.

O teste grátis de 14 dias continua sendo o do backend, igual para todos. Os
produtos **não** têm oferta introdutória.

## Vínculo com a conta

Toda compra leva `appAccountToken` = id do usuário no Saqz (UUID), obtido em
`GET /subscriptions/app-store/account-token`. A transação original fica presa à
conta que a comprou: outra conta Saqz no mesmo Apple ID não herda a assinatura.

## Contrato HTTP

### `GET /plans`

Cada item ganha os product IDs da App Store:

```json
{ "id": "ORGANIZADOR", "monthlyPriceCents": 5990, "annualPriceCents": 53910, "...": "...",
  "appStoreProductIds": { "monthly": "app.saqz.organizador.mensal", "annual": "app.saqz.organizador.anual" } }
```

### `GET /subscriptions/app-store/account-token` (autenticado)

```json
{ "appAccountToken": "8f0c2c1e-...-uuid" }
```

### `POST /subscriptions/app-store/transactions` (autenticado)

Corpo: `{ "signedTransaction": "<JWS de VerificationResult.jwsRepresentation>" }`.
Idempotente: reenviar a mesma transação devolve o mesmo resultado.

| Resposta | Quando | O app deve |
|---|---|---|
| 200 + corpo de `GET /subscriptions/me` | Transação verificada e gravada (mesmo expirada ou estornada) | `finish()` e usar o estado devolvido |
| 409 `APP_STORE_TRANSACTION_OWNED_BY_ANOTHER_ACCOUNT` | A assinatura pertence a outra conta Saqz | `finish()` e avisar que esse Apple ID já assina em outra conta |
| 422 `APP_STORE_TRANSACTION_INVALID` | JWS inválido, outro app, produto desconhecido ou ambiente não aceito | `finish()`; reenviar não muda nada |
| 5xx / rede | Falha transitória | **não** chamar `finish()`; a transação volta em `Transaction.updates` |

### `POST /webhooks/app-store` (anônimo)

App Store Server Notifications V2, corpo `{ "signedPayload": "<JWS>" }`. Mesma
URL para produção e sandbox. 200 quando processou ou ignorou; 5xx em falha
transitória, para a Apple reenviar.

### `GET /subscriptions/me`

Ganha dois campos, compatíveis com versões antigas do app:

| Campo | Valores |
|---|---|
| `provider` | `ASAAS` (web) ou `APP_STORE` |
| `autoRenew` | `true`/`false` para `APP_STORE`; `null` para `ASAAS` |

Estado de uma assinatura `APP_STORE`:

| Situação na Apple | `status` | `entitled` | Observação |
|---|---|---|---|
| Vigente, renovação ligada | `ACTIVE` | sim | `currentPeriodEnd` = expiração do período |
| Vigente, renovação desligada | `CANCELED` | sim | `canceledAt` = quando a renovação foi desligada |
| Vencida, Apple tentando cobrar (billing retry) | `PAST_DUE` | só dentro do período de carência da Apple | `pastDueSince` = vencimento |
| Vencida sem nova cobrança | `CANCELED` | não | |
| Estornada ou revogada | `CANCELED` | não | `currentPeriodEnd` = data do estorno |
| Downgrade agendado | estado atual | — | `pendingPlan`/`pendingPlanEffectiveAt` = próxima renovação |

Com assinatura na web e na App Store ao mesmo tempo, `/subscriptions/me` mostra
a que dá acesso; se as duas dão, a da App Store.

### Regras cruzadas

- Assinante vigente pela App Store recebe 409 `SUBSCRIPTION_CONFLICT` em
  `POST /subscriptions` (checkout web).
- Troca de plano e cancelamento de assinatura `APP_STORE` acontecem só na App
  Store (`AppStore.showManageSubscriptions`); `change-plan` e `cancel` do backend
  continuam só para `ASAAS`.
- `GET /subscriptions/me/receipts` inclui as transações da App Store
  (`asaasEventId` = transaction ID, `valueCents` = preço pago).
- Excluir a conta não cancela a assinatura da Apple (só o usuário cancela). O app
  avisa e oferece "Gerenciar assinatura" antes da exclusão.
