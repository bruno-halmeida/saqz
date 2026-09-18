# CONTRATO — redesenho do detalhe do grupo (leitura obrigatória de quem redige ou executa um ticket)

Repo: `/Users/bruno_almeida/orca/workspaces/saqz/main-2`. Módulo: `mobile/features/groups/presentation`.
Abreviações: `PRES = mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation` · `DET = PRES/ui/details` · `TEST = mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation` · `TDET = TEST/ui/details` · `SDET = mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/details` · `E2E = mobile/android-app/src/e2e/kotlin/br/com/saqz/androidapp`.

## 1. Ondas e donos (ninguém edita arquivo de outro dono)

| Onda | Tickets em paralelo |
|---|---|
| 1 | **S** strings · **A** backend (`ownAttendance` na listagem de jogos) · **B** extração das peças de UI da Início para `PRES/ui/components` · **T** andaime de `DET` (um arquivo por bloco) · **V1** contrato do hero e da cobrança (Contract + ViewModel) |
| 2 | **V2** contrato das listas (Contract + ViewModel + `Game.ownAttendance` no domain/data + DI + NavHost + e2e do texto do saldo) · **C1** hero + toast · **C2** topo + mural + galera/gestão + quadra + skeleton + sair · **C3** minhas cobranças |
| 3 | **C4** agenda · **C5** esperando você · **H** `HomeViewModel` passa a usar `PRES/game/GameLabels.kt` |
| 4 | **D** fecho |

Donos de arquivo depois da onda 1:

| Arquivo | Dono |
|---|---|
| `PRES/details/GroupDetailsContract.kt`, `PRES/details/GroupDetailsViewModel.kt`, `TEST/details/GroupDetailsViewModelTest.kt` | V1, depois V2 (sequenciais) |
| `DET/GroupHeroBlock.kt`, `DET/GroupToastBlock.kt`, `TDET/GroupHeroBlockTest.kt`, `E2E/AttendanceE2eTest.kt`, `E2E/AttendanceOrderE2eTest.kt` | C1 |
| `DET/GroupTopBlock.kt`, `DET/GroupMuralBlock.kt`, `DET/GroupPeopleBlock.kt`, `TDET/GroupShellBlocksTest.kt` | C2 |
| `DET/GroupOwnDebtBlock.kt`, `TDET/GroupOwnDebtBlockTest.kt`, `SDET/GroupOwnDebtScreenshotTest.kt` | C3 |
| `DET/GroupAgendaBlock.kt` | C4 |
| `DET/GroupWaitingBlock.kt`, `TDET/GroupWaitingBlockTest.kt` | C5 |
| `DET/GroupDetailsScreen.kt`, `DET/GroupDetailsPreviewData.kt`, `TDET/GroupDetailsScreenTest.kt`, `SDET/GroupDetailsScreenshotTest.kt`, `DET/GroupDetailsSections.kt`, `DET/GroupGameResponseSection.kt` | D (ninguém toca antes) |
| `DET/GroupDetailsTags.kt`, `TDET/GroupDetailsTestSupport.kt`, `SDET/GroupDetailsScreenshotSupport.kt`, `composeResources/values/strings_group_details.xml` | fechados: ninguém edita |
| `DET/GroupOwnChargesSection.kt` | ninguém edita (Perfil → Mensalidades reutiliza; e2e confere 12 linhas lá) |

Cada ticket de bloco cria os PRÓPRIOS arquivos novos de preview/captura: `DET/<Bloco>PreviewData.kt` (deriva de `GroupDetailsPreviewData` com `.copy(...)`) e `SDET/<Bloco>ScreenshotTest.kt` (usa `compose.captureDetails(name, state, directory)` de `GroupDetailsScreenshotSupport.kt`).

## 2. Composição final da tela (fixada pelo T em `DET/GroupDetailsScreen.kt` — NÃO editar)

