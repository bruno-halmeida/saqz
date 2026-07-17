# Tasks de Autenticacao e Acesso

**Spec:** `.specs/features/authentication-access/spec.md`
**Design:** `.specs/features/authentication-access/design.md`
**Status:** Execute em andamento - T01..T50 concluidas
**Data:** 2026-07-16

## Execution Protocol (MANDATORY -- do not skip)

Implementar estas tasks com a skill `tlc-spec-driven` ativada por nome e
seguindo o fluxo Execute e suas Critical Rules. Se a skill nao puder ser
ativada, parar e avisar o usuario; nao executar por um fluxo improvisado.

- Fases e tasks sao sequenciais. Uma task so comeca apos commit e gate verde da
  anterior.
- Testes derivam dos resultados da spec e ficam no mesmo commit do codigo que
  verificam. Nenhum teste e adiado para uma task posterior.
- O gate decide conclusao. Nao reduzir, apagar, ignorar ou neutralizar teste,
  suite, device, mutacao ou constraint para obter verde.
- Um commit atomico por task, com a mensagem planejada.
- Antes de cada task, registrar SHA inicial, contagem baseline da suite afetada
  e arquivos esperados. Ao concluir, registrar SHA, delta e comando/evidencia.
- Depois de T58, um Verifier novo e independente executa outcome check,
  discrimination sensor e escreve `validation.md`. PASS e obrigatorio.
- Como ha mais de oito tasks, antes de Execute oferecer batches sequenciais de
  subagentes, sem dividir fase e somente se o usuario autorizar.

## Controle de Drift

1. `spec.md` define comportamento e outputs observaveis.
2. `context.md` define decisoes confirmadas com o usuario.
3. `design.md` define arquitetura, contratos e ownership.
4. Este arquivo define ordem, arquivos, testes e evidencias.
5. O codigo existente fornece estilo quando nao conflita com 1-4.

Se uma task exigir decisao ausente ou contrariar essa hierarquia, registrar
`SPEC_DEVIATION` e parar antes de editar.

Regras fixas:

- sem produto web, co-owner, assinatura, perfil esportivo ou configuracao de
  jogos/financeiro;
- backend e autoridade de usuario, membership, papel e convite;
- Firebase UID e a unica chave externa; email nunca faz merge;
- SDKs Firebase/Google/Branch permanecem nas bordas nativas;
- nenhum bearer token, senha, invite code ou email completo em logs/metrica;
- sem OpenAPI gerado, ORM, banco em memoria ou cache de conteudo protegido;
- convite e sempre ATHLETE, um ativo por grupo, sem TTL automatico;
- OWNER e unico e nunca vive em `group_memberships`;
- API 35 preserva a tupla bloqueante de `AD-023`; CI iOS roda somente SaqzDev
  conforme `AD-024`, enquanto o gate local completo preserva SaqzProd;
- “+N casos” significa N casos nomeados novos; a contagem nao pode diminuir.

## Perfis de Ferramentas Propostos

| Perfil | Ferramentas | MCPs | Skills |
| --- | --- | --- | --- |
| SHELL | `apply_patch`, `rg`, scripts POSIX e harness scratch | nenhum | `tlc-spec-driven`; `backprop` em falha |
| BACKEND | Gradle, JUnit, Spring Boot, PostgreSQL/Testcontainers, Firebase emulator | nenhum | `tlc-spec-driven`; `backprop` em falha |
| KMP | Gradle, kotlin.test, Ktor MockEngine, Compose UI test | nenhum | `tlc-spec-driven`; `backprop` em falha |
| ANDROID | KMP + JUnit, adb, emulator, Firebase/Branch/Credential Manager fakes | nenhum | `tlc-spec-driven`; `backprop` em falha |
| IOS | `xcodebuild`, simctl, XCTest/XCUITest, SDK fakes | nenhum | `tlc-spec-driven`; `backprop` em falha |
| CI | SHELL + leitura de workflows/runs | GitHub opcional, se autorizado | `tlc-spec-driven`; `backprop` em falha |

## Test Coverage Matrix

> Gerada de `README.md`, `RTK.md`, `scripts/check-*`,
> `.github/workflows/initialization-gate.yml`, testes JUnit/Spring existentes,
> `mobile/**/commonTest`, Android JUnit/instrumented e XCTest/XCUITest. Nao ha
> threshold numerico documentado; aplica-se o default forte da spec: todo AC e
> edge case, todos os branches de dominio e happy/edge/error por endpoint.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Scripts, Gradle e arquitetura | contract/mutation | Inventario, ordem, allowlist minima, falha propagada e mutacoes proibidas | `tests/scripts/**`, `backend/architecture-tests/**` | `scripts/test-scripts` |
| Shared identity/HTTP | unit + Spring integration | Principal neutro, todos os problems/status, correlation e redaction | `backend/shared-kernel/src/test`, `backend/features/identity/src/test`, `backend/bootstrap/src/test` | `backend/gradlew -p backend :shared-kernel:check :features:identity:test :bootstrap:test :architecture-tests:test --console=plain` |
| Access domain/application | unit | Todos os branches, matriz OWNER/ADMIN/ATHLETE/nao membro, 1:1 com ACs e edges | `backend/features/access/src/test/kotlin/**` | `backend/gradlew -p backend :features:access:test --console=plain` |
| Access JDBC/migrations | PostgreSQL integration | Constraints, queries, rollback, idempotencia, locks e concorrencia real | `backend/features/access/src/integrationTest/kotlin/**` | `backend/gradlew -p backend :features:access:integrationTest --console=plain` |
| Access HTTP | Spring integration + emulator | Cada rota: happy, validacao, auth, papel, inexistente, conflito e falha | `backend/bootstrap/src/test/kotlin/**` | `backend/gradlew -p backend :bootstrap:test :bootstrap:emulatorTest --console=plain` |
| Core network | KMP unit/MockEngine | Serialization, problem mapping, timeout, refresh unico concorrente e segundo 401 | `mobile/core/network/src/commonTest/kotlin/**` | `mobile/gradlew -p mobile :core:network:allTests --console=plain` |
| Access shared state/API | KMP unit | Cada transicao, retry, idempotencia, storage e client route 1:1 com ACs | `mobile/features/access/src/commonTest/kotlin/**` | `mobile/gradlew -p mobile :features:access:allTests --console=plain` |
| Access Compose UI/app shell | Compose UI KMP | Toda tela/estado, semantics, callbacks, back stack, single-flight e tamanho maximo | `mobile/features/access/src/commonTest`, `mobile/compose-app/src/commonTest` | `mobile/gradlew -p mobile :features:access:allTests :compose-app:allTests --console=plain` |
| Android adapters/app | JUnit + instrumented | SDK mapping, emulator, App Link, storage cifrado, restart, rotacao, IME/font scale | `mobile/android-app/src/test`, `mobile/android-app/src/androidTest` | `mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain` |
| iOS adapters/app | XCTest + XCUITest | SDK mapping, NativeLink, Keychain, restart, Dynamic Type, semantics e back stack | `mobile/ios-app/SaqzIOSTests`, `mobile/ios-app/SaqzIOSUITests` | `scripts/check-ios` |
| Config/schema sem comportamento | none | Build/migration gate; inventario exato; sem teste placebo | build files, plist, manifest, migrations | gate Build/Full da task |

## Gate Check Commands

> Gerados dos wrappers e scripts existentes; os novos targets passam a existir
> nas tasks de fundacao antes de serem usados.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Contract | scripts, Gradle, arquitetura, CI | `scripts/test-scripts` |
| Quick identity | shared-kernel/identity/HTTP transversal | `backend/gradlew -p backend :shared-kernel:check :features:identity:test :bootstrap:test :architecture-tests:test --console=plain` |
| Quick access | dominio/aplicacao | `backend/gradlew -p backend :features:access:test --console=plain` |
| SQL integration | migration/JDBC/concorrencia | `backend/gradlew -p backend :features:access:integrationTest --console=plain` |
| HTTP full | controllers/security/emulator | `backend/gradlew -p backend :bootstrap:test :bootstrap:emulatorTest --console=plain` |
| Quick network | Ktor/core | `mobile/gradlew -p mobile :core:network:allTests --console=plain` |
| Quick access mobile | API/state/UI feature | `mobile/gradlew -p mobile :features:access:compileAndroidMain :features:access:allTests --console=plain` |
| Quick app | root/navegacao | `mobile/gradlew -p mobile :compose-app:allTests --console=plain` |
| Full Android | adapters/lifecycle/UI | `mobile/gradlew -p mobile :android-app:testDevDebugUnitTest :android-app:connectedDevDebugAndroidTest --console=plain` |
| Full iOS | adapters/lifecycle/UI | `scripts/check-ios` |
| Isolation | workspaces independentes | `tests/scripts/check-workspace-isolation.test.sh backend && tests/scripts/check-workspace-isolation.test.sh mobile` |
| Full local | fechamento/verifier | `scripts/check-all` |

## Execution Plan

Fases e tasks executam estritamente em sequencia:

```text
Fase 0: T01 -> T02 -> T03 -> T04 -> T05 -> T06
Fase 1: T07 -> T08 -> T09 -> T10 -> T11
Fase 2: T12 -> T13 -> T14 -> T15 -> T16 -> T17 -> T18 -> T19 -> T20
Fase 3: T21 -> T22 -> T23 -> T24 -> T25 -> T26 -> T27 -> T28 -> T29 -> T30
Fase 4: T31 -> T32 -> T33 -> T34 -> T35 -> T36 -> T37 -> T38
Fase 5: T39 -> T40 -> T41 -> T42 -> T43 -> T44 -> T45
Fase 6: T46 -> T47 -> T48 -> T49
Fase 7: T50 -> T51 -> T52 -> T53
Fase 8: T54 -> T55 -> T56 -> T57 -> T58
```

## Task Breakdown

## Fase 0 - Guardrails e fundacao backend

### T01 - Abrir o scope gate para o Epico 03

- **What:** substituir os bloqueios do Epico 01 por allowlists estreitas para
  persistencia, `features:access`, `core:network`, UI auth e adapters nativos.
