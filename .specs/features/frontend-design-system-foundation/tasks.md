# Tasks da Fundação Mobile de Interface e Design System

**Spec:** .specs/features/frontend-design-system-foundation/spec.md
**Design:** .specs/features/frontend-design-system-foundation/design.md
**Status:** Verified — T01–T43 concluídas; `validation.md` PASS
**Data:** 2026-07-15
**Baseline:** main em 8f54dfc

## Progresso Execute

| Fase | Tasks | Status | Commits |
| --- | --- | --- | --- |
| 0 | T01–T07 | ✅ Done (gates verdes; device Saqz_API_30 API 30 arm64; simulador Xcode 26.2) | b3e4fbe, 53d2fd0, fca62e7, de2ed01, 867d7c5, c2cc637, 4dd2cb4 |
| 1 | T08–T10 | ✅ Done (Quick core + Build shared verdes) | b38cf69, 49018b0, d45fec9 |
| 2A | T11–T14 | ✅ Done (Quick design + Build shared verdes) | dbf0a81, 3de7f23, 3333204, 491ce26 |
| 2B | T15–T19 | ✅ Done (Quick design/app, Full Android, brand assets, Build shared verdes) | c2ac16d, 495b347, 5fcb631, 3cdfb61, 4f64aef |
| 3 | T20–T24 | ✅ Done (Quick design por task + Build shared verdes) | b32bfa5, e4fe860, 1c80021, 03765be, 22d757e |
| 4 | T25–T28 | ✅ Done (Quick design por task + Build shared verdes) | 72efca0, b93e20e, f9ff9b4, be5fed7 |
| 5 | T29–T33 | ✅ Done (Quick app por task + Build shared verdes) | 99c4072, 4407ab9, 25909b8, 9eaf45b, 992733a |
| 6 | T34–T37 | ✅ Done (Full Android device + SaqzDev sim verdes) | 5baeabd, 01f9c0d, 6b9a92c, 3555da1 |
| 7 | T38–T43 | ✅ Done (scripts, isolamento, API 35 e gate local completo verdes) | 62fb89c, e7cd1c4, 0390574, bf5cc79, 7560804, 5e16f63 |

Deltas de teste Fase 0: check-scope 10→22; :core:design-system 0→2;
:compose-app 1→7; android connected 1→3; SaqzIOSUITests 1→3.
Deltas Fases 1+2A: :core:common 0→24; :core:design-system 2→29.
Deltas Fase 2B: :core:design-system 29→46; :compose-app 7→11; android
connected 3→10. Notas: config indispensável adicional — android-app
androidTest asset srcDir (T15, checksum da licença on-device) e
compose.resources block no compose-app (T17, Res tipado); T16 shipou
SaqzTheme(content) e T19 completou SaqzTheme(preferences, content) igual ao
design; chrome é CompositionLocal internal (SaqzChrome). Limite de cobertura
honesto p/ Verifier: pareamento arquivo↔weight da Inter é asserted por
inventário+checksums (Font não expõe identidade de resource — swap
light↔bold não é externamente observável pela API Compose).
Deltas Fase 3: :core:design-system 46→89 (+43). Notas p/ Verifier: contratos
de token/motion exercitados por resolvers puros usados pelos componentes
(buttonColors/badgeColors, keyboardTypeFor/visualTransformationFor,
saqzPressScale); press feedback observável via semantics key internal
SaqzPressFeedbackKey (iosSim não captura screenshot); password toggle usa
glyph decorativo ○/● (sem icons-extended) não-focável;
pressDurationIs120ms asserta o valor de motion alimentado no tween.
Deltas Fase 4: :core:design-system 89→130 (+41; T26 entregou +12).
Notas p/ Verifier: modais em Compose Dialog + scrim próprio (testTag
saqz-modal-scrim) com resolver puro saqzDialogProperties(back,outside);
StateHost usa when exaustivo + resolver saqzStateTransition(motion);
BottomNav com indicator bar como sinal não-cromático (ler via
useUnmergedTree). SaqzModalScaffold internal compartilhado T25/T26
(refinamento folded no commit de T25 via amend para manter escopo por card).
Deltas Fase 5: :compose-app 11→52 (+41, inclui T29). Nota operacional: o
worker original do Batch 6 bateu limite de sessão após commitar T29 (99c4072)
com T30 em progresso, uncommitted e quebrado (2 referências de resource não
importadas); um segundo worker retomou, corrigiu forward (sem reescrever) e
completou T30–T33. Nenhum trabalho commitado foi perdido. Limites de
observabilidade honestos no iosSim (documentados em comentário de teste):
reduceTransparency provado via mapeamento toPreferences(), não screenshot;
restauração de destino/overlay provada via round-trip do bottom-nav
(StateRestorationTester não implementado no Skiko/iosSim) — cobertura real
de recreation de processo fica para T35 (Android). SaqzApp boundary pública:
dois booleans; helper interno NavHostController.navigateTopLevel reusado por
Home e shell.
Deltas Fase 6: android unit 2→8; android connected 10→21 (+ModernAndroidBehaviorTest
4 standalone); iOS unit 2→12; iOS UI 3→8. Notas p/ Verifier: pbxproj sem
synchronized folders exigiu registro manual de cada arquivo novo (Assets,
testes); MainViewController(accessibilityController:) injetado, launch-arg
de preflight preservado; sourceHashMatchesT18 lê o SVG via #filePath e
confere SHA + path-data (derivação, não redesenho); Dynamic Type testado via
launch arg -UIPreferredContentSizeCategoryName.
Notas de ambiente: gates Gradle exigem JAVA_HOME JDK 21
(/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home);
ui-contract.json com seções colors/metrics/motion/typography lido via
Res.readBytes em runTest; defaults dos registries em companion vals
(SaqzColorTokens.Light, SaqzMetrics.Default, SaqzMotionPolicy.Normal/
Reduced, SaqzTypography.Default).
Deltas Fase 7: T39 fortaleceu scripts/check-ios para SaqzDev
test, SaqzProd Release build e SaqzProd Release unit-only com JDK 21 propagado
ao xcodebuild; T40 cobre isolamento mobile em scratch com stubs discriminatórios
e removeu acoplamento de XCTest ao landing SVG; T41 adicionou job não bloqueante
android-api35-probe em bf5cc79 e validou o contrato local via check-ci 29/29.
T42 registrou três probes verdes no SHA bf5cc79 (runs 29449093693,
29463089984 e 29463088994; boots 37.329s, 35.728s e 35.417s), promoveu
android-api35-gate em 7560804 e teve aggregate pós-promoção verde no run
29463568482; check-ci ficou 29→36. T43 elevou check-all.test 7→8 e
check-readme.test 13→20 (+8 total), documentou as três suites allTests,
Android e SaqzDev/SaqzProd, e concluiu scripts/test-scripts + scripts/check-all
verdes. O primeiro check-all.test de T43 teve um timeout SIGINT transitório;
rerun imediato 8/8 e o aggregate posterior passaram, sem processo residual.

## Execution Protocol (MANDATORY -- do not skip)

Implementar estas tasks com a skill tlc-spec-driven ativada por nome e seguindo
o fluxo Execute e suas Critical Rules. Se a skill não puder ser ativada, parar
e avisar o usuário; não executar por um fluxo improvisado.

- Fases e tasks são sequenciais. Uma task só começa após commit e gate verde da
  anterior.
- Uma task produz um único conceito. Arquivos de teste e configuração
  indispensável pertencem ao mesmo conceito e não contam como entregas
  separadas.
- Testes derivam da spec e de seus outputs, nunca da estrutura escolhida pela
  implementação.
- O gate decide conclusão. Não reduzir, apagar, skipar ou neutralizar teste,
  suite, device ou mutação para obter verde.
- Um commit atômico por task, com exatamente a mensagem planejada, salvo
  correção factual aprovada no próprio tasks.md.
- Depois de T43, um Verifier novo e independente do autor executa outcome
  check, discrimination sensor e escreve validation.md. PASS é obrigatório
  para Verified; checklist manual continua recomendado e não bloqueante.

## Controle de Drift

### Hierarquia de fontes

1. spec.md define comportamento, valores visuais e outputs de aceite.
2. context.md define respostas e limites decididos com o usuário.
3. design.md define módulos, ownership, versões e integrações.
4. Este tasks.md define ordem, arquivos, API, testes e evidência.
5. Código existente fornece estilo somente quando não conflita com 1–4.

Se uma task exigir decisão não coberta nessa hierarquia, registrar
SPEC_DEVIATION e parar antes de editar. Não escolher uma alternativa “parecida”.

### Regras fixas para todas as tasks

- Não criar Angular, Compose Web, Kotlin/JS, Kotlin/Wasm ou outro produto web.
- Não alterar conteúdo da landing; o SVG é somente fonte de derivação.
- Não introduzir auth UI, perfil, avatar, logout, negócio, rede, cache,
  persistência, OpenAPI ou tipos de domínio backend no mobile.
- UI, semantics, navegação, recursos, tipografia, Dynamic Type e motion são
  Compose-first. Nativo cobre apenas launcher, SDK já existente e preferência
  sem API Compose comum.
- APIs públicas novas usam Saqz; nenhuma API Ais.
- Versões pinadas: Kotlin 2.4.10, Compose 1.11.1, Navigation 2.9.2,
  kotlinx-datetime 0.8.0, serialization-json 1.11.0, core-splashscreen 1.2.0.
- Antes de cada task, registrar no update da task: SHA inicial, contagem
  baseline da suite afetada e arquivos esperados. Ao concluir, registrar SHA,
  delta de testes e comando/evidência.
- “+N casos” significa N novos casos nomeados ou N cenários shell
  discriminatórios. A contagem final deve ser baseline + N ou maior; qualquer
  remoção exige parar e explicar.

## Perfis de Ferramentas Propostos

Estes perfis são provisórios até confirmação do usuário antes de Execute.

| Perfil | Ferramentas | MCPs | Skills |
| --- | --- | --- | --- |
| LOCAL-KMP | apply_patch, rg, mobile/gradlew, kotlin.test, Compose UI test | nenhum | tlc-spec-driven; backprop somente em falha |
| ANDROID | LOCAL-KMP, adb, emulador API 30/35, ActivityScenario | nenhum | tlc-spec-driven; backprop somente em falha |
| IOS | apply_patch, rg, xcodebuild, simctl, XCTest, XCUITest | nenhum | tlc-spec-driven; backprop somente em falha |
| SHELL | apply_patch, rg, harness scratch de tests/scripts | nenhum | tlc-spec-driven; backprop somente em falha |
| CI | SHELL, GitHub Actions e leitura de runs por gh/conector aprovado | GitHub somente se aprovado | tlc-spec-driven; backprop somente em falha |

## Test Coverage Matrix

> Gerada de README.md, RTK.md, mobile/compose-app/src/commonTest,
> mobile/android-app/src/test, mobile/android-app/src/androidTest,
> mobile/ios-app/SaqzIOSTests, mobile/ios-app/SaqzIOSUITests,
> tests/scripts e .github/workflows/initialization-gate.yml. A spec e seus edge
> cases elevam esses padrões ao default forte. Confirmar antes de Execute.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Configuração Gradle/Xcode sem comportamento | none | Build/sync gate; inventário exato; sem teste placebo | arquivos de build e project | gate Build da task |
| Core common | unit KMP | Todos os branches; 1:1 com FMT/STATE; todo edge listado | mobile/core/common/src/commonTest/kotlin | mobile/gradlew -p mobile :core:common:allTests --console=plain |
| Tokens e recursos | unit KMP + contract | Cada token/asset/resource 1:1 com spec; pares de contraste; ausência deve falhar | mobile/core/design-system/src/commonTest e composeResources/files | mobile/gradlew -p mobile :core:design-system:allTests --console=plain |
| Componentes | Compose UI KMP | Toda variante/estado; semantics; bounds; press/release; callbacks; edges | mobile/core/design-system/src/commonTest/kotlin | mobile/gradlew -p mobile :core:design-system:allTests --console=plain |
| App compartilhado | Compose UI KMP | Home/catálogo/startup; duas rotas; happy, back, restore, reselection e edges | mobile/compose-app/src/commonTest/kotlin | mobile/gradlew -p mobile :compose-app:allTests --console=plain |
| Android | JUnit + instrumentado | Recursos, cold start, API 30/35, rotação, fontScale, insets/IME e bootstrap | mobile/android-app/src/test e src/androidTest | mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain |
| iOS | XCTest + XCUITest | Launch, Compose semantics, SaqzDev/SaqzProd, Dynamic Type e preferências | mobile/ios-app/SaqzIOSTests e SaqzIOSUITests | scripts/check-ios |
| Scripts/CI | contract + mutation | Inventário, ordem, failure, cancel, zero tests/device, scope, jobs e aggregate | tests/scripts | scripts/test-scripts |
| Isolamento | integration scratch | Mobile sem siblings executa matriz inteira e mata acoplamentos indevidos | tests/scripts/check-workspace-isolation.test.sh | tests/scripts/check-workspace-isolation.test.sh mobile |

