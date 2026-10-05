# Assinatura pelo Google Play (Play Billing)

Mesma regra da App Store ([app-store.md](app-store.md)): o Android vende a
assinatura do Saqz pelo Google Play Billing; a web continua no Asaas; as cobranças
dos grupos (quadra) ficam fora. O backend é a fonte da verdade do acesso e sempre
consulta o estado da assinatura direto na API do Google — nem o app nem a
notificação dizem ao backend o que a assinatura é.

O teste grátis de 14 dias continua sendo o do backend; nenhum plano base tem oferta
de teste no Play.

## Produtos

No Play cada plano é uma assinatura com dois planos base. Todos só no Brasil, em BRL.

| Product ID | Plano base | Plano | Ciclo | Preço |
|---|---|---|---|---|
| `app.saqz.titular` | `mensal` (1 mês) | `TITULAR` | `MONTHLY` | R$ 39,90 |
| `app.saqz.titular` | `anual` (1 ano) | `TITULAR` | `ANNUAL` | R$ 359,90 |
| `app.saqz.organizador` | `mensal` | `ORGANIZADOR` | `MONTHLY` | R$ 59,90 |
| `app.saqz.organizador` | `anual` | `ORGANIZADOR` | `ANNUAL` | R$ 539,90 |
| `app.saqz.ilimitado` | `mensal` | `ILIMITADO` | `MONTHLY` | R$ 89,90 |
| `app.saqz.ilimitado` | `anual` | `ILIMITADO` | `ANNUAL` | R$ 809,90 |

O mapeamento produto/plano base → plano/ciclo existe só no backend e chega ao app por
`GET /plans`. O preço exibido vem sempre do Play (`ProductDetails`, `formattedPrice`
da fase do plano base).

Troca de plano nesta primeira versão acontece no Play (central de assinaturas); a
troca dentro do app com `SubscriptionUpdateParams` fica para depois.

## Vínculo com a conta

Toda compra leva `obfuscatedAccountId` = id do usuário no Saqz, o mesmo UUID que
`GET /subscriptions/app-store/account-token` já devolve (o endpoint vale para as duas
lojas). O token de compra fica preso à conta que o enviou primeiro.

## Contrato HTTP

### `GET /plans`

Cada item ganha os IDs do Play:

```json
{ "id": "ORGANIZADOR", "...": "...",
  "googlePlay": { "productId": "app.saqz.organizador", "monthlyBasePlanId": "mensal", "annualBasePlanId": "anual" } }
```

### `POST /subscriptions/google-play/purchases` (autenticado)

Corpo: `{ "productId": "app.saqz.organizador", "purchaseToken": "<Purchase.purchaseToken>" }`.
O backend lê a assinatura em `purchases.subscriptionsv2.get`, confere o pacote
`app.saqz`, o `obfuscatedExternalAccountId` e o produto, grava e **reconhece**
(`acknowledge`) a compra. O app não reconhece nem consome nada. Idempotente.

| Resposta | Quando | O app deve |
|---|---|---|
| 200 + corpo de `GET /subscriptions/me` | Compra verificada, gravada e reconhecida | usar o estado devolvido |
| 409 `GOOGLE_PLAY_PURCHASE_OWNED_BY_ANOTHER_ACCOUNT` | A assinatura pertence a outra conta Saqz | avisar; não reenviar |
| 422 `GOOGLE_PLAY_PURCHASE_INVALID` | Token inexistente, outro app ou produto desconhecido | não reenviar |
| 5xx / rede | Falha transitória | reenviar depois (`queryPurchasesAsync` na abertura) |

Compra não reconhecida em 3 dias é estornada pelo Google: por isso o app reenvia na
abertura toda compra que o Play devolver como não reconhecida.

### `POST /webhooks/google-play?token=<segredo>` (anônimo)

Push do Pub/Sub com as Real-time Developer Notifications. Corpo do Pub/Sub
(`message.data` em base64 com `subscriptionNotification` ou `testNotification`). O
backend confere o `token`, ignora mensagens repetidas (`messageId`) e relê a
assinatura no Google pelo `purchaseToken` — a notificação só diz *qual* assinatura
mudou. 204 quando processou ou ignorou; 401 com token errado; 5xx em falha
transitória (o Pub/Sub reenvia).

### `GET /subscriptions/me`

`provider` ganha `GOOGLE_PLAY`. Estado a partir do `subscriptionState`:

| `subscriptionState` | `status` | `entitled` |
|---|---|---|
| `ACTIVE` | `ACTIVE` | sim |
| `CANCELED` (renovação desligada, ainda no período) | `CANCELED` | até `expiryTime` |
| `IN_GRACE_PERIOD` | `PAST_DUE` | sim |
| `ON_HOLD`, `PAUSED` | `PAST_DUE` | não |
| `EXPIRED`, `PENDING_PURCHASE_CANCELED` | `CANCELED` | não |
| `PENDING` | não grava | — |

Uma assinatura substituída (upgrade/recompra com `linkedPurchaseToken`) deixa de valer
no lugar da nova.

### Regras cruzadas

As mesmas da App Store: assinante vigente pelo Play recebe 409 `SUBSCRIPTION_CONFLICT`
no checkout web; troca e cancelamento só no Play; recibos incluem as cobranças do
Play; excluir a conta não cancela a assinatura do Play (o app avisa).

## Roteiro (Google Cloud e Play Console)

1. **Perfil de pagamentos** no Play Console (Configurar → Perfil de pagamentos), sem o
   qual não dá para criar produtos pagos.
2. **API:** no projeto do Google Cloud `saquz-app`, ativar a *Google Play Android
   Developer API*. No Play Console → Usuários e permissões, convidar a conta de serviço
   do backend, a mesma do Firebase Admin
   (`firebase-adminsdk-fbsvc@saquz-app.iam.gserviceaccount.com`), com "Ver dados
   financeiros" e "Gerenciar pedidos e assinaturas".
3. **Assinaturas:** criar as três assinaturas e os planos base da tabela, só Brasil.
4. **Notificações:** tópico Pub/Sub `play-billing` no `saquz-app`, com permissão de
   publicação para `google-play-developer-notifications@system.gserviceaccount.com`;
   assinatura push para `https://api.saqz.app/webhooks/google-play?token=<segredo>`;
   no Play Console → Monetização → Configuração, apontar o tópico e enviar a
   notificação de teste.
5. **Segredo do webhook:** gerar um valor aleatório, guardar em `SAQZ_GOOGLE_PLAY_WEBHOOK_TOKEN`
   (Secret `backend-env`) e usar o mesmo na URL da assinatura push.
6. **Teste:** app numa faixa de teste interno, testadores de licença cadastrados.