- **Where:** `scripts/check-scope`; `tests/scripts/check-scope.test.sh`.
- **Depends on:** none.
- **Reuses:** harness scratch e mensagens discriminatorias existentes.
- **Requirements:** SEC-04.
- **Fixed contract:** aceitar somente paths/tecnologias do design e continuar
  rejeitando produto web, ORM, banco client-side, OpenAPI gerado, co-owner,
  cross-workspace e secrets.
- **Must not:** apagar mutacao negativa ou trocar o gate por regex generica.
- **Tools:** SHELL.
- **Tests:** contract/mutation, +12 cenarios positivos/negativos.
- **Gate:** Contract.
- **Done when:** [x] todas as categorias possuem fixture discriminatoria; [x]
  baseline +12 ou maior sem remocoes; [x] `scripts/check-scope` e Contract verdes.
- **Commit:** `chore(scope): allow authentication access epic`

### T02 - Publicar RequestIdentity no shared-kernel

- **What:** mover e renomear o principal provider-neutral, incluindo
  `displayName`, e migrar identity/bootstrap sem compatibilidade duplicada.
- **Where:** `backend/shared-kernel/**`; `backend/features/identity/**`;
  testes afetados em identity/bootstrap.
- **Depends on:** T01.
- **Reuses:** `AuthenticatedPrincipal`, `TokenVerification` e Firebase verifier.
- **Requirements:** SESSION-01, SESSION-05, SEC-01.
- **Fixed contract:** `subject`, `email?`, `emailVerified?`, `displayName?`; sem
  Spring/Firebase no shared-kernel e sem merge por email.
- **Must not:** criar dependencia access -> identity ou manter dois principais.
- **Tools:** BACKEND.
- **Tests:** unit + architecture, +6 casos de mapping/neutralidade.
- **Gate:** Quick identity.
- **Done when:** [x] todos os consumidores usam `RequestIdentity`; [x] remover o
  antigo tipo nao quebra contrato publico remanescente; [x] Gate verde.
- **Commit:** `refactor(identity): share request identity contract`

### T03 - Centralizar diagnostics HTTP no bootstrap

- **What:** mover correlation filter/problem writer/advice para infraestrutura
  HTTP transversal e serializar `ApiProblem` com Jackson.
- **Where:** `backend/bootstrap/.../configuration/http/**`;
  remover equivalentes de `features/identity`; integration tests.
- **Depends on:** T02.
- **Reuses:** `CorrelationId`, `ErrorCode`, security chain e diagnostics tests.
- **Requirements:** SEC-01, SESSION-04.
- **Fixed contract:** `application/problem+json`, header `X-Correlation-ID`,
  campos `status/code/correlationId/fieldErrors?/retryAfterSeconds?`.
- **Must not:** interpolar JSON manualmente ou logar request body/Auth header.
- **Tools:** BACKEND.
- **Tests:** Spring integration, +9 casos de escaping, status, header e redaction.
- **Gate:** Quick identity.
- **Done when:** [x] identity nao possui HTTP transversal; [x] todos os problems
  tem correlation ID igual ao log; [x] baseline +9 e Gate verde.
- **Commit:** `refactor(http): centralize safe api diagnostics`

### T04 - Criar modulo hexagonal features:access

- **What:** registrar o modulo, source sets `test`/`integrationTest` e regras
  escalaveis de arquitetura para todas as features.
- **Where:** `backend/settings.gradle.kts`; catalogs/builds de backend;
  `backend/features/access/build.gradle.kts`; architecture tests.
- **Depends on:** T03.
- **Reuses:** convention `saqz.jvm-backend` e regras de identity.
- **Requirements:** SEC-04.
- **Fixed contract:** `bootstrap -> features:* -> shared-kernel`; nenhum
  feature-to-feature; domain/application sem Spring/JDBC/adapters.
- **Must not:** special-case permissivo para access ou framework no dominio.
- **Tools:** BACKEND.
- **Tests:** architecture/contract, +8 regras/mutacoes.
- **Gate:** Contract e Quick identity.
- **Done when:** [x] modulo vazio compila isolado; [x] mutacoes cross-feature,
  adapter e bootstrap morrem; [x] baseline +8 e gates verdes.
- **Commit:** `build(access): add hexagonal backend module`

### T05 - Criar schema PostgreSQL versionado

- **What:** adicionar Flyway/Testcontainers/JdbcClient e migration inicial para
  users, groups, memberships, invites e redemption limits.
- **Where:** build/catalog backend; `backend/features/access/src/main/resources/db/migration/**`;
  `src/integrationTest/**/AccessSchemaIntegrationTest.kt`.
- **Depends on:** T04.
- **Reuses:** UUID/timestamps do design e Spring Boot datasource conventions.
- **Requirements:** SESSION-02, GROUP-01, GROUP-02, INVITE-01, INVITE-07, SEC-02, SEC-04.
- **Fixed contract:** constraints exatas do design; owner FK obrigatorio;
  memberships so ADMIN/ATHLETE; um invite por grupo; sem `expires_at`.
- **Must not:** H2, schema.sql, ORM, migration mutavel ou token em texto puro.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +14 casos de migration/constraints/rollback.
- **Gate:** SQL integration.
- **Done when:** [x] Flyway sobe banco vazio e reexecuta sem drift; [x] cada
  constraint mata a mutacao correspondente; [x] baseline +14 e Gate verde.
- **Commit:** `feat(access): add relational access schema`

### T06 - Implementar tipos e politica do dominio access

- **What:** criar value objects, roles e `GroupAccessPolicy` puros.
- **Where:** `backend/features/access/src/main/kotlin/.../domain/**`; unit tests.
- **Depends on:** T05.
- **Reuses:** tipos provider-neutral do shared-kernel.
- **Requirements:** GROUP-03..07, INVITE-03, EDGE-03..05.
- **Fixed contract:** nome 2..80 sem controle; timezone IANA; OWNER sintetico;
  ADMIN/ATHLETE persistiveis; nao membro 404 e papel insuficiente 403.
- **Must not:** aceitar role do cliente como autoridade ou modelar co-owner.
- **Tools:** BACKEND.
- **Tests:** unit, +24 casos cobrindo todos os branches/roles/validadores.
- **Gate:** Quick access.
- **Done when:** [x] cada branch da matriz tem caso nomeado; [x] timezone real e
  invalido discriminam; [x] baseline +24 e Gate verde.
- **Commit:** `feat(access): define access domain policy`

## Fase 1 - Sessao persistida e transporte autenticado

### T07 - Implementar BootstrapSession

- **What:** criar o caso de uso idempotente de sincronizacao por Firebase UID.
- **Where:** `backend/features/access/.../application/session/**`; unit tests.
- **Depends on:** T06.
- **Reuses:** `RequestIdentity`, value objects e repository port local.
- **Requirements:** SESSION-01, SESSION-02, SESSION-05, EDGE-01.
- **Fixed contract:** rejeitar email nao verificado/nome inutil antes de write;
  atualizar espelhos sem trocar ID/memberships.
- **Must not:** localizar ou mesclar por email.
- **Tools:** BACKEND.
- **Tests:** unit, +12 casos de create/update/retry/concurrency contract/falhas.
- **Gate:** Quick access.
- **Done when:** [x] todos os outputs de SESSION-01/05 sao asserted; [x] write
  zero para identidade bloqueada; [x] baseline +12 e Gate verde.
- **Commit:** `feat(access): add session bootstrap use case`

### T08 - Persistir bootstrap de sessao com JdbcClient

- **What:** implementar upsert atomico do usuario e leitura ordenada de
  memberships, incluindo OWNER sintetico.
- **Where:** `backend/features/access/.../adapter/output/jdbc/session/**`;
  integration tests.
- **Depends on:** T07.
- **Reuses:** schema T05 e Spring `JdbcClient`.
- **Requirements:** SESSION-01, SESSION-02, SESSION-05, SELECT-01..03.
- **Fixed contract:** `ON CONFLICT(firebase_subject)`; concorrencia devolve um
  user ID; memberships contem nome/papel e ordem estavel.
- **Must not:** check-then-insert em memoria ou query por email.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +10 casos incluindo duas conexoes concorrentes.
- **Gate:** SQL integration.
- **Done when:** [x] concorrencia deixa uma linha; [x] roles por grupo sao
  preservadas; [x] baseline +10 e Gate verde.
- **Commit:** `feat(access): persist idempotent session bootstrap`

### T09 - Expor PUT /api/session

- **What:** substituir o GET de prova pelo endpoint idempotente e conectar
  identidade verificada, use case e problem contract.
- **Where:** access HTTP adapter; bootstrap wiring; session integration/emulator tests.
- **Depends on:** T08.
- **Reuses:** security filter, Firebase emulator fixture e diagnostics T03.
- **Requirements:** SESSION-01..05, SEC-01.
- **Fixed contract:** nenhum email/nome no body; 403 sem write para nao
  verificado; `SessionView` com user e memberships; GET removido.
- **Must not:** manter duas semanticas `/api/session` ou expor Firebase UID.
- **Tools:** BACKEND.
- **Tests:** Spring + emulator integration, +11 casos happy/auth/unverified/update/failure.
- **Gate:** HTTP full.
- **Done when:** [x] endpoint passa com token emulator real; [x] GET retorna
  method-not-allowed/not-found; [x] baseline +11 e Gate verde.
- **Commit:** `feat(access): expose idempotent session bootstrap`

### T10 - Criar core:network com Ktor

- **What:** registrar modulo KMP sem Compose, engines Android/Darwin, JSON,
  timeout e mapping provider-neutral de `ApiProblem`.
- **Where:** `mobile/core/network/**`; settings/catalog; common tests.
- **Depends on:** T09.
- **Reuses:** build conventions e kotlinx.serialization do mobile.
- **Requirements:** AUTH-04, SESSION-04, SEC-01.
- **Fixed contract:** base URL por ambiente; bodies de erro limitados; nenhum
  logging de Authorization/body sensivel; tipos backend nao importados.
- **Must not:** persistir token, depender de Compose ou gerar OpenAPI.
- **Tools:** KMP.
- **Tests:** KMP unit/MockEngine, +12 casos JSON/problem/timeout/redaction.
- **Gate:** Quick network.
- **Done when:** [x] engines compilam nos tres targets; [x] erros estaveis sao
  discriminados; [x] baseline +12 e Gate verde.