```
Column(fillMaxSize, background, tag "group-details") {
  GroupTopBar(state, onBack, onIntent)                                   // C2
  when { isLoading -> GroupDetailsLoading()                              // C2
         loadFailed -> GroupLoadFailure(...)
         else -> Box { Column(verticalScroll, padding h=16 v=12, spacedBy(sectionGap=24), tag "group-details-content") {
             GroupTopContent(state, onIntent, photoFailed)               // C2  (ColumnScope)
             GroupHeroBlock(state, onIntent)                             // C1
             GroupOwnDebtBlock(state, onIntent)                          // C3
             GroupWaitingBlock(state, onIntent)                          // C5
             GroupAgendaBlock(state, onIntent)                           // C4
             GroupHomeCourtBlock(state, onIntent)                        // C2
             GroupMuralBlock(state, onIntent)                            // C2
             GroupPeopleBlock(state, onIntent)                           // C2
             GroupOwnChargesSettledBlock(state, onIntent)                // C3
             GroupLeaveBlock(state, onIntent)                            // C2
           }
           GroupToastBlock(state, onIntent, Modifier.align(BottomCenter)) // C1
         } }
}
GroupLeaveSheet(state, onIntent)
```

Assinatura de todo bloco: `@Composable internal fun Group<X>Block(state: GroupDetailsState, onIntent: (GroupDetailsIntent) -> Unit, modifier: Modifier = Modifier)`. **Bloco sem conteúdo não emite nada** (nem um `Column` vazio): é o que mantém o `spacedBy` sem buracos. Dentro de um bloco, o respiro é `metrics.blockGap` (12). Um bloco emite UM nó raiz (regra `MultipleEmitters` do compose-rules).

Os blocos vazios do andaime levam `@Suppress("UnusedParameter")` + KDoc "Andaime (T)": o ticket dono **remove o `@Suppress` e o KDoc de andaime** ao preencher o corpo.

## 3. Estado — o que cada ticket de UI pode ler

Já existe hoje em `GroupDetailsState` (ver `PRES/details/GroupDetailsContract.kt`): `isLoading, loadFailed, error, isAdmin, isOwner, header(name, subtitle, summaryChips, photoUrl), nextGame, attendance(confirmedCount, capacity, going, notGoing, pending, availableSpots), cashbox(summary), ownCharges(pending, history, pix, isLoading, failed), venue(name, address), latestNotice(author, authorIsAdmin, body, timestamp), memberPreview(id, name, meta, status), memberCount, scheduleSummary, memberResponse(status, waitlistPosition, memberId), responding, responseFailed, rosterStale, rosterRefreshing, membershipType, autoConfirmationVisible/Enabled/Updating/Failed, confirmingLeave, leaving, leaveFailed, mapFailed, notifying, notificationFailed, notifiedCount, onboarding, athleteIntroVisible, athleteShareFailed`.
`NextGameUi` hoje: `gameId, date, venue, deadline, confirmationDeadline, confirmedCount, capacity, confirmedNames, availableSpots, confirmationOpen, hasGameFee`.

**V1 acrescenta** (onda 1):
```kotlin
// GroupDetailsState
val waitlist: GroupWaitlistUi? = null        // != null ⇔ memberResponse?.status == Waitlisted
val toast: GroupDetailsToast? = null
val pixCopied: Boolean = false
// NextGameUi
val display: String = ""        // "Terça, 19h30"
val meta: String = ""           // "4 de agosto · CERET — Quadra 2"
val address: String = ""        // endereço DO JOGO; vazio esconde a linha e o mapa
val deadlineLine: String = ""   // frase do prazo ABERTO; encerrado, a UI usa game_response_deadline_closed
val deadlineShort: String = ""  // "Encerra 04/08 · 12h00"
val bellLabel: String = ""      // "Avisamos você se abrir vaga até 12h00 de 04/08."
@Immutable data class GroupWaitlistUi(val kind: HomeWaitlistKind, val rows: List<HomeWaitlistRowUi> = emptyList())
enum class GroupDetailsToast { Confirmed, Declined, Waitlisted, PixCopied }
// OwnChargesUi
val debt: GroupOwnDebtUi? = null             // != null ⇔ pending.isNotEmpty()
@Immutable data class GroupOwnDebtUi(val eyebrow: String, val totalLabel: String, val dueLabel: String,
    val overdue: Boolean, val countLabel: String? = null, val receiverLabel: String? = null)
// GroupDetailsIntent
data object DismissToast : GroupDetailsIntent
```
V1 também muda comportamento: `OpenVenueMap` abre o endereço do jogo (cai na quadra padrão sem jogo); `Respond(Confirm)` é ignorado para quem já está na fila; o otimista prevê a fila; `CopyPix` liga `pixCopied` por 2 s e `toast = PixCopied`; resposta com sucesso liga o toast correspondente.

