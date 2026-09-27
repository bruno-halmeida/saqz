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

## T4 — Exclusão no backend

Gates passaram: access:test (147), access:integrationTest (117),
subscriptions:test (257), subscriptions:integrationTest (60), bootstrap:test
selecionado (50: SessionEndpoint, AdminUsersEndpoint, AccountDeletionPersistence).
Zero falhas/skip. PostgreSQL real temporário via Zonky; provedores externos em fakes.
A migração V89 acrescentou uma tabela ao inventário contratual (18 migrations).

| Critério SR4 | Evidência | Resultado esperado |
|---|---|---|
| Confirmação recente | SessionEndpointIntegrationTest: teste de auth_time antigo/ausente/futuro | 403 RECENT_AUTHENTICATION_REQUIRED, conta intacta |
| Dados e registros financeiros | AccountDeletionPersistenceTest: exclusão transacional | perfil/foto/tokens/mensagens removidos, registro PAID de 4500 preservado e anonimizado; outro usuário intacto |
| Identidade esperada | AccountDeletionPersistenceTest: expected user mismatch | falha sem alterar conta |
| Retomada e concorrência | AccountDeletionPersistenceTest: claim/retry/complete | lease e tentativa impedem conclusão obsoleta; subject removido após completar; digest impede recriação por token antigo |
| Cancelamento e Firebase | CompleteAccountDeletionTest | sucesso encerra os dois; falha mantém trabalho pendente, sem apagar identidade antes de cancelar cobrança |
| Cancelamento repetido | HttpAsaasGatewayTest | 404 considerado já cancelado; falhas de servidor propagadas para nova tentativa |

Mapeamento reverso: todos os testes novos acima verificam SR4. Testes de exclusão
anteriores agora exigem anonimização e bloqueiam reativação pelo mesmo UID, conforme
a decisão autorizada. Integração ao Firebase/Asaas de produção ainda exige homologação.

## T5 — Jornada de exclusão

Gates: 7 testes VM/UI de AccountDeletion no iOS, 4 da composição/autorização,
10 do KtorProfileGateway, 45 testes nativos Swift, 25 Node das páginas; sem falhas.
Compilação compartilhada iOS e captura Robolectric Android passaram. A imagem
`/tmp/saqz-account-deletion.png` foi inspecionada: texto legível, confirmação
explícita, senha mascarada e ações desabilitadas até consentir.
Resultado Swift: `/tmp/saqz-store-ios/Logs/Test/Test-SaqzDev-2026.09.27_17-56-56--0300.xcresult`.

| Critério SR5 | Evidência | Resultado esperado |
|---|---|---|
| Consentimento/senha | AccountDeletionViewModelTest.kt:24–29 | conta intacta sem confirmação/senha |
| Sucesso | AccountDeletionViewModelTest.kt:40–45 | mesma identidade excluída, senha limpa, efeito DELETED |
| Cancelamento/falhas | AccountDeletionViewModelTest.kt:48–74 | conta preservada, erro recuperável, retry conclui |
| Duplo clique | AccountDeletionViewModelTest.kt:77–97 | uma exclusão enquanto diálogo pendente |
| Troca de sessão/cancelamento | AccountDeletionAuthorizationBindingTest.kt:21–56 | REJECTED ou callback cancelado, sem revogar outra identidade |
| Token recente e provedor | AccountDeletionAuthorizationBindingTest.kt:9–18/:34–46 | token renovado antes de revogar; erro não autoriza DELETE |
| Apple | IOSAuthAdapterTests.swift:400–447 | código consumido, revogação só na exclusão; falha impede sucesso |
| UI | AccountDeletionScreenTest.kt:14–28 | botões protegidos; Apple só em plataforma suportada |
| Identidade no HTTP | KtorProfileGatewayTest.kt:145–158 | header X-Expected-User-Id, DELETE sem corpo e repetível |
| Canal público | account-deletion.test.cjs:8–16 | passos, contato direto, retenção e canonical acessíveis sem login |

Mapeamento reverso: os testes novos verificam SR5 e a parte Apple de SR4; nenhum
teste foi removido/ignorado. O conflito inicial entre tags do campo senha e do
botão foi corrigido e a suíte repetida passou. Publicar a página e operar as
solicitações recebidas pelo contato são etapas externas à implementação.