- **Commit:** `feat(network): add shared ktor client`

### T11 - Implementar bearer refresh e SessionApi

- **What:** adicionar token bridge, retry unico concorrente e client de
  `PUT /api/session`.
- **Where:** `mobile/core/network/**`; common tests.
- **Depends on:** T10.
- **Reuses:** Ktor Auth bearer plugin e DTOs serialization T10.
- **Requirements:** SESSION-01, SESSION-03, SESSION-04, AUTH-06, AUTH-07.
- **Fixed contract:** um refresh para 401 concorrentes; repetir uma vez; segundo
  401 chama invalidator; 5xx/network preserva sessao.
- **Must not:** loop de retry ou tratar indisponibilidade como logout.
- **Tools:** KMP.
- **Tests:** KMP unit/MockEngine, +15 casos incluindo rajada concorrente.
- **Gate:** Quick network.
- **Done when:** [x] sensor de dois 401 mata implementacao sem limite; [x] 5xx
  nao chama invalidator; [x] baseline +15 e Gate verde.
- **Commit:** `feat(network): add authenticated session transport`

## Fase 2 - Grupos e configuracoes gerais

### T12 - Implementar CreateGroup

- **What:** criar use case de grupo/owner atomico com requestId idempotente.
- **Where:** access application group/create; unit tests.
- **Depends on:** T11.
- **Reuses:** value objects T06 e transaction/repository ports.
- **Requirements:** GROUP-01, GROUP-02, EDGE-05, SEC-02.
- **Fixed contract:** qualquer user sincronizado; owner e ator; mesmo requestId
  retorna mesmo grupo; nome/timezone invalido nao abre transacao.
- **Must not:** impedir atleta de criar outro grupo ou criar owner membership row.
- **Tools:** BACKEND.
- **Tests:** unit, +10 casos happy/invalid/retry/rollback contract.
- **Gate:** Quick access.
- **Done when:** [x] outputs GROUP-01/02 asserted; [x] chamadas de port exatas;
  [x] baseline +10 e Gate verde.
- **Commit:** `feat(access): add group creation use case`

### T13 - Persistir criacao idempotente de grupo

- **What:** implementar insert/upsert transacional por owner+creation_key.
- **Where:** JDBC group create adapter; PostgreSQL integration tests.
- **Depends on:** T12.
- **Reuses:** schema T05 e row mappers de sessao.
- **Requirements:** GROUP-01, GROUP-02, SEC-02.
- **Fixed contract:** uma linha group, um owner FK; duas requests concorrentes
  com mesma key retornam mesmo UUID e dados originais.
- **Must not:** capturar unique violation como 500 ou gravar parcialmente.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +9 casos incluindo timeout/retry concorrente.
- **Gate:** SQL integration.
- **Done when:** [x] falha injetada reverte tudo; [x] sensor sem unique constraint
  falha; [x] baseline +9 e Gate verde.
- **Commit:** `feat(access): persist idempotent group creation`

### T14 - Expor POST /api/groups

- **What:** adicionar endpoint de criacao com `requestId`, nome e timezone.
- **Where:** access HTTP group controller; bootstrap wiring/tests.
- **Depends on:** T13.
- **Reuses:** problems T03 e security principal T02.
- **Requirements:** GROUP-01, GROUP-02, EDGE-05, SEC-01.
- **Fixed contract:** `201` primeira vez, resposta equivalente no retry;
  fieldErrors 400; nenhum selected-group state no backend.
- **Must not:** confiar userId/ownerRole do body.
- **Tools:** BACKEND.
- **Tests:** Spring integration, +9 casos happy/retry/validation/auth/failure.
- **Gate:** HTTP full.
- **Done when:** [x] cada campo invalido e apontado; [x] duplo POST nao duplica;
  [x] baseline +9 e Gate verde.
- **Commit:** `feat(access): expose group creation endpoint`

### T15 - Implementar leitura autorizada de grupo

- **What:** criar `GetGroup` com resolucao de papel e non-enumeration.
- **Where:** access application group/read; unit tests.
- **Depends on:** T14.
- **Reuses:** `GroupAccessPolicy` T06.
- **Requirements:** GROUP-07, SELECT-04, SELECT-06.
- **Fixed contract:** membro recebe group/settings/role/version; nao membro e ID
  ausente produzem o mesmo `GROUP_NOT_FOUND`.
- **Must not:** revelar existencia antes de checar membership.
- **Tools:** BACKEND.
- **Tests:** unit, +9 casos owner/admin/athlete/nonmember/missing.
- **Gate:** Quick access.
- **Done when:** [x] 404 outcomes sao indistinguiveis; [x] role vem do repository;
  [x] baseline +9 e Gate verde.
- **Commit:** `feat(access): add authorized group read`

### T16 - Implementar query JDBC de grupo

- **What:** carregar grupo, owner/membership e version em query limitada.
- **Where:** JDBC group read adapter; PostgreSQL integration tests.
- **Depends on:** T15.
- **Reuses:** schema/row mappers T05/T08.
- **Requirements:** GROUP-07, SELECT-04, SELECT-06.
- **Fixed contract:** no maximo uma query bounded por group/user; owner
  sintetizado; nenhum N+1.
- **Must not:** retornar outro membership ou dados de outro grupo.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +8 casos de isolamento/role/version.
- **Gate:** SQL integration.
- **Done when:** [x] fixtures multi-grupo nao vazam; [x] query plan usa PK/index;
  [x] baseline +8 e Gate verde.
- **Commit:** `feat(access): persist authorized group reads`

### T17 - Expor GET /api/groups/{groupId}

- **What:** adicionar endpoint de leitura com ETag de versao.
- **Where:** group controller; bootstrap integration tests.
- **Depends on:** T16.
- **Reuses:** GetGroup e problem contract.
- **Requirements:** GROUP-07, SELECT-04, SELECT-06, SEC-01.
- **Fixed contract:** `ETag: "<version>"`; 200 para todo membro; mesmo 404 para
  inexistente/nao membro.
- **Must not:** omitir enforcement por esconder acao na UI.
- **Tools:** BACKEND.
- **Tests:** Spring integration, +8 casos role/404/etag/auth.
- **Gate:** HTTP full.
- **Done when:** [x] matriz publica exata; [x] ETag corresponde ao body; [x]
  baseline +8 e Gate verde.
- **Commit:** `feat(access): expose authorized group read`

### T18 - Implementar UpdateGroupSettings

- **What:** criar use case de atualizacao atomica com version esperada.
- **Where:** access application group/settings; unit tests.
- **Depends on:** T17.
- **Reuses:** policy/value objects T06.
- **Requirements:** GROUP-06, EDGE-05, SEC-02.
- **Fixed contract:** OWNER/ADMIN; nome+timezone atualizam juntos; ATHLETE 403;
  version stale vira conflito sem write.
- **Must not:** PATCH parcial ou last-write-wins silencioso.
- **Tools:** BACKEND.
- **Tests:** unit, +11 casos roles/fields/version/rollback contract.
- **Gate:** Quick access.
- **Done when:** [x] matriz de roles e atomicidade asserted; [x] baseline +11;
  [x] Gate verde.
- **Commit:** `feat(access): add group settings update`

### T19 - Persistir settings com optimistic locking

- **What:** atualizar ambos os campos e incrementar version via compare-and-set.
- **Where:** JDBC settings adapter; PostgreSQL integration tests.
- **Depends on:** T18.
- **Reuses:** `access_groups.version` T05.
- **Requirements:** GROUP-06, EDGE-05, SEC-02.
- **Fixed contract:** `WHERE id=? AND version=?`; zero row distingue not-found
  por lookup autorizado; commit unico.
- **Must not:** duas atualizacoes concorrentes ambas vencerem.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +9 casos incluindo writers concorrentes.
- **Gate:** SQL integration.
- **Done when:** [x] exatamente um writer vence; [x] falha preserva dois campos;
  [x] baseline +9 e Gate verde.
- **Commit:** `feat(access): persist versioned group settings`

### T20 - Expor PUT /api/groups/{groupId}/settings

- **What:** adicionar endpoint de settings com `If-Match` obrigatorio.
- **Where:** group settings controller; bootstrap integration tests.
- **Depends on:** T19.
- **Reuses:** ETag T17 e ApiProblem T03.
- **Requirements:** GROUP-06, GROUP-07, EDGE-05, SEC-01.
- **Fixed contract:** 200+novo ETag; 409 `VERSION_CONFLICT`; 400 fieldErrors;
  403 membro insuficiente; 404 nao membro.
- **Must not:** aceitar owner/user/role no payload.
- **Tools:** BACKEND.
- **Tests:** Spring integration, +10 casos happy/roles/etag/fields/404.
- **Gate:** HTTP full.
- **Done when:** [x] todo status tem problem estavel; [x] baseline +10; [x] Gate verde.
- **Commit:** `feat(access): expose group settings endpoint`

## Fase 3 - Papeis e convites

### T21 - Implementar administracao de papeis

- **What:** criar `ListAccessMemberships` e `ChangeMemberRole` como uma politica
  coesa de delegacao do OWNER.
- **Where:** access application membership; unit tests.
- **Depends on:** T20.
- **Reuses:** GroupAccessPolicy e roles T06.
- **Requirements:** GROUP-03..05, EDGE-04.
- **Fixed contract:** somente OWNER lista/promove/rebaixa; roles alvo apenas
  ADMIN/ATHLETE; repeticao idempotente; owner imutavel.
- **Must not:** endpoint/use case para transferir ou rebaixar OWNER.
- **Tools:** BACKEND.
- **Tests:** unit, +14 casos da matriz e varios admins.
- **Gate:** Quick access.
- **Done when:** [x] todas as tentativas ADMIN/ATHLETE falham sem write; [x] um
  admin nao afeta outro; [x] baseline +14 e Gate verde.
- **Commit:** `feat(access): add owner role administration`

### T22 - Persistir memberships e mudanca de papel

