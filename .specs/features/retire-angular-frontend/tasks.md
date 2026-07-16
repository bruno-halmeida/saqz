# Retirada do Frontend Angular — Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implementar com o skill `tlc-spec-driven`, uma tarefa por vez, gate antes do
commit e um commit atômico por tarefa. Após T5, executar Verifier independente.

**Design:** `.specs/features/retire-angular-frontend/design.md`
**Status:** ✅ Verified

## Test Coverage Matrix

> Gerada a partir de `README.md`, scripts shell, workflow e oito testes de
> contrato existentes. Diretriz adicional: `/Users/bruno_almeida/Private/merkos/.code/RTK.md`.

| Camada | Tipo | Expectativa | Local | Comando |
| --- | --- | --- | --- | --- |
| Estrutura do repositório | contrato shell | Ausência total de `frontend/` e rejeição de reintrodução | `tests/scripts/check-scope.test.sh` | `tests/scripts/check-scope.test.sh` |
| Fronteiras Gradle/backend | unitário Kotlin | `ARCH-08` rejeita acoplamento com o único cliente mobile | `backend/architecture-tests/src/test/**` | `backend/gradlew -p backend :architecture-tests:test --console=plain` |
| Gate local | contrato shell | Ordem, fail-fast e sinal para Gradle/iOS/landing | `tests/scripts/check-all.test.sh` | `tests/scripts/check-all.test.sh` |
| CI/evaluator | contrato shell | Três jobs obrigatórios; failure/cancelled sempre falham | `tests/scripts/check-ci.test.sh` | `tests/scripts/check-ci.test.sh` |
| README/orquestração | contrato shell | Nenhuma instrução Angular e tooling Firebase preservado | `tests/scripts/check-readme.test.sh` | `tests/scripts/check-readme.test.sh` |
| Landing | integração shell | Conteúdo e workflow Pages permanecem no baseline | `tests/scripts/check-landing.test.sh` | `tests/scripts/check-landing.test.sh` |

## Gate Check Commands

| Nível | Quando | Comando |
| --- | --- | --- |
| Quick | Após uma alteração de contrato isolada | O teste shell/Kotlin indicado na tarefa |
| Full | Após scripts, CI ou documentação | `scripts/test-scripts` |
| Build | Encerramento | `scripts/check-all` |

## Execution Plan

```text
T1 -> T2 -> T3 -> T4 -> T5
```

## Task Breakdown

### T1: Remover workspace Angular

**Status:** ✅ Complete (`d17c472`)

**What:** Excluir todos os arquivos rastreados sob `frontend/`.
**Where:** `frontend/`
**Depends on:** None
**Requirement:** `RET-01`
**Tools:** `apply_patch`, shell; skill `tlc-spec-driven`
**Tests:** contrato estrutural
**Gate:** `test ! -e frontend && test -z "$(git ls-files frontend)"`
**Done when:** diretório e arquivos rastreados não existem; landing intacta.
**Commit:** `chore(frontend)!: remove angular workspace`

### T2: Reforçar fronteiras mobile-only

**Status:** ✅ Complete (`d77afbd`)

**What:** Atualizar scope, `ARCH-08` e isolation para o único cliente mobile,
incluindo mutação que rejeita qualquer novo path `frontend/`.
**Where:** `scripts/check-scope`, `tests/scripts/check-scope.test.sh`,
`tests/scripts/check-workspace-isolation.test.sh`,
`backend/architecture-tests/src/test/kotlin/br/com/saqz/architecture/BackendArchitectureTest.kt`
**Depends on:** T1
**Requirements:** `RET-04`, `RET-08`
**Tests:** contrato shell + unitário Kotlin
**Gate:** `tests/scripts/check-scope.test.sh && backend/gradlew -p backend :architecture-tests:test --console=plain`
**Done when:** clean passa, reintrodução de frontend e mutações proibidas falham,
`ARCH-08` passa sem referência ao workspace removido.
**Commit:** `test(scope): enforce mobile-only client boundary`

### T3: Remover Angular do gate local

**Status:** ✅ Complete (`0f0a8e0`)

**What:** Reduzir `scripts/check-all` a Gradle, iOS e landing e adaptar seu
contrato de ordem/fail-fast/sinais.
**Where:** `scripts/check-all`, `tests/scripts/check-all.test.sh`
**Depends on:** T2
**Requirement:** `RET-02`
**Tests:** contrato shell
**Gate:** `tests/scripts/check-all.test.sh`
**Done when:** nenhuma invocação npm existe e os sete casos do gate passam.
**Commit:** `build(gates): remove angular from local aggregate`

### T4: Remover Angular do CI

**Status:** ✅ Complete (`4ceaaf3`)

**What:** Excluir `angular-gate`, reduzir evaluator a três resultados e adaptar
o contrato de workflow.
**Where:** `.github/workflows/initialization-gate.yml`,
`scripts/evaluate-ci-gates`, `tests/scripts/check-ci.test.sh`
**Depends on:** T3
**Requirement:** `RET-03`
**Tests:** contrato shell
**Gate:** `tests/scripts/check-ci.test.sh`
**Done when:** CI possui três jobs nativos + aggregate e rejeita failure/cancelled.
**Commit:** `ci(gates): retire angular job`

### T5: Atualizar documentação e orquestração

**Status:** ✅ Complete (`8f54dfc`)

**What:** Documentar backend/mobile/landing, manter Node para Firebase, remover
metadados Angular e incluir CI/README no aggregate de contratos.
**Where:** `README.md`, `.gitignore`, `tests/scripts/check-readme.test.sh`,
`scripts/test-scripts`
**Depends on:** T4
**Requirements:** `RET-05`, `RET-08`
**Tests:** contrato shell
**Gate:** `scripts/test-scripts`
**Done when:** referências operacionais Angular somem, Node/Firebase permanece e
todos os contratos shell passam.
**Commit:** `docs(project): describe mobile-only product surface`

## Diagram-Definition Cross-Check

| Task | Depends on | Diagram | Status |
| --- | --- | --- | --- |
| T1 | None | início | ✅ |
| T2 | T1 | T1 -> T2 | ✅ |
| T3 | T2 | T2 -> T3 | ✅ |
| T4 | T3 | T3 -> T4 | ✅ |
| T5 | T4 | T4 -> T5 | ✅ |

## Test Co-location Validation

| Task | Camada | Matriz | Task | Status |
| --- | --- | --- | --- | --- |
| T1 | Estrutura | contrato | contrato | ✅ |
| T2 | Scope/arquitetura | shell + unit | shell + unit | ✅ |
| T3 | Gate local | shell | shell | ✅ |
| T4 | CI | shell | shell | ✅ |
| T5 | Docs/orquestração | shell | shell | ✅ |
