# Assinatura pela App Store (In-App Purchase)

O iOS vende a assinatura do Saqz pela App Store (StoreKit 2). A web continua
vendendo pelo Asaas, e as cobranças dos grupos (mensalidade da quadra) continuam
no Asaas: são serviço presencial, fora do In-App Purchase.

O backend é a fonte da verdade do acesso. O app só compra e entrega a transação
assinada; quem decide se a conta tem plano é o backend, a partir da transação
verificada e das notificações da App Store.

## Produtos

Um único grupo de assinatura, **saqz** (ID 22443325, nome exibido "Saqz"), com seis
assinaturas auto-renováveis, todas só no Brasil. Anuais só na modalidade "1 Year
Upfront" (o ano pago à vista); "Monthly with a 12-Month Commitment" fica desligada,
porque o backend trata `anual` como cobrança anual.

| Product ID | Plano | Ciclo | Preço | Nível no grupo |
|---|---|---|---|---|
| `app.saqz.ilimitado.mensal` | `ILIMITADO` | `MONTHLY` | R$ 89,90 | 1 |
| `app.saqz.ilimitado.anual` | `ILIMITADO` | `ANNUAL` | R$ 809,90 | 1 |
| `app.saqz.organizador.mensal` | `ORGANIZADOR` | `MONTHLY` | R$ 59,90 | 2 |
| `app.saqz.organizador.anual` | `ORGANIZADOR` | `ANNUAL` | R$ 539,90 | 2 |
| `app.saqz.titular.mensal` | `TITULAR` | `MONTHLY` | R$ 39,90 | 3 |
| `app.saqz.titular.anual` | `TITULAR` | `ANNUAL` | R$ 359,90 | 3 |

O nível define upgrade e downgrade: subir de nível vale na hora (com reembolso
proporcional), descer vale na renovação; trocar de ciclo dentro do mesmo nível (mensal
↔ anual) é "crossgrade" e vale na renovação. Mensal e anual do mesmo plano ficam **no
mesmo nível**: a revisão de 08/10/2026 rejeitou por 3.1.2(b) a configuração anterior,
com um nível por assinatura. Para mudar: página do grupo → Edit → marcar a assinatura →
Edit Level → arrastar uma linha sobre a outra → Save. Enquanto grupo e assinaturas
estão numa submissão (mesmo rejeitada), as linhas vêm desabilitadas: é preciso "Cancel
Submission" antes, e aí tudo volta a "Developer Rejected" até a próxima submissão.
O backend não depende dos níveis (`pendingPlan` ignora troca de ciclo no mesmo plano) e
o `Saqz.storekit` já usa `groupNumber` 1/2/3. Os anuais da web (9 mensalidades)
não existem na tabela de preços da Apple; ficou o ponto ",90" mais próximo.

O mapeamento product ID → plano/ciclo existe só no backend e chega ao app por
`GET /plans`. O preço exibido no app vem sempre do StoreKit (`displayPrice`),
nunca do backend: é a App Store que cobra.

O teste grátis de 14 dias continua sendo o do backend, igual para todos. Os
produtos **não** têm oferta introdutória.

## Vínculo com a conta

Toda compra leva `appAccountToken` = id do usuário no Saqz (UUID), obtido em
`GET /subscriptions/app-store/account-token`. Enquanto a assinatura dá acesso
(período pago ou carência de cobrança), a transação original fica presa à conta
que a comprou: outra conta Saqz no mesmo Apple ID recebe "já vinculada a outra
conta", inclusive em "Restaurar compras". Depois de vencer ou ser estornada, a
assinatura segue o pagamento: se o mesmo Apple ID volta a assinar logado em outra
conta (a Apple reaproveita o `originalTransactionId`), a assinatura passa para a
conta do `appAccountToken` da compra nova, no envio pelo app e no webhook. Regra
de 09/10/2026, depois que a revisora da Apple comprou com a conta de revisão e a
compra de teste seguinte, com o mesmo Apple ID sandbox, caiu nesse bloqueio.

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
3. **Notificações do servidor (V2):** feito em 05/10/2026 — URL de produção **e** de
   sandbox = `https://api.saqz.app/webhooks/app-store` (App Information → App Store
   Server Notifications). O App Store Connect não tem botão de teste: a notificação de
   teste só sai pela App Store Server API (exige chave de In-App Purchase). A primeira
   compra sandbox comprova: uma linha `SUBSCRIBED` em `app_store_notifications`.
4. **Apple ID do app:** `6812743525`, em `SAQZ_APP_STORE_APP_APPLE_ID` no
   `deploy/k8s/overlays/prod/kustomization.yaml`. Em produção desde o backend `v.0.0.5`
   (05/10/2026).
5. **Testadores sandbox:** Usuários e Acesso → Sandbox, para testar no iPhone e no
   TestFlight.
6. **Envio para revisão:** a primeira assinatura só vai junto de uma versão nova do
   app — na página da versão, seção Compras e Assinaturas, adicionar os seis produtos.
   Nos metadados, link para os Termos de Uso (EULA) e para a Política de Privacidade.
   Nas notas da revisão: a assinatura do Saqz é vendida pelo In-App Purchase; o
   pagamento de mensalidade dos grupos (quadra) é serviço presencial e está desligado
   nesta versão. Dizer também que as seis assinaturas estão num único grupo, em três
   níveis (Ilimitado 1, Organizador 2, Titular 3), com mensal e anual no mesmo nível.