- **What:** implementar lista minima e upsert ADMIN/ATHLETE idempotente.
- **Where:** JDBC membership adapter; PostgreSQL integration tests.
- **Depends on:** T21.
- **Reuses:** schema T05 e user row mapper T08.
- **Requirements:** GROUP-03, GROUP-04, EDGE-04, SEC-02.
- **Fixed contract:** PK group+user; mudanca bloqueia linha; lista contem userId,
  displayName e role, incluindo OWNER sintetico.
- **Must not:** gravar OWNER na tabela ou incluir email na lista.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +10 casos upsert/list/isolation/concurrency.
- **Gate:** SQL integration.
- **Done when:** [x] promotion repetida deixa uma row; [x] demotion isolada; [x]
  baseline +10 e Gate verde.
- **Commit:** `feat(access): persist membership roles`

### T23 - Expor endpoints de memberships

- **What:** adicionar GET da lista e PUT idempotente do papel.
- **Where:** membership controller; bootstrap integration tests.
- **Depends on:** T22.
- **Reuses:** ApiProblem e RequestIdentity.
- **Requirements:** GROUP-03..05, GROUP-07, EDGE-04, SEC-01.
- **Fixed contract:** GET OWNER-only; PUT body `{role: ADMIN|ATHLETE}`; 403 para
  membro insuficiente; 404 nao membro/alvo ausente sem enumeracao.
- **Must not:** aceitar OWNER ou endpoint DELETE.
- **Tools:** BACKEND.
- **Tests:** Spring integration, +11 casos das duas rotas e matriz de papeis.
- **Gate:** HTTP full.
- **Done when:** [x] cada rota tem happy/403/404/400; [x] baseline +11; [x] Gate verde.
- **Commit:** `feat(access): expose membership administration`

### T24 - Gerar token e Branch Long Link

- **What:** implementar `SecureTokenGenerator` e `InviteLinkFactory` puros.
- **Where:** access application ports + adapters crypto/link; unit tests.
- **Depends on:** T23.
- **Reuses:** Base64URL/JCA e URI builder padrao.
- **Requirements:** INVITE-01, INVITE-02, INVITE-06, SEC-01.
- **Fixed contract:** 32 bytes aleatorios, sem padding; digest SHA-256; long link
  HTTPS com `saqz_invite`, rota invite e `$ios_nativelink=true` encoded.
- **Must not:** group ID/PII no link, Math.random, short-link API ou logs.
- **Tools:** BACKEND.
- **Tests:** unit/contract, +13 casos entropia/formato/digest/URI/redaction.
- **Gate:** Quick access.
- **Done when:** [x] 10k tokens nao colidem no teste deterministico; [x] fixtures
  de URI decodificam exatamente; [x] baseline +13 e Gate verde.
- **Commit:** `feat(access): generate opaque invite links`

### T25 - Implementar RotateInvite e ExpireInvite

- **What:** criar casos de uso autorizados de rotacao e expiracao manual.
- **Where:** access application invite/manage; unit tests.
- **Depends on:** T24.
- **Reuses:** policy T06 e token/link factory T24.
- **Requirements:** INVITE-01..03, SEC-02.
- **Fixed contract:** OWNER/ADMIN; rotacao substitui digest atomicamente e
  retorna raw URL uma vez; expire idempotente; ATHLETE 403.
- **Must not:** TTL, status ativo paralelo ou persistir raw URL/code.
- **Tools:** BACKEND.
- **Tests:** unit, +11 casos roles/rotate/expire/factory failure/rollback.
- **Gate:** Quick access.
- **Done when:** [x] raw code nao entra no repository port; [x] falha do link
  nao muta estado; [x] baseline +11 e Gate verde.
- **Commit:** `feat(access): add invite lifecycle use cases`

### T26 - Persistir rotacao e expiracao de convite

- **What:** implementar lock/upsert/delete de uma linha por grupo.
- **Where:** JDBC invite manage adapter; PostgreSQL integration tests.
- **Depends on:** T25.
- **Reuses:** schema `group_invites` e transaction runner.
- **Requirements:** INVITE-01..03, SEC-02.
- **Fixed contract:** `SELECT ... FOR UPDATE`/upsert na mesma transacao; digest
  antigo invalido apos commit; delete repetido e sucesso.
- **Must not:** duas linhas ativas ou raw code em coluna/test log.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +10 casos incluindo rotacoes concorrentes.
- **Gate:** SQL integration.
- **Done when:** [x] apenas digest vencedor existe; [x] rollback preserva antigo;
  [x] baseline +10 e Gate verde.
- **Commit:** `feat(access): persist invite lifecycle`

### T27 - Expor endpoints de gerencia de convite

- **What:** adicionar POST de rotacao e DELETE de expiracao.
- **Where:** invite controller; bootstrap integration tests.
- **Depends on:** T26.
- **Reuses:** manage use cases e safe diagnostics.
- **Requirements:** INVITE-01..03, SEC-01.
- **Fixed contract:** POST retorna somente `{inviteUrl}`; DELETE 204 idempotente;
  OWNER/ADMIN autorizados, ATHLETE 403, nao membro 404.
- **Must not:** colocar code no path de API/backend log.
- **Tools:** BACKEND.
- **Tests:** Spring integration, +10 casos das duas rotas/roles/falhas.
- **Gate:** HTTP full.
- **Done when:** [x] captura de output nao encontra code; [x] baseline +10; [x] Gate verde.
- **Commit:** `feat(access): expose invite management endpoints`

### T28 - Implementar RedeemInvite

- **What:** criar resgate idempotente, preservacao de papel e rate-limit policy.
- **Where:** access application invite/redeem; unit tests.
- **Depends on:** T27.
- **Reuses:** token digest T24, access policy e clock port.
- **Requirements:** INVITE-05..08, EDGE-03, SEC-02.
- **Fixed contract:** valido cria ATHLETE uma vez; membro existente preserva
  OWNER/ADMIN; dez falhas permitidas, 11a recebe 429; valido nao consome limite.
- **Must not:** consumir/inativar convite no resgate ou revelar grupo em erro.
- **Tools:** BACKEND.
- **Tests:** unit, +16 casos retry/roles/window/boundaries/parallel contract.
- **Gate:** Quick access.
- **Done when:** [x] boundary 10/11 e reset temporal asserted; [x] roles superiores
  preservados; [x] baseline +16 e Gate verde.
- **Commit:** `feat(access): add invite redemption policy`

### T29 - Persistir resgate e limite concorrentes

- **What:** implementar lookup por digest, upsert membership e janela bloqueada.
- **Where:** JDBC redeem adapter; PostgreSQL integration tests.
- **Depends on:** T28.
- **Reuses:** `group_invites`, memberships e redemption limits T05.
- **Requirements:** INVITE-05..08, EDGE-03, SEC-02.
- **Fixed contract:** transacao unica; row lock por user limit; dois usuarios
  entram em paralelo; retries do mesmo user nao duplicam.
- **Must not:** serializar todos os resgates no group ou apagar invite.
- **Tools:** BACKEND.
- **Tests:** PostgreSQL integration, +14 casos incluindo barriers reais.
- **Gate:** SQL integration.
- **Done when:** [x] dois users concorrentes viram ATHLETE; [x] 11a falha 429 sem
  membership; [x] baseline +14 e Gate verde.
- **Commit:** `feat(access): persist concurrent invite redemption`

### T30 - Expor POST /api/invites/redeem

- **What:** adicionar endpoint com code somente no JSON body.
- **Where:** redeem controller; bootstrap integration tests.
- **Depends on:** T29.
- **Reuses:** ApiProblem e RedeemInvite.
- **Requirements:** INVITE-05..08, EDGE-03, SEC-01.
- **Fixed contract:** 200 com group/papel; mesmo 404
  `INVITE_INVALID_OR_EXPIRED` para ausente/malformado/rotacionado; 429 com retry.
- **Must not:** ecoar code, group ou causa no problem invalido.
- **Tools:** BACKEND.
- **Tests:** Spring integration, +11 casos happy/retry/invalid/rate/roles/redaction.
- **Gate:** HTTP full.
- **Done when:** [x] problems invalidos sao byte-equivalentes salvo correlation;
  [x] baseline +11; [x] Gate verde.
- **Commit:** `feat(access): expose invite redemption endpoint`

## Fase 4 - API e maquina de estados KMP

### T31 - Criar modulo mobile features:access e ports nativos

- **What:** registrar feature KMP Compose, contracts provider-neutral e export
  pelo unico framework `SaqzMobile`.
- **Where:** `mobile/features/access/**`; settings/build logic; compose-app build.
- **Depends on:** T30.
- **Reuses:** `core:common`, design-system, core:network e umbrella existing.
- **Requirements:** AUTH-01..08, INVITE-04, SELECT-05.
- **Fixed contract:** interfaces/callbacks do design; feature sem Firebase,
  Branch, UIKit/Android types; compose-app usa `api` e framework export correto.
- **Must not:** segundo framework ou SDK nativo em commonMain.
- **Tools:** KMP.
- **Tests:** contract/build, +8 casos de callbacks/cancelation/export sentinel.
- **Gate:** Quick access mobile e Quick app.
- **Done when:** [x] Swift header expoe apenas contracts aprovados; [x] Android
  compila implementacao externa; [x] baseline +8 e gates verdes.
- **Commit:** `build(access-mobile): add shared access feature`

### T32 - Implementar client mobile de grupos

- **What:** adicionar DTOs e chamadas create/read/update com ETag.
- **Where:** `mobile/features/access/.../data/GroupApi.kt`; common tests.
- **Depends on:** T31.
- **Reuses:** core network SessionApi/problem mapping.
- **Requirements:** GROUP-01, GROUP-02, GROUP-06, GROUP-07, EDGE-05.
- **Fixed contract:** rotas/bodies/status do design; requestId preservado em
  retry; ETag obrigatorio no update; DTOs independentes do backend.
- **Must not:** gerar client ou incluir selected group no servidor.
- **Tools:** KMP.
- **Tests:** MockEngine unit, +13 casos das tres operacoes/happy/error/serialization.
- **Gate:** Quick access mobile.
- **Done when:** [x] requests exatas sao asserted; [x] 404/403/409/400 mapeiam
  distinto; [x] baseline +13 e Gate verde.
