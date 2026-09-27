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

## T6 — Capacidade de lançamento

Gates: testes iOS focados de composição (57), grupos (122) e planos/trial (34),
74 unitários + 59 integrações de recebimentos e 2 testes bootstrap; sem falhas.
O app preserva leitura do plano, cancelamento e atualização de acesso obtido na web.
Compras/upgrade/links por e-mail, chat e jornadas de recebimentos não ficam disponíveis.
O bloqueio de navegação acontece antes de compor destinos, inclusive após restauração.

| Critério SR6 | Evidência | Esperado |
|---|---|---|
| Rotas antigas | StoreLaunchNavigationTest.kt:13–29 | somente shell sobrevive; perfil, avisos e consulta do plano permanecem |
| Compra por e-mail | SubscriptionGateViewModelTest.kt:41–62 | zero requisições; atualização reconhece assinatura ativa |
| UI sem direcionamento | SubscriptionGateScreenTest.kt:24–47; MyPlanTrialScreenTest.kt:18–29 | sem botão de compra; refresh e voltar funcionam |
| Chat removido | GroupShellBlocksTest.kt:140–157; GroupDetailsViewModelTest.kt:286–292 | ausência de entrada/efeito; avisos continuam |
| Notificações antigas | CommunicationViewModelTest: launchInboxRemovesChatAndKeepsNotices | apenas NOTICE visível |
| Recebimentos desligados | ReceivablesLaunchConfigurationTest.kt:15–26 | mesmo ALL_USERS no banco resulta false/false no deployment padrão |

Mapeamento reverso: os testes alterados refletem as decisões do usuário. Testes de
componentes de compra ainda existentes usam opt-in explícito isolado; o grafo
real mantém a capacidade desligada e foi validado pelo SaqzKoinModulesTest.
A manutenção de registros financeiros anteriores permanece no backend, sem criar
novas operações. Para futuro lançamento de recebimentos são necessários deployment
`SAQZ_RECEIVABLES_LAUNCH_ENABLED=true`, rollout e nova versão mobile revisada.

## T7 — Privacidade

Gates: 38 testes Node (páginas, configuração Firebase e manifesto iOS), 45 testes
nativos Swift, 38 testes KMP de login/serialização, 15 de perfil e 1 Robolectric
sobre permissões/metadata do manifesto Android mesclado. Após ajustes de estilo,
`detektAll` passou, assim como os 22 testes de perfil/exclusão. Não houve remoção
de testes. Os dois contratos nativos de autenticação mantêm superfície única por
identidade e têm exceção localizada/documentada à métrica TooManyFunctions.
Logs: `/tmp/saqz-store-privacy-node.log`, `/tmp/saqz-store-privacy-mobile.log`
(testes passaram; a primeira checagem estática apontou estilo),
`/tmp/saqz-store-quality-mobile.log` (gate final passou) e
`/tmp/saqz-store-privacy-swift.log`.

| Critério SR7 | Evidência | Esperado |
|---|---|---|
| Coleta real | privacy-config.test.cjs | 15 tipos vinculados à conta, sem tracking, com finalidades |
| IDFA | privacy-config.test.cjs + build/test Swift | produto FirebaseAnalyticsCore, sem produto de publicidade; IDFV e consentimentos publicitários false |
| Permissões Android efetivas | StorePrivacyPermissionsTest.kt | manifesto empacotado sem AD_ID/AD_SERVICES, consentimentos publicitários false |
| Política pública | landing-page/tests/privacy.test.cjs | Apple/relay, dados de grupos, exclusão, ausência de chat e APIs desligadas; sem referência incorreta ao Branch |

O login oferece termos/política; o perfil oferece política e exclusão.
A declaração do App Store Connect/Data safety deve ser revisada com o archive
assinado final e os operadores reais, conforme o roteiro de submissão. Os testes
não equivalem ao preenchimento dos consoles nem à publicação da política.

## T8 — Links e fechamento

12 testes Node de associação/fallback/signing passaram; suíte consolidada das
páginas e configuração nativa: 50 testes, zero falhas/skips.
`node links-page/scripts/configure-app-links.cjs --check` retorna 1 no arquivo
atual, recusando o certificado debug conhecido: pendência externa explícita de
SR8. O usuário ainda não forneceu o certificado de assinatura do Play. Nenhum
fingerprint de teste foi colocado no arquivo publicado/versionado de associação.

O fallback Android limita destinos ao pacote app.saqz e códigos válidos de
convite/onboarding/presença. iOS Debug/Release usam a associação pública.
`links-page/README.md` contém instruções de Play/Firebase e validação em aparelhos.
`audits/store-review/preparacao-final-2026-09-27.md` consolida pendências e os
rascunhos de declaração/revisão das lojas; a auditoria original fica histórica.