## Gate Check Commands

> Gerados dos wrappers, scripts e workflow existentes. Confirmar antes de
> Execute.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick core | T08–T10 | mobile/gradlew -p mobile :core:common:allTests --console=plain |
| Quick design | T03, T11–T28 | mobile/gradlew -p mobile :core:design-system:allTests --console=plain |
| Quick app | T04, T07, T29–T33 | mobile/gradlew -p mobile :compose-app:allTests --console=plain |
| Full Android | T05, T15, T34–T35 | mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain |
| Full iOS | T06, T36–T37, T39 | scripts/check-ios |
| Contract | T01, T38–T43 | scripts/test-scripts |
| Isolation | T40 | tests/scripts/check-workspace-isolation.test.sh mobile |
| Build shared | Final de fases 0–5 | mobile/gradlew -p mobile :core:common:allTests :core:design-system:allTests :compose-app:allTests --console=plain |
| Full local | Após T43 e pelo Verifier | scripts/check-all |
| External API 35 | Entre T41 e T42 | três runs verdes no mesmo SHA/tuple; boot até 300s |

## Execution Plan

Fases e tasks executam estritamente em sequência:

    Fase 0: T01 → T02 → T03 → T04 → T05 → T06 → T07
    Fase 1: T08 → T09 → T10
    Fase 2A: T11 → T12 → T13 → T14
    Fase 2B: T15 → T16 → T17 → T18 → T19
    Fase 3: T20 → T21 → T22 → T23 → T24
    Fase 4: T25 → T26 → T27 → T28
    Fase 5: T29 → T30 → T31 → T32 → T33
    Fase 6: T34 → T35 → T36 → T37
    Fase 7: T38 → T39 → T40 → T41 → T42 → T43

Cada primeira task de fase depende da última task da fase anterior. T42 possui,
além de T41, a condição externa dos três probes.

## Task Breakdown

## Fase 0 — Guardrails e preflight de build

### T01 — Ajustar o contrato de scope da fundação

- **What:** permitir somente paths, nomes e dependências aprovados pela feature.
- **Where:** scripts/check-scope; tests/scripts/check-scope.test.sh.
- **Depends on:** none.
- **Reuses:** helpers scratch, mutações e mensagens já existentes em
  check-scope.test.sh.
- **Requirements:** VIS-01, CMP-06, GATE-01.
- **Fixed contract:** aceitar mobile/core/common, mobile/core/design-system,
  packages Saqz, Navigation Compose e serialization; manter rejeição explícita
  a web product, Ais, auth, persistência, negócio, OpenAPI, backend domain e
  dependência entre workspaces.
- **Must not:** ampliar allowlist com regex genérica; alterar landing/backend;
  remover uma mutação negativa existente.
- **Tools:** SHELL.
- **Tests:** contract/mutation, +12 cenários: dois positivos dos módulos e dez
  negativos, um por categoria proibida.
- **Gate:** tests/scripts/check-scope.test.sh; depois scripts/check-scope.
- **Done when:**
  - [ ] cada categoria proibida possui fixture e mensagem discriminatória;
  - [ ] contagem final é baseline +12 ou maior, sem remoções;
  - [ ] os dois comandos do Gate terminam 0 no repo limpo.
- **Commit:** chore(scope): allow mobile design foundation

### T02 — Criar o módulo core:common vazio e isolado

- **What:** registrar um módulo KMP sem Compose e sem comportamento de domínio.
- **Where:** mobile/settings.gradle.kts;
  mobile/core/common/build.gradle.kts;
  mobile/build.gradle.kts (amendment Execute 2026-07-15: declarar plugins KMP/
  Compose/Android com apply false na raiz — correção indispensável do
  classloader split de Kotlin 2.4.10/Gradle 9.5 ao registrar o segundo módulo
  KMP; sem mudança de comportamento; aprovado pelo orquestrador).
- **Depends on:** T01.
- **Reuses:** plugins e targets de mobile/compose-app/build.gradle.kts e
  saqz.kmp-compose-library.
- **Requirements:** VIS-01, VIS-03.
- **Fixed contract:** namespace br.com.saqz.core.common; targets android,
  iosArm64 e iosSimulatorArm64; commonTest depende somente de kotlin(test);
  módulo não produz framework.
- **Must not:** adicionar Compose, Firebase, Android API, UIKit, backend,
  source set web ou project dependency para sibling.
- **Tools:** LOCAL-KMP.
- **Tests:** none, configuração pura; delta esperado 0 e build gate obrigatório.
- **Gate:** mobile/gradlew -p mobile :core:common:allTests --console=plain.
- **Done when:**
  - [ ] Gradle reconhece exatamente :core:common e seus targets aprovados;
  - [ ] dependency report não contém dependência proibida;
  - [ ] Gate termina 0 sem zero-task silencioso.
- **Commit:** build(core): add common kmp module

### T03 — Criar core:design-system com sentinels Compose

- **What:** registrar o módulo KMP Compose e definir string/drawable sentinels
  para o preflight genérico.
- **Where:** mobile/settings.gradle.kts;
  mobile/core/design-system/build.gradle.kts;
  mobile/core/design-system/src/commonMain/composeResources/values/strings.xml;
  mobile/core/design-system/src/commonMain/composeResources/drawable/preflight_sentinel.xml;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/resources/ResourceSentinelTest.kt.
- **Depends on:** T02.
- **Reuses:** saqz.kmp-compose-library, dependências Compose pinadas do
  compose-app e padrão runComposeUiTest de SaqzAppTest.
- **Requirements:** VIS-01, VIS-03, EDGE-04.
- **Fixed contract:** namespace br.com.saqz.designsystem; depende de
  :core:common; string/drawable acessados por Res; recursos obrigatórios locais.
- **Must not:** expor componente final, usar Android res/font no core, fazer
  download ou criar framework iOS próprio.
- **Tools:** LOCAL-KMP.
- **Tests:** unit/resource, +2 casos:
  resolvesSentinelString e resolvesSentinelDrawable.
- **Gate:** mobile/gradlew -p mobile :core:design-system:allTests --console=plain.
- **Done when:**
  - [ ] os dois accessors gerados resolvem no source set correto;
  - [ ] remover qualquer sentinel faz compile/test falhar;
  - [ ] contagem final é baseline +2 ou maior e Gate termina 0.
- **Commit:** build(design-system): add compose resource sentinels

### T04 — Consumir sentinels pelo umbrella compose-app

- **What:** provar dependência transitiva e consumo dos recursos pelo único
  umbrella exportado.
- **Where:** mobile/compose-app/build.gradle.kts;
  mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/ResourcePreflight.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/ResourcePreflightTest.kt.
- **Depends on:** T03.
- **Reuses:** SaqzAppTest e framework SaqzMobile já configurado.
- **Requirements:** VIS-03, EDGE-04.
- **Fixed contract:** compose-app depende de :core:common e
  :core:design-system; ResourcePreflight é internal e resolve string/drawable
  sem UI ou tag permanente de produção.
- **Must not:** duplicar recursos no compose-app nesta task; alterar SaqzApp;
  exportar módulos core como frameworks.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI/resource, +2 casos:
  umbrellaResolvesSentinelString e umbrellaResolvesSentinelDrawable.
- **Gate:** mobile/gradlew -p mobile :compose-app:allTests --console=plain.
- **Done when:**
  - [ ] os dois recursos resolvem através da dependência do umbrella;
  - [ ] nenhuma cópia existe em compose-app;
  - [ ] contagem final é baseline +2 ou maior e Gate termina 0.
- **Commit:** test(compose-app): consume design resource sentinels

### T05 — Provar sentinels no APK devDebug API 30