- **Commit:** `feat(access-mobile): add group api client`

### T33 - Implementar client mobile de papeis e convites

- **What:** adicionar list/change role, rotate/expire/redeem clients.
- **Where:** feature access data API; common tests.
- **Depends on:** T32.
- **Reuses:** GroupApi patterns e core network.
- **Requirements:** GROUP-03..05, INVITE-01..08.
- **Fixed contract:** code somente body de redeem; URL somente response de
  rotate; DELETE 204; retryAfter mapeado.
- **Must not:** logar DTOs sensiveis ou aceitar OWNER target.
- **Tools:** KMP.
- **Tests:** MockEngine unit, +17 casos de cinco operacoes/status/redaction.
- **Gate:** Quick access mobile.
- **Done when:** [x] todas as rotas tem fixture exata; [x] code nao aparece no
  erro/log fake; [x] baseline +17 e Gate verde.
- **Commit:** `feat(access-mobile): add roles and invites api`

### T34 - Implementar reducer de autenticacao

- **What:** coordenar cadastro, login por senha/Google, reset e erros sem
  provider details.
- **Where:** feature access presentation/coordinator auth; common tests.
- **Depends on:** T33.
- **Reuses:** NativeAuthPort e `SaqzUiState`.
- **Requirements:** AUTH-01, AUTH-03..05, AUTH-08, EDGE-01, EDGE-06, EDGE-07.
- **Fixed contract:** password transiente; campos nao sensiveis preservados;
  cancel Google e no-op; reset sempre neutro; submit single-flight.
- **Must not:** criar user backend antes de verificacao/nome valido.
- **Tools:** KMP.
- **Tests:** unit, +18 transicoes/erros/cancelamento/duplo submit.
- **Gate:** Quick access mobile.
- **Done when:** [x] cada AUTH mapeado tem output exato; [x] password some apos
  submit; [x] baseline +18 e Gate verde.
- **Commit:** `feat(access-mobile): coordinate authentication flows`

### T35 - Implementar verificacao, bootstrap e logout

- **What:** coordenar reload/refresh, name completion, SessionApi, retry e
  limpeza local no logout/segundo 401.
- **Where:** feature access coordinator session; common tests.
- **Depends on:** T34.
- **Reuses:** auth reducer, SessionApi T11 e local state port.
- **Requirements:** AUTH-02, AUTH-06, AUTH-07, SESSION-01, SESSION-03..05, EDGE-01.
- **Fixed contract:** unverified fica bloqueado; backend indisponivel preserva
  Firebase session; logout limpa group/invite e e local.
- **Must not:** mostrar protected state em bootstrap error.
- **Tools:** KMP.
- **Tests:** unit, +17 transicoes/restart/retry/refresh/logout.
- **Gate:** Quick access mobile.
- **Done when:** [x] segundo 401 e unico caminho de auth invalidation; [x] 5xx
  mantem session; [x] baseline +17 e Gate verde.
- **Commit:** `feat(access-mobile): coordinate verified sessions`

### T36 - Implementar selecao de grupo

- **What:** reduzir zero/um/varios memberships, restauracao valida e troca
  protegida de contexto.
- **Where:** feature access coordinator selection; common tests.
- **Depends on:** T35.
- **Reuses:** SessionView e LocalAccessStatePort.
- **Requirements:** SELECT-01..06.
- **Fixed contract:** zero NoGroup; um auto-select; varios selector; selection
  local so se membership atual; troca limpa conteudo antes da carga.
- **Must not:** cachear conteudo protegido ou confiar role antiga.
- **Tools:** KMP.
- **Tests:** unit, +14 casos memberships/restart/remocao/troca/role refresh.
- **Gate:** Quick access mobile.
- **Done when:** [x] sensor que mantem conteudo antigo falha; [x] stale selection
  e apagada; [x] baseline +14 e Gate verde.
- **Commit:** `feat(access-mobile): coordinate group selection`

### T37 - Implementar acoes de grupo e papeis

- **What:** coordenar create/read/settings/list/change role e atualizar estado.
- **Where:** feature access coordinator group administration; common tests.
- **Depends on:** T36.
- **Reuses:** clients T32/T33 e role policy de apresentacao.
- **Requirements:** GROUP-01..07, SELECT-06, EDGE-04, EDGE-05, EDGE-07.
- **Fixed contract:** grupo criado sempre selecionado; 409 recarrega; role
  refresh governa acoes; validacao associa field error; intents single-flight.
- **Must not:** usar UI hidden como enforcement ou inventar gestao de atleta.
- **Tools:** KMP.
- **Tests:** unit, +18 casos create/settings/roles/conflict/field/single-flight.
- **Gate:** Quick access mobile.
- **Done when:** [x] todos os roles produzem acoes corretas; [x] baseline +18;
  [x] Gate verde.
- **Commit:** `feat(access-mobile): coordinate group administration`

### T38 - Implementar convite pendente e resgate

- **What:** guardar ultimo code, adiar ate sessao verificada, resgatar e limpar
  nos momentos definidos.
- **Where:** feature access coordinator invite; common tests.
- **Depends on:** T37.
- **Reuses:** NativeLinkPort, secure local state e invite API T33.
- **Requirements:** INVITE-04..08, EDGE-02, EDGE-03, EDGE-07.
- **Fixed contract:** ultimo valido vence; restart/login/verification preservam;
  success/invalid/logout limpam; 429 mantem feedback; sucesso seleciona grupo.
- **Must not:** resgatar pre-bootstrap ou revelar grupo para code invalido.
- **Tools:** KMP.
- **Tests:** unit, +18 casos lifecycle/duplicate/invalid/rate/roles/single-flight.
- **Gate:** Quick access mobile.
- **Done when:** [x] eventos duplicados geram um redeem; [x] code anterior nunca
  e usado; [x] baseline +18 e Gate verde.
- **Commit:** `feat(access-mobile): coordinate deferred invitations`

## Fase 5 - Interface Compose e navegacao

### T39 - Implementar tela de login

- **What:** criar login email/senha e acao Google nos estados definidos.
- **Where:** `mobile/features/access/.../ui/LoginScreen.kt`; Compose tests/resources.
- **Depends on:** T38.
- **Reuses:** `SaqzInput`, `SaqzButton`, theme e auth coordinator.
- **Requirements:** AUTH-03, AUTH-04, AUTH-06, AUTH-08, EDGE-06, EDGE-07.
- **Fixed contract:** password mascarada; loading single-flight; erro acionavel;
  acoes cadastro/recuperacao; semantics/foco/IME acessiveis.
- **Must not:** texto de feature/atalho, provider error bruto ou nested cards.
- **Tools:** KMP.
- **Tests:** Compose UI, +11 casos estados/callbacks/semantics/IME/font scale.
- **Gate:** Quick access mobile.
- **Done when:** [x] todos os controles cabem no viewport maximo; [x] baseline
  +11 e Gate verde.
- **Commit:** `feat(access-ui): add login screen`

### T40 - Implementar tela de cadastro

- **What:** criar formulario nome/email/senha com validacao e estado de envio.
- **Where:** feature access UI registration; Compose tests/resources.
- **Depends on:** T39.
- **Reuses:** inputs/buttons e auth coordinator.
- **Requirements:** AUTH-01, AUTH-04, AUTH-08, EDGE-05, EDGE-07.
- **Fixed contract:** nome/email validados localmente sem divergir da senha
  Firebase; field errors; password nao restaurada; submit single-flight.
- **Must not:** confirmar conta backend ou guardar password em saved state.
- **Tools:** KMP.
- **Tests:** Compose UI, +10 casos fields/errors/loading/semantics/lifecycle.
- **Gate:** Quick access mobile.
- **Done when:** [x] password nao aparece em restoration fixture; [x] baseline
  +10 e Gate verde.
- **Commit:** `feat(access-ui): add registration screen`

### T41 - Implementar verificacao, nome e recuperacao

- **What:** criar telas coesas de conclusao de identidade antes do bootstrap.
- **Where:** feature access UI verification/name/reset; Compose tests/resources.
- **Depends on:** T40.
- **Reuses:** state host, dialog/input e session coordinator.
- **Requirements:** AUTH-02, AUTH-05, EDGE-01, EDGE-06, EDGE-07.
- **Fixed contract:** resend/cooldown feedback; `Ja verifiquei`; nome obrigatorio
  quando ausente; reset sempre confirma neutralmente; back stack unico.
- **Must not:** revelar existencia de email ou liberar grupo nao verificado.
- **Tools:** KMP.
- **Tests:** Compose UI, +13 casos dos tres estados e navigation callbacks.
- **Gate:** Quick access mobile.
- **Done when:** [x] confirmacao de reset e identica para outcomes; [x] baseline
  +13 e Gate verde.
- **Commit:** `feat(access-ui): add identity completion screens`

### T42 - Implementar bootstrap, vazio, selecao e criacao de grupo

- **What:** renderizar estados pre-conteudo e onboarding do owner.
- **Where:** feature access UI bootstrap/group onboarding; Compose tests/resources.
- **Depends on:** T41.
- **Reuses:** state host, list item, badge e selection coordinator.
- **Requirements:** SESSION-04, GROUP-01, SELECT-01..05, EDGE-05, EDGE-07.
- **Fixed contract:** erro com retry sem login; zero com Criar grupo; varios com
  nome/papel e Criar grupo; form nome/timezone; um auto-select sem flash.
- **Must not:** descoberta publica de grupo ou mostrar conteudo anterior na troca.
- **Tools:** KMP.
- **Tests:** Compose UI, +15 casos estados/selection/form/retry/large text.
- **Gate:** Quick access mobile.
- **Done when:** [x] fixture de troca nao encontra texto anterior; [x] baseline
  +15 e Gate verde.
- **Commit:** `feat(access-ui): add group onboarding and selector`

### T43 - Implementar contexto e settings do grupo

- **What:** criar header de contexto/papel e editor nome/timezone com conflito.
- **Where:** feature access UI group context/settings; Compose tests/resources.
- **Depends on:** T42.
- **Reuses:** badge/input/dialog e group coordinator.
- **Requirements:** GROUP-06, GROUP-07, SELECT-04, SELECT-06, EDGE-05, EDGE-07.
- **Fixed contract:** ATHLETE read-only; OWNER/ADMIN editam; 409 pede recarga;
  group switch acessivel; logout com confirmacao.
