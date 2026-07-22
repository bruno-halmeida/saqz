# Mobile SOLID Refactor Wave 2 Specification

## Problem Statement

A análise SOLID de 2026-07-21 encontrou 4 bugs comportamentais com perda
silenciosa de dados/sessão e violações estruturais concentradas em god files:
o hub de navegação `AuthenticatedAccessRoot` (891L, wiring + orquestração +
negócio em composable), duplicação quase integral de state machines e
ViewModels financeiros (~400L), `NetworkClient` com pipeline duplicado (390L) e
`GroupSetupScreen` com 6 responsabilidades (1020L). Isso eleva o custo e o
risco de qualquer mudança nessas áreas.

## Goals

- [ ] Eliminar os 4 bugs comportamentais com testes de regressão (zero perda
      silenciosa de drafts, attendance links e invalidação de sessão)
- [ ] Nenhum composable instancia rede, gateways ou coordinators; wiring vive
      em módulos Koin por feature (um único grafo de rede autenticado)
- [ ] Zero duplicação entre os coordinators de links diferidos e entre os
      ViewModels financeiros (state machine/base compartilhados)
- [ ] `NetworkClient` e `GroupSetupScreen` decompostos em unidades de
      responsabilidade única, com suites existentes verdes

## Out of Scope

Explicitamente excluído. Documentado para prevenir scope creep.

| Item | Razão |
| --- | --- |
| Segregação de interfaces gordas (`OrganizerFinanceGateway`, `NativeAuthPort`, `AccessRuntimeContract`) | Muda contratos internos; usuário escolheu refatoração pura. Deferred p/ onda 3 |
| Decomposição de `AccessViewModel` por feature; colapso `AccessIntent`/`AccessRuntimeIntent` | Deferred p/ onda 3 |
| Shells paralelos (catálogo/Home), canal `effects` morto, `CoilGroupPhotoCache`, `GroupPhotoEditor` flags, i18n de strings | Não selecionados no escopo |
| Mensagem de erro do servidor no setup (`"Revise este campo."`) | Mudança comportamental não aprovada; deferred |
| Testes instrumentados novos | Verificação definida como unitários + gates existentes |
| Mudanças em backend, iOS nativo (Swift) e landing page | Fronteira do repositório |
| Reabrir requisitos da onda 1 (`mobile-solid-refactor`) | Verificada PASS; independente |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| DI framework | Koin, módulos por feature | Escolha do usuário; maduro em KMP, sem KSP. AD-028 | y |
| Sequência | Fase 0 = 4 fixes isolados; refactors depois | Escolha do usuário; diffs limpos | y |
| Formato BRL canônico | `R$ 1.234,56` único em `core/common` | Escolha do usuário; micro-mudança visual aceita | y |
| Verificação dos fixes | Unitários + gates existentes, sem instrumentados novos | Escolha do usuário | y |
| Regra de nome ao unificar validadores | Vence a mais restrita (2..80 chars, sem ISO control) em ambos os fluxos | Micro-mudança comportamental; regra já existente em `VerifiedSessionCoordinator` | y |
| ETag (`"\"${version}\""`) permanece montado nos ViewModels financeiros | Manter como está | Mover p/ gateway muda contrato interno; fora da estratégia | y |
| Política de cache de fotos (`clearAll` no init) | Inalterada | Não selecionada no escopo | y |

**Open questions:** none — all resolved or logged above (spec confirmed 2026-07-21).

---

## User Stories

### P1: Correção dos 4 bugs comportamentais ⭐ MVP

**User Story**: Como mantenedor, quero os 4 bugs corrigidos isoladamente com
testes de regressão, para que nenhum refactor posterior esconda regressão.

**Why P1**: Perda silenciosa de dados (drafts, links) e sessão não invalidada
são defeitos em produção, não dívida técnica.

**Acceptance Criteria**:

1. **FIX-01** — WHEN `ExpenseDraftStorePort.clear(groupId, expenseId, commandKey)`
   é chamado no adapter Android THEN o sistema SHALL limpar/validar apenas o
   envelope de draft cuja referência inclui esse `expenseId` como `resourceId`,
   preservando drafts de outras despesas do mesmo grupo.
2. **FIX-02** — WHEN o adapter Android de estado local de grupos recebe
   `writePendingAttendanceLink(value)` seguido de `readPendingAttendanceLink()`
   THEN ele SHALL persistir em store real e retornar o valor escrito (sem
   adapter no-op retornando sucesso).
3. **FIX-03** — WHEN a sessão do Branch contém o parâmetro `saqz_attendance`
   THEN `AndroidLinkAdapter` SHALL emitir o mesmo `GroupLinkEvent` de
   attendance que o caminho de URL direta emite para esse código.
4. **FIX-04** — WHEN uma requisição autenticada do grafo de rede usado pelo
   fluxo de game-detail recebe HTTP 401 THEN o sistema SHALL invalidar a sessão
   pelo mesmo `SessionInvalidator` real do grafo primário (sem no-op).
5. WHEN qualquer fix é commitado THEN o commit SHALL conter apenas o fix e seu
   teste de regressão (sem refactor estrutural no mesmo diff).

**Independent Test**: cada fix tem teste de regressão que falha antes e passa
depois; suites `:android-app` e `:compose-app` verdes.

---

### P1: DI com Koin + decomposição do hub de navegação ⭐ MVP

**User Story**: Como mantenedor, quero o wiring em módulos Koin e a
orquestração cross-feature fora de composables, para alterar rotas sem tocar
em construção de infraestrutura.

**Why P1**: `AuthenticatedAccessRoot` é o ponto de maior acoplamento do app e
dono de 2 dos 4 bugs (grafos de rede paralelos); bloqueia as demais
refatorações de presentation.

**Acceptance Criteria**:

1. **NAV-01** — WHEN o app compõe a rota autenticada THEN nenhum composable
   SHALL instanciar `NetworkClient`, APIs, gateways ou coordinators; todas as
   dependências SHALL vir de módulos Koin por feature.
2. **NAV-02** — WHEN uma sessão autenticada está ativa THEN SHALL existir
   exatamente um grafo de cliente de rede autenticado (sem segundo grafo
   criado no composable).
3. **NAV-03** — WHEN state machines de features distintas precisam se
   reconciliar THEN a reconciliação SHALL viver em um orchestrator
   não-composable com dono único; o composable de rota SHALL apenas coletar
   estado e despachar intents.
4. **NAV-04** — WHEN `SaqzAppDependencies` for eliminado THEN o default de
   produção SHALL ser o grafo Koin real (sem `Unconfigured` como default do
   entry point); stubs/fakes SHALL viver em source sets de teste.
5. **NAV-05** — WHEN a decomposição estiver completa THEN as suites
   existentes de `:compose-app` (AccessViewModel, GroupsNavigationViewModel,
   SaqzNavHost, AuthenticatedAccessRoot, GroupsRouteHost) SHALL passar sem
   alteração de expectativas comportamentais (apenas wiring/construção).

**Independent Test**: `:compose-app:allTests` verde + app Android/iOS sobe e
navega login → home → grupos (jornada coberta pelos gates existentes).

---

### P2: Eliminação da duplicação de state machines

**User Story**: Como mantenedor, quero um state machine genérico de links
diferidos e uma base de ViewModel de mutação com draft, para corrigir uma
vez e não duas.

**Why P2**: ~400L duplicadas; cada regra de retry/logout/restore hoje precisa
ser editada em dois arquivos.

**Acceptance Criteria**:

1. **DUP-01** — WHEN os fluxos de convite diferido e attendance link diferido
   executam THEN ambos SHALL rodar sobre um único state machine genérico
   parametrizado por (filtro de evento, par de persistência, resolver, mapeador
   de erro, callback de resolução); os coordinators concretos SHALL conter
   apenas configuração.