- **What:** verificar que os recursos transitivos chegam ao artifact Android.
- **Where:** mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/ResourcePackagingTest.kt;
  nenhum arquivo de produção.
  Amendment Execute 2026-07-15 (SPEC_DEVIATION aprovado pelo orquestrador):
  inclui build-logic (KmpComposeLibraryConventionPlugin +
  AndroidApplicationConventionPlugin) e mobile/android-app/build.gradle.kts
  (test deps) como configuração indispensável.
  Nota T34: AndroidFirebaseBootstrap.initialize tornado idempotente
  (dedup por FirebaseApp.getApps) — indispensável para o próprio cold-start
  test de T34 relançar MainActivity in-process; satisfaz também EDGE-05. Root cause: sob
  com.android.kotlin.multiplatform.library (AGP 9.1), o task
  CopyResourcesToAndroidAssetsTask falha ("property 'outputDirectory' doesn't
  have a configured value") e composeResources nunca chegam ao APK; o fallback
  umbrella do design é inviável (compose-app usa o mesmo plugin). Correção:
  outputDirectory explícito no convention plugin das libs Compose +
  android-app coleta os composeAndroidAssets via lista estática
  COMPOSE_RESOURCE_MODULES no build-logic (:core:design-system e
  :compose-app — cobre T17 sem tocar android-app; iteração dinâmica é
  inviável: android-app é avaliado antes dos módulos de dependência).
  Versões pinadas intocadas; ownership dos recursos permanece em
  :core:design-system.
- **Depends on:** T04.
- **Reuses:** createAndroidComposeRule de MainActivityTest e flavor dev.
- **Requirements:** VIS-03, GATE-03, EDGE-04.
- **Fixed contract:** APK devDebug contém/renderiza string e drawable em API
  30; packaging de Inter pertence exclusivamente a T15.
- **Must not:** migrar para R.font; aplicar fallback por hipótese; aceitar
  teste skipped/device ausente.
- **Tools:** ANDROID.
- **Tests:** instrumentado, +2 casos:
  apkRendersSentinelString e apkRendersSentinelDrawable.
- **Gate:** Full Android em emulador API 30.
- **Done when:**
  - [ ] os dois casos rodam no device, não apenas compilam;
  - [ ] contagem final é baseline +2 ou maior;
  - [ ] Gate termina 0 e registra device/API usados.
- **Commit:** test(android): prove packaged compose resources

### T06 — Provar sentinels no framework e app iOS

- **What:** verificar resolução dos recursos pelo SaqzMobile no simulador.
- **Where:** mobile/ios-app/SaqzIOSUITests/ResourcePackagingUITests.swift;
  nenhum arquivo de produção.
  Amendment Execute 2026-07-15 (SPEC_DEVIATION aprovado pelo orquestrador):
  o cartão original era insatisfazível — o Done when exige sentinels
  observáveis na UI Compose via XCUITest (black-box), mas T04 fez o preflight
  internal/sem UI e este cartão proibia arquivo de produção; os recursos iOS
  não ficam no SaqzMobile.framework e sim no .app bundle via build phase.
  Resolução: permitido um caminho de preflight gated por launch argument em
  mobile/compose-app/src/iosMain/kotlin/br/com/saqz/composeapp/MainViewController.kt
  — quando o processo recebe o argumento de preflight, renderiza os dois
  sentinels via Compose real (mesmo resource path do framework); launch
  normal permanece inalterado. Continua proibido: UILabel/overlay sintético,
  segundo framework, teste que só inspeciona arquivo (inspeção de bundle é
  evidência auxiliar, nunca critério final), mascarar falha.
  Nota adicional: os 2 casos XCUITest vivem em SaqzIOSUITests.swift (não em
  ResourcePackagingUITests.swift novo) — o xcodeproj não tem synchronized
  folders e arquivo novo exigiria cirurgia frágil no pbxproj; aceito pelo
  orquestrador. Evidência auxiliar: recursos chegam a
  SaqzDev.app/compose-resources/ sem correção iOS.
- **Depends on:** T05.
- **Reuses:** SaqzIOSUITests launch pattern e SaqzMobile framework existente.
- **Requirements:** VIS-03, GATE-03, EDGE-04.
- **Fixed contract:** app SaqzDev resolve string/drawable vindos do umbrella;
  se AGP/Compose perder recursos transitivos, registrar evidência e aplicar
  somente o fallback umbrella definido no design, reexecutando T04–T06.
  Nota (amendment T05): a perda de recursos transitivos no Android já ocorreu
  e foi corrigida na fonte pelo wiring do convention plugin (ver amendment em
  T05); o fallback umbrella foi provado inviável e está superado. iOS não é
  afetado (bundling nativo funciona).
- **Must not:** editar project/config para mascarar falha; criar UILabel/test
  overlay sintético; criar segundo framework; aceitar teste que apenas
  inspeciona arquivo sem renderizar. Falha exige SPEC_DEVIATION e amendment do
  fallback antes de qualquer edição adicional.
- **Tools:** IOS.
- **Tests:** XCUITest, +2 casos:
  appRendersSentinelStringFromFramework e
  appRendersSentinelDrawableFromFramework.
- **Gate:** xcodebuild SaqzDev test no simulador selecionado por scripts/check-ios.
- **Done when:**
  - [ ] ambos os sentinels são observáveis a partir da UI Compose;
  - [ ] contagem final é baseline +2 ou maior;
  - [ ] Gate termina 0 com bundle/framework únicos.
- **Commit:** test(ios): prove framework compose resources

### T07 — Configurar destinos type-safe e serialization

- **What:** adicionar dependências pinadas e os dois tipos de destino, sem
  montar NavHost.
- **Where:** mobile/gradle/libs.versions.toml;
  mobile/compose-app/build.gradle.kts;
  mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzDestination.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzDestinationTest.kt.
- **Depends on:** T06.
- **Reuses:** aliases/version catalog atuais e commonTest kotlin.test.
- **Requirements:** NAV-04, EDGE-06.
- **Fixed contract:** Navigation 2.9.2; plugin serialization 2.4.10; JSON
  1.11.0; sealed interface SaqzDestination com @Serializable data object Home
  e Catalog, sem string route pública.
- **Must not:** usar enum/string route; introduzir terceiro destino; acoplar
  destino a UI, perfil ou payload de negócio.
- **Tools:** LOCAL-KMP.
- **Tests:** unit, +4 casos:
  homeRoundTrips, catalogRoundTrips, destinationsAreDistinct,
  inventoryContainsExactlyHomeAndCatalog.
- **Gate:** mobile/gradlew -p mobile :compose-app:allTests --console=plain.
- **Done when:**
  - [ ] plugin e runtime estão declarados separadamente nas versões fixas;
  - [ ] inventário contém exatamente dois data objects;
  - [ ] contagem final é baseline +4 ou maior e Gate termina 0.
- **Commit:** build(navigation): configure typed destinations

## Fase 1 — Contratos compartilhados sem UI

### T08 — Implementar SaqzUiState

- **What:** criar o contrato sealed exaustivo de estado assíncrono.
- **Where:** mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/state/SaqzUiState.kt;
  mobile/core/common/src/commonTest/kotlin/br/com/saqz/core/common/state/SaqzUiStateTest.kt.
- **Depends on:** T07.
- **Reuses:** estilo kotlin.test de commonTest.
- **Requirements:** STATE-01.
- **Fixed contract:** sealed interface SaqzUiState<out T>; Loading data object;
  Content<T>(value: T); Empty data object; Error data object.
- **Must not:** incluir Compose, callback, rede, mensagem localizada,
  Throwable, política de retry ou estado adicional.
- **Tools:** LOCAL-KM
- **Tests:** unit, +5 casos:
  loadingIsSingleton, contentCarriesValue, contentIsCovariant,
  emptyAndErrorAreDistinct, whenCoversExactlyFourStates.
- **Gate:** Quick core.
- **Done when:**
  - [ ] API pública corresponde literalmente ao Fixed contract;
  - [ ] when de teste é exaustivo sem else;
  - [ ] contagem final é baseline +5 ou maior e Gate termina 0.
- **Commit:** feat(core): add exhaustive ui state

### T09 — Implementar formatação de data/hora pt-BR

- **What:** fornecer timezone injetável e três outputs determinísticos.
- **Where:** mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/formatting/SaqzTimeZoneProvider.kt;
  mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/formatting/SaqzDateTimeFormatter.kt;
  mobile/core/common/src/commonTest/kotlin/br/com/saqz/core/common/formatting/SaqzDateTimeFormatterTest.kt.
- **Depends on:** T08.
- **Reuses:** kotlinx-datetime 0.8.0 e kotlin.test.
- **Requirements:** FMT-01, FMT-02, FMT-03, FMT-04, FMT-06, FMT-07, EDGE-02.
- **Fixed contract:** fun interface SaqzTimeZoneProvider com
  fun timeZone(): TimeZone; SaqzDateTimeFormatter(provider) expõe
  formatDate, formatTime e formatDateTime para Instant; padding decimal manual
  e formato 24h, sem locale formatter.
- **Must not:** usar timezone global dentro das funções; java.time/NSDate;
  locale do device; I/O; fallback silencioso para timezone inválido.
- **Tools:** LOCAL-KMP.
- **Tests:** unit, +9 casos:
  canonicalDate, canonicalTime, canonicalDateTime, previousDayBoundary,
  nextDayBoundary, invalidZoneFails, deviceLocaleDoesNotChangeDate,
  deviceLocaleDoesNotChangeTime, formatterIsOffline.
- **Gate:** Quick core.
- **Done when:**
  - [ ] fixture canônica produz 15/05/2025, 20:00 e 15/05/2025 20:00;
  - [ ] timezone inválido falha antes de output;
  - [ ] contagem final é baseline +9 ou maior e Gate termina 0.
- **Commit:** feat(formatting): add deterministic pt br date time

### T10 — Implementar formatação BRL por centavos

- **What:** converter Long de centavos em string BRL sem ponto flutuante.
- **Where:** mobile/core/common/src/commonMain/kotlin/br/com/saqz/core/common/formatting/SaqzCurrencyFormatter.kt;
  mobile/core/common/src/commonTest/kotlin/br/com/saqz/core/common/formatting/SaqzCurrencyFormatterTest.kt.
- **Depends on:** T09.
- **Reuses:** helpers de padding da task anterior somente se permanecerem
  platform-neutral e internal.
- **Requirements:** FMT-05, FMT-06, FMT-07, EDGE-03.
- **Fixed contract:** fun formatBrl(cents: Long): String; limite inclusivo
  ±9_007_199_254_740_991; NBSP entre R$ e valor; sinal antes de R$; grupos de
  milhar por ponto; -0L normaliza para zero.
- **Must not:** BigDecimal/Double/Float; NumberFormat/locale; arredondamento;
  output parcial em overflow.
- **Tools:** LOCAL-KMP.
- **Tests:** unit, +10 casos:
  zero, negativeZero, canonicalPositive, canonicalNegative, oneCent,
  positiveSafeLimit, negativeSafeLimit, positiveOverflowFails,
  negativeOverflowFails, deviceLocaleDoesNotChangeBrl.
- **Gate:** Quick core.
- **Done when:**
  - [ ] quatro outputs canônicos da spec são byte a byte exatos;
  - [ ] ambos os overflows falham explicitamente;
  - [ ] contagem final é baseline +10 ou maior e Gate termina 0.
- **Commit:** feat(formatting): add deterministic brl formatter

## Fase 2A — Registries visuais imutáveis

### T11 — Implementar SaqzColorTokens e contrato de contraste

- **What:** codificar os 28 tokens de cor da spec em um registry imutável.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzColorTokens.kt;
  mobile/core/design-system/src/commonTest/composeResources/files/ui-contract.json;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/theme/SaqzColorTokensTest.kt.
- **Depends on:** T10.
- **Reuses:** tabela Contrato Visual da spec como única fonte de valores.
- **Requirements:** VIS-02, VIS-06, A11Y-01, A11Y-02.
- **Fixed contract:** data class @Immutable com um campo camelCase por token,
  inclusive textMuted #707075 e controlBorder #85858A; default light registry;
  teste calcula luminância/contraste, não armazena ratios mágicos.
- **Must not:** renomear/omitir token; adicionar dark theme; usar accent como
  ação; fazer border/hairline carregar significado essencial.
- **Tools:** LOCAL-KMP.
- **Tests:** unit/contract, +8 casos:
  inventoryHasExactly28Tokens, valuesMatchSpec, mutedOnBackgroundIsAA,
  mutedOnSurfaceIsAA, controlBorderOnBackgroundIsThreeToOne,
  controlBorderOnSurfaceIsThreeToOne, accentUsesOnAccent,
  decorativeLinesAreNotControlIndicators.
- **Gate:** Quick design.
- **Done when:**
  - [ ] JSON e registry correspondem 1:1 à tabela da spec;
  - [ ] ratios são calculados e atendem 4.5:1/3:1 conforme o uso;
  - [ ] contagem final é baseline +8 ou maior e Gate termina 0.
- **Commit:** feat(theme): add exact saqz color tokens

### T12 — Implementar SaqzMetrics

- **What:** codificar grid, paddings, raios, nav e touch target da spec.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzMetrics.kt;
  mobile/core/design-system/src/commonTest/composeResources/files/ui-contract.json;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/theme/SaqzMetricsTest.kt.
- **Depends on:** T11.
- **Reuses:** tabela Métricas da spec e padrão @Immutable de T11.
- **Requirements:** VIS-02, VIS-08, A11Y-03.
- **Fixed contract:** campos grid 8dp, subGrid 4dp, horizontalPadding 16dp,
  sectionVerticalPadding 48dp, utilityCardPadding 16dp,
  primaryButtonRadius 12dp, compactControlRadius 8dp, cardRadius 16dp,
  bottomNavHeight 56dp e minimumTouchTarget 48dp.
- **Must not:** embutir safe-area no valor de 16/56dp; adicionar breakpoint ou
  métrica web; hardcode posterior fora do registry.
- **Tools:** LOCAL-KMP.
- **Tests:** unit/contract, +4 casos:
  inventoryHasExactlyTenMetrics, valuesMatchSpec,
  gridDerivedValuesAreMultiplesOfFour, minimumTouchTargetIs48Dp.
- **Gate:** Quick design.
- **Done when:**
  - [ ] os dez campos e unidades batem com a spec;
  - [ ] insets permanecem aditivos e fora do registry;
  - [ ] contagem final é baseline +4 ou maior e Gate termina 0.
- **Commit:** feat(theme): add exact saqz metrics

### T13 — Implementar SaqzMotionPolicy base

- **What:** fixar os números de motion dentro das faixas aprovadas.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzMotionPolicy.kt;
  mobile/core/design-system/src/commonTest/composeResources/files/ui-contract.json;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/theme/SaqzMotionPolicyTest.kt.
- **Depends on:** T12.
- **Reuses:** seção Composição e Motion da spec.
- **Requirements:** VIS-02, STATE-05, A11Y-04, A11Y-05.
- **Fixed contract:** normal usa pressScale 0.95, press 120ms, focus 180ms,
  route/state 220ms e translate máximo 8dp; reduced usa scale 1.0,
  translate 0dp e feedback por opacity 120ms.
- **Must not:** spring/bounce; duração acima de 250ms; remover feedback no modo
  reduzido; animação espacial fora da policy.
- **Tools:** LOCAL-KMP.
- **Tests:** unit/contract, +6 casos:
  normalValuesMatchContract, pressFallsInsideSpecRange,
  focusFallsInsideSpecRange, routeFallsInsideSpecRange,
  reducedHasNoSpatialMotion, reducedKeepsOpacityFeedback.
- **Gate:** Quick design.
- **Done when:**
  - [ ] números exatos aparecem no registry e no contract JSON;
  - [ ] ranges da spec continuam satisfeitos;
  - [ ] contagem final é baseline +6 ou maior e Gate termina 0.
- **Commit:** feat(theme): add deterministic motion policy

### T14 — Implementar SaqzTypography sem fonte de plataforma

- **What:** criar os oito TextStyles base não pré-escalados.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzTypography.kt;
  mobile/core/design-system/src/commonTest/composeResources/files/ui-contract.json;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/theme/SaqzTypographyTest.kt.
- **Depends on:** T13.
- **Reuses:** data class e oito campos definidos literalmente no design.
- **Requirements:** VIS-02, VIS-08, A11Y-06.
- **Fixed contract:** heroDisplay, displayLarge, displayMedium, lead, body,
  bodyStrong, caption e navigation; size/weight/line-height ratio/tracking em
  correspondem exatamente à tabela Tipografia; nenhum multiplicador de escala.
- **Must not:** resolver FontFamily nesta task; converter tracking em dp;
  arredondar ratios para inteiros; criar style extra.
- **Tools:** LOCAL-KMP.
- **Tests:** unit/contract, +9 casos: um por style e
  inventoryHasExactlyEightStyles; cada caso valida quatro propriedades.
- **Gate:** Quick design.
- **Done when:**
  - [ ] oito styles x quatro propriedades batem com a spec;
  - [ ] valores base permanecem em sp/em sem ler configuração do device;
  - [ ] contagem final é baseline +9 ou maior e Gate termina 0.
- **Commit:** feat(theme): add exact saqz typography

## Fase 2B — Fontes, theme, recursos e identidade

### T15 — Empacotar Inter 4.1 e implementar saqzFontFamily

- **What:** fixar origem/licença/checksums e mapear quatro TTFs estáticas no
  Android; manter fonte do sistema no iOS.
- **Where:** mobile/core/design-system/src/androidMain/composeResources/font/inter_light.ttf;
  mobile/core/design-system/src/androidMain/composeResources/font/inter_regular.ttf;
  mobile/core/design-system/src/androidMain/composeResources/font/inter_semibold.ttf;
  mobile/core/design-system/src/androidMain/composeResources/font/inter_bold.ttf;
  mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzFontFamily.kt;
  mobile/core/design-system/src/androidMain/kotlin/br/com/saqz/designsystem/theme/SaqzFontFamily.android.kt;
  mobile/core/design-system/src/iosMain/kotlin/br/com/saqz/designsystem/theme/SaqzFontFamily.ios.kt;
  mobile/core/design-system/THIRD_PARTY_LICENSES/Inter-OFL-1.1.txt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/theme/SaqzFontFamilyTest.kt;
  mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/InterPackagingTest.kt.
- **Depends on:** T14.
- **Reuses:** @Composable expect/actual do design e probe de T05.
- Amendment Execute 2026-07-15 (SPEC_DEVIATION aprovado pelo orquestrador):
  :core:design-system:allTests executa somente iosSimulatorArm64Test (o
  plugin com.android.kotlin.multiplatform.library não expõe test task
  Android), e as fontes são androidMain-only — logo os 6 casos de checksum/
  weight não podem viver em commonTest sem virarem placebo. Realocação:
  iosUsesSystemDefault permanece em commonTest (Quick design, iOS sim);
  fourFilesMatchChecksums, licenseMatchesChecksum, lightMapsTo300,
  regularMapsTo400, semiboldMapsTo600 e boldMapsTo700 movem para
  InterPackagingTest.kt (androidTest instrumentado, junto de
  apkRendersAllFourWeights), verificando os bytes reais empacotados no APK
  no device (connected 3→10). Nenhum caso removido ou enfraquecido;
  checksums também verificados no download antes de entrar no repo.
- **Requirements:** VIS-04, EDGE-04, A11Y-06.
- **Fixed contract:** fonte oficial
  https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip,
  archive SHA-256 9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e;
  Light 164414f0aacbe98a7e64addc43f7b3bfd2e32f7b90e101feeab227f14c371bda;
  Regular 40d692fce188e4471e2b3cba937be967878f631ad3ebbbdcd587687c7ebe0c82;
  SemiBold 78a843fade9d4612a5567302fb595b56976eb5fcebf4fea5a5912d638bafcde3;
  Bold 288316099b1e0a47a4716d159098005eef7c0066921f34e3200393dbdb01947f;
  weights 300/400/600/700; iOS FontFamily.Default; licença SIL OFL 1.1
  SHA-256 262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a.
- **Must not:** usar variable font, InterDisplay, CDN, R.font, subset/rename da
  família, SF empacotada ou escala manual.
- **Tools:** LOCAL-KMP + curl somente para o release oficial pinado.
- **Tests:** resource/contract/instrumentado, +8 casos:
  fourFilesMatchChecksums, licenseMatchesChecksum, lightMapsTo300,
  regularMapsTo400, semiboldMapsTo600, boldMapsTo700,
  iosUsesSystemDefault, apkRendersAllFourWeights.
- **Gate:** Quick design e Full Android.
- **Done when:**
  - [ ] archive e cinco arquivos passam SHA-256 antes de entrar no repo;
  - [ ] expect/actual corresponde literalmente ao design;
  - [ ] contagem final é baseline +8 ou maior e Gates terminam 0.
- **Commit:** feat(theme): package pinned inter static fonts

### T16 — Implementar SaqzTheme e ponte Material 2

- **What:** fornecer os quatro registries por CompositionLocal e derivar o
  subconjunto Material do mesmo estado.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzTheme.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/theme/SaqzThemeTest.kt.
- **Depends on:** T15.
- **Reuses:** registries T11–T15 e snippet de API do design.
- **Requirements:** VIS-02, VIS-06, CMP-06.
- **Fixed contract:** SaqzTheme(preferences, content); LocalSaqzColors,
  LocalSaqzMetrics, LocalSaqzTypography, LocalSaqzMotion privados/internal;
  únicos acessores públicos SaqzTheme.colors/metrics/typography/motion;
  MaterialTheme usa colorScheme/tipografia/shapes derivados, nunca paralelos.
- **Must not:** exportar constantes soltas; duplicar valor Material; adicionar
  dark mode; permitir componente acessar CompositionLocal diretamente.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI/unit, +6 casos:
  exposesFourRegistries, nestedThemeOverridesTogether,
  materialColorsDeriveFromSaqz, materialTypographyDerivesFromSaqz,
  shapesDeriveFromMetrics, noLoosePublicTokenAccessors.
- **Gate:** Quick design.
- **Done when:**
  - [ ] API pública possui apenas os acessores aprovados;
  - [ ] alterar registry no teste altera ponte Material correspondente;
  - [ ] contagem final é baseline +6 ou maior e Gate termina 0.
- **Commit:** feat(theme): add saqz theme composition locals

### T17 — Criar catálogo pt-BR em Compose Resources

- **What:** centralizar todos os textos e accessibility names da fundação.
- **Where:** mobile/core/design-system/src/commonMain/composeResources/values/strings.xml;
  mobile/compose-app/src/commonMain/composeResources/values/strings.xml;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/resources/ResourceCatalogTest.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/resources/ResourceCatalogTest.kt.
- **Depends on:** T16.
- **Reuses:** accessors Compose Resources provados em T03–T06.
- **Requirements:** L10N-01, L10N-02, L10N-03, L10N-04.
- **Fixed contract:** design-system possui labels genéricos de componentes/
  estados; compose-app possui Home, catálogo e navegação; o mesmo accessor
  alimenta label visível e nome acessível; locale alternativo não é criado.
- **Must not:** literal visível em Kotlin/Swift; arquivo values-en; formatter
  dependente do device; duplicar texto de acessibilidade.
- **Tools:** LOCAL-KMP.
- **Tests:** resource/Compose UI, +8 casos: quatro por módulo cobrindo inventário,
  locale invariance, label/name parity e ausência de literal público.
- **Gate:** Quick design e Quick app.
- **Done when:**
  - [ ] inventário de labels da spec está completo e sem duplicatas semânticas;
  - [ ] trocar locale do device mantém pt-BR;
  - [ ] contagem final é baseline +8 ou maior e ambos os Gates terminam 0.
- **Commit:** feat(resources): add pt br compose catalog

### T18 — Derivar assets mobile do SVG baseline

- **What:** gerar wordmark e símbolo quadrado rastreáveis para Compose/launchers.
- **Where:** landing-page/assets/saqz-logo.svg somente leitura;
  mobile/core/design-system/src/commonMain/composeResources/drawable/saqz_wordmark.xml;
  mobile/core/design-system/src/commonMain/composeResources/drawable/saqz_symbol.xml;
  mobile/brand/PROVENANCE.md;
  tests/scripts/check-mobile-brand-assets.test.sh;
  scripts/test-scripts.
- **Depends on:** T17.
- **Reuses:** paths/cores do SVG baseline.
- **Requirements:** VIS-05, LAUNCH-01, EDGE-04.
- **Fixed contract:** source SHA-256
  0c732546309e7143f60203472c368a3cebbb3a53721f142898724023aa33a473;
  wordmark viewBox 0 0 1200 360; símbolo é crop 0 0 360 360 dos mesmos paths;
  cores #0638DF, #FFFFFF, #C7F300; outputs vetoriais locais.
- **Must not:** redesenhar paths, rasterizar, alterar o SVG source, mudar cores,
  usar image generation ou buscar asset remoto em runtime.
- **Tools:** SHELL + validação XML/vector; sem imagegen.
- **Tests:** contract/mutation, +6 cenários:
  sourceHashPinned, wordmarkViewBox, symbolViewBox, pathDataPreserved,
  colorsPreserved, missingOutputFails.
- **Gate:** tests/scripts/check-mobile-brand-assets.test.sh, Quick design,
  scripts/check-landing e scripts/test-scripts.
- **Done when:**
  - [ ] hashes/path data provam derivação e original fica byte a byte igual;
  - [ ] remover qualquer output obrigatório falha;
  - [ ] contagem final é baseline +6 ou maior e Gates terminam 0.
- **Commit:** feat(brand): add deterministic mobile assets

### T19 — Aplicar preferences de motion/transparência no theme

- **What:** transformar dois booleans primitivos na policy/chrome efetivos.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzAccessibilityPreferences.kt;
  mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/theme/SaqzTheme.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/theme/SaqzAccessibilityPreferencesTest.kt.
- **Depends on:** T18.
- **Reuses:** policies exatas de T13 e theme de T16.
- **Requirements:** STATE-05, A11Y-05, EDGE-08.
- **Fixed contract:** SaqzAccessibilityPreferences(reduceMotion: Boolean,
  reduceTransparency: Boolean); reduceMotion seleciona policy reduced;
  reduceTransparency troca chrome translúcido por surface opaca e preserva
  hairline 1dp; nenhuma preferência tipográfica entra nessa classe.
- **Must not:** observar APIs nativas em commonMain; remover opacity/feedback;
  tornar hairline indicador único de controle.
- **Tools:** LOCAL-KMP.
- **Tests:** unit/Compose UI, +6 casos:
  defaultsAreFalse, reduceMotionSelectsReducedPolicy,
  normalSelectsNormalPolicy, transparencyKeepsTranslucentChrome,
  reducedTransparencyUsesOpaqueSurface,
  opaqueChromeKeepsHairline.
- **Gate:** Quick design.
- **Done when:**
  - [ ] quatro combinações booleanas produzem estado determinístico;
  - [ ] API não contém fontScale/content size;
  - [ ] contagem final é baseline +6 ou maior e Gate termina 0.
- **Commit:** feat(theme): apply accessibility preferences

## Fase 3 — Componentes básicos

### T20 — Implementar SaqzButton

- **What:** criar um botão público com quatro variantes e cinco estados.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzButton.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzButtonTest.kt.
- **Depends on:** T19.
- **Reuses:** SaqzTheme, resources pt-BR e runComposeUiTest.
- **Requirements:** CMP-01, CMP-02, A11Y-01, A11Y-03, A11Y-04.
- **Fixed contract:** enum SaqzButtonVariant com Primary, Secondary, Ghost,
  Destructive; SaqzButton(label, onClick, modifier, variant, enabled, loading);
  48dp; loading mantém largura/nome e marca busy; press usa T13.
- **Must not:** usar accent como botão; disparar em loading/disabled; hardcode
  token; executar callback no press antes do release.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +12 casos:
  fourVariantsUseExpectedTokens, focusIsThreeToOne, pressStartsBeforeRelease,
  pressScaleIs095, pressDurationIs120ms, releaseActivatesOnce,
  disabledHasSemantics, disabledDoesNotActivate, loadingIsBusy,
  loadingKeepsName, loadingKeepsWidth, reducedMotionKeepsFeedback.
- **Gate:** Quick design.
- **Done when:**
  - [ ] API e enum correspondem ao Fixed contract;
  - [ ] matriz variante/estado passa com alvo e semantics;
  - [ ] contagem final é baseline +12 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz button

### T21 — Implementar SaqzInput

- **What:** criar campo controlado para text/email/password.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzInput.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzInputTest.kt.
- **Depends on:** T20.
- **Reuses:** TextFieldValue, SaqzTheme e resources T17.
- **Requirements:** CMP-01, CMP-07, A11Y-01, A11Y-03, A11Y-04, A11Y-07.
- **Fixed contract:** enum SaqzInputKind Text, Email, Password;
  SaqzInput(value, onValueChange, label, modifier, kind, helperText, errorText,
  enabled); error substitui helper e associa semantics; password toggle muda
  somente VisualTransformation e tem alvo 48dp.
- **Must not:** guardar cópia interna do TextFieldValue; validar negócio;
  alterar valor/seleção/foco no toggle; literal acessível fora de resources.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +11 casos:
  valueIsControlled, labelIsAssociated, helperIsAssociated,
  errorReplacesHelper, errorIsAnnounced, emailUsesEmailKeyboard,
  passwordStartsObscured, togglePreservesValue, togglePreservesSelection,
  togglePreservesFocus, toggleTargetIs48Dp.
- **Gate:** Quick design.
- **Done when:**
  - [ ] teste mantém explicitamente TextRange antes/depois do toggle;
  - [ ] árvore semantics contém label/erro/role/state na ordem esperada;
  - [ ] contagem final é baseline +11 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz input

### T22 — Implementar SaqzCard

- **What:** criar superfície estática ou interativa sem falsa affordance.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzCard.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzCardTest.kt.
- **Depends on:** T21.
- **Reuses:** surface/cardRadius/minimumTouchTarget de SaqzTheme.
- **Requirements:** CMP-01, CMP-03, A11Y-03, A11Y-04.
- **Fixed contract:** SaqzCard(modifier, onClick: (() -> Unit)?, content);
  null é estático sem click role/indication; não-null é interativo com target
  mínimo 48dp, feedback no press e callback uma vez no release.
- **Must not:** shadow/gradiente; accent como affordance; click semantics no
  modo estático; callback no press.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +6 casos:
  staticHasNoClickAction, staticHasNoPressIndication, interactiveHasClickRole,
  interactiveTargetIs48Dp, feedbackStartsOnPress, releaseActivatesOnce.
- **Gate:** Quick design.
- **Done when:**
  - [ ] null/non-null mudam semantics e feedback de modo binário;
  - [ ] tokens vêm somente do theme;
  - [ ] contagem final é baseline +6 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz card

### T23 — Implementar SaqzListItem

- **What:** criar item estático/interativo com slots opcionais.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzListItem.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzListItemTest.kt.
- **Depends on:** T22.
- **Reuses:** contrato de interação de SaqzCard e tokens T12/T14.
- **Requirements:** CMP-01, CMP-03, A11Y-03, A11Y-04.
- **Fixed contract:** SaqzListItem(headline, modifier, supportingContent?,
  leadingContent?, trailingContent?, onClick?); ordem semantics leading,
  headline/supporting, trailing; modo interativo cobre linha inteira e 48dp.
- **Must not:** tornar slot opcional clicável por padrão; reordenar leitura;
  duplicar click em child/row; hardcode de altura abaixo de 48dp.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +7 casos:
  staticHasNoClickAction, interactiveRowIsClickable, rowTargetIs48Dp,
  readingOrderIsStable, optionalSlotsDoNotChangeOrder,
  pressFeedbackPrecedesRelease, nestedContentActivatesOnce.
- **Gate:** Quick design.
- **Done when:**
  - [ ] todas as combinações de slots mantêm bounds e ordem;
  - [ ] um gesto produz exatamente um callback;
  - [ ] contagem final é baseline +7 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz list item

### T24 — Implementar SaqzBadge

- **What:** criar seis variantes semânticas de badge.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzBadge.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzBadgeTest.kt.
- **Depends on:** T23.
- **Reuses:** pares surface/foreground de T11.
- **Requirements:** CMP-01, CMP-04, A11Y-01, A11Y-02.
- **Fixed contract:** enum SaqzBadgeVariant Neutral, Accent, Info, Success,
  Warning, Error; SaqzBadge(label, variant, modifier); cada variante usa par
  semântico; Accent usa accent/onAccent.
- **Must not:** tornar badge clicável; usar somente cor para significado;
  inventar variante; trocar foreground por aproximação.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI/contrast, +7 casos:
  inventoryHasSixVariants e um caso por variante validando tokens/AA.
- **Gate:** Quick design.
- **Done when:**
  - [ ] enum possui exatamente seis entradas;
  - [ ] label acessível acompanha a cor e todos os pares passam contraste;
  - [ ] contagem final é baseline +7 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz badge

## Fase 4 — Overlays, estados e navegação visual

### T25 — Implementar SaqzDialog

- **What:** criar dialog modal com dismiss channels independentes.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzDialog.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzDialogTest.kt.
- **Depends on:** T24.
- **Reuses:** Compose Dialog/DialogProperties, SaqzButton e resources T17.
- **Requirements:** CMP-01, CMP-05, CMP-08, A11Y-03, A11Y-04, A11Y-07,
  EDGE-07.
- **Fixed contract:** SaqzDialog(title, onCloseRequest, primaryAction,
  modifier, dismissOnBackPress, dismissOnClickOutside, showCloseAction,
  content); defaults false/false/true; fundo modal indisponível; foco inicial
  anuncia título e ação principal; conteúdo rola, footer fica fixo.
- **Must not:** colapsar flags em dismissible único; esconder close por causa
  das flags; permitir interação/semantics no fundo; fechar por swipe.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +11 casos:
  backgroundIsBlocked, titleIsAnnouncedFirst, primaryActionIsAnnounced,
  closeIsAccessible, backDismissesWhenEnabled, backIgnoredWhenDisabled,
  outsideDismissesWhenEnabled, outsideIgnoredWhenDisabled,
  explicitCloseAlwaysWorks, longContentScrolls, actionsRemainVisible.
- **Gate:** Quick design.
- **Done when:**
  - [ ] quatro combinações back/outside passam separadamente;
  - [ ] footer continua visível com conteúdo longo;
  - [ ] contagem final é baseline +11 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz dialog

### T26 — Implementar SaqzBottomSheet

- **What:** criar superfície modal inferior sem drag usando Dialog.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzBottomSheet.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzBottomSheetTest.kt.
- **Depends on:** T25.
- **Reuses:** contrato modal e API de flags de T25.
- **Requirements:** CMP-01, CMP-05, CMP-08, A11Y-03, A11Y-04, A11Y-07,
  EDGE-07.
- **Fixed contract:** mesma API semântica de T25, ancorada em bottom, largura
  limitada pela safe area; body rolável e footer fixo; nenhum gesture state ou
  dependency experimental.
- **Must not:** ModalBottomSheet experimental; drag handle interativo;
  drag-to-dismiss; ação dentro da região rolável.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +11 casos equivalentes aos canais de T25, trocando o
  último por noDragDismissSemantics e incluindo bottomAnchoring.
- **Gate:** Quick design.
- **Done when:**
  - [ ] árvore modal, foco e flags igualam T25;
  - [ ] bounds confirmam footer fixo e alinhamento inferior;
  - [ ] contagem final é baseline +11 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz bottom sheet

### T27 — Implementar state views e SaqzStateHost

- **What:** renderizar os quatro branches de SaqzUiState por slots explícitos.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzStateHost.kt;
  mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzLoadingState.kt;
  mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzEmptyState.kt;
  mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzErrorState.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzStateHostTest.kt.
- **Depends on:** T26.
- **Reuses:** SaqzUiState T08, SaqzButton T20 e motion T13/T19.
- **Requirements:** CMP-01, LAUNCH-03, STATE-02, STATE-03, STATE-04, STATE-05,
  A11Y-03, A11Y-07.
- **Fixed contract:** SaqzStateHost(state, modifier, loading, content, empty,
  error, onRetry); defaults públicos SaqzLoadingState/SaqzEmptyState/
  SaqzErrorState; Error recebe retry delegado; fullscreen states centralizam
  apenas no content slot fornecido pelo caller.
- **Must not:** rede, coroutine, retry policy, nav/inset próprio, estado extra,
  callback duplicado ou transição fora de motion policy.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +9 casos:
  rendersLoading, rendersContentValue, rendersEmpty, rendersError,
  retryActivatesOnce, customSlotsReplaceDefaults, normalTransitionUses220ms,
  reducedTransitionHasNoTranslate, semanticsOrderMatchesState.
- **Gate:** Quick design.
- **Done when:**
  - [ ] when é exaustivo sem else e cada slot roda somente no branch correto;
  - [ ] retry é chamado uma vez e nenhum I/O existe no módulo;
  - [ ] contagem final é baseline +9 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz state host

### T28 — Implementar SaqzBottomNav

- **What:** criar chrome de navegação genérico para exatamente dois itens no
  app.
- **Where:** mobile/core/design-system/src/commonMain/kotlin/br/com/saqz/designsystem/component/SaqzBottomNav.kt;
  mobile/core/design-system/src/commonTest/kotlin/br/com/saqz/designsystem/component/SaqzBottomNavTest.kt.
- **Depends on:** T27.
- **Reuses:** metrics T12, preferences T19 e resources T17.
- **Requirements:** CMP-01, NAV-02, A11Y-01, A11Y-03, A11Y-04, A11Y-07,
  EDGE-08.
- **Fixed contract:** data class SaqzBottomNavItem(label, selected, onClick,
  icon); SaqzBottomNav(items, modifier, contentWindowInsets); altura 56dp mais
  inset; item 48dp; selected semantics; hairline 1dp contínuo.
- **Must not:** conhecer SaqzDestination; aceitar item sem nome; usar hairline
  ou cor como único selected signal; disparar duas vezes na reselection.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +9 casos:
  baseHeightIs56Dp, bottomInsetIsAdded, eachItemIs48Dp,
  selectedStateIsAnnounced, selectionHasNonColorSignal, readingOrderMatchesList,
  clickActivatesOnce, reselectionActivatesOnce, hairlineSurvivesBothChromes.
- **Gate:** Quick design.
- **Done when:**
  - [ ] layout e semantics passam com dois fixtures Início/Componentes;
  - [ ] chrome opaco/translúcido mantém divisor e contraste;
  - [ ] contagem final é baseline +9 ou maior e Gate termina 0.
- **Commit:** feat(components): add saqz bottom navigation

## Fase 5 — Home, catálogo e shell compartilhado

### T29 — Implementar SaqzHomeScreen

- **What:** substituir o placeholder compartilhado pela Home da spec.
- **Where:** mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/home/SaqzHomeScreen.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/home/SaqzHomeScreenTest.kt.
- **Depends on:** T28.
- **Reuses:** wordmark T18, SaqzButton T20 e strings T17.
- **Requirements:** NAV-01, L10N-01, L10N-02, L10N-04.
- **Fixed contract:** wordmark central, heading Saqz, ação Explorar componentes;
  callback onExploreComponents; ordem de leitura wordmark/heading/action.
- **Must not:** avatar, perfil, login/logout, role, dado de negócio, rede ou
  string literal duplicada.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI, +6 casos:
  rendersWordmark, rendersHeading, rendersExploreAction,
  accessibilityOrderIsStable, actionNameMatchesVisibleLabel,
  exploreActivatesOnce.
- **Gate:** Quick app.
- **Done when:**
  - [ ] inventário e ordem são exatamente os três elementos aprovados;
  - [ ] callback é a única saída da tela;
  - [ ] contagem final é baseline +6 ou maior e Gate termina 0.
- **Commit:** feat(app): add saqz home

### T30 — Implementar SaqzCatalogScreen

- **What:** criar uma tela de inspeção 1:1 do design system aprovado.
- **Where:** mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/catalog/SaqzCatalogScreen.kt;
  mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/catalog/CatalogFixtures.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/catalog/SaqzCatalogScreenTest.kt.
- **Depends on:** T29.
- **Reuses:** ui-contract.json, T11–T28 e strings T17.
- **Requirements:** VIS-08, CMP-01, NAV-03, A11Y-06.
- **Fixed contract:** seções nesta ordem: cores, tipografia, métricas, Button,
  Input, Card, ListItem, Badge, Dialog, BottomSheet, estados, BottomNav e menus
  owner/atleta; cada variante/estado visível; fixtures de menu são labels
  não clicáveis e não destinos.
- **Must not:** omitir variante; criar screen separada; tornar role selecionável;
  mostrar avatar/login/logout; usar dado backend.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI/contract, +10 casos:
  sectionOrderMatchesContract, allColorsShown, allTypographyShown,
  allMetricsShown, allComponentVariantsShown, allStatesShown,
  ownerFixtureIsNonInteractive, athleteFixtureIsNonInteractive,
  productionHasNoForbiddenContent, fontScale2ReflowsWithoutCuttingActions.
- **Gate:** Quick app.
- **Done when:**
  - [ ] inventário de runtime é igual ao contract JSON sem ausentes/extras;
  - [ ] tela inteira permanece alcançável em fontScale 2.0;
  - [ ] contagem final é baseline +10 ou maior e Gate termina 0.
- **Commit:** feat(app): add complete design catalog

### T31 — Integrar SaqzAppEnvironment e startup state

- **What:** injetar estado inicial e duas preferências primitivas no root.
- **Where:** mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzAppEnvironment.kt;
  mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/SaqzAppEnvironmentTest.kt.
- **Depends on:** T30.
- **Reuses:** SaqzUiState T08, preferences T19, StateHost T27 e Home T29.
- **Requirements:** LAUNCH-03, STATE-02, STATE-04, A11Y-05, A11Y-06.
- **Fixed contract:** internal SaqzAppEnvironment(startupState:
  SaqzUiState<Unit>, reduceMotion, reduceTransparency); SaqzApp aceita
  environment internal para testes e default Content(Unit); boundary nativa
  expõe somente dois booleans, nunca tipo core nem escala.
- **Must not:** segunda splash Compose; timer; exportar SaqzUiState a Swift;
  UIFont/fontScale; observar API nativa em commonMain.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI/unit, +7 casos:
  defaultShowsContent, loadingUsesStateHost, emptyUsesStateHost,
  errorUsesStateHost, reduceMotionReachesTheme,
  reduceTransparencyReachesTheme, nativeBoundaryHasOnlyTwoBooleans.
- **Gate:** Quick app.
- **Done when:**
  - [ ] quatro startup states entram no mesmo shell/content slot;
  - [ ] API pública gerada para nativo não inclui core/font scale;
  - [ ] contagem final é baseline +7 ou maior e Gate termina 0.
- **Commit:** feat(app): integrate startup environment

### T32 — Implementar SaqzNavHost type-safe

- **What:** ligar Home e Catalog a um NavHost com navegação idempotente.
- **Where:** mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHost.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/navigation/SaqzNavHostTest.kt.
- **Depends on:** T31.
- **Reuses:** destinos T07 e telas T29/T30.
- **Requirements:** NAV-01, NAV-04, NAV-05, EDGE-01, EDGE-06.
- **Fixed contract:** start Home; ação Home navega Catalog com
  launchSingleTop=true, restoreState=true e popUpTo start com saveState=true;
  back de Catalog volta Home; reselection não cria entrada.
- **Must not:** string route; terceiro destino; deep link; login/logout/perfil;
  NavController em design-system.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI/navigation, +8 casos:
  startsAtHome, exploreOpensCatalog, backReturnsHome,
  homeReselectionIsIdempotent, catalogReselectionIsIdempotent,
  repeatedSequenceHasNoDuplicates, restoreReturnsSelectedDestination,
  graphHasExactlyTwoDestinations.
- **Gate:** Quick app.
- **Done when:**
  - [ ] sequência Home→Catalog→back e reselections mantém uma entrada;
  - [ ] graph contém apenas os dois tipos de T07;
  - [ ] contagem final é baseline +8 ou maior e Gate termina 0.
- **Commit:** feat(navigation): add typed saqz nav host

### T33 — Implementar SaqzAppShell com insets e restauração

- **What:** compor theme, NavHost, content slot e BottomNav em um shell.
- **Where:** mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/shell/SaqzAppShell.kt;
  mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt;
  mobile/compose-app/src/commonTest/kotlin/br/com/saqz/composeapp/shell/SaqzAppShellTest.kt.
- **Depends on:** T32.
- **Reuses:** Environment T31, NavHost T32 e BottomNav T28.
- **Requirements:** VIS-07, NAV-02, NAV-03, NAV-05, A11Y-03, A11Y-06,
  A11Y-07, EDGE-05, EDGE-08.
- **Fixed contract:** content recebe safeDrawing + ime; BottomNav aplica apenas
  bottom inset uma vez; destino e estado overlay fechado usam saveable state;
  bottom chrome pode sobrepor scroll, com padding final que mantém ações
  alcançáveis.
- **Must not:** consumir inset duas vezes; reabrir overlay após recreation;
  inicializar Firebase; criar destination/profile extra; cortar ação em
  landscape/fontScale 2.0.
- **Tools:** LOCAL-KMP.
- **Tests:** Compose UI/restoration, +10 casos:
  bottomInsetAppliedOnce, imeKeepsFocusedControlVisible,
  landscapeKeepsActionsReachable, fontScale2KeepsActionsReachable,
  contentScrollsUnderChrome, finalPaddingClearsChrome,
  selectedDestinationRestores, closedOverlayStaysClosed,
  readingOrderIsContentThenNav, productionNavHasTwoItems.
- **Gate:** Quick app e Build shared.
- **Done when:**
  - [ ] bounds/insets passam em portrait, landscape, IME e fontScale 2.0;
  - [ ] restore preserva destino e overlay fechado;
  - [ ] contagem final é baseline +10 ou maior e ambos os Gates terminam 0.
- **Commit:** feat(app): add accessible mobile shell

## Fase 6 — Launchers e adapters nativos mínimos

### T34 — Implementar launch screen Android nativa

- **What:** configurar splash de sistema e transição direta ao shell.
- **Where:** mobile/gradle/libs.versions.toml;
  mobile/android-app/build.gradle.kts;
  mobile/android-app/src/main/AndroidManifest.xml;
  mobile/android-app/src/main/res/values/colors.xml;
  mobile/android-app/src/main/res/values/themes.xml;
  mobile/android-app/src/main/res/values-v31/themes.xml;
  mobile/android-app/src/main/res/drawable/saqz_launch_symbol.xml;
  mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/AndroidLaunchContractTest.kt;
  mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/AndroidColdStartTest.kt.
- **Depends on:** T33.
- **Reuses:** símbolo T18, MainActivity e AndroidFirebaseBootstrap existentes.
- **Requirements:** VIS-05, LAUNCH-01, LAUNCH-02, LAUNCH-04.
- **Fixed contract:** core-splashscreen 1.2.0; Theme.Saqz.Starting;
  windowSplashScreenBackground #F5F5F7; símbolo local; postSplashScreenTheme
  do app; installSplashScreen antes de super.onCreate; edge-to-edge; API 23–30
  compat e 31+ sistema.
- **Must not:** setKeepOnScreenCondition; timer; Activity/screen Compose de
  splash; rede; mudar ordem de Firebase antes do root útil.
- **Tools:** ANDROID.
- **Tests:** JUnit/instrumentado, +7 casos:
  manifestUsesStartingTheme, legacyThemeUsesCoreSplash,
  v31ThemeUsesSystemSplash, backgroundMatchesSpec, symbolIsLocal,
  coldStartReachesHome, noIntermediateComposeSplash.
- **Gate:** Full Android em API 30.
- **Done when:**
  - [ ] launch termina no shell/estado real sem retenção artificial;
  - [ ] resource qualifiers cobrem 23–30 e 31+;
  - [ ] contagem final é baseline +7 ou maior e Gate termina 0.
- **Commit:** feat(android): add native saqz launch screen

### T35 — Cobrir restauração e acessibilidade Android

- **What:** provar font, rotação, insets, IME e bootstrap no launcher real.
- **Where:** mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/MainActivityTest.kt;
  mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/AndroidAccessibilityTest.kt;
  mobile/android-app/src/androidTest/kotlin/br/com/saqz/androidapp/ModernAndroidBehaviorTest.kt;
  mobile/android-app/src/test/kotlin/br/com/saqz/androidapp/FirebaseAuthBootstrapTest.kt;
  mobile/android-app/src/main/kotlin/br/com/saqz/androidapp/MainActivity.kt;
  mobile/compose-app/src/commonMain/kotlin/br/com/saqz/composeapp/SaqzApp.kt.
- **Depends on:** T34.
- **Reuses:** fake Firebase existente, shell T33 e resource test T05.
- **Requirements:** VIS-04, VIS-07, A11Y-03, A11Y-06, A11Y-07, EDGE-05.
- **Fixed contract:** API30 suite completa; classe ModernAndroidBehaviorTest é
  subset autocontido para API35; fontScale 2.0; rotação preserva destino e
  overlay fechado; Firebase nomeado inicializa uma vez por processo, nunca por
  recomposition.
- **Must not:** resetar estado para fazer teste passar; desligar animações da
  lógica; reinicializar Firebase em Composable; aceitar clipped/offscreen action.
- **Tools:** ANDROID.
- **Tests:** JUnit/instrumentado, +10 casos:
  fourInterWeightsRender, fontScale2Reflows, portraitInsets,
  landscapeInsets, imeKeepsInputVisible, rotationKeepsCatalog,
  rotationKeepsOverlayClosed, firebaseInitializesOnce,
  talkBackOrderMatchesSemantics, modernSubsetRunsStandalone.
- **Gate:** Full Android API30 e execução local isolada da classe modern.
- **Done when:**
  - [ ] todos os bounds/semantics são medidos no launcher real;
  - [ ] modern subset possui pelo menos fontScale, rotation, insets e cold start;
  - [ ] contagem final é baseline +10 ou maior e Gates terminam 0.
- **Commit:** test(android): cover accessibility and restoration

### T36 — Implementar launch screen e semantics Compose no iOS

- **What:** configurar UILaunchScreen estática e remover o falso elemento UIKit.
- **Where:** mobile/ios-app/SaqzIOS/Info.plist;
  mobile/ios-app/SaqzIOS/Assets.xcassets/LaunchBackground.colorset/Contents.json;
  mobile/ios-app/SaqzIOS/Assets.xcassets/LaunchSymbol.imageset/Contents.json;
  mobile/ios-app/SaqzIOS/Assets.xcassets/LaunchSymbol.imageset/saqz-symbol.svg;
  mobile/ios-app/SaqzIOS.xcodeproj/project.pbxproj;
  mobile/ios-app/SaqzIOS/SaqzIOSApp.swift;
  mobile/ios-app/SaqzIOSTests/LaunchContractTests.swift;
  mobile/ios-app/SaqzIOSUITests/SaqzIOSUITests.swift.
- **Depends on:** T35.
- **Reuses:** source symbol T18, FirebaseBootstrap atual e MainViewController.
- **Requirements:** VIS-05, LAUNCH-01, LAUNCH-02, LAUNCH-04, A11Y-07.
- **Fixed contract:** UILaunchScreen referencia LaunchBackground #F5F5F7 e
  LaunchSymbol; ambos entram no target/build phase; iOS 15+; ComposeRootView
  contém somente controller Compose; XCUITest consulta Home/nav/state por
  semantics Compose.
- **Must not:** storyboard; timer; splash SwiftUI; UILabel de 1x1; elemento de
  acessibilidade sintético; segunda cópia visual divergente.
- **Tools:** IOS.
- **Tests:** XCTest/XCUITest, +7 casos:
  plistUsesUILaunchScreen, backgroundAssetIsTargetMember,
  symbolAssetIsTargetMember, sourceHashMatchesT18,
  coldStartReachesComposeHome, composeNavIsAccessible,
  noSyntheticUIKitAccessibilityElement.
- **Gate:** SaqzDev test no simulador.
- **Done when:**
  - [ ] remover semantics Compose faz XCUITest falhar;
  - [ ] busca por UILabel/accessibilityLabel sintético retorna zero;
  - [ ] contagem final é baseline +7 ou maior e Gate termina 0.
- **Commit:** feat(ios): add native launch and compose semantics

### T37 — Integrar Reduce Motion/Transparency no iOS

- **What:** observar duas preferências UIKit e atualizá-las no Compose por
  boundary primitiva.
- **Where:** mobile/ios-app/SaqzIOS/AccessibilityPreferencesObserver.swift;
  mobile/ios-app/SaqzIOS/SaqzIOSApp.swift;
  mobile/compose-app/src/iosMain/kotlin/br/com/saqz/composeapp/SaqzAccessibilityController.kt;
  mobile/compose-app/src/iosMain/kotlin/br/com/saqz/composeapp/MainViewController.kt;
  mobile/ios-app/SaqzIOSTests/AccessibilityPreferencesObserverTests.swift;
  mobile/ios-app/SaqzIOSUITests/AccessibilityUITests.swift.
- **Depends on:** T36.
- **Reuses:** SaqzAccessibilityPreferences T19 e Environment T31.
- **Requirements:** VIS-07, A11Y-05, A11Y-06, A11Y-07.
- **Fixed contract:** observer injetável lê
  UIAccessibility.isReduceMotionEnabled/isReduceTransparencyEnabled e suas
  notificações; SaqzAccessibilityController expõe somente
  update(reduceMotion, reduceTransparency); Compose continua owner de
  semantics/foco/Dynamic Type; maior categoria aplica escala uma vez.
- **Must not:** UIFont.TextStyle, preferredContentSize, multiplicador/clamp,
  token/TextStyle/nav em Swift, recriar controller para cada notificação.
- **Tools:** IOS + Quick app para iosSimulator.
- **Tests:** XCTest/XCUITest/KMP, +8 casos:
  initialValuesReachCompose, motionNotificationUpdatesCompose,
  transparencyNotificationUpdatesCompose, observerStopsCleanly,
  swiftBoundaryHasOnlyTwoBooleans, noTypographyApiInSwift,
  largestDynamicTypeReflows, dynamicTypeIsAppliedOnce.
- **Gate:** SaqzDev test e compose-app:allTests.
- **Done when:**
  - [ ] toggles em runtime atualizam o mesmo controller Compose;
  - [ ] maior Dynamic Type mantém ações visíveis sem dupla escala;
  - [ ] contagem final é baseline +8 ou maior e ambos os Gates terminam 0.
- **Commit:** feat(ios): bridge accessibility preferences

## Fase 7 — Gates, isolamento e CI

### T38 — Atualizar scripts/check-gradle

- **What:** tornar as três suites KMP e as duas Android obrigatórias.
- **Where:** scripts/check-gradle; tests/scripts/check-gradle.test.sh.
  Amendment Execute 2026-07-15 (aprovado pelo orquestrador): inclui reescrita
  de 2 comentários em mobile/compose-app/.../catalog/SaqzCatalogScreen.kt e
  .../shell/SaqzAppShell.kt (commit 4407ab9, Fase 5) que descreviam o que a
  fundação OMITE (login/logout/perfil) e disparavam falso positivo no regex
  de scope do check-scope (T01). Sem mudança de comportamento; check-scope
  não foi enfraquecido. Indispensável: scripts/check-gradle chama
  scripts/check-scope primeiro, então T38 não fecha o gate real sem isso.
- **Depends on:** T37.
- **Reuses:** harness/stubs e verificações adb existentes.
- **Requirements:** GATE-03.
- **Fixed contract:** ordem mobile:
  :core:common:allTests, :core:design-system:allTests,
  :compose-app:allTests, :android-app:testDevDebugUnitTest,
  :android-app:connectedDevDebugAndroidTest; credentials/scope antes; backend
  permanece inalterado.
- **Must not:** wildcard task; continue-on-error; fallback sem device; remover
  suite backend; aceitar BUILD SUCCESS sem testes descobertos.
- **Tools:** SHELL.
- **Tests:** contract/mutation, +9 cenários:
  exactInventory, exactOrder, credentialsFirst, scopeSecond,
  eachSuiteFailurePropagates, adbMissingFails, deviceMissingFails,
  zeroTestsFails, backendInventoryUnchanged.
- **Gate:** tests/scripts/check-gradle.test.sh e scripts/check-gradle.
- **Done when:**
  - [ ] uma mutação de remoção para cada suite é morta;
  - [ ] contagem final é baseline +9 ou maior;
  - [ ] ambos os comandos terminam 0 no ambiente suportado.
- **Commit:** chore(gates): include all mobile gradle suites

### T39 — Atualizar scripts/check-ios para dois schemes

- **What:** exigir SaqzDev UI/unit e SaqzProd Release build/unit.
- **Where:** scripts/check-ios; tests/scripts/check-ios.test.sh;
  mobile/ios-app/SaqzIOS.xcodeproj/xcshareddata/xcschemes/SaqzDev.xcscheme;
  mobile/ios-app/SaqzIOS.xcodeproj/xcshareddata/xcschemes/SaqzProd.xcscheme.
  Amendment Execute 2026-07-15 (aprovado pelo orquestrador): inclui
  mobile/gradle.properties (org.gradle.jvmargs -Xmx1g → -Xmx4g
  -XX:MaxMetaspaceSize=1g) como configuração indispensável — T39 é a
  primeira task a linkar o framework Kotlin/Native em Release
  (:compose-app:linkReleaseFrameworkIosSimulatorArm64), que estoura o heap
  de 1g e mata o Gradle daemon (GC thrashing), reproduzido 2x. Debug nunca
  aciona esse caminho. Só eleva o teto de heap; não enfraquece nada; ajuda
  builds anteriores também.
- **Depends on:** T38.
- **Reuses:** runtime matching atual e harness xcodebuild mock.
- **Requirements:** GATE-03.
- **Fixed contract:** mesma UDID compatível; invocação 1 SaqzDev test;
  invocação 2 SaqzProd -configuration Release build; invocação 3 SaqzProd
  -configuration Release -only-testing:SaqzIOSTests test; sempre
  CODE_SIGNING_ALLOWED=NO; fallback local quando plist de produção faltar.
- **Must not:** executar UI tests no Prod; exigir signing/credential real;
  escolher simulator de runtime diferente; aceitar zero XCTest.
- **Tools:** IOS + SHELL.
- **Tests:** contract, +9 cenários:
  exactThreeInvocations, sameUdid, devIncludesUi,
  prodBuildIsRelease, prodOnlyRunsUnit, codeSigningDisabled,
  missingRuntimeFails, eachInvocationFailurePropagates, zeroTestsFails.
- **Gate:** tests/scripts/check-ios.test.sh e scripts/check-ios.
- **Done when:**
  - [ ] ambos os schemes/configurações ficam explicitamente cobertos;
  - [ ] ausência de credencial de produção continua caminho suportado;
  - [ ] contagem final é baseline +9 ou maior e Gates terminam 0.
- **Commit:** chore(ios): gate dev and prod schemes

### T40 — Expandir isolamento do workspace mobile

- **What:** provar a matriz completa em scratch contendo somente mobile.
- **Where:** tests/scripts/check-workspace-isolation.test.sh.
- **Depends on:** T39.
- **Reuses:** scratch copier e stubs discriminatórios já usados para backend/mobile.
- **Requirements:** VIS-03, GATE-02, GATE-03.
- **Fixed contract:** inventário direto no scratch:
  core:common allTests → core:design-system allTests → compose-app allTests →
  Android host/instrumentado → SaqzDev → SaqzProd Release; sem backend,
  landing, web ou credentials; Gradle/Xcode usam apenas paths do scratch.
- **Must not:** chamar script fora do scratch; copiar sibling; mock que passa
  sem validar args; omitir platform suite por host.
- **Tools:** SHELL.
- **Tests:** integration/mutation, +10 cenários:
  exactInventory, exactOrder, noBackend, noLanding, noWeb,
  noAbsoluteOriginalPath, noProdCredential, eachFailurePropagates,
  zeroTestsFails, siblingDependencyMutationDies.
- **Gate:** tests/scripts/check-workspace-isolation.test.sh mobile.
- **Done when:**
  - [ ] scratch não contém siblings e registra todas as invocações exatas;
  - [ ] cada acoplamento indevido possui mutação morta;
  - [ ] contagem final é baseline +10 ou maior e Gate termina 0.
- **Commit:** test(isolation): cover complete mobile workspace

### T41 — Adicionar probe não bloqueante API 35

- **What:** criar job estável fora do aggregate para o subset moderno.
- **Where:** .github/workflows/initialization-gate.yml;
  tests/scripts/check-ci.test.sh.
- **Depends on:** T40.
- **Reuses:** setup/KVM do gradle-gate e ModernAndroidBehaviorTest T35.
- **Requirements:** LAUNCH-04, GATE-03.
- **Fixed contract:** job android-api35-probe; ubuntu-latest, JDK21,
  ReactiveCircus/android-emulator-runner@v2; api-level 35,
  target google_apis, arch x86_64, profile pixel_7, RAM 4096M,
  avd saqz-api35-probe, emulator-build 13823996, boot timeout 300,
  comando executa somente ModernAndroidBehaviorTest; job não entra em needs.
- **Must not:** continue-on-error dentro do job; latest não pinado para tuple;
  rodar check-gradle completo novamente; entrar antecipadamente no aggregate.
- **Tools:** CI.
- **Tests:** workflow contract, +8 cenários:
  jobExists, tuplePinned, bootTimeoutIs300, kvmEnabled,
  exactModernClassRuns, internalFailureIsFatal,
  probeOutsideAggregate, missingProbeDoesNotFailCurrentEvaluator.
- **Gate:** tests/scripts/check-ci.test.sh e primeiro run verde do probe.
- **Done when:**
  - [ ] contrato local prova job não bloqueante mas internamente estrito;
  - [ ] um run real verde registra SHA/tuple/boot duration;
  - [ ] contagem final é baseline +8 ou maior.
- **Commit:** ci(android): add api 35 stability probe

### T42 — Promover API 35 após três probes estáveis

- **What:** tornar android-api35-gate condição do aggregate após evidência.
- **Where:** .github/workflows/initialization-gate.yml;
  scripts/evaluate-ci-gates;
  tests/scripts/check-ci.test.sh;
  .specs/features/frontend-design-system-foundation/api35-evidence.md.
- **Depends on:** T41 + três runs verdes independentes no mesmo SHA/tuple,
  cada bootando em até 300s; qualquer falha reinicia a sequência.
- **Reuses:** job T41 e evaluator atual de gradle/iOS/landing.
- **Requirements:** GATE-04.
- **Fixed contract:** evidence.md registra três run IDs/URLs, SHA, tuple e
  duração; job é renomeado android-api35-gate; initialization-gate needs inclui
  gradle-gate, android-api35-gate, ios-gate, landing-gate; evaluator recebe
  quatro resultados e exige success em todos.
- **Must not:** promover com SHAs/tuples distintos; editar evidence manualmente
  sem runs; remover API30; ler .specs/checklist no evaluator.
- **Tools:** CI.
- **Tests:** contract/mutation, +7 cenários:
  aggregateNeedsFourJobs, evaluatorAcceptsFourSuccesses,
  missingFails, failureFails, cancelledFails, api30StillRequired,
  manualChecklistIsIgnored.
- **Gate:** tests/scripts/check-ci.test.sh e run aggregate pós-promoção.
- **Done when:**
  - [x] evidence contém três provas válidas e verificáveis;
  - [x] mutação de cada resultado para missing/failure/cancelled falha;
  - [x] contagem final é baseline +7 ou maior e Gates terminam 0.
- **Commit:** ci(android): promote stable api 35 gate

### T43 — Fechar gate local, README e handoff ao Verifier

- **What:** sincronizar orquestração/documentação e executar o baseline final.
- **Where:** scripts/check-all; tests/scripts/check-all.test.sh;
  tests/scripts/check-readme.test.sh; README.md;
  .specs/features/frontend-design-system-foundation/tasks.md;
  .specs/STATE.md apenas para registrar resultados, sem marcar Verified.
- **Depends on:** T42.
- **Reuses:** check-gradle T38, check-ios T39, check-landing existente,
  scripts/test-scripts e aggregate T42.
- **Requirements:** GATE-03, GATE-04.
- **Fixed contract:** check-all macOS executa Gradle → iOS → landing, com
  failure/signal propagation; README lista três allTests, Android, SaqzDev e
  SaqzProd; verificação final executa scripts/test-scripts e scripts/check-all;
  checklist manual não é input de nenhum gate.
- **Must not:** criar gate Angular/web; marcar Verified; omitir credentials/
  scope por estarem dentro de check-gradle; esconder achado manual.
- **Tools:** SHELL + LOCAL-KMP + ANDROID + IOS.
- **Tests:** contract, +8 cenários:
  exactCheckAllOrder, gradleFailurePropagates, iosFailurePropagates,
  landingFailurePropagates, signalPropagates, readmeListsAllSuites,
  noWebGate, manualChecklistNotRequired.
- **Gate:** scripts/test-scripts e scripts/check-all.
- **Done when:**
  - [x] ambos os gates passam e contagens são registradas em tasks.md;
  - [x] README e comandos reais são idênticos;
  - [x] contagem final é baseline +8 ou maior;
  - [x] task muda para Done e aciona Verifier independente automaticamente.
- **Commit:** chore(gates): finalize mobile design foundation

## Rastreabilidade Requisito → Task

| Requisitos | Tasks |
| --- | --- |
| VIS-01 | T01–T03 |
| VIS-02 | T11–T14, T16 |
| VIS-03 | T02–T06, T40 |
| VIS-04 | T15, T35 |
| VIS-05 | T18, T34, T36 |
| VIS-06 | T11, T16 |
| VIS-07 | T33, T35, T37 |
| VIS-08 | T12, T14, T30 |
| CMP-01 | T20–T30 |
| CMP-02 | T20 |
| CMP-03 | T22–T23 |
| CMP-04 | T24 |
| CMP-05 | T25–T26 |
| CMP-06 | T01, T16 |
| CMP-07 | T21 |
| CMP-08 | T25–T26 |
| NAV-01 | T29, T32 |
| NAV-02 | T28, T33 |
| NAV-03 | T30, T33 |
| NAV-04 | T07, T32 |
| NAV-05 | T32–T33 |
| LAUNCH-01 | T18, T34, T36 |
| LAUNCH-02 | T34, T36 |
| LAUNCH-03 | T27, T31 |
| LAUNCH-04 | T34, T36, T41 |
| STATE-01 | T08 |
| STATE-02 | T27, T31 |
| STATE-03 | T27 |
| STATE-04 | T27, T31 |
| STATE-05 | T13, T19, T27 |
| FMT-01..04 | T09 |
| FMT-05 | T10 |
| FMT-06..07 | T09–T10 |
| L10N-01..04 | T17, T29 |
| A11Y-01 | T11, T20–T28 |
| A11Y-02 | T11, T24 |
| A11Y-03 | T12, T20–T28, T33, T35 |
| A11Y-04 | T13, T20–T28 |
| A11Y-05 | T13, T19, T31, T37 |
| A11Y-06 | T14–T15, T30, T33, T35, T37 |
| A11Y-07 | T21, T25–T28, T33, T35–T37 |
| GATE-01 | T01 |
| GATE-02 | T40 |
| GATE-03 | T05–T06, T38–T41, T43 |
| GATE-04 | T42–T43 e Verifier |
| EDGE-01 | T32 |
| EDGE-02 | T09 |
| EDGE-03 | T10 |
| EDGE-04 | T03–T06, T15, T18 |
| EDGE-05 | T32–T33, T35 |
| EDGE-06 | T07, T32 |
| EDGE-07 | T25–T26 |
| EDGE-08 | T19, T28, T33 |

**Cobertura:** 60 de 60 requisitos aparecem nominalmente nos cartões e possuem
implementação, teste e gate.

## Task Granularity Check

| Task | Única entrega | Status |
| --- | --- | --- |
| T01 | allowlist/denylist de scope | ✅ |
| T02 | módulo core:common | ✅ |
| T03 | módulo design-system + sentinels inseparáveis | ✅ |
| T04 | consumidor umbrella dos sentinels | ✅ |
| T05 | packaging Android | ✅ |
| T06 | packaging iOS | ✅ |
| T07 | tipos de destino + build indispensável | ✅ |
| T08 | SaqzUiState | ✅ |
| T09 | formatter data/hora | ✅ |
| T10 | formatter BRL | ✅ |
| T11 | registry de cores | ✅ |
| T12 | registry de métricas | ✅ |
| T13 | registry de motion | ✅ |
| T14 | registry tipográfico | ✅ |
| T15 | FontFamily e quatro arquivos da mesma família | ✅ |
| T16 | SaqzTheme/Material bridge | ✅ |
| T17 | catálogo de strings | ✅ |
| T18 | derivação de brand assets | ✅ |
| T19 | aplicação das duas preferences | ✅ |
| T20 | SaqzButton | ✅ |
| T21 | SaqzInput | ✅ |
| T22 | SaqzCard | ✅ |
| T23 | SaqzListItem | ✅ |
| T24 | SaqzBadge | ✅ |
| T25 | SaqzDialog | ✅ |
| T26 | SaqzBottomSheet | ✅ |
| T27 | único contrato StateHost e defaults inseparáveis | ✅ |
| T28 | SaqzBottomNav | ✅ |
| T29 | Home | ✅ |
| T30 | Catálogo | ✅ |
| T31 | Environment/startup | ✅ |
| T32 | NavHost/back stack | ✅ |
| T33 | Shell/insets/restore | ✅ |
| T34 | launch Android | ✅ |
| T35 | integração Android do shell real | ✅ |
| T36 | launch/semantics iOS | ✅ |
| T37 | adapter das duas preferences iOS | ✅ |
| T38 | gate Gradle | ✅ |
| T39 | gate iOS | ✅ |
| T40 | isolamento mobile | ✅ |
| T41 | probe API35 | ✅ |
| T42 | promoção API35 | ✅ |
| T43 | aggregate local/documentação | ✅ |

Nenhum cartão mistura dois componentes públicos. Arquivos de teste, resource ou
build aparecem junto somente quando são necessários para tornar a única entrega
compilável e verificável no mesmo commit.

## Diagram-Definition Cross-Check

| Task | Depends on no cartão | Seta no Execution Plan | Status |
| --- | --- | --- | --- |
| T01 | none | início → T01 | ✅ |
| T02 | T01 | T01 → T02 | ✅ |
| T03 | T02 | T02 → T03 | ✅ |
| T04 | T03 | T03 → T04 | ✅ |
| T05 | T04 | T04 → T05 | ✅ |
| T06 | T05 | T05 → T06 | ✅ |
| T07 | T06 | T06 → T07 | ✅ |
| T08 | T07 | T07 → T08 | ✅ |
| T09 | T08 | T08 → T09 | ✅ |
| T10 | T09 | T09 → T10 | ✅ |
| T11 | T10 | T10 → T11 | ✅ |
| T12 | T11 | T11 → T12 | ✅ |
| T13 | T12 | T12 → T13 | ✅ |
| T14 | T13 | T13 → T14 | ✅ |
| T15 | T14 | T14 → T15 | ✅ |
| T16 | T15 | T15 → T16 | ✅ |
| T17 | T16 | T16 → T17 | ✅ |
| T18 | T17 | T17 → T18 | ✅ |
| T19 | T18 | T18 → T19 | ✅ |
| T20 | T19 | T19 → T20 | ✅ |
| T21 | T20 | T20 → T21 | ✅ |
| T22 | T21 | T21 → T22 | ✅ |
| T23 | T22 | T22 → T23 | ✅ |
| T24 | T23 | T23 → T24 | ✅ |
| T25 | T24 | T24 → T25 | ✅ |
| T26 | T25 | T25 → T26 | ✅ |
| T27 | T26 | T26 → T27 | ✅ |
| T28 | T27 | T27 → T28 | ✅ |
| T29 | T28 | T28 → T29 | ✅ |
| T30 | T29 | T29 → T30 | ✅ |
| T31 | T30 | T30 → T31 | ✅ |
| T32 | T31 | T31 → T32 | ✅ |
| T33 | T32 | T32 → T33 | ✅ |
| T34 | T33 | T33 → T34 | ✅ |
| T35 | T34 | T34 → T35 | ✅ |
| T36 | T35 | T35 → T36 | ✅ |
| T37 | T36 | T36 → T37 | ✅ |
| T38 | T37 | T37 → T38 | ✅ |
| T39 | T38 | T38 → T39 | ✅ |
| T40 | T39 | T39 → T40 | ✅ |
| T41 | T40 | T40 → T41 | ✅ |
| T42 | T41 + 3 probes | T41 → 3 probes → T42 | ✅ |
| T43 | T42 | T42 → T43 | ✅ |

Não há dependência para task futura e não há seta sem Depends on correspondente.

## Test Co-location Validation

| Task | Layer | Matrix requires | Cartão inclui | Status |
| --- | --- | --- | --- | --- |
| T01 | Scripts/scope | contract+mutation | +12 contract | ✅ |
| T02 | Config Gradle | none/build | build gate | ✅ |
| T03 | Tokens/resources | unit+contract | +2 resource | ✅ |
| T04 | App compartilhado | Compose UI | +2 Compose UI | ✅ |
| T05 | Android | instrumentado | +2 instrumentado | ✅ |
| T06 | iOS | XCUITest | +2 XCUITest | ✅ |
| T07 | App compartilhado | unit KMP | +4 unit | ✅ |
| T08 | Core common | unit KMP | +5 unit | ✅ |
| T09 | Core common | unit KMP | +9 unit | ✅ |
| T10 | Core common | unit KMP | +10 unit | ✅ |
| T11 | Tokens/resources | unit+contract | +8 contract | ✅ |
| T12 | Tokens/resources | unit+contract | +4 contract | ✅ |
| T13 | Tokens/resources | unit+contract | +6 contract | ✅ |
| T14 | Tokens/resources | unit+contract | +9 contract | ✅ |
| T15 | Tokens/resources | unit+packaging | +8 contract/instrumentado | ✅ |
| T16 | Tokens/theme | Compose UI | +6 Compose UI | ✅ |
| T17 | Resources/app | resource+Compose UI | +8 nos dois módulos | ✅ |
| T18 | Assets | contract+mutation | +6 contract | ✅ |
| T19 | Theme | unit+Compose UI | +6 | ✅ |
| T20 | Component | Compose UI | +12 | ✅ |
| T21 | Component | Compose UI | +11 | ✅ |
| T22 | Component | Compose UI | +6 | ✅ |
| T23 | Component | Compose UI | +7 | ✅ |
| T24 | Component | Compose UI | +7 | ✅ |
| T25 | Component | Compose UI | +11 | ✅ |
| T26 | Component | Compose UI | +11 | ✅ |
| T27 | Component/state | Compose UI | +9 | ✅ |
| T28 | Component/nav | Compose UI | +9 | ✅ |
| T29 | App compartilhado | Compose UI | +6 | ✅ |
| T30 | App compartilhado | Compose UI+contract | +10 | ✅ |
| T31 | App compartilhado | Compose UI+unit | +7 | ✅ |
| T32 | App navigation | Compose UI | +8 | ✅ |
| T33 | App shell | Compose UI+restore | +10 | ✅ |
| T34 | Android | unit+instrumentado | +7 | ✅ |
| T35 | Android | unit+instrumentado | +10 | ✅ |
| T36 | iOS | XCTest+XCUITest | +7 | ✅ |
| T37 | iOS/KMP | XCTest+XCUITest+KMP | +8 | ✅ |
| T38 | Scripts | contract+mutation | +9 | ✅ |
| T39 | Scripts/iOS | contract+integration | +9 | ✅ |
| T40 | Isolation | integration+mutation | +10 | ✅ |
| T41 | CI | contract+real probe | +8 + run | ✅ |
| T42 | CI | contract+3-run evidence | +7 + evidence | ✅ |
| T43 | Aggregate/docs | contract+full gate | +8 + full | ✅ |

**Resultado:** 43 de 43 cartões possuem o tipo de teste exigido pela matriz,
delta mínimo, comando de gate e critério binário. Não existe “testar depois”.

## Aprovação e início de Execute

Antes de Execute, o usuário confirma:

1. se aprova estes contratos e números exatos;
2. quais perfis/MCPs/skills podem ser usados;
3. se deseja a oferta de workers em batches sequenciais, obrigatória por haver
   mais de oito tasks.

Depois da confirmação, iniciar somente T01. T34 não autorizava T42; a promoção
foi concluída somente após a evidência externa dos três probes.