Gate integrado: `:android-app:testDevDebugUnitTest :android-app:assembleDevDebug`
passou, 231 testes Android sem falhas/erros/skips. APK de desenvolvimento
compilado; isso não equivale a AAB/IPA de produção assinado. Log:
`/tmp/saqz-store-final-android.log`. `detektAll` passou no fechamento de T7.

Verificação pública somente de leitura via curl em 27/09/2026:
- privacidade: HTTP 200;
- excluir-conta: HTTP 404 (página local ainda não publicada);
- AASA e assetlinks: HTTP 200, application/json, sem redirecionar;
- assetlinks publicado ainda contém somente a chave debug conhecida.

Permanece pendente a homologação com consoles/provedores reais, mais a avaliação
operacional de UGC restante. A validação independente será registrada em
`validation.md`; não substitui esses passos externos.

## Correções após a primeira verificação independente

O primeiro relatório encontrou falhas concretas em SR4 e falta de cobertura
integrada em SR5. As correções acrescentam lock transacional por UID, redação dos
snapshots de jogos/séries e a migração V90 para redação limitada do histórico de
presença. O restante do histórico permanece imutável.

O adapter de exclusão agora aceita configuração sem billing, mas recusa concluir
quando existe assinatura não cancelada. Foram acrescentadas assertions das
chamadas reais ao SDK Firebase e teste de cancelamento com caso de uso/repositório
reais, PostgreSQL e gateway simulado, incluindo purge do token/last4/brand.

| Verificação após correção | Resultado / log |
|---|---|
| Access unitário + integração e bootstrap focado em exclusão/Firebase/contexto sem billing | PASS; `/tmp/saqz-store-verifier-fixes.log` |
| Bootstrap completo | 518/519; única falha era data fixa vencida no teste de trial, `ATTENDANCE_DEADLINE_PASSED`; `/tmp/saqz-store-final-backend.log` |
| Classe OrganizerTrialEndpointIntegrationTest após tornar o jogo futuro nos dois relógios | PASS; `/tmp/saqz-store-final-backend-retry.log`; nenhuma assertion removida |
| Integração de grupos com migração V90 | 615 testes, zero falhas/pulados; XML `features/groups/build/test-results/integrationTest` |
| Exclusão pela composição real + cancelamento real/purge | 5 testes, zero falhas/pulados; `/tmp/saqz-store-deletion-final-focused.log` |
| Navegação após exclusão via Root/AccessViewModel reais | 1 teste Compose em iOS Simulator, zero falhas/pulados; `/tmp/saqz-store-deletion-navigation.log` |

O teste de composição também prova o extrato preservado para o dono autorizado,
nome neutro e valor exato; conta excluída, grupo encerrado e usuário sem vínculo
não obtêm o extrato. A resolução de identidade nesse teste é simulada; autenticação
HTTP é coberta separadamente pelo gate de endpoints.

O Android Lint completo foi executado e **não passou**: nove erros preexistentes
nos testes AndroidReduceMotionTest e SaqzNavHostViewModelScopeTest, mais 19 avisos.
Log `/tmp/saqz-store-final-lint.log`. O teste de navegação acrescentado não criou
erro adicional. Não confundir esse resultado com detekt, testes Android e build
de desenvolvimento, que passaram.

Lições fundamentadas F1–F7, M5/M6 e precisão de SR4 registradas pelo script da
skill em `.specs/lessons.json` (candidatas L-021 a L-029). A revalidação independente
do commit das correções está registrada a seguir.

## Revalidação do commit 96a04c02

O verificador repetiu o gate amplo e obteve **2.378 testes de backend, zero
falhas/erros/pulados**: access 147 + 117, subscriptions 257, groups 723 + 615 e
bootstrap 519. O primeiro gate executou 1.242 casos e reutilizou 1.136 do cache;
uma execução adicional sem cache confirmou esses 1.136 casos. A classe Compose
SaqzNavHostViewModelScopeTest passou 10/10 no simulador iOS.

Logs: `/tmp/store-verifier-recheck-backend-gate.log`,
`/tmp/store-verifier-recheck-cache-confirmation.log` e
`/tmp/store-verifier-recheck-ui-gate.log`. XMLs preservados em
`/tmp/store-verifier-recheck-results`. Esses números não devem ser somados aos
testes focados anteriores, que se sobrepõem. O resultado completo do sensor e
dos probes independentes fica em `validation.md`.

Resultado adicional: **6/6 mutações detectadas, zero sobreviventes**, incluindo
remoção da chamada Firebase e preservação de credenciais financeiras. Os três
probes independentes de PostgreSQL passaram: presença, snapshots e corrida de
bootstrap/exclusão, inclusive após concluir o job. O conjunto restaurado passou
15/15. O verificador encerrou F1–F7 e os oito critérios no escopo local; isso não
encerra as pendências dos consoles, publicação, UGC e homologação assinada.