**V2 acrescenta** (onda 2):
```kotlin
// GroupDetailsState
val agenda: List<GroupAgendaRowUi> = emptyList()   // próximos jogos do grupo SEM o jogo do hero; no máximo 12
val waiting: GroupWaitingUi? = null                // só gestor; null = nenhuma das três linhas
enum class GroupAgendaStatus { Pending, Going, Out, Waitlisted, Draft }
@Immutable data class GroupAgendaRowUi(val gameId: String, val day: String, val month: String, val title: String,
    val meta: String, val status: GroupAgendaStatus, val statusLabel: String, val contentDescription: String)
@Immutable data class GroupWaitingRowUi(val title: String, val meta: String, val contentDescription: String, val count: Int = 0)
@Immutable data class GroupSettleRowUi(val gameId: String, val title: String, val meta: String, val contentDescription: String)
@Immutable data class GroupWaitingUi(val entryRequests: GroupWaitingRowUi? = null, val monthly: GroupWaitingRowUi? = null,
    val settle: GroupSettleRowUi? = null)
// GroupDetailsIntent
data class OpenAgendaGame(val gameId: String) : GroupDetailsIntent   // emite o Effect OpenGame que já existe
data class OpenSettlement(val gameId: String) : GroupDetailsIntent   // emite o Effect OpenSettlement que já existe
```
V2 também PREENCHE campos que já existem e hoje ficam vazios em produção: `memberPreview` (os 4 primeiros do roster de atletas: `id = userId`, `name = displayName`, `meta = ""`, `status = null`), `memberCount` (tamanho do roster), `scheduleSummary` ("Terça e Quinta · 19h30") e troca o conteúdo de `cashbox.summary` para `"Saldo R$ 380,00"` (chave `group_details_cash_balance`). A linha de quórum do gestor NÃO tem campo próprio: a UI lê `attendance.pending`, `nextGame.confirmationOpen`, `nextGame.deadlineShort`, `notifying`, `notificationFailed`, `notifiedCount`.

Intents que já existem e continuam valendo: `Retry, CreateNextGame, EditGroup, NotifyPending, ManageMembers, ViewAllMembers, ManageSchedule, InviteByLink, ViewGame, OpenVenueMap, OpenNotices, OpenChat, OpenCashbox, Leave/ConfirmLeave/CancelLeave, RetryRoster, RetryOwnCharges, CopyPix, Respond(AttendanceIntent), ToggleAutoConfirmation(Boolean), OnboardingAction, DismissAthleteIntro, ShareSaqz`. Não usar mais na UI nova: `EditVenue`, `OpenSchedule` (atalho "Jogos" — dá 403 para atleta), `Invite` (convite do membro — endpoint só de admin), `ConfirmAttendance`.

## 4. Tags (todas já declaradas pelo T em `DET/GroupDetailsTags.kt` — usar, nunca criar)