2. **DUP-02** — WHEN `FinanceViewModel` e `ExpenseViewModel` executam
   restore/persist/retry/mapeamento de erro THEN ambos SHALL delegar a uma base
   compartilhada de mutação com draft; o papel (atleta/organizador) SHALL ser
   modelado como configuração de capacidade tipada (sealed), não como gateway
   nullable.
3. WHEN a extração estiver completa THEN os testes existentes
   `GroupPhotoCoordinatorTest`, `DeferredInvite*`, `FinanceViewModelTest` e
   `ExpenseViewModelTest` SHALL passar sem alteração de expectativas, incluindo
   `failed(Forbidden)` para atleta e todas as transições de retry/logout/restore.

**Independent Test**: `:features:groups:allTests` verde; busca estrutural não
encontra os corpos duplicados de start/stop/restore/retry nos dois coordinators.

---

### P2: Decomposição do NetworkClient

**User Story**: Como mantenedor, quero um pipeline HTTP único com mapeador de
erro injetável, para mudar política de erro/header em um só lugar.

**Why P2**: Pipeline duplicado (`executeResponse`/`executeMediaRequest`) exige
edição dupla para qualquer mudança de transporte.

**Acceptance Criteria**:

1. **NET-01** — WHEN qualquer requisição (JSON ou mídia) executa THEN ela
   SHALL passar por um único pipeline de transporte (montagem de request,
   cadeia de catches, leitura limitada do corpo de erro); o builder multipart
   SHALL viver em arquivo/classe próprios.
2. **NET-02** — WHEN respostas de erro chegam THEN a decodificação de
   `ApiProblem`, mapeamento de status e limites de payload SHALL preservar a
   semântica atual, com o `Json` e a estratégia de erro injetáveis por
   construtor.
3. **NET-03** — WHEN a factory de plataforma configura logging THEN ela SHALL
   decidir por ambiente tipado (ex.: `Environment.Prod`), não por string
   mágica `"prod"`.
4. WHEN a decomposição estiver completa THEN os testes existentes de
   `:core:network` e os consumidores (`AuthenticatedNetworkClient`, features)
   SHALL passar sem alteração comportamental.

**Independent Test**: `:core:network` tests + `:compose-app:allTests` verdes.

---

### P2: Decomposição do GroupSetupScreen

**User Story**: Como mantenedor, quero a tela de setup apenas compondo seções,
com formatação, labels e componentes extraídos, para mudar o form sem navegar
1020 linhas.

**Why P2**: Maior god file de UI do workspace; concentra regras duplicadas.

**Acceptance Criteria**:

1. **UI-01** — WHEN valores BRL são parseados/formatados no setup THEN a tela
   SHALL delegar ao formatter central (SHR-01); nenhuma função
   `parseBrlCents`/`formatBrlInput`/`sanitizeBrlInput` SHALL permanecer no
   arquivo da tela.
2. **UI-02** — WHEN a tela renderiza labels de enums e componentes
   (Input, SegmentedChoice, SelectorField, Stepper, FeeEditor, SelectionSheet)
   THEN os mappers de label SHALL viver fora do arquivo da tela e os
   componentes reutilizáveis SHALL viver em `ui/setup/components/`.
3. **UI-03** — WHEN a extração estiver completa THEN regras de negócio
   (capacidade 2..100, dia de vencimento default, editabilidade) SHALL ter
   fonte única (sem duplicação tela × support); os testes de UI/support
   existentes SHALL passar sem alteração de expectativas.

**Independent Test**: `:features:groups:allTests` verde + inspeção de que o
arquivo da tela só compõe seções.

---

### P2: Centralização de formatação, erros e validação

**User Story**: Como mantenedor, quero um formatter BRL/data, um mapeador de
erros auth e validadores de access únicos, para eliminar implementações
divergentes.

**Why P2**: BRL implementado 5x com 2 formatos; `AuthUiError`→string triplicado
e divergente; validação de nome divergente entre fluxos.

**Acceptance Criteria**:

1. **SHR-01** — WHEN qualquer valor monetário BRL é exibido ou parseado nos
   features mobile THEN o sistema SHALL usar o formatter central de
   `core/common` com formato canônico `R$ 1.234,56`; as 5 implementações
   locais (setup screen, ExpenseRules, FinanceChargeRules, FinanceScreen,
   ExpenseScreen) SHALL ser removidas; datas ISO→pt-BR duplicadas
   (`localDate`, `monthPtBr`) SHALL centralizar igualmente.
2. **SHR-02** — WHEN falhas de rede chegam aos bounded contexts de groups
   THEN o mapeamento de status HTTP (403/404/400/`VERSION_CONFLICT`/
   `retryAfterSeconds`) para falhas de domínio SHALL viver na data layer de
   cada contexto (padrão de `FinanceApi.toFinanceGatewayFailure`);
   ViewModels/Coordinators de presentation SHALL não inspecionar
   `ApiProblemError` por status; a extensão `isProblem` duplicada SHALL ter
   fonte única.
3. **SHR-03** — WHEN qualquer tela de access exibe erro de autenticação THEN
   o mapeamento `AuthUiError`→string SHALL vir de um mapeador único e
   exaustivo (sem branch `else`); o mapper `NativeFailureCode`→`AuthUiError`
   duplicado entre coordinators SHALL ter fonte única.
4. **SHR-04** — WHEN e-mail ou nome são validados em qualquer fluxo de access
   THEN a regra SHALL vir de validadores centrais consumidos por coordinator e
   estados de UI; a regra de nome SHALL ser uniforme (2..80 chars, sem ISO
   control — ver Assumptions).

**Independent Test**: testes de formatação/validação centralizados + suites de
`:features:access`, `:features:groups` e `:core:common` verdes.

---

## Edge Cases

- WHEN um atleta (sem capacidade de organizador) dispara mutação financeira
  THEN o sistema SHALL continuar falhando com `Forbidden` (DUP-02 não pode
  alterar autorização).
- WHEN um link diferido é restaurado após process death THEN o state machine
  genérico SHALL reproduzir as transições de restore/retry/logout atuais.
- WHEN o corpo de erro excede o limite de payload THEN o pipeline único SHALL
  truncar exatamente como `toBoundedError` faz hoje (NET-02).
- WHEN um draft de despesa é limpo para um `expenseId` inexistente THEN o fix
  FIX-01 SHALL completar sem erro e sem afetar outros drafts.
- WHEN o app roda no iOS THEN os módulos Koin SHALL resolver sem APIs
  Android-only em commonMain (umbrella framework intacto — AD-001).
- WHEN o segundo grafo de rede é removido THEN o fluxo de game-detail SHALL
  continuar funcionando sobre o grafo primário, incluindo refresh de token.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| FIX-01..04 | P1: Bugs | Specify | Pending |
| FIX-05 (sequência isolada) | P1: Bugs | Specify | Pending |
| NAV-01..05 | P1: DI + hub | Specify | Pending |
| DUP-01..02 | P2: State machines | Specify | Pending |
| NET-01..03 | P2: NetworkClient | Specify | Pending |
| UI-01..03 | P2: GroupSetupScreen | Specify | Pending |
| SHR-01..04 | P2: Centralização | Specify | Pending |

**Coverage:** 21 total, 0 mapeados a tasks (fase Tasks pendente), 21 unmapped (esperado nesta fase)

---

## Success Criteria

- [ ] 4 testes de regressão novos (um por fix) falham sem o fix e passam com ele
- [ ] `rtk scripts/check-gradle` e suites `:compose-app:allTests`,
      `:features:groups:allTests`, `:features:access`, `:core:network`,
      `:core:common` verdes ao final de cada fase
- [ ] Zero instanciação de rede/gateways/coordinators em composables
      (verificação estrutural por busca)
- [ ] Corpos duplicados dos state machines/ViewModels financeiros eliminados
      (verificação estrutural por busca)
- [ ] Comportamento observável preservado (exceto FIX-01..04, formato BRL e
      regra de nome, conforme Assumptions)
