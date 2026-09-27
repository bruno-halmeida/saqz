# Evidências de implementação

## T1 — Crashlytics Android

Gate: `:android-app:testDevDebugUnitTest --tests '*CrashlyticsBuildIdTest'` passou
(1 teste, sem falhas), compilando o aplicativo e lendo os recursos mesclados pelo
Robolectric. `bundleProdRelease` recusou a ausência de `src/prod/google-services.json`.

| Critério | Evidência / asserção | Resultado esperado |
|---|---|---|
| SR1 build ID | CrashlyticsBuildIdTest.kt:20 `assertNotNull(..., buildId)`; :21 `assertFalse(buildId.isNullOrBlank())` | ID empacotado não vazio |
| SR1 config ausente | comando prod retornou 1 com `Missing Android Firebase config` | não gerar release incompleto |

As duas asserções do teste mapeiam a SR1. Nenhum teste removido/ignorado. O teste
detecta o recurso que faltava no binário anterior; não garante envio real ao
Firebase, que depende da configuração de produção e homologação em dispositivo.