Contrato do e2e (nome NÃO muda, e cada uma existe EXATAMENTE UMA vez na árvore): `group-details`, `group-details-leave`, `group-details-shortcut-chat`, `group-details-shortcut-notices`, `group-details-notify-pending`, `group-details-cashbox`, `group-details-manage-members`, `group-details-view-all-members` (o clicável é DESCENDENTE deste nó), `group-details-own-charge-<id>` (título, vencimento, valor e status são DESCENDENTES, árvore não mesclada), `group-details-own-charges-pix` (a chave Pix é um `Text` próprio cujo texto é EXATAMENTE a chave), `group-game-response-going`, `group-game-response-not-going`.
Objetos: `GroupDetailsTags` (Screen, Content, Skeleton, Toast, EditGroup, PhotoFailed, Hero, HeroMap, HeroMapFailure, HeroRosterStale, HeroRosterRetry, HeroFeeNote, CreateNextGame, HeroInvite, ViewGame, Venue, OwnCharges, OwnDebt, OwnChargesPending, OwnChargesHistory, OwnChargesHistoryToggle, OwnChargesSettled, OwnChargesPix, OwnChargesPixCopy, OwnChargesSkeleton, OwnChargesFailure, OwnChargesRetry, Waiting, WaitingQuorum, NotifyPending, NotifyFeedback, WaitingEntryRequests, WaitingMonthly, WaitingSettle, Agenda, AgendaCreate, AgendaMore, Mural, ShortcutNotices, ShortcutChat, Notice, People, ViewAllMembers, Invite, Manage, Cashbox, ManageMembers, ManageSchedule, ManageInviteLink, HomeCourt, HomeCourtMap, Leave, `ownCharge(id)`, `agendaGame(id)`) e `GroupGameResponseTags` (Section, Going, NotGoing, Change, Cancel, Error, AutoConfirmation).

## 5. Textos

Chaves novas: só as 32 de `strings_group_details.xml` (ticket S). Reuso: a tabela "O que é REUSO" do ticket S. **Nenhum ticket de tela cria, renomeia ou edita chave**; faltou texto = parar e avisar o orquestrador. Textos que são contrato do e2e e não mudam: "Sua presença está confirmada.", "Você não vai jogar.", "Lembrete enviado no Saqz para N pessoa(s).", os status de cobrança ("Em aberto", "Paga", "Isenta", "Cancelada") e a chave Pix.

## 6. Mock (referência visual) e como ler

- PNGs de todos os estados (2x, 390dp de largura): `mobile/features/groups/presentation/screenshots/_mock-grupo/png/<Estado>.png` — nomes em `_mock-grupo/build.mjs` (objeto `boards`). Membro: `Main, MembroConfirmado, MembroRespondendo, MembroNaoVou, MembroAlterando, MembroErroResposta, MembroEncerradas, MembroEncerradasSemResposta, MembroAvulsoTaxa, MembroListaDesatualizada, MembroMapaFalhou, MembroAutoFalha, MembroIntro, MembroSemJogo, MembroCarregando, MembroErro, MembroReserva, MembroListaAvulso, MembroDeve, MembroChaveCopiada, MembroDeveUma, MembroSemPix, MembroHistorico, MembroCobrancasCarregando, MembroNomeLongo, MembroCobrancasFalha`. Gestor: `GestorPendencias, GestorConfirmado, GestorAvisando, GestorAvisado, GestorAvisoFalhou, GestorEncerradas, GestorSemPendencias, GestorTambemDeve, GestorFinancasFalha, GestorSemJogo, GestorGrupoNovo, GestorOnboardingConvite, GestorOnboardingAcerto`.
- Fonte do mock: `_mock-grupo/build.mjs` — cada bloco é uma função JS com medidas exatas em px (= dp). Mapa mock → tokens: `.title`→`typography.title` · `.subtitle`→`subtitle` · `.body`→`body` · `.support`→`support` · `.label`→`label` · `.caption`→`caption` · `.eyebrow`→`eyebrow` · `.ctitle`→`compactTitle` · `.cmeta`→`compactMeta` · `.display`→`display`; `C.primary`→`colors.primary` · `C.accent`→`colors.accent` · `C.ink`→`textPrimary` · `C.sec`→`textSecondary` · `C.soft`→`surfaceSoft` · `C.surface`→`surface` · `C.border`→`border` · `C.success`→`success` · `C.warningFg`→`warningForeground` · `C.error`→`errorForeground`; raio 12→`metrics.cardRadius` · 20→`metrics.blockRadius` · 10→`metrics.inputRadius`; gap 12→`blockGap` · 24→`sectionGap` · 8→`grid` · 4→`subGrid` · 16→`horizontalPadding`.
- **Onde o PNG e a receita divergirem, vale a receita.** Onde o mock pede medida fora da grade, a regra da casa é `private val` nomeado no topo do arquivo (como `HomeScreen.kt` faz) — nunca `dp` cru solto no meio do código.

