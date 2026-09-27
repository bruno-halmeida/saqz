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

## T2 — Configuração iOS

8 testes Node executam os scripts reais do Xcode com plists temporários; todos
passaram. `plutil -lint` do projeto e `swiftc -frontend -parse` do bootstrap passam.
Archive assinado depende do plist de produção ainda ausente.

| SR2 / teste | Asserção em ios-app/tests/firebase-config.test.cjs | Resultado |
|---|---|---|
| Ausente | :45 `assert.equal(result.status, 1)` | falha de build |
| Outro bundle | :51 `assert.equal(result.status, 1)` | falha de build |
| OAuth incompleto | :58 `assert.equal(result.status, 1)` | falha de build |
| Emulador em release | :64 `assert.equal(result.status, 1)` | falha de build |
| Válido/idempotente | :74 `assert.deepEqual(read('GoogleService-Info.plist'), config)` | arquivo correto |
| Debug sem credencial | :87 `assert.equal(fs.existsSync(stale), false)` | não reusar produção |
| Integração Xcode | :93 `assert.match(phase.shellScript, ...)` | script ligado ao build |
| API | :115 `assert.equal(info.SaqzAPIBaseURL, 'https://api.saqz.app')` | produção exata |

Mapeamento reverso: todos os testes acima verificam SR2; nenhum teste de dependência
ou escopo adicional. Não há remoção/skip. Asserções de erro verificam também causa.