- **Must not:** jogos, financeiro, billing ou athlete profile.
- **Tools:** KMP.
- **Tests:** Compose UI, +13 casos roles/edit/conflict/switch/logout.
- **Gate:** Quick access mobile.
- **Done when:** [x] role refresh muda acoes sem confiar no estado anterior; [x]
  baseline +13 e Gate verde.
- **Commit:** `feat(access-ui): add group context and settings`

### T44 - Implementar administracao de roles e convite

- **What:** criar lista de acesso, toggle ADMIN/ATHLETE e ferramenta de convite.
- **Where:** feature access UI membership/invite; Compose tests/resources.
- **Depends on:** T43.
- **Reuses:** list item, segmented/menu control, dialog e native share port.
- **Requirements:** GROUP-03..05, INVITE-01..03, INVITE-06, INVITE-07, EDGE-04.
- **Fixed contract:** tela roles so OWNER; invite OWNER/ADMIN; gerar substitui
  anterior; expirar confirma; invalid/rate feedback; compartilhar por icone.
- **Must not:** botao de co-owner/transferencia ou mostrar raw code separadamente.
- **Tools:** KMP.
- **Tests:** Compose UI, +16 casos roles/invite/share/expire/errors/semantics.
- **Gate:** Quick access mobile.
- **Done when:** [x] ATHLETE nao alcanca ferramenta; [x] varios admins isolados;
  [x] baseline +16 e Gate verde.
- **Commit:** `feat(access-ui): add roles and invite tools`

### T45 - Integrar root e navegacao autenticada

- **What:** substituir shell catalogo pelo fluxo real, injetar
  `SaqzAppDependencies` e aplicar guards/back stack unicos.
- **Where:** `mobile/compose-app/**`; common tests; resource packaging list.
- **Depends on:** T44.
- **Reuses:** `SaqzApp`, NavHost, accessibility controller e feature entry points.
- **Requirements:** AUTH-06, AUTH-07, SELECT-01..06, EDGE-06, EDGE-07.
- **Fixed contract:** auth sem protected shell; uma destination por state; logout
  limpa stack; feature resources chegam ao umbrella; acessibilidade preservada.
- **Must not:** duplicar UI nativa, manter Home/Catalog como produto ou criar framework.
- **Tools:** KMP.
- **Tests:** Compose UI/contract, +18 casos guards/back/reselection/restore/resources.
- **Gate:** Quick app e Quick access mobile.
- **Done when:** [x] cada state tem exatamente um destino; [x] sensor de stack
  duplicada falha; [x] baseline +18 e gates verdes.
- **Commit:** `feat(compose-app): integrate authenticated access flow`

## Fase 6 - Adapters Android

### T46 - Implementar auth Firebase e Google no Android

- **What:** implementar `NativeAuthPort` com Firebase Auth e Credential Manager.
- **Where:** `mobile/android-app/.../access/AndroidAuthAdapter.kt`; Gradle; unit tests.
- **Depends on:** T45.
- **Reuses:** named Firebase bootstrap/emulator existente.
- **Requirements:** AUTH-01..08, SESSION-03, EDGE-01, EDGE-06.
- **Fixed contract:** email/password/verification/reset/displayName/token/logout;
  Google credential vira Firebase credential; cancel e provider errors mapeados.
- **Must not:** One Tap legado, token app-persisted ou provider exception na UI.
- **Tools:** ANDROID.
- **Tests:** JUnit + emulator seam, +18 casos de mapping/callback/session.
- **Gate:** Full Android.
- **Done when:** [x] dev sem config usa Auth Emulator; [x] prod nao usa emulator;
  [x] baseline +18 e Gate verde.
- **Commit:** `feat(android): add native authentication adapter`

### T47 - Implementar Branch/App Links no Android

- **What:** inicializar Branch por ambiente e entregar `saqz_invite` de cold/warm intent.
- **Where:** Android manifest/config/adapter; unit/instrumented tests.
- **Depends on:** T46.
- **Reuses:** single-activity launcher e NativeLinkPort.
- **Requirements:** INVITE-04, INVITE-06, EDGE-02, EDGE-07.
- **Fixed contract:** HTTPS App Link verificado; Branch test/live key separada;
  somente Base64URL valido; cold e `onNewIntent`; ultimo evento vence no reducer.
- **Must not:** hardcode live key, group ID ou Firebase Dynamic Links.
- **Tools:** ANDROID.
- **Tests:** JUnit + instrumented, +12 casos cold/warm/duplicate/invalid/config.
- **Gate:** Full Android.
- **Done when:** [x] intent sem Branch/code e no-op; [x] baseline +12 e Gate verde.
- **Commit:** `feat(android): add deferred invite links`

### T48 - Implementar estado local cifrado e compartilhamento Android

- **What:** implementar selected group em preferences, pending invite cifrado
  com Keystore e share sheet.
- **Where:** Android access storage/share adapters; unit/instrumented tests.
- **Depends on:** T47.
- **Reuses:** Native ports T31 e AndroidX Security/Keystore suportado.
- **Requirements:** AUTH-07, INVITE-04, SELECT-05, SEC-01.
- **Fixed contract:** group ID nao secreto; invite ciphertext somente; null apaga;
  share recebe URL inteira; falha de storage vira erro recuperavel.
- **Must not:** SharedPreferences plaintext para invite ou logar value.
- **Tools:** ANDROID.
- **Tests:** JUnit + instrumented, +11 casos roundtrip/delete/restart/redaction/share.
- **Gate:** Full Android.
- **Done when:** [x] arquivo raw nao contem code; [x] logout remove ambos; [x]
  baseline +11 e Gate verde.
- **Commit:** `feat(android): add secure access state adapters`

### T49 - Compor fluxo Android e provar lifecycle

- **What:** construir dependencies apos Firebase/Branch, injetar em Compose e
  cobrir restart/rotacao/IME/font scale/single-flight.
- **Where:** `MainActivity`, bootstrap composition e androidTest.
- **Depends on:** T48.
- **Reuses:** adapters T46-48, SaqzAppDependencies e launch screen.
- **Requirements:** AUTH-06, AUTH-07, INVITE-04, SELECT-05, SEC-04, EDGE-07.
- **Fixed contract:** uma instancia coordenadora por activity lifecycle salvo;
  intent forwarding; protected content ausente durante failure.
- **Must not:** recriar Firebase app ou perder submit em configuracao.
- **Tools:** ANDROID.
- **Tests:** instrumented, +14 fluxos lifecycle/accessibility/auth/invite fixtures.
- **Gate:** Full Android.
- **Done when:** [x] rotacao durante submit produz uma chamada; [x] por decisao
  do usuario em 2026-07-17, Full Android local no API36 verde (45 unit +39
  instrumented, zero skip/failure) e focused T49 14/14; os smokes API30/API35
  nao sao alegados como executados e permanecem no contrato CI de T57; [x]
  baseline +14 e Gate verde.
- **Commit:** `feat(android): compose authenticated app lifecycle`

## Fase 7 - Adapters iOS

### T50 - Implementar auth Firebase e Google no iOS

- **What:** implementar protocol Kotlin com FirebaseAuth/GoogleSignIn em Swift.
- **Where:** SaqzIOS access auth adapter; SwiftPM/pbxproj; XCTest.
- **Depends on:** T49.
- **Reuses:** FirebaseBootstrap e Compose root existentes.
- **Requirements:** AUTH-01..08, SESSION-03, EDGE-01, EDGE-06.
- **Fixed contract:** mesmas operacoes/outcomes Android; callbacks no MainActor;
  URL return Google volta a um destino; emulator apenas local.
- **Must not:** tipos SDK atravessarem framework ou armazenar ID token.
- **Tools:** IOS.
- **Tests:** XCTest com clients fake/emulator seam, +18 casos mapping/order/cancel.
- **Gate:** Full iOS.
- **Done when:** [x] Firebase configura antes do adapter/root; [x] baseline +18;
  [x] SaqzDev/Prod gates verdes.
- **Commit:** `feat(ios): add native authentication adapter`

### T51 - Implementar Branch/NativeLink no iOS

- **What:** configurar Branch/Universal Links/NativeLink e entregar invite code.
- **Where:** iOS entitlements/plist/app delegate bridge/adapter; XCTest.
- **Depends on:** T50.
- **Reuses:** NativeLinkPort e SwiftUI app lifecycle.
- **Requirements:** INVITE-04, INVITE-06, EDGE-02, EDGE-06.
- **Fixed contract:** test/live config; universal link instalado; deferred data na
  primeira sessao; paste denial e no-op recuperavel; Base64URL somente.
- **Must not:** Firebase Dynamic Links, synthetic UI ou PII em Branch metadata.
- **Tools:** IOS.
- **Tests:** XCTest, +13 casos direct/deferred/duplicate/denial/invalid/config.
- **Gate:** Full iOS.
- **Done when:** [ ] cold/warm/deferred chegam ao mesmo callback; [ ] baseline
  +13 e Gate verde.
- **Commit:** `feat(ios): add deferred invite links`

### T52 - Implementar Keychain e compartilhamento iOS

- **What:** implementar estado local, invite no Keychain e share sheet nativo.
- **Where:** iOS storage/share adapters; XCTest.
- **Depends on:** T51.
- **Reuses:** LocalAccessStatePort/NativeSharePort e UserDefaults.
- **Requirements:** AUTH-07, INVITE-04, SELECT-05, SEC-01.
- **Fixed contract:** group ID UserDefaults; code Keychain after-first-unlock;
  delete idempotente; activity controller sem elemento acessivel sintetico.
- **Must not:** code em UserDefaults/log ou Keychain sincronizavel.
- **Tools:** IOS.
- **Tests:** XCTest, +11 casos roundtrip/delete/restart/error/share/redaction.
- **Gate:** Full iOS.
- **Done when:** [ ] logout apaga ambos; [ ] baseline +11 e Gate verde.
- **Commit:** `feat(ios): add secure access state adapters`

