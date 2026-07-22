# Mobile SOLID Refactor Wave 2 Context

**Gathered:** 2026-07-21
**Spec:** `.specs/features/mobile-solid-refactor-wave-2/spec.md`
**Status:** Ready for design

---

## Feature Boundary

Segunda onda de refatoração SOLID do workspace `mobile/`: corrigir 4 bugs
comportamentais encontrados na análise, introduzir DI com Koin, decompor o hub
de navegação (`AuthenticatedAccessRoot`), eliminar state machines duplicados,
decompor `NetworkClient` e `GroupSetupScreen`, e centralizar
formatação/erros/validação — como refatoração pura (sem mudança de comportamento
observável além dos 4 fixes e das duas micro-mudanças explicitamente aprovadas).

A onda 1 (`.specs/features/mobile-solid-refactor/`, verificada PASS em
2026-07-21) cobriu separação de arquivos/declarações; esta onda cobre
responsabilidades, duplicação e inversão de dependência. Não reabre a onda 1.

---

## Implementation Decisions

### Framework de DI

- **Koin** (não Metro). DI em runtime, maduro em KMP, sem KSP/compiler plugin.
  Registrado como decisão arquitetural AD-028 em `.specs/STATE.md`.
- Módulos Koin por feature (access, groups, drafts, network) substituem o
  god-bag `SaqzAppDependencies`; wiring sai dos composables.

### Sequenciamento bugs vs. refactors

- **Bugs primeiro, isolados.** Fase 0 corrige os 4 bugs em commits próprios com
  testes de regressão; os refactors estruturais vêm depois, sobre código já
  corrigido. Nenhum commit mistura fix comportamental com refactor estrutural.

### Formatação BRL centralizada

- Centralizar em `core/common` (onde `SaqzCurrencyFormatter` já vive).
- **Formato canônico único: `R$ 1.234,56`.** Telas que hoje exibem `1.234,56`
  (sem símbolo) migram — micro-mudança visual explicitamente aceita pelo usuário.

### Verificação dos fixes

- Testes unitários/commonTest de regressão por fix + suites existentes de
  `:compose-app` e `:features:groups` + gates de plataforma existentes.
- **Sem** novos testes instrumentados Android/iOS nesta feature.

### Agent's Discretion

- Estrutura interna dos módulos Koin (nomes, granularidade por feature).
- Nome e shape do orchestrator não-composable e da base genérica de
  draft-mutation (Design decide, preservando os contratos comportamentais).

### Declined / Undiscussed Gray Areas → Assumptions

- Mensagem de erro hardcoded `"Revise este campo."` em `GroupSetupScreen.kt:972`
  (descarta `state.fieldErrors` do servidor): **mantida como está** — mudança de
  comportamento fora dos 4 fixes aprovados. Registrada em Deferred Ideas.
- Regra de nome divergente (registro aceita `isNotBlank()`; completar perfil
  exige 2..80 chars): ao centralizar validadores, **a regra mais restrita
  (2..80, sem ISO control) vence uniformemente** — micro-mudança comportamental
  registrada como assumption na spec para confirmação.

---

## Specific References

- Análise SOLID completa produzida em 2026-07-21 (relatório em 4 módulos,
  referências file:line) — base evidencial de cada requisito da spec.
- Padrão de referência a generalizar: mapeamento de erro na data layer em
  `mobile/features/groups/.../data/finance/FinanceApi.kt:47-62`.
- Padrão de referência a preservar: `AuthenticatedNetworkClient` (coeso,
  refresh deduplicado por mutex).

---

## Deferred Ideas

- Segregação de interfaces gordas (`OrganizerFinanceGateway`, `NativeAuthPort`,
  `AccessRuntimeContract`) — mudança de contratos internos, fora da estratégia
  "refatoração pura" escolhida. Candidata a onda 3.
- Decomposição de `AccessViewModel` em ViewModels por feature; colapso de
  `AccessIntent` vs `AccessRuntimeIntent`; canal `effects` morto de
  `GroupsNavigationViewModel`; shells paralelos (catálogo); `CoilGroupPhotoCache`
  (side effect no construtor); i18n de strings hardcoded; `GroupPhotoEditor`
  (sopa de booleans); correção da mensagem de erro do servidor no setup.
- Teste instrumentado para 401→logout (se a verificação unitária se mostrar
  insuficiente na validação).