## App (iOS)

- O StoreKit 2 fica em `mobile/ios-app/SaqzIOS/IOSAppStorePurchases.swift`, atrás do
  `AppStorePurchasesPort` (`:features:subscriptions:domain`). O Android passa `null`
  nesta porta e vende pelo Google Play ([google-play.md](google-play.md)).
- `AppStoreTransactionSync` (`:features:subscriptions:presentation`) aplica a tabela de
  `finish()` acima e reentrega `Transaction.unfinished` a cada login.
- A tela de compra substitui o portão de assinatura quando há produtos; sem produtos,
  sem permissão de compra no aparelho ou com assinatura web ativa, o portão de antes
  volta. `StoreLaunchPolicy.appStorePurchases = false` desliga a compra no iOS.
- Local: o scheme **SaqzDev** usa `SaqzIOS/Saqz.storekit` (preços só de referência) e o
  backend de dev precisa aceitar `XCODE` em `SAQZ_APP_STORE_ENVIRONMENTS`. Os schemes de
  produção usam o sandbox da App Store, com testador sandbox.

## Cupons de campanha (códigos de oferta)

Cupom com duração criado no adm-web vira, para cada produto coberto, um **código de oferta**
(offer code) com **código personalizado** na App Store Connect API. Fluxo do job
(`SyncCouponStoreOffers`, cada id gravado assim que a Apple devolve, para retomar do passo certo):

1. `GET /v1/apps/{SAQZ_APP_STORE_APP_APPLE_ID}/subscriptionGroups` e
   `GET /v1/subscriptionGroups/{id}/subscriptions?filter[productId]=…` → id da assinatura.
2. `GET /v1/subscriptions/{id}/pricePoints?filter[territory]=BRA` → price point de `BRA` mais
   próximo de preço da loja × (1 − desconto). É o preço que o admin mostra na prévia.
3. `POST /v1/subscriptionOfferCodes`: `name` = código personalizado, `offerMode`
   `PAY_AS_YOU_GO`, `duration` `ONE_MONTH`/`ONE_YEAR`, `numberOfPeriods` = duração,
   `offerEligibility` `STACK_WITH_INTRO_OFFERS` (não há oferta introdutória),
   `customerEligibilities` `NEW` ("Nunca assinou") e/ou `EXPIRED` ("Já cancelou"), preço só
   em `BRA`.
4. `POST /v1/subscriptionOfferCodeCustomCodes`: `customCode`, `numberOfCodes` = limite de
   usos (sem limite = 25.000, o máximo por código) e `expirationDate` quando o cupom tem validade.

**Código personalizado é único no app inteiro** ("Custom codes can't be used for other offer
campaigns or subscriptions for the app", ajuda do App Store Connect): por isso um código por
produto, o do cupom + inicial do plano + `M`/`A` (ex.: `VOLEI30OM` = Organizador mensal). O
app recebe esse código em `appStoreOfferCode` e o link
`https://apps.apple.com/redeem?ctx=offercodes&id=<app-apple-id>&code=<código>`.

- Limites: pay as you go numa assinatura de 1 mês aceita 1 a 12 meses; de 1 ano, só 1 ano.
  Até 64 caracteres sem especiais. Código personalizado aceita no máximo 25.000 resgates por
  criação. Com validade no cupom, o código personalizado vai com `expirationDate`
  (`AAAA-MM-DD`) = dia da validade no fuso do Pacífico: a Apple desliga à 0h PT desse dia, então
  o código nunca vale depois do cupom (pode parar algumas horas antes, no último dia). Validade
  além de 6 meses (máximo da Apple) é recusada no admin. Além disso, o job desativa nas lojas
  toda oferta (inclusive já `ACTIVE`) de cupom vencido ou encerrado.
- A Apple não avisa quando o código passa a valer (até 1 hora): a oferta fica
  `WAITING_STORE` ("Liberando na Apple (até 1 h)") e vira `ACTIVE` 60 minutos depois.
- Encerrar = `PATCH active=false` no código personalizado e no código de oferta (a oferta
  não pode ser reativada; quem resgatou mantém o desconto).
- Compra: a transação assinada traz `offerType` 3 (código de oferta) e `offerIdentifier`;
  o backend acha a oferta pelo produto + identificador e grava o resgate com `platform = IOS`
  (no envio pelo app e no webhook). A compra pelo código vem sem `appAccountToken`: fica com
  quem enviou primeiro, como já era.

Estados, contrato do `POST /coupons/validate` e "quem pode usar": ver
`google-play.md` (seção Cupons de campanha).

| Variável | Produção | Dev / local |
|---|---|---|
| `SAQZ_COUPON_STORE_SYNC` | `true` | `false` (padrão; nenhuma chamada) |
| `SAQZ_APP_STORE_CONNECT_ISSUER_ID` | issuer da chave de equipe | vazio |
| `SAQZ_APP_STORE_CONNECT_KEY_ID` | key id | vazio |
| `SAQZ_APP_STORE_CONNECT_PRIVATE_KEY` | conteúdo do `.p8` (PEM; `\n` literal aceito) | vazio |

Token JWT ES256 (`aud` `appstoreconnect-v1`, 20 min). A chave nunca vai para log nem
resposta; chave ausente ou malformada não derruba o boot (as ofertas da Apple falham com
"credenciais não configuradas" até corrigir e "Tentar de novo").