### T53 - Compor fluxo iOS e provar lifecycle

- **What:** injetar adapters no MainViewController e cobrir restart, links,
  Dynamic Type, Reduce Motion e back stack.
- **Where:** `SaqzIOSApp.swift`, Compose bridge, XCTest/XCUITest.
- **Depends on:** T52.
- **Reuses:** accessibility controller, root composition e adapters T50-52.
- **Requirements:** AUTH-06, AUTH-07, INVITE-04, SELECT-05, SEC-04, EDGE-07.
- **Fixed contract:** ordem Firebase/Branch -> dependencies -> Compose; uma
  arvore semantics Compose; pending actions persistem em foreground/background.
- **Must not:** SwiftUI product screen ou duplicate UIKit accessibility node.
- **Tools:** IOS.
- **Tests:** XCTest + XCUITest, +15 casos lifecycle/semantics/large type/links.
- **Gate:** Full iOS.
- **Done when:** [ ] max Dynamic Type mantem acoes alcancaveis; [ ] baseline +15;
  [ ] SaqzDev e SaqzProd verdes.
- **Commit:** `feat(ios): compose authenticated app lifecycle`

## Fase 8 - Observabilidade e gates finais

### T54 - Instrumentar metricas e redaction de acesso

- **What:** adicionar `AccessMetrics`/Micrometer e eventos estruturados sem PII.
- **Where:** access application port/adapter; bootstrap tests.
- **Depends on:** T53.
- **Reuses:** correlation diagnostics T03 e operations existentes.
- **Requirements:** SEC-01, SEC-03.
- **Fixed contract:** bootstrap success/failure, 401/403/404/429,
  invite generated/expired/redeemed e provider failure; labels so operation/result/status.
- **Must not:** user/group ID, email, token/code ou cardinalidade nao limitada.
- **Tools:** BACKEND.
- **Tests:** unit + Spring output/registry, +14 casos labels/events/redaction.
- **Gate:** Quick access e HTTP full.
- **Done when:** [ ] forbidden values nao aparecem em registry/output; [ ]
  baseline +14 e gates verdes.
- **Commit:** `feat(access): add safe access observability`

### T55 - Provar fluxo descartavel Firebase e PostgreSQL

- **What:** criar fixture end-to-end backend que combina Auth Emulator,
  PostgreSQL Testcontainer e endpoints reais de session/group/invite.
- **Where:** firebase fixture extensions; bootstrap emulator/integration tests.
- **Depends on:** T54.
- **Reuses:** lifecycle compartilhado do emulator e migrations T05.
- **Requirements:** SESSION-01, GROUP-01..04, INVITE-01..08, SEC-02, SEC-04.
- **Fixed contract:** sem credencial externa; duas identidades; criar grupo,
  gerar/rotacionar/resgatar/promover; concorrencia e rollback observaveis.
- **Must not:** mockar repository/security ou chamar Branch/Google real.
- **Tools:** BACKEND.
- **Tests:** end-to-end integration, +8 jornadas independentes.
- **Gate:** HTTP full e SQL integration.
- **Done when:** [ ] fixture sobe/derruba recursos sem processo residual; [ ]
  baseline +8 e gates verdes em rerun limpo.
- **Commit:** `test(access): prove disposable authenticated flows`

### T56 - Expandir o gate Gradle completo

- **What:** incluir access unit/integration, core:network e feature mobile com
  inventario/ordem/failure/zero-test garantidos.
- **Where:** `scripts/check-gradle`; `tests/scripts/check-gradle.test.sh`.
- **Depends on:** T55.
- **Reuses:** harness stub e descoberta XML existentes.
- **Requirements:** SEC-04.
- **Fixed contract:** credentials/scope primeiro; backend access antes de adb;
  todas as suites novas obrigatorias e falha de qualquer uma propaga.
- **Must not:** remover suite antiga ou exigir credencial Branch/Google/prod.
- **Tools:** SHELL.
- **Tests:** contract/mutation, +12 cenarios inventory/order/fail/zero tests.
- **Gate:** Contract; depois `scripts/check-gradle` em ambiente apto.
- **Done when:** [ ] cada suite possui failure mutation; [ ] baseline +12; [ ]
  Contract e gate Gradle completos verdes.
- **Commit:** `test(gates): include authentication access suites`

### T57 - Atualizar isolamento e CI do monorepo

- **What:** provar backend/mobile isolados com novos modulos e manter jobs
  credential-free com Docker/Firebase disponiveis.
- **Where:** workspace isolation test; CI workflow e contract tests.
- **Depends on:** T56.
- **Reuses:** scratch stubs, `initialization-gate` e aggregate evaluator.
- **Requirements:** SEC-04.
- **Fixed contract:** workspaces compilam sem sibling; CI roda suites access,
  mantem API30, a tupla API35 de AD-023, iOS SaqzDev-only de AD-024 e landing;
  timeout/cancel/zero tests preservados; SaqzProd continua no gate local.
- **Must not:** acoplar Gradle graphs, adicionar deploy ou secret de producao.
- **Tools:** CI.
- **Tests:** isolation + CI contract/mutation, +12 cenarios.
- **Gate:** Isolation e Contract.
- **Done when:** [ ] mutacoes cross-workspace morrem; [ ] workflow inventory
  exato; [ ] baseline +12 e gates verdes.
- **Commit:** `ci(access): gate isolated authenticated flows`

### T58 - Fechar documentacao e gate local

- **What:** atualizar README para arquitetura/comandos/configuracao local e
  executar a matriz completa antes do Verifier.
- **Where:** `README.md`; README/check-all contract tests; status deste arquivo.
- **Depends on:** T57.
- **Reuses:** secoes architecture/gates/environments existentes.
- **Requirements:** SEC-04.
- **Fixed contract:** documentar PostgreSQL/Testcontainers, Firebase emulator,
  Branch test config, novos modulos e comandos exatos; sem instruir secrets.
- **Must not:** marcar Done/Verified antes dos gates/Verifier ou alterar landing.
- **Tools:** SHELL, BACKEND, KMP, ANDROID, IOS.
- **Tests:** docs/aggregate contract, +10 cenarios de comandos/inventario/falha.
- **Gate:** Full local.
- **Done when:** [ ] todos os comandos do README existem e sao os executados; [ ]
  baseline +10; [ ] `scripts/check-all` verde; [ ] pronto para Verifier.
- **Commit:** `docs(access): document and close local gate`

## Rastreabilidade de Requisitos

| Requisitos | Tasks primarias | Evidencia final |
| --- | --- | --- |
| AUTH-01..08 | T31, T34, T35, T39..T41, T45, T46, T50 | reducers KMP + Compose UI + adapters Android/iOS |
| SESSION-01..05 | T02, T07..T11, T35, T54, T55 | unit/JDBC/HTTP/emulator/MockEngine |
| GROUP-01..07 | T05, T06, T12..T23, T32, T37, T42..T44, T55 | policy + PostgreSQL + endpoints + UI |
| INVITE-01..08 | T05, T24..T30, T33, T38, T44, T47..T53, T55 | crypto/link + concorrencia + deferred links |
| SELECT-01..06 | T08, T15..T17, T35..T37, T42, T43, T45, T48..T53 | session view + coordinator + storage/lifecycle |
| SEC-01..04 | T01..T05, T09..T11, T14, T17, T20, T23..T30, T48, T52, T54..T58 | problems/redaction/transactions/metrics/gates |
| EDGE-01..07 | T06..T08, T12, T18..T21, T28..T30, T34..T53 | validacao, roles, reducers, UI e lifecycle nativo |

**Cobertura planejada:** 45/45 requisitos, sem requisito orfao.

## Phase Execution Map

```text
Phase 0: T01 -> T02 -> T03 -> T04 -> T05 -> T06
                                             |
Phase 1:                                    T07 -> T08 -> T09 -> T10 -> T11
                                                                           |
Phase 2: T12 -> T13 -> T14 -> T15 -> T16 -> T17 -> T18 -> T19 -> T20 <-----+
                                                                      |
Phase 3: T21 -> T22 -> T23 -> T24 -> T25 -> T26 -> T27 -> T28 -> T29 -> T30
                                                                            |
Phase 4: T31 -> T32 -> T33 -> T34 -> T35 -> T36 -> T37 -> T38 <-------------+
                                                               |
Phase 5: T39 -> T40 -> T41 -> T42 -> T43 -> T44 -> T45 <-------+
                                                        |
Phase 6: T46 -> T47 -> T48 -> T49 <---------------------+
                                      |
Phase 7: T50 -> T51 -> T52 -> T53 <---+
                                      |
Phase 8: T54 -> T55 -> T56 -> T57 -> T58
```

Cada seta representa exatamente o unico `Depends on` direto da task destino;
dependencias anteriores sao satisfeitas transitivamente. Nao ha paralelismo
intra-fase.

## Task Granularity Check

