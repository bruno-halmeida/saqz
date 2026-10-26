# Flags

Liga e desliga features do app sem versão nova na loja (DEB-119). As regras de produto estão no cofre, em
"Flags e experimentos" (FLG-001 a FLG-009); aqui fica só o que é preciso para mexer no código.

## Onde está

- Registro e avaliação: `backend/features/access/.../application/flags/` (`FeatureCatalog`, `FeatureRules`, `FeatureFlags`).
- Banco: `V189__feature_flags.sql` (`feature_flags`, `feature_user_groups`, `feature_user_group_members`,
  `feature_app_versions` e `feature_history`, que recusa UPDATE, DELETE e TRUNCATE).
- Rotas: `FeaturesController` (`GET /api/features`) e `AdminFeaturesController` (`/admin/features`, `/admin/feature-groups`,
  `/admin/app-versions`). Fiação em `FeatureFlagsConfiguration`.
- Tela: aba "Flags" do `adm-web/index.html`.

## Registrar uma flag nova

1. Ponha a chave em `FeatureKeys` e uma `FeatureFlagDefinition` em `FeatureCatalog.definitions`: descrição,
   `backendEnforced`, parâmetros (`NUMBER`, `TEXT` ou `CHOICE`, com padrão) e a `initialRule`.
2. A `initialRule` é gravada na primeira subida em que a chave não tem linha no banco, com uma linha no histórico
   (`initialReason`). Depois disso só o adm muda a regra; mudar a `initialRule` no código não mexe no que está gravado.
3. Tirar a chave do registro esconde a flag do adm e do app no deploy seguinte. A linha fica no banco, sem uso.

## Barrar uma rota com `FEATURE_DISABLED`

Ponha a rota em `FeatureGateFilter.ROUTES`: método, caminho, chave e `appOnly`. Com a flag desligada para quem chama,
a resposta é 403 problem+json com `code: FEATURE_DISABLED`; o app mostra "Essa função não está disponível no momento"
e busca as flags de novo.

- Barre só a ação nova. Consultar, cancelar e manter o que existe não entram na lista.
- `appOnly = true` barra só quem manda `X-Saqz-Platform: android|ios`. É o caso da assinatura pelo Asaas, porque o site
  (`/assinar`, `/comecar`) usa a mesma rota sem os cabeçalhos do app.
- Nunca barre a validação de compra da App Store ou do Google Play.
- Para o que roda sem pessoa (filas, jobs), use `FeatureFlags.enabledOnServer(chave)`: ligada quando o estado não é
  "desligada" e ao menos um interruptor de plataforma está ligado. É assim que a fila do WhatsApp obedece a flag.

Hoje barram: `POST /subscriptions` (`purchases_asaas`, só app) e `PUT /api/groups/{id}/whatsapp-binding` (`whatsapp`).
O chat ainda não tem rota no backend.

## `GET /api/features`

Responde com e sem login. Plataforma e versão vêm de `X-Saqz-Platform` e `X-Saqz-App-Version` (o `ClientApp` do
`ClientAppFilter`). Sem login, só valem "ligada para todos", o interruptor e a versão.

```json
{
  "flags": {
    "chat": { "enabled": false, "values": {} },
    "purchases_asaas": { "enabled": false, "values": {} },
    "purchases_app_store": { "enabled": true, "values": {} },
    "purchases_google_play": { "enabled": false, "values": {} },
    "whatsapp": { "enabled": true, "values": {} }
  },
  "platform": "IOS",
  "appVersion": { "minimum": "1.0.12", "recommended": "1.0.14" }
}
```

- `values` traz todos os parâmetros da flag: o valor da regra para quem recebe, o padrão para quem não recebe.
- `platform` e `appVersion` vêm `null` sem cabeçalho de Android ou iOS.
- A resposta nunca traz regra, lista, grupo nem porcentagem. O app ignora chave que não conhece.

## Aviso ao vivo

Cada publicação no adm (flag, grupo ou versões) troca o parâmetro `versao_das_flags` no Remote Config do projeto do
ambiente (`RemoteConfigFeatureNotifier`, fora da requisição). O app escuta o parâmetro e chama `GET /api/features`;
o Remote Config não decide nada. No perfil `local`/`test` (emulador, sem credencial) só sai o log
`feature_remote_config_skipped`. Falha ao publicar sai em `feature_remote_config_failed` e não desfaz a mudança.

A conta de serviço do backend precisa do papel "Firebase Remote Config Admin" (ou equivalente) em `saqz-dev` e `saquz-app`.

## Adm

Toda escrita leva `reason` (3 a 500 caracteres) e `expectedVersion`; versão errada volta 409.

```
PUT /admin/features/{chave}        {"requestId","expectedVersion","reason","rule":{...}}
POST /admin/features/{chave}/revert {"historyId","expectedVersion","reason"}
PUT /admin/feature-groups/{nome}   {"expectedVersion":0 para criar,"reason","members":[userId]}
PUT /admin/app-versions            {"expectedVersion","reason","android":{"minimum","recommended"},"ios":{...}}
GET /admin/features/history?type=FLAG|GROUP|APP_VERSIONS&key=&page=&size=
GET /admin/feature-groups/users?q=  (e-mail ou telefone)
```

A `rule` tem `android`, `ios`, `alwaysOn`/`alwaysOff` (`users`, `groups`), `state` (`OFF`, `AUDIENCE`, `ALL`),
`audience` (`platforms`, `minVersion`, `maxVersion`, `plans`, `subscriptionStatuses`, `roles`), `percentage`, `values`,
`variants` (reservado para a DEB-121; hoje precisa vir vazio) e `alertDisabled`. Campo ausente vale o padrão.
