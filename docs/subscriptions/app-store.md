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

## Backend

| Variável | Produção | Dev local |
|---|---|---|
| `SAQZ_APP_STORE_BUNDLE_ID` | `app.saqz` (padrão) | `app.saqz` |
| `SAQZ_APP_STORE_APP_APPLE_ID` | Apple ID numérico do app — **obrigatório** para aceitar compras reais | vazio |
| `SAQZ_APP_STORE_ENVIRONMENTS` | `PRODUCTION,SANDBOX` (padrão) | `SANDBOX,XCODE` (`compose.yaml`) |
| `SAQZ_APP_STORE_ONLINE_CHECKS` | `true` (OCSP dos certificados) | `true` |

- Produção aceita `SANDBOX` de propósito: a revisão da Apple e o TestFlight compram
  em sandbox contra `api.saqz.app`.
- `XCODE` aceita JWS sem assinatura da Apple (arquivo `.storekit` local). Nunca em
  produção.
- A raiz de confiança é a Apple Root CA - G3, versionada em
  `backend/features/subscriptions/src/main/resources/app-store/`
  (SHA-256 `63:34:3A:BF:…:3E:91:79`).
- Tabelas: `app_store_subscriptions` (uma linha por transação original),
  `app_store_transactions` (cada cobrança) e `app_store_notifications` (idempotência
  das notificações). Migração `V92`.

Sem chave da App Store Server API por enquanto: o estado vem só da transação
enviada pelo app e das notificações. Se uma notificação se perder, o app reenvia
as transações quando o usuário abre o app.

## Roteiro no App Store Connect

1. **Contratos, Impostos e Bancos:** contrato de Apps Pagos ativo, com conta bancária
   e formulários fiscais. Sem isso o StoreKit não devolve os produtos, nem em sandbox.
   Vale aderir ao App Store Small Business Program (comissão de 15%).
2. **Assinaturas:** criar o grupo **Saqz** e os seis produtos da tabela acima, com
   duração de 1 mês ou 1 ano, preço em BRL, nome e descrição em pt-BR e o nível de
   cada um. Cada produto precisa de captura de tela da tela de compra e nota para a
   revisão. Sem oferta introdutória.
3. **Notificações do servidor (V2):** em Informações do app → App Store Server
   Notifications, URL de produção **e** de sandbox =
   `https://api.saqz.app/webhooks/app-store`. Depois, pedir uma notificação de teste:
   a API responde 200 e grava uma linha `TEST` em `app_store_notifications`.
4. **Apple ID do app:** copiar de Informações do app → Apple ID para
   `SAQZ_APP_STORE_APP_APPLE_ID` em `deploy/k8s/overlays/prod/kustomization.yaml` e
   publicar o backend. Antes disso, compras reais em produção recebem 422.
5. **Testadores sandbox:** Usuários e Acesso → Sandbox, para testar no iPhone e no
   TestFlight.
6. **Envio para revisão:** a primeira assinatura só vai junto de uma versão nova do
   app — na página da versão, seção Compras e Assinaturas, adicionar os seis produtos.
   Nos metadados, link para os Termos de Uso (EULA) e para a Política de Privacidade.
   Nas notas da revisão: a assinatura do Saqz é vendida pelo In-App Purchase; o
   pagamento de mensalidade dos grupos (quadra) é serviço presencial e está desligado
   nesta versão.