## 7. Regras de toda receita (o worker NÃO decide nada)

- Passo numerado por arquivo, na ordem de execução. Arquivo NOVO: conteúdo completo pronto para colar (package, imports reais, KDoc curto em PT-BR no estilo do módulo). Arquivo EDITADO: âncora literal (trecho antigo citado) + trecho novo completo.
- Proibido na receita: "ou", "se preferir", "confira se", "no padrão dos existentes", "algo como". Quem redige resolve tudo lendo o código e confere que o que escreveu compila contra os tipos reais (imports, tokens do `SaqzTheme`, `SaqzIcons.*`, `Res.string.*`, assinaturas exportadas por V1/V2/B/T).
- Seção Testes: arquivo, nome exato, arranjo e asserções literais de cada teste.
- Seção Cenas: lista fechada das capturas Roborazzi (uma por estado do bloco; "estado que não está na cena não está sendo conferido") e dos prints que vão no corpo do PR (branch órfã `screenshots`, pasta `vul-XXX/`).
- Compose: parâmetros sem default antes de `modifier: Modifier = Modifier`; `testTag` só via os objetos de tags; `contentDescription` significativo no interativo e `null` no decorativo; linha clicável com `clickable(onClickLabel = …, role = Role.Button)`; alvo de toque ≥ 48dp (`metrics.minimumTouchTarget`); estado puramente visual (expandir histórico, "ver mais") em `remember`, nunca no ViewModel; `Column` + `verticalScroll` (nunca `LazyColumn` nesta tela).
- Detekt: `MaxLineLength` 140; sem `@Suppress` novo (ÚNICA exceção: classe `*ScreenshotTest` em `androidHostTest` com 11+ funções leva `@Suppress("TooManyFunctions")`, igual ao `HomeScreenshotTest` — o detekt varre `androidHostTest`, não `commonTest`); sem baseline novo; regra densa fora do ViewModel em arquivo próprio com teste puro.
- Teto do PR: 2000 linhas de adições+remoções; estimar o diff na receita.

## 8. Estrutura obrigatória do Markdown de cada ticket

`# <Letra> · <Título>` → `**Onda N · depende de: … · bloqueia: … · paralelo com: …**` → `## Objetivo` → `## Fora do escopo` → `## Arquivos` (lista FECHADA + a frase "Tocar em arquivo fora desta lista = parar e avisar o orquestrador.") → `## Passo a passo` → `## Testes` → `## Cenas de screenshot e prints do PR` (tickets de UI) → `## Gates` → `## Critérios de aceite` → `## Protocolo do worker`.

Gates de um ticket mobile (rodar da raiz do worktree; acrescentar `--tests` do pacote tocado para o ciclo rápido):
```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
```
Ticket que toca `compose-app`: `JAVA_HOME=… mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test`. Ticket que toca `E2E/`: compilar o source set e2e (`JAVA_HOME=… mobile/gradlew -p mobile :android-app:compileDevDebugE2eKotlin` — confirmar o nome real da task com `mobile/gradlew -p mobile :android-app:tasks --all | grep -i e2e` ao redigir) e rodar os cenários citados com `node tests/e2e/android/run.mjs --serial <serial> --scenario <nome>` (ler `tests/e2e/android/README.md`).

Protocolo do worker (copiar literalmente, trocando o nome da branch e o título):
1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-<slug> origin/main` (XXX = número do ticket no Linear).
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): …`, `test(groups): …`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR. Ticket de UI: **abrir e olhar os PNGs gravados** antes de abrir o PR e embutir os prints obrigatórios.
4. PR contra `main`, aberto como ready (não draft), título `<tipo>(groups): <título> (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
