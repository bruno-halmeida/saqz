# VUL-177 Tasks

## Execution Protocol

Implement with the `tlc-spec-driven` skill. Each task is implemented, tested, gated and committed
atomically before the next task begins. After T4, run the independent feature-level validation.

**Design:** `.specs/features/vul-177-finance-overview/design.md`
**Status:** In Progress

## Test Coverage Matrix

> Generated from the existing Kotlin/JUnit/Testcontainers suites and the requested acceptance
> criteria. Guidelines found: no backend `AGENTS.md`/`CONTRIBUTING.md`; strong defaults applied.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Query/period application | unit | All validation branches and period edge cases; 1:1 with OVERVIEW-05–07 | `features/groups/src/test/**/application/finance/overview/*Test.kt` | `cd backend && ./gradlew :features:groups:test` |
| Controller/HTTP mapping | unit | Happy path, empty result, invalid filters and structured payload fields | `features/groups/src/test/**/adapter/input/http/*Overview*Test.kt` | `cd backend && ./gradlew :features:groups:test` |
| JDBC repository | integration | Authorization, formula, period totals, status fields, recent limit/order and soft-delete | `features/groups/src/integrationTest/**/finance/*Overview*Test.kt` | `cd backend && ./gradlew :features:groups:integrationTest` |
| Wiring/requests | build + integration | Application context starts and requests are valid/new | `backend/bootstrap/...`, `bruno/Finance/Overview/*.bru` | `cd backend && ./gradlew :features:groups:test :features:groups:integrationTest` |

## Gate Check Commands

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | T1/T3 unit tests | `cd backend && ./gradlew :features:groups:test` |
| Full | T2 repository integration | `cd backend && ./gradlew :features:groups:test :features:groups:integrationTest` |
| Build | T4/final feature | `cd backend && ./gradlew :features:groups:test :features:groups:integrationTest` |

## Execution Plan

### Phase 1: Contract and query period

`T1`

### Phase 2: Data access

`T2`

### Phase 3: HTTP and delivery

`T3 → T4`

## Task Breakdown

### T1: Criar contrato de consulta e resolução de período

**What:** Criar os modelos estruturados do agregado, a porta de repositório e a resolução de
`month`/`year` com clock/fuso injetáveis.
**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/application/finance/overview/FinanceOverview.kt`
**Depends on:** None
**Reuses:** `YearMonth`, `Clock`, resultados de application existentes.
**Requirement:** OVERVIEW-05, OVERVIEW-06, OVERVIEW-07

**Done when:**

- [x] Mês corrente, mês específico e ano completo são resolvidos com precisão.
- [x] Ambos os filtros e formatos inválidos retornam erros de campo em português.
- [x] Os modelos carregam todos os campos estruturados exigidos pelo contrato.
- [x] Unit gate passa sem testes reduzidos ou ignorados.

**Tests:** unit
**Gate:** quick
**Commit:** `feat(finance): add overview query contract`

### T2: Implementar repositório JDBC agregado

**What:** Criar queries próprias para grupos OWNER/ADMIN, saldo acumulado, totais do período,
configuração/status e cinco lançamentos recentes.
**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcFinanceOverviewRepository.kt`
e `backend/features/groups/src/integrationTest/kotlin/br/com/saqz/groups/adapter/output/jdbc/finance/JdbcFinanceOverviewRepositoryIntegrationTest.kt`
**Depends on:** T1
**Reuses:** Migrações V5/V9/V22/V25/V32 e `JdbcClient`.
**Requirement:** OVERVIEW-01, OVERVIEW-02, OVERVIEW-03, OVERVIEW-04, OVERVIEW-08, OVERVIEW-09,
OVERVIEW-10

**Done when:**

- [x] Só OWNER/ADMIN e grupos ativos são retornados.
- [x] A fórmula de saldo e os totais de período são conferidos com charges/despesas mistas.
- [x] `pendingMonthlyCount`/`hasBillingConfigured` e lançamentos estruturados são conferidos.
- [x] Usuário sem grupo retorna agregado vazio e não 403.
- [x] Teste de integração direcionado passa com Testcontainers; full gate será repetido em rodada limpa.

**Tests:** integration
**Gate:** full
**Commit:** `feat(finance): add jdbc multi-group overview repository`

### T3: Expor endpoint `/api/me/finance/overview`

**What:** Criar controller próprio, mapear actor autenticado, parâmetros, erros e resposta JSON;
adicionar testes unitários do happy path, vazio e validação.
**Where:** `backend/features/groups/src/main/kotlin/br/com/saqz/groups/adapter/input/http/MyFinanceOverviewController.kt`
e `backend/features/groups/src/test/kotlin/br/com/saqz/groups/adapter/input/http/MyFinanceOverviewControllerTest.kt`
**Depends on:** T1, T2
**Reuses:** `VerifiedGroupActorResolver`, `RequestIdentity`, `InvalidGroupRequestException`.
**Requirement:** OVERVIEW-01, OVERVIEW-04, OVERVIEW-05, OVERVIEW-06, OVERVIEW-07, OVERVIEW-08,
OVERVIEW-09

**Done when:**

- [x] Endpoint retorna período, totais, grupos e recentes estruturados.
- [x] Actor logado é o único identificador usado na consulta.
- [x] Resposta vazia permanece HTTP 200.
- [x] Filtros inválidos e mutuamente exclusivos retornam HTTP-contract exception.
- [x] Unit gate passa com todos os campos do payload assertados.

**Tests:** unit
**Gate:** quick
**Commit:** `feat(finance): expose multi-group overview endpoint`

### T4: Registrar beans e requests Bruno

**What:** Adicionar o mínimo de wiring Spring e requests Bruno novos para default, mês, ano e
validação do endpoint.
**Where:** `backend/bootstrap/src/main/kotlin/br/com/saqz/bootstrap/configuration/AccessSessionConfiguration.kt`
e `bruno/Finance/Overview/*.bru`
**Depends on:** T3
**Reuses:** Configuração JDBC existente e ambiente Dev.
**Requirement:** OVERVIEW-01, OVERVIEW-05, OVERVIEW-06, OVERVIEW-07

**Done when:**

- [x] Contexto Spring consegue construir repository, query e controller.
- [x] Existem somente arquivos `.bru` novos sob `bruno/Finance/Overview/`.
- [ ] Build gate `test` + `integrationTest` passa.

**Tests:** integration
**Gate:** build
**Commit:** `feat(finance): wire overview endpoint and Bruno requests`

## Phase Execution Map

```text
Phase 1: T1
Phase 2: T1 ──→ T2
Phase 3: T1 ──→ T2 ──→ T3 ──→ T4
```

## Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1 | one application contract/period file + co-located unit tests | ✅ Granular |
| T2 | one repository and its co-located integration suite | ✅ Granular |
| T3 | one endpoint controller and its co-located unit suite | ✅ Granular |
| T4 | wiring plus requests required to expose the endpoint | ✅ Cohesive delivery task |

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram shows | Status |
| --- | --- | --- | --- |
| T1 | None | phase root | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T1, T2 | T1 → T2 → T3 | ✅ Match |
| T4 | T3 | T3 → T4 | ✅ Match |

## Test Co-location Validation

| Task | Layer | Matrix requires | Task says | Status |
| --- | --- | --- | --- |
| T1 | application | unit | unit | ✅ OK |
| T2 | repository | integration | integration | ✅ OK |
| T3 | controller | unit | unit | ✅ OK |
| T4 | wiring/requests | integration/build | integration | ✅ OK |
