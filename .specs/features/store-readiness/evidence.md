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

9 testes Node executam os scripts reais do Xcode com plists temporários; todos
passaram. `plutil -lint` do projeto e `swiftc -frontend -parse` do bootstrap passam.
O teste nativo revelou dois ajustes adicionais: Debug não declarava a condição
Swift DEBUG e a chave fictícia local tinha formato recusado pelo Firebase
Installations. Ambos corrigidos; 41 testes Swift passaram no simulador após isso.
Archive assinado depende do plist de produção ainda ausente.

| Critério SR2 | Evidência em ios-app/tests/firebase-config.test.cjs | Esperado |
|---|---|---|
| Ausente | :40 `assert.equal(result.status, 1)` | recusar build |
| Outro bundle | :46 `assert.equal(result.status, 1)` | recusar build |
| Chave inválida | :52 `assert.equal(result.status, 1)` | recusar antes do crash |
| OAuth incompleto | :59 `assert.equal(result.status, 1)` | recusar build |
| Emulador Release | :65 `assert.equal(result.status, 1)` | recusar build |
| Válido/idempotente | :74 `assert.deepEqual(read('GoogleService-Info.plist'), config)` | arquivo correto |
| Debug sem config | :86 `assert.equal(fs.existsSync(stale), false)` | remover config antiga |
| Condição Swift | :95/:96 `assert.match(..., /\bDEBUG\b/)` / `assert.doesNotMatch(...)` | emulador só Debug |
| API | :117 `assert.equal(info.SaqzAPIBaseURL, 'https://api.saqz.app')` | produção exata |

Mapeamento reverso: todos os nove testes verificam SR2, sem testes removidos/skip.

## T3 — Login Apple

Gates: 24 testes KMP iOS (11 AuthenticationStateMachine + 13 SerializedNativeAuthPort),
compilação Android, 41 testes Swift no iPhone 17 Pro/iOS 26.2, sem falhas/skip.
Resultado nativo: `/tmp/saqz-store-ios/Logs/Test/Test-SaqzDev-2026.09.27_17-28-44--0300.xcresult`.
O botão é o controle oficial ASAuthorizationAppleIDButton; o domínio indica a
capacidade para exibi-lo somente quando há adaptador Apple.

| Critério SR3 | Evidência (caminhos relativos aos testes) | Esperado |
|---|---|---|
| Sucesso compartilhado | AuthenticationCoordinatorTest.kt:120 `assertEquals(AuthTransition.Authenticated(verifiedUser), fixture.transitions.single())` | sessão pelo mesmo bootstrap |
| Cancelamento | AuthenticationCoordinatorTest.kt:130 `assertNull(fixture.machine.state.value.error)` | continuar deslogado sem erro |
| Falha | AuthenticationCoordinatorTest.kt:140 `assertEquals(AuthUiError.NETWORK_UNAVAILABLE, fixture.machine.state.value.error)` | erro recuperável |
| Resposta após logout | SerializedNativeAuthPortTest.kt:36 `assertEquals(AuthResult.Cancelled, result)`; :38 `assertEquals(listOf<AuthState>(AuthState.SignedOut), observed)` | não reabrir sessão |
| Credencial e identidade privada | IOSAuthAdapterTests.swift:364–368 `XCTAssertEqual(...)` | token, nonce, nome, relay e subject preservados |
| Falha/cancelamento nativo | IOSAuthAdapterTests.swift:352/:354 e :376/:378 | resultado tipado sem contato Firebase |
| Nonce novo/hasheado | IOSAuthAdapterTests.swift:394–397 `XCTAssertEqual(first.request.nonce, expected)` / `XCTAssertNotEqual(...)` | desafio próprio de cada tentativa |

Mapeamento reverso: os testes novos acima correspondem a SR3; não testam além do
escopo. Login real ainda exige habilitar o provedor no Firebase, capability no
Apple Developer e configurar relay de e-mail. Nenhuma credencial real foi criada.