| Task | Entrega atomica | Status |
| --- | --- | --- |
| T01 | um contrato de scope | OK - granular |
| T02 | um principal compartilhado | OK - granular |
| T03 | uma infraestrutura problem/correlation | OK - coesa |
| T04 | um modulo/boundary backend | OK - coesa |
| T05 | um schema relacional versionado | OK - coesa |
| T06 | uma politica de dominio | OK - coesa |
| T07 | um use case de bootstrap | OK - granular |
| T08 | um adapter JDBC de session | OK - granular |
| T09 | um endpoint session | OK - granular |
| T10 | um core HTTP client | OK - coesa |
| T11 | um transporte session bearer | OK - coesa |
| T12 | um use case create group | OK - granular |
| T13 | um adapter JDBC create group | OK - granular |
| T14 | um endpoint create group | OK - granular |
| T15 | um use case read group | OK - granular |
| T16 | um adapter JDBC read group | OK - granular |
| T17 | um endpoint read group | OK - granular |
| T18 | um use case settings | OK - granular |
| T19 | um adapter JDBC settings | OK - granular |
| T20 | um endpoint settings | OK - granular |
| T21 | uma politica de delegacao | OK - coesa |
| T22 | um adapter JDBC membership | OK - granular |
| T23 | um controller membership | OK - coesa |
| T24 | uma capability de invite link | OK - coesa |
| T25 | um lifecycle de invite | OK - coesa |
| T26 | um adapter JDBC invite lifecycle | OK - granular |
| T27 | um controller invite lifecycle | OK - coesa |
| T28 | um use case redeem | OK - granular |
| T29 | um adapter JDBC redeem | OK - granular |
| T30 | um endpoint redeem | OK - granular |
| T31 | um modulo/contrato mobile | OK - coesa |
| T32 | um client de grupos | OK - coesa |
| T33 | um client roles/invites | OK - coesa |
| T34 | um reducer auth | OK - granular |
| T35 | um coordinator session | OK - coesa |
| T36 | um reducer selection | OK - granular |
| T37 | um coordinator group admin | OK - coesa |
| T38 | um coordinator invite | OK - granular |
| T39 | uma tela login | OK - granular |
| T40 | uma tela cadastro | OK - granular |
| T41 | um fluxo identity completion | OK - coesa |
| T42 | um fluxo group onboarding | OK - coesa |
| T43 | uma tela group context/settings | OK - coesa |
| T44 | uma ferramenta roles/invite | OK - coesa |
| T45 | um root/navigation graph | OK - coesa |
| T46 | um auth adapter Android | OK - coesa |
| T47 | um link adapter Android | OK - granular |
| T48 | um local-state adapter Android | OK - coesa |
| T49 | uma composition root Android | OK - coesa |
| T50 | um auth adapter iOS | OK - coesa |
| T51 | um link adapter iOS | OK - granular |
| T52 | um local-state adapter iOS | OK - coesa |
| T53 | uma composition root iOS | OK - coesa |
| T54 | uma observability adapter | OK - coesa |
| T55 | uma fixture disposable E2E | OK - granular |
| T56 | um gate Gradle | OK - granular |
| T57 | um contrato isolation/CI | OK - coesa |
| T58 | um fechamento docs/aggregate | OK - coesa |

Nenhuma task entrega duas features independentes. Arquivos de build, wiring e
testes citados pertencem ao mesmo conceito e sao indispensaveis para torna-lo
compilavel e verificavel no proprio commit.

## Diagram-Definition Cross-Check

| Task | Depends on no corpo | Predecessor no diagrama | Status |
| --- | --- | --- | --- |
| T01 | none | none | Match |
| T02 | T01 | T01 | Match |
| T03 | T02 | T02 | Match |
| T04 | T03 | T03 | Match |
| T05 | T04 | T04 | Match |
| T06 | T05 | T05 | Match |
| T07 | T06 | T06 | Match |
| T08 | T07 | T07 | Match |
| T09 | T08 | T08 | Match |
| T10 | T09 | T09 | Match |
| T11 | T10 | T10 | Match |
| T12 | T11 | T11 | Match |
| T13 | T12 | T12 | Match |
| T14 | T13 | T13 | Match |
| T15 | T14 | T14 | Match |
| T16 | T15 | T15 | Match |
| T17 | T16 | T16 | Match |
| T18 | T17 | T17 | Match |
| T19 | T18 | T18 | Match |
| T20 | T19 | T19 | Match |
| T21 | T20 | T20 | Match |
| T22 | T21 | T21 | Match |
| T23 | T22 | T22 | Match |
| T24 | T23 | T23 | Match |
| T25 | T24 | T24 | Match |
| T26 | T25 | T25 | Match |
| T27 | T26 | T26 | Match |
| T28 | T27 | T27 | Match |
| T29 | T28 | T28 | Match |
| T30 | T29 | T29 | Match |
| T31 | T30 | T30 | Match |
| T32 | T31 | T31 | Match |
| T33 | T32 | T32 | Match |
| T34 | T33 | T33 | Match |
| T35 | T34 | T34 | Match |
| T36 | T35 | T35 | Match |
| T37 | T36 | T36 | Match |
| T38 | T37 | T37 | Match |
| T39 | T38 | T38 | Match |
| T40 | T39 | T39 | Match |
| T41 | T40 | T40 | Match |
| T42 | T41 | T41 | Match |
| T43 | T42 | T42 | Match |
| T44 | T43 | T43 | Match |
| T45 | T44 | T44 | Match |
| T46 | T45 | T45 | Match |
| T47 | T46 | T46 | Match |
| T48 | T47 | T47 | Match |
| T49 | T48 | T48 | Match |
| T50 | T49 | T49 | Match |
| T51 | T50 | T50 | Match |
| T52 | T51 | T51 | Match |
| T53 | T52 | T52 | Match |
| T54 | T53 | T53 | Match |
| T55 | T54 | T54 | Match |
| T56 | T55 | T55 | Match |
| T57 | T56 | T56 | Match |
| T58 | T57 | T57 | Match |

**Resultado:** 58/58 dependencias coincidem; nenhuma aponta para fase futura.

## Test Co-location Validation

| Task | Camada modificada | Matriz exige | Task declara | Status |
| --- | --- | --- | --- | --- |
| T01 | Scripts/scope | contract/mutation | contract/mutation | OK |
| T02 | Shared identity | unit + architecture | unit + architecture | OK |
| T03 | HTTP transversal | Spring integration | Spring integration | OK |
| T04 | Gradle/architecture | contract/mutation | architecture/contract | OK |
| T05 | Schema/JDBC config | PostgreSQL integration | PostgreSQL integration | OK |
| T06 | Access domain | unit | unit | OK |
| T07 | Access application | unit | unit | OK |
| T08 | Access JDBC | PostgreSQL integration | PostgreSQL integration | OK |
| T09 | Access HTTP | Spring + emulator | Spring + emulator integration | OK |
| T10 | Core network | KMP unit/MockEngine | KMP unit/MockEngine | OK |
| T11 | Core network/session | KMP unit/MockEngine | KMP unit/MockEngine | OK |
| T12 | Access application | unit | unit | OK |
| T13 | Access JDBC | PostgreSQL integration | PostgreSQL integration | OK |
| T14 | Access HTTP | Spring integration | Spring integration | OK |
| T15 | Access application | unit | unit | OK |
| T16 | Access JDBC | PostgreSQL integration | PostgreSQL integration | OK |
| T17 | Access HTTP | Spring integration | Spring integration | OK |
| T18 | Access application | unit | unit | OK |
| T19 | Access JDBC | PostgreSQL integration | PostgreSQL integration | OK |
| T20 | Access HTTP | Spring integration | Spring integration | OK |
| T21 | Access application | unit | unit | OK |
| T22 | Access JDBC | PostgreSQL integration | PostgreSQL integration | OK |
| T23 | Access HTTP | Spring integration | Spring integration | OK |
| T24 | Access crypto/link | unit/contract | unit/contract | OK |
| T25 | Access application | unit | unit | OK |
| T26 | Access JDBC | PostgreSQL integration | PostgreSQL integration | OK |
| T27 | Access HTTP | Spring integration | Spring integration | OK |
| T28 | Access application | unit | unit | OK |
| T29 | Access JDBC | PostgreSQL integration | PostgreSQL integration | OK |
| T30 | Access HTTP | Spring integration | Spring integration | OK |
| T31 | Access KMP contracts | KMP unit/contract | contract/build + KMP cases | OK |
| T32 | Access KMP API | KMP unit/MockEngine | MockEngine unit | OK |
| T33 | Access KMP API | KMP unit/MockEngine | MockEngine unit | OK |
| T34 | Access KMP state | KMP unit | unit | OK |
| T35 | Access KMP state | KMP unit | unit | OK |
| T36 | Access KMP state | KMP unit | unit | OK |
| T37 | Access KMP state | KMP unit | unit | OK |
| T38 | Access KMP state | KMP unit | unit | OK |
| T39 | Access Compose UI | Compose UI KMP | Compose UI | OK |
| T40 | Access Compose UI | Compose UI KMP | Compose UI | OK |
| T41 | Access Compose UI | Compose UI KMP | Compose UI | OK |
| T42 | Access Compose UI | Compose UI KMP | Compose UI | OK |
| T43 | Access Compose UI | Compose UI KMP | Compose UI | OK |
| T44 | Access Compose UI | Compose UI KMP | Compose UI | OK |
| T45 | Compose app shell | Compose UI KMP | Compose UI/contract | OK |
| T46 | Android auth adapter | JUnit + instrumented | JUnit + emulator seam | OK |
| T47 | Android link adapter | JUnit + instrumented | JUnit + instrumented | OK |
| T48 | Android local adapters | JUnit + instrumented | JUnit + instrumented | OK |
| T49 | Android app lifecycle | instrumented | instrumented | OK |
| T50 | iOS auth adapter | XCTest | XCTest | OK |
| T51 | iOS link adapter | XCTest | XCTest | OK |
| T52 | iOS local adapters | XCTest | XCTest | OK |
| T53 | iOS app lifecycle/UI | XCTest + XCUITest | XCTest + XCUITest | OK |
| T54 | Access metrics/HTTP | unit + Spring integration | unit + Spring registry/output | OK |
| T55 | Backend E2E/JDBC | emulator + PostgreSQL integration | E2E integration | OK |
| T56 | Gradle gate script | contract/mutation | contract/mutation | OK |
| T57 | Isolation/CI | integration + contract/mutation | isolation + contract/mutation | OK |
| T58 | Docs/aggregate | contract + Full local | docs/aggregate contract | OK |

**Resultado:** 58/58 tasks possuem o teste exigido no mesmo commit; nenhum
`Tests: none` indevido e nenhuma cobertura foi adiada.

## Aprovacao Antes de Execute

Para iniciar Execute, confirmar:

1. este `tasks.md` e a matriz de cobertura;
2. os perfis de ferramentas propostos por task;
3. se o conector GitHub pode ser usado em T57 para ler evidencias de CI;
4. se deseja batches sequenciais de subagentes por fase ou execucao integral no
   agente principal. Subagentes nunca rodam fases em paralelo.

Skills disponiveis e relevantes: `tlc-spec-driven` obrigatoria; `backprop` em
qualquer falha; `security-best-practices` somente se uma revisao de seguranca
separada for solicitada. Nenhum MCP e necessario para T01-T56/T58; GitHub e
opcional em T57.
