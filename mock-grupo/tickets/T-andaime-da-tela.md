# T · Andaime da tela — um arquivo por bloco, composição final, testes por dono

**Onda 1 · depende de: nada · bloqueia: C1, C2, C3, C4, C5 · paralelo com: S, A, B, V1**

## Objetivo

Preparar `ui/details/` para que os cinco tickets de UI (C1–C5) rodem **em paralelo sem dividir nenhum arquivo**. Este PR não redesenha nada: ele reagrupa as peças que já existem na ordem final da tela e dá a cada bloco um arquivo com um único dono.

Depois deste PR:

1. `GroupDetailsScreen.kt` contém **só a composição** (a ordem dos blocos) e **nenhum ticket posterior toca nele** até o fecho (D).
2. Cada bloco é um composable `Group<Bloco>Block(state, onIntent, modifier)` no próprio arquivo. Bloco sem conteúdo **não emite nada** (é o que mantém o `spacedBy` sem buracos).
3. As tags (`GroupDetailsTags`, `GroupGameResponseTags`) moram em `GroupDetailsTags.kt`, já com **todas** as tags novas dos tickets seguintes.
4. `GroupDetailsPreviewData` mora em arquivo próprio e só descreve **estados que o ViewModel produz** (hoje `admin` tem `attendance` sem `nextGame`, combinação que o ViewModel nunca emite).
5. Os testes de tela e de captura são divididos por dono, no mesmo recorte dos blocos.

Mudança visual deste PR (esperada, vai no corpo do PR com print): ordem dos blocos, respiro de 24 entre blocos e 12 dentro, e o botão "Avisar quem falta confirmar" sai do card de contadores e vira um botão solto logo abaixo do bloco do jogo.

## Fora do escopo

- Nenhum componente novo, nenhuma string nova, nenhum campo de estado novo (o V1 roda em paralelo: **não usar** `state.waitlist`, `state.toast`, `state.pixCopied`, `nextGame.display` etc.).
- Não apagar nada de `GroupDetailsSections.kt` nem `GroupOwnChargesSection.kt`: os blocos continuam chamando essas peças. Quem apaga é o D.
- Não tocar em `details/` (Contract/ViewModel), `ui/home/`, `ui/components/`, `composeResources/`, e2e.

## Dono de cada arquivo depois deste PR

| Arquivo | Dono | Conteúdo na onda 1 |
|---|---|---|
| `GroupDetailsScreen.kt` | D | só a composição |
| `GroupDetailsTags.kt` | — (fechado; ninguém edita) | todas as tags |
| `GroupDetailsPreviewData.kt` | D | estados-base alcançáveis |
| `GroupTopBlock.kt` | **C2** | top bar, loading e `GroupTopContent` (banner da foto + cabeçalho antigo) |
| `GroupHeroBlock.kt` | **C1** | onboarding + card do jogo + resposta + intro + contadores + quadra |
| `GroupToastBlock.kt` | **C1** | vazio |
| `GroupOwnDebtBlock.kt` | **C3** | seção antiga de cobranças no topo; bloco "em dia" vazio |
| `GroupWaitingBlock.kt` | **C5** | botão "Avisar" + retorno |
| `GroupAgendaBlock.kt` | **C4** | vazio |
| `GroupMuralBlock.kt` | **C2** | atalhos + aviso recente |
| `GroupPeopleBlock.kt` | **C2** | membros/convite (membro), caixa/gerenciar (admin), quadra sem jogo (vazio), sair |

## Arquivos

Base: `DET = mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details`, `TDET = mobile/features/groups/presentation/src/commonTest/kotlin/br/com/saqz/groups/presentation/ui/details`, `SDET = mobile/features/groups/presentation/src/androidHostTest/kotlin/br/com/saqz/groups/presentation/ui/details`.

| Ação | Arquivo |
|---|---|
| criar | `DET/GroupDetailsTags.kt` |
| criar | `DET/GroupDetailsPreviewData.kt` |
| criar | `DET/GroupTopBlock.kt` |
| criar | `DET/GroupHeroBlock.kt` |
| criar | `DET/GroupToastBlock.kt` |
| criar | `DET/GroupOwnDebtBlock.kt` |
| criar | `DET/GroupWaitingBlock.kt` |
| criar | `DET/GroupAgendaBlock.kt` |
| criar | `DET/GroupMuralBlock.kt` |
| criar | `DET/GroupPeopleBlock.kt` |
| editar | `DET/GroupDetailsScreen.kt` (reescrito) |
| editar | `DET/GroupGameResponseSection.kt` (só remover o `object GroupGameResponseTags`) |
| criar | `TDET/GroupDetailsTestSupport.kt` |
| criar | `TDET/GroupHeroBlockTest.kt` |
| criar | `TDET/GroupOwnDebtBlockTest.kt` |
| criar | `TDET/GroupShellBlocksTest.kt` |
| criar | `TDET/GroupWaitingBlockTest.kt` |
| editar | `TDET/GroupDetailsScreenTest.kt` (reescrito, fica com 2 testes) |
| criar | `SDET/GroupDetailsScreenshotSupport.kt` |
| criar | `SDET/GroupOwnDebtScreenshotTest.kt` |
| editar | `SDET/GroupDetailsScreenshotTest.kt` |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Passo a passo

### 1. Criar `DET/GroupDetailsTags.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

/**
 * Todas as tags do detalhe do grupo. Várias são CONTRATO do e2e Android
 * (`mobile/android-app/src/e2e`): `group-details`, `-leave`, `-shortcut-chat`,
 * `-shortcut-notices`, `-notify-pending`, `-cashbox`, `-manage-members`,
 * `-view-all-members`, `-own-charge-<id>`, `-own-charges-pix` e as duas
 * `group-game-response-going` / `-not-going`. Renomear qualquer uma quebra um cenário que
 * não roda no gate do PR — e o harness exige EXATAMENTE um nó por tag na árvore.
 */
internal object GroupDetailsTags {
    const val Screen = "group-details"
    const val Content = "group-details-content"
    const val Skeleton = "group-details-skeleton"
    const val Toast = "group-details-toast"

    // topo
    const val EditGroup = "group-details-edit-group"
    const val PhotoFailed = "group-details-photo-failed"

    // hero
    const val Hero = "group-details-hero"
    const val HeroMap = "group-details-hero-map"
    const val HeroMapFailure = "group-details-hero-map-failure"
    const val HeroRosterStale = "group-details-hero-roster-stale"
    const val HeroRosterRetry = "group-details-hero-roster-retry"
    const val HeroFeeNote = "group-details-hero-fee-note"
    const val CreateNextGame = "group-details-create-next-game"
    const val HeroInvite = "group-details-hero-invite"
    const val ViewGame = "group-details-view-game"
    const val ConfirmAttendance = "group-details-confirm-attendance"
    const val Venue = "group-details-venue"

    // minhas cobranças
    const val OwnCharges = "group-details-own-charges"
    const val OwnDebt = "group-details-own-debt"
    const val OwnChargesPending = "group-details-own-charges-pending"
    const val OwnChargesHistory = "group-details-own-charges-history"
    const val OwnChargesHistoryToggle = "group-details-own-charges-history-toggle"
    const val OwnChargesSettled = "group-details-own-charges-settled"
    const val OwnChargesPix = "group-details-own-charges-pix"
    const val OwnChargesPixCopy = "group-details-own-charges-pix-copy"
    const val OwnChargesSkeleton = "group-details-own-charges-skeleton"
    const val OwnChargesFailure = "group-details-own-charges-failure"
    const val OwnChargesRetry = "group-details-own-charges-retry"

    // esperando você
    const val Waiting = "group-details-waiting"
    const val WaitingQuorum = "group-details-waiting-quorum"
    const val NotifyPending = "group-details-notify-pending"
    const val NotifyFeedback = "group-details-notify-feedback"
    const val WaitingEntryRequests = "group-details-waiting-entry-requests"
    const val WaitingMonthly = "group-details-waiting-monthly"
    const val WaitingSettle = "group-details-waiting-settle"

    // agenda
    const val Agenda = "group-details-agenda"
    const val AgendaCreate = "group-details-agenda-create"
    const val AgendaMore = "group-details-agenda-more"

    // mural
    const val Mural = "group-details-mural"
    const val ShortcutNotices = "group-details-shortcut-notices"
    const val ShortcutCashbox = "group-details-shortcut-cashbox"
    const val ShortcutSchedule = "group-details-shortcut-schedule"
    const val ShortcutChat = "group-details-shortcut-chat"
    const val Notice = "group-details-notice"

    // pessoas e gestão
    const val People = "group-details-people"
    const val ViewAllMembers = "group-details-view-all-members"
    const val Invite = "group-details-invite"
    const val Manage = "group-details-manage"
    const val Cashbox = "group-details-cashbox"
    const val ManageMembers = "group-details-manage-members"
    const val ManageSchedule = "group-details-manage-schedule"
    const val ManageInviteLink = "group-details-manage-invite-link"
    const val HomeCourt = "group-details-home-court"
    const val HomeCourtMap = "group-details-home-court-map"
    const val Leave = "group-details-leave"

    fun ownCharge(chargeId: String) = "group-details-own-charge-$chargeId"

    fun agendaGame(gameId: String) = "group-details-agenda-game-$gameId"
}

internal object GroupGameResponseTags {
    const val Section = "group-game-response-section"
    const val Going = "group-game-response-going"
    const val NotGoing = "group-game-response-not-going"
    const val Change = "group-game-response-change"
    const val Cancel = "group-game-response-cancel"
    const val Error = "group-game-response-error"
    const val AutoConfirmation = "group-game-response-auto-confirmation"
}
```

### 2. Editar `DET/GroupGameResponseSection.kt`

Apagar o bloco inteiro (agora mora em `GroupDetailsTags.kt`):

```kotlin
internal object GroupGameResponseTags {
    const val Section = "group-game-response-section"
    const val Going = "group-game-response-going"
    const val NotGoing = "group-game-response-not-going"
    const val AutoConfirmation = "group-game-response-auto-confirmation"
}
```

e a linha em branco que sobra logo depois dele. Nada mais muda nesse arquivo.

### 3. Criar `DET/GroupDetailsPreviewData.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.details.AttendanceSummaryUi
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupHeaderUi
import br.com.saqz.groups.presentation.details.GroupSummaryChipUi
import br.com.saqz.groups.presentation.details.MemberPreviewUi
import br.com.saqz.groups.presentation.details.MemberStatusUi
import br.com.saqz.groups.presentation.details.NextGameUi
import br.com.saqz.groups.presentation.details.NoticeUi
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.details.VenueUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi

/**
 * Os estados-base das previews e das capturas. Regra (AGENTS.md §11): só entra aqui o que o
 * `GroupDetailsViewModel` produz — com jogo, `nextGame` e `attendance` chegam JUNTOS; sem
 * jogo, os dois são `null`. Os estados de cada bloco derivam daqui com `.copy(...)` no
 * arquivo de preview do próprio bloco, nunca neste.
 */
internal object GroupDetailsPreviewData {
    val header = GroupHeaderUi(
        name = "Vôlei do CERET",
        subtitle = "Tatuapé · Misto · Intermediário",
    )
    val memberHeader = header.copy(
        summaryChips = listOf(
            GroupSummaryChipUi("Vôlei de quadra"),
            GroupSummaryChipUi("Terças e quintas", highlighted = true),
        ),
    )
    val venue = VenueUi(name = "CERET — Quadra 2", address = "R. Canuto Abreu, s/n · Tatuapé")

    val nextGame = NextGameUi(
        gameId = "game-1",
        date = "Ter, 28/07 · 19h30",
        venue = "CERET — Quadra 2 · Tatuapé",
        deadline = "Encerra hoje · 18h",
        confirmedCount = 9,
        capacity = 12,
        confirmedNames = listOf(
            "Lucas Prado",
            "Bia Souza",
            "Thiago Melo",
            "Ana Lima",
            "Caio Reis",
            "Duda Nunes",
            "Eva Rocha",
            "Fábio Sá",
            "Gil Matos",
        ),
        availableSpots = 3,
    )

    val attendance = AttendanceSummaryUi(
        confirmedCount = 9,
        capacity = 12,
        going = 9,
        notGoing = 1,
        pending = 2,
        availableSpots = 3,
    )

    // VUL-203 — uma pendente vencida, uma a vencer e o histórico com os três desfechos.
    val ownCharges = OwnChargesUi(
        pending = listOf(
            OwnChargeUi(
                id = "c-1",
                title = "Mensalidade · Agosto",
                dueLabel = "Venceu em 10/08",
                amountLabel = "R$ 70,00",
                status = OwnChargeStatusUi.Pending,
            ),
            OwnChargeUi(
                id = "c-2",
                title = "Jogo avulso",
                dueLabel = "Vence em 28/08",
                amountLabel = "R$ 25,00",
                status = OwnChargeStatusUi.Pending,
            ),
        ),
        history = listOf(
            OwnChargeUi(
                id = "c-3",
                title = "Mensalidade · Julho",
                dueLabel = "Vencimento 10/07",
                amountLabel = "R$ 70,00",
                status = OwnChargeStatusUi.Paid,
            ),
            OwnChargeUi(
                id = "c-4",
                title = "Mensalidade · Junho",
                dueLabel = "Vencimento 10/06",
                amountLabel = "R$ 70,00",
                status = OwnChargeStatusUi.Waived,
            ),
            OwnChargeUi(
                id = "c-5",
                title = "Jogo avulso",
                dueLabel = "Vencimento 03/06",
                amountLabel = "R$ 25,00",
                status = OwnChargeStatusUi.Cancelled,
            ),
        ),
        pix = PixUi(key = "ceret@volei.com.br", label = "Lucas Prado"),
    )

    /** Dono COM jogo marcado e ainda sem resposta: o que o gestor real vê. */
    val admin = GroupDetailsState(
        isLoading = false,
        isAdmin = true,
        isOwner = true,
        header = header,
        nextGame = nextGame,
        attendance = attendance,
        cashbox = CashboxUi(summary = "Saldo R$ 380,00 · 8 mensalidades em aberto"),
        venue = venue,
        memberCount = 26,
        scheduleSummary = "Ter, Qui",
    )

    /** Dono sem jogo marcado: `nextGame` e `attendance` somem juntos, como no ViewModel. */
    val adminNoGame = admin.copy(nextGame = null, attendance = null)

    val member = GroupDetailsState(
        isLoading = false,
        isAdmin = false,
        header = memberHeader,
        nextGame = nextGame,
        attendance = attendance,
        memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Confirmed),
        membershipType = AthleteMembershipType.MENSALISTA,
        autoConfirmationVisible = true,
        autoConfirmationEnabled = true,
        venue = venue,
        latestNotice = NoticeUi(
            author = "Lucas",
            authorIsAdmin = true,
            body = "Cheguem 15 min antes para montar a rede.",
            timestamp = "Hoje, 10h30",
        ),
        memberPreview = listOf(
            MemberPreviewUi("1", "Lucas Prado", "Organizador · levantador", MemberStatusUi.Admin),
            MemberPreviewUi("2", "Bia Souza", "Ponteira", MemberStatusUi.Going),
            MemberPreviewUi("3", "Thiago Melo", "Central"),
        ),
        memberCount = 26,
        ownCharges = ownCharges,
    )

    val memberNoGame = member.copy(
        nextGame = null,
        attendance = null,
        memberResponse = null,
        autoConfirmationVisible = false,
    )

    val memberOwnChargesLoading = member.copy(ownCharges = OwnChargesUi(isLoading = true))

    val memberOwnChargesFailed = member.copy(ownCharges = OwnChargesUi(failed = true))

    /** Sem pendência não há Pix: a seção fica só com o histórico. */
    val memberOwnChargesSettled = member.copy(
        ownCharges = ownCharges.copy(pending = emptyList(), pix = null),
    )
}
```

(O chip `"26 membros"` saiu do `memberHeader`: o ViewModel nunca emite chip de contagem.)

### 4. Criar `DET/GroupTopBlock.kt` (arquivo completo) — dono: C2

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_created_photo_failed
import br.com.saqz.groups.resources.group_details_created_photo_failed_title
import org.jetbrains.compose.resources.stringResource

/** Andaime (T): hoje só o título; o C2 põe "Editar" no slot de ações e passa a usar [onIntent]. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupTopBar(
    state: GroupDetailsState,
    onBack: () -> Unit,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzTopAppBar(modifier = modifier, title = state.header?.name, onBack = onBack)
}

/** Andaime (T): o spinner de hoje; o C2 troca pelo skeleton que espelha o layout. */
@Composable
internal fun GroupDetailsLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SaqzSpinner()
    }
}

/**
 * Andaime (T): o que vem no topo da coluna rolável — o banner da foto (só no pós-criação) e o
 * card de cabeçalho antigo. O C2 apaga o card: o nome fica só na barra. Emite os filhos direto
 * na coluna da tela (sem contêiner), para o respiro entre eles ser o mesmo dos outros blocos —
 * por isso é extensão de `ColumnScope`: é o que o compose-rules exige de quem emite mais de um nó.
 */
@Composable
internal fun ColumnScope.GroupTopContent(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    photoFailed: Boolean,
) {
    if (photoFailed) GroupPhotoFailedBanner()
    state.header?.let { GroupHeaderCard(header = it, isAdmin = state.isAdmin, onIntent = onIntent) }
}

@Composable
private fun GroupPhotoFailedBanner(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    SaqzCard(modifier = modifier.testTag(GroupDetailsTags.PhotoFailed)) {
        Text(
            text = stringResource(Res.string.group_details_created_photo_failed_title),
            color = colors.textPrimary,
            style = SaqzTheme.typography.body,
        )
        Text(
            text = stringResource(Res.string.group_details_created_photo_failed),
            color = colors.textSecondary,
            style = SaqzTheme.typography.support,
        )
    }
}
```

### 5. Criar `DET/GroupHeroBlock.kt` (arquivo completo) — dono: C1

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.VenueUi
import br.com.saqz.groups.presentation.ui.components.GroupVenueRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_map_failure
import br.com.saqz.groups.resources.group_details_venue_edit
import br.com.saqz.groups.resources.group_details_venue_map
import org.jetbrains.compose.resources.stringResource

/**
 * Andaime (T): tudo que é do próximo jogo, ainda com as peças antigas — guia do gestor, card do
 * jogo, "Você vai jogar?", intro do atleta, contadores e a quadra. O C1 troca o corpo inteiro
 * pelo hero azul.
 *
 * Os contadores entram com `isAdmin = false` de propósito: o botão "Avisar quem falta
 * confirmar" mora no [GroupWaitingBlock], para o C1 e o C5 não dividirem arquivo.
 */
@Composable
internal fun GroupHeroBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasContent = state.nextGame != null || state.venue != null || (state.isAdmin && state.onboarding != null)
    if (!hasContent) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        if (state.isAdmin) state.onboarding?.let { GroupOnboardingCard(it, onIntent) }
        state.nextGame?.let { GroupNextGameCard(nextGame = it, onIntent = onIntent) }
        // Dono e admin respondem presença no mesmo lugar que o atleta: o papel
        // administrativo muda o que ele gerencia, não o fato de que ele joga.
        if (state.nextGame != null) GroupGameResponseSection(state = state, onIntent = onIntent)
        if (state.athleteIntroVisible && !state.isAdmin && !state.responding) {
            AthleteOnboardingCard(state.athleteShareFailed, onIntent)
        }
        state.attendance?.let { GroupAttendanceStats(attendance = it, isAdmin = false, onIntent = onIntent) }
        state.venue?.let { GroupVenueCard(venue = it, isAdmin = state.isAdmin, onIntent = onIntent) }
        if (state.mapFailed) {
            Text(stringResource(Res.string.group_details_map_failure), color = SaqzTheme.colors.textPrimary)
        }
    }
}

/**
 * A quadra: o `GroupVenueRow` do VUL-66 dentro do card branco do export. A ação é a única
 * diferença entre as duas visões — "Ver no mapa" no `2e`, "Editar" no `2f`.
 */
@Composable
private fun GroupVenueCard(
    venue: VenueUi,
    isAdmin: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
) = SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.Venue)) {
    GroupVenueRow(
        name = venue.name,
        address = venue.address,
        actionLabel = stringResource(
            if (isAdmin) Res.string.group_details_venue_edit else Res.string.group_details_venue_map,
        ),
        onAction = {
            onIntent(if (isAdmin) GroupDetailsIntent.EditVenue else GroupDetailsIntent.OpenVenueMap)
        },
    )
}
```

### 6. Criar `DET/GroupToastBlock.kt` (arquivo completo) — dono: C1

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/** Andaime (T): o toast chega no ticket C1, junto com `state.toast` do V1. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupToastBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
```

### 7. Criar `DET/GroupOwnDebtBlock.kt` (arquivo completo) — dono: C3

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/**
 * Andaime (T): a seção antiga inteira (pendentes + histórico + Pix), já na posição final —
 * logo abaixo do jogo. O C3 troca pelo ticket enxuto. `GroupOwnChargesSection` continua
 * existindo: a tela Perfil → Mensalidades a reutiliza.
 */
@Composable
internal fun GroupOwnDebtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownCharges = state.ownCharges ?: return
    GroupOwnChargesSection(ownCharges = ownCharges, onIntent = onIntent, modifier = modifier)
}

/** Andaime (T): a linha "Tudo em dia" do fim da tela chega no ticket C3. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupOwnChargesSettledBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
```

(Fato do código: `GroupOwnChargesSection(ownCharges: OwnChargesUi, onIntent: (GroupDetailsIntent) -> Unit, modifier: Modifier = Modifier)` — `DET/GroupOwnChargesSection.kt:51-55`. Esse arquivo NÃO é editado.)

### 8. Criar `DET/GroupWaitingBlock.kt` (arquivo completo) — dono: C5

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.communication_failure
import br.com.saqz.groups.resources.communication_reminded
import br.com.saqz.groups.resources.group_details_notify_pending
import org.jetbrains.compose.resources.stringResource

/**
 * Andaime (T): o botão "Avisar quem falta confirmar" e o retorno dele, fora do card de
 * contadores. O C5 troca o corpo pelo bloco "Esperando você". O texto de sucesso
 * (`communication_reminded`) é contrato do e2e e precisa continuar na árvore.
 */
@Composable
internal fun GroupWaitingBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isAdmin || state.nextGame == null) return
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Waiting),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzButton(
            label = stringResource(Res.string.group_details_notify_pending),
            onClick = { onIntent(GroupDetailsIntent.NotifyPending) },
            modifier = Modifier.testTag(GroupDetailsTags.NotifyPending),
            variant = SaqzButtonVariant.Ghost,
            fullWidth = true,
            leadingContent = { tint -> SaqzIcon(SaqzIcons.Megaphone, tint = tint) },
        )
        if (state.notifying) SaqzSpinner()
        if (state.notificationFailed) Text(stringResource(Res.string.communication_failure))
        state.notifiedCount?.let { Text(stringResource(Res.string.communication_reminded, it)) }
    }
}
```

### 9. Criar `DET/GroupAgendaBlock.kt` (arquivo completo) — dono: C4

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/** Andaime (T): "Próximos jogos" chega no ticket C4, junto com `state.agenda` do V2. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupAgendaBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
```

### 10. Criar `DET/GroupMuralBlock.kt` (arquivo completo) — dono: C2

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/** Andaime (T): os três atalhos e o aviso recente. O C2 funde tudo em duas linhas. */
@Composable
internal fun GroupMuralBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Mural),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        GroupShortcutTiles(onIntent = onIntent)
        state.latestNotice?.let { GroupLatestNoticeCard(notice = it) }
    }
}
```

### 11. Criar `DET/GroupPeopleBlock.kt` (arquivo completo) — dono: C2

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/**
 * Andaime (T): membro vê a prévia de membros e o convite; gestor vê o caixa e "Gerenciar". O
 * C2 troca por "Galera" (membro) e "Gestão" (gestor). A linha do caixa fica AQUI, e não no
 * bloco "Esperando você", porque a tag `group-details-cashbox` só pode existir uma vez na
 * árvore e o destino final dela é a lista de gestão.
 */
@Composable
internal fun GroupPeopleBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.People),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        if (state.isAdmin) {
            state.cashbox?.let { GroupCashboxRow(cashbox = it, onIntent = onIntent) }
            GroupManageList(
                memberCount = state.memberCount,
                scheduleSummary = state.scheduleSummary,
                onIntent = onIntent,
            )
        } else {
            GroupMemberPreview(members = state.memberPreview, onIntent = onIntent)
            GroupInviteCard(onIntent = onIntent)
        }
    }
}

/** Andaime (T): a quadra padrão, quando não há jogo marcado, chega no ticket C2. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupHomeCourtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit

/** Dono não sai do grupo: para ele o bloco não emite nada. */
@Composable
internal fun GroupLeaveBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isOwner) return
    GroupLeaveButton(onIntent = onIntent, modifier = modifier)
}
```

### 12. Reescrever `DET/GroupDetailsScreen.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.GroupLoadFailure

/**
 * A tela só empilha, e cada bloco mora no próprio arquivo, com um único dono:
 * topo · jogo · cobrança em aberto · esperando você · agenda · quadra · mural · pessoas ·
 * cobranças em dia · sair. Bloco sem conteúdo não emite nada — é o que mantém o `spacedBy`
 * sem buracos. O contêiner é `Column` + `verticalScroll` de propósito: o e2e rola até os
 * nós com `performScrollTo`, que exige todos compostos (com `LazyColumn` ele quebra).
 */
@Composable
internal fun GroupDetailsScreen(
    state: GroupDetailsState,
    onBack: () -> Unit,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
    photoFailed: Boolean = false,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(GroupDetailsTags.Screen),
    ) {
        GroupTopBar(state = state, onBack = onBack, onIntent = onIntent)
        when {
            state.isLoading -> GroupDetailsLoading()
            state.loadFailed -> GroupLoadFailure(error = state.error, onRetry = { onIntent(GroupDetailsIntent.Retry) })
            else -> Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
                        .testTag(GroupDetailsTags.Content),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {
                    GroupTopContent(state = state, onIntent = onIntent, photoFailed = photoFailed)
                    GroupHeroBlock(state = state, onIntent = onIntent)
                    GroupOwnDebtBlock(state = state, onIntent = onIntent)
                    GroupWaitingBlock(state = state, onIntent = onIntent)
                    GroupAgendaBlock(state = state, onIntent = onIntent)
                    GroupHomeCourtBlock(state = state, onIntent = onIntent)
                    GroupMuralBlock(state = state, onIntent = onIntent)
                    GroupPeopleBlock(state = state, onIntent = onIntent)
                    GroupOwnChargesSettledBlock(state = state, onIntent = onIntent)
                    GroupLeaveBlock(state = state, onIntent = onIntent)
                }
                GroupToastBlock(
                    state = state,
                    onIntent = onIntent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
    GroupLeaveSheet(state = state, onIntent = onIntent)
}

@Preview
@Composable
private fun GroupDetailsAdminPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsPreviewData.admin, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupDetailsMemberPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsPreviewData.member, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupDetailsLoadingPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsState(), onBack = {}, onIntent = {})
}
```

### 13. Testes de tela — criar `TDET/GroupDetailsTestSupport.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/** A montagem única dos testes de bloco: a tela inteira, com o tema, sem DI. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.setDetailsScreen(
    state: GroupDetailsState,
    photoFailed: Boolean = false,
    onIntent: (GroupDetailsIntent) -> Unit = {},
) = setContent {
    SaqzTheme {
        GroupDetailsScreen(
            state = state,
            onBack = {},
            onIntent = onIntent,
            photoFailed = photoFailed,
        )
    }
}
```

### 14. Reescrever `TDET/GroupDetailsScreenTest.kt` (arquivo completo) — dono: D

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsState
import kotlin.test.Test

/** Só a composição. O que é de cada bloco mora no teste do bloco. */
@OptIn(ExperimentalTestApi::class)
class GroupDetailsScreenTest {
    @Test
    fun loadingShowsNoSectionAtAll() = runComposeUiTest {
        setDetailsScreen(GroupDetailsState())

        onNodeWithTag(GroupDetailsTags.Screen).assertExists()
        onAllNodesWithTag(GroupDetailsTags.Content).assertCountEquals(0)
    }

    @Test
    fun loadedScreenComposesTheScrollableContent() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

        onNodeWithTag(GroupDetailsTags.Content).assertExists()
        onNodeWithTag(GroupDetailsTags.Mural).assertExists()
        onNodeWithTag(GroupDetailsTags.People).assertExists()
    }
}
```

### 15. Criar `TDET/GroupHeroBlockTest.kt` (arquivo completo) — dono: C1

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupHeroBlockTest {
    // O seletor de presença não é de nenhuma das duas visões em particular: é de quem joga —
    // e dono e admin também jogam.
    @Test
    fun adminWithNextGameStillGetsTheAttendanceSelector() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin)

        onNodeWithTag(GroupGameResponseTags.Section).assertExists()
        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertExists()
        onNodeWithTag(GroupDetailsTags.ViewGame).assertExists()
    }

    @Test
    fun withoutNextGameThereIsNoSelector() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.memberNoGame)

        onNodeWithTag(GroupGameResponseTags.Section).assertDoesNotExist()
        onNodeWithTag(GroupDetailsTags.ViewGame).assertDoesNotExist()
    }

    @Test
    fun venueActionFollowsTheView() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithText("Editar").performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.EditVenue, intents.single())
    }

    @Test
    fun memberVenueActionOpensTheMap() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithText("Ver no mapa").performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.OpenVenueMap, intents.single())
    }

    @Test
    fun `member response is shown in the group detail`() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithTag(GroupGameResponseTags.Going).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Confirm), intents.single())
        onNodeWithText("Sua presença está confirmada.").assertExists()
        onAllNodesWithText("Talvez").assertCountEquals(0)
    }

    @Test
    fun `group response shows waitlist position and locks after deadline`() = runComposeUiTest {
        setDetailsScreen(
            GroupDetailsPreviewData.member.copy(
                memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Waitlisted, 3),
                membershipType = AthleteMembershipType.AVULSO,
                autoConfirmationVisible = false,
                nextGame = GroupDetailsPreviewData.nextGame.copy(confirmationOpen = false, hasGameFee = true),
            ),
        )

        onNodeWithText("Você está em 3º na lista de espera.").assertExists()
        onNodeWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertExists()
        onNodeWithText("As confirmações estão encerradas.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertExists()
    }

    @Test
    fun `day-member fee notice is hidden when the next game has no fee`() = runComposeUiTest {
        setDetailsScreen(
            GroupDetailsPreviewData.member.copy(
                membershipType = AthleteMembershipType.AVULSO,
                nextGame = GroupDetailsPreviewData.nextGame.copy(hasGameFee = false),
            ),
        )

        onAllNodesWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertCountEquals(0)
    }
}
```

### 16. Criar `TDET/GroupOwnDebtBlockTest.kt` (arquivo completo) — dono: C3

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupOwnDebtBlockTest {
    @Test
    fun ownChargesShowPendingFirstHistoryBelowAndThePixToPay() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

        onNodeWithTag(GroupDetailsTags.OwnCharges).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPending).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesHistory).assertExists()
        onNodeWithTag(GroupDetailsTags.ownCharge("c-1")).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPix).assertExists()
        onNodeWithText("Venceu em 10/08").assertExists()
        onNodeWithText("Paga").assertExists()
    }

    @Test
    fun ownChargesWithoutPendingHideThePixCard() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.memberOwnChargesSettled)

        onNodeWithTag(GroupDetailsTags.OwnChargesHistory).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPending).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPix).assertCountEquals(0)
    }

    @Test
    fun ownChargesCopyAsksForThePixKey() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithTag(GroupDetailsTags.OwnChargesPixCopy).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.CopyPix, intents.single())
    }

    // A seção falha sozinha: o resto do detalhe continua na tela, com retry só dela.
    @Test
    fun ownChargesFailureKeepsTheScreenAndOffersRetry() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.memberOwnChargesFailed) { intents += it }

        onNodeWithTag(GroupDetailsTags.Mural).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesFailure).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesRetry).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.RetryOwnCharges, intents.single())
    }

    @Test
    fun ownChargesShowASkeletonWhileLoading() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.memberOwnChargesLoading)

        onNodeWithTag(GroupDetailsTags.OwnChargesSkeleton).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPending).assertCountEquals(0)
    }

    @Test
    fun memberWithoutChargesHasNoOwnChargesSection() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member.copy(ownCharges = null))

        onAllNodesWithTag(GroupDetailsTags.OwnCharges).assertCountEquals(0)
    }
}
```

### 17. Criar `TDET/GroupShellBlocksTest.kt` (arquivo completo) — dono: C2

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Topo, mural, pessoas/gestão e sair: os blocos do ticket C2. */
@OptIn(ExperimentalTestApi::class)
class GroupShellBlocksTest {
    private val adminOnly = listOf(
        GroupDetailsTags.CreateNextGame,
        GroupDetailsTags.EditGroup,
        GroupDetailsTags.Cashbox,
        GroupDetailsTags.ManageMembers,
        GroupDetailsTags.ManageSchedule,
        GroupDetailsTags.ManageInviteLink,
    )

    private val memberOnly = listOf(
        GroupDetailsTags.ViewAllMembers,
        GroupDetailsTags.Invite,
    )

    @Test
    fun adminViewHasNoMemberSection() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin)

        adminOnly.forEach { onNodeWithTag(it).assertExists() }
        memberOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun memberViewHasNoAdminSection() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

        memberOnly.forEach { onNodeWithTag(it).assertExists() }
        adminOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
        onNodeWithTag(GroupDetailsTags.Notice).assertExists()
    }

    @Test
    fun ownerCannotLeaveButAdminAndAthleteCan() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(isOwner = true))
        onAllNodesWithTag(GroupDetailsTags.Leave).assertCountEquals(0)
    }

    @Test
    fun adminCanRequestDeparture() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(isOwner = false)) { intents += it }
        onNodeWithTag(GroupDetailsTags.Leave).performScrollTo().performClick()
        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.Leave), intents)
    }

    @Test
    fun memberWithoutPreviewStillOpensMembers() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member.copy(memberPreview = emptyList())) { intents += it }

        onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(GroupDetailsTags.ViewAllMembers)),
        ).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.ViewAllMembers), intents)
    }

    @Test
    fun adminWithoutPreviewStillOpensManageMembers() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(memberPreview = emptyList())) { intents += it }

        onNodeWithTag(GroupDetailsTags.ManageMembers).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.ManageMembers), intents)
        onAllNodesWithTag(GroupDetailsTags.ViewAllMembers).assertCountEquals(0)
    }

    @Test
    fun memberViewDoesNotExposeOrganizerCashboxShortcut() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

        onAllNodesWithTag(GroupDetailsTags.ShortcutCashbox).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
    }

    @Test
    fun adminCashboxRowStillOpensOrganizerCashbox() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithTag(GroupDetailsTags.Cashbox).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.OpenCashbox, intents.single())
    }

    @Test
    fun adminCashboxRowRemainsVisibleWithoutFinanceSummary() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(cashbox = CashboxUi()))

        onNodeWithTag(GroupDetailsTags.Cashbox).assertExists()
        onNodeWithText("Caixa do grupo").assertExists()
        onAllNodesWithText("Saldo R$ 380,00 · 8 mensalidades em aberto").assertCountEquals(0)
    }

    @Test
    fun memberViewDoesNotRenderCashboxFromStaleState() = runComposeUiTest {
        setDetailsScreen(
            GroupDetailsPreviewData.member.copy(
                cashbox = CashboxUi(summary = "Saldo R$ 380,00 · 8 mensalidades em aberto"),
            ),
        )

        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
        onAllNodesWithText("Caixa do grupo").assertCountEquals(0)
    }

    @Test
    fun createdPhotoFailedBannerExplainsTheGroupExists() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin, photoFailed = true)

        onNodeWithTag(GroupDetailsTags.PhotoFailed).assertExists()
        onNodeWithText("Grupo criado").assertExists()
        onNodeWithText("A foto não carregou. Você pode tentar de novo em Editar grupo.").assertExists()
    }
}
```

### 18. Criar `TDET/GroupWaitingBlockTest.kt` (arquivo completo) — dono: C5

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupWaitingBlockTest {
    @Test
    fun adminWithNextGameCanNotifyWhoIsPending() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithTag(GroupDetailsTags.NotifyPending).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.NotifyPending, intents.single())
    }

    @Test
    fun memberAndAdminWithoutGameNeverSeeTheNotifyAction() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun adminWithoutGameHasNothingToNotify() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.adminNoGame)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    // O texto é contrato do e2e (`CommunicationDetailsE2eTest`): precisa continuar na árvore.
    @Test
    fun reminderFeedbackStaysOnScreen() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(notifiedCount = "2"))

        onNodeWithText("Lembrete enviado no Saqz para 2 pessoa(s).").assertExists()
    }
}
```

### 19. Capturas — criar `SDET/GroupDetailsScreenshotSupport.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.captureRoboImage

/** A captura única da tela inteira: cada suíte de bloco só escolhe estado, nome e pasta. */
internal fun ComposeContentTestRule.captureDetails(
    name: String,
    state: GroupDetailsState,
    directory: String,
    photoFailed: Boolean = false,
) {
    setContent {
        SaqzTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SaqzTheme.colors.background),
            ) {
                GroupDetailsScreen(state = state, onBack = {}, onIntent = {}, photoFailed = photoFailed)
            }
        }
    }
    onRoot().captureRoboImage("screenshots/$directory/$name.png")
}
```

(Fato do código: `androidx.compose.ui.test.junit4.v2.createComposeRule()` devolve `androidx.compose.ui.test.junit4.ComposeContentTestRule` — `ui-test-junit4-android 1.11.2`, `v2/AndroidComposeTestRule.android.kt:57`.)

### 20. Criar `SDET/GroupOwnDebtScreenshotTest.kt` (arquivo completo) — dono: C3

```kotlin
package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// VUL-203 — os quatro estados da seção de cobranças: com pendência (e Pix), quitada,
// carregando e com falha. Estado fora da cena é estado não conferido (AGENTS.md §11).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupOwnDebtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "+h2400dp")
    fun ownCharges() = compose.captureDetails("group-details-own-charges", GroupDetailsPreviewData.member, "own-debt")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun ownChargesSettled() =
        compose.captureDetails("group-details-own-charges-settled", GroupDetailsPreviewData.memberOwnChargesSettled, "own-debt")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun ownChargesLoading() =
        compose.captureDetails("group-details-own-charges-loading", GroupDetailsPreviewData.memberOwnChargesLoading, "own-debt")

    @Test
    @Config(qualifiers = "+h2000dp")
    fun ownChargesFailed() =
        compose.captureDetails("group-details-own-charges-failed", GroupDetailsPreviewData.memberOwnChargesFailed, "own-debt")
}
```

### 21. Reescrever `SDET/GroupDetailsScreenshotTest.kt` (arquivo completo) — dono: D

```kotlin
package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A tela inteira nos estados-base. Os estados de cada bloco moram na suíte do bloco. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupDetailsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    // A tela rola: a altura sobe só na captura, para o print do PR mostrar a pilha inteira.
    @Test
    @Config(qualifiers = "+h2400dp")
    fun admin() = compose.captureDetails("group-details-admin", GroupDetailsPreviewData.admin, "details")

    @Test
    @Config(qualifiers = "+h1400dp")
    fun adminNoGame() = compose.captureDetails("group-details-admin-no-game", GroupDetailsPreviewData.adminNoGame, "details")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun member() = compose.captureDetails("group-details-member", GroupDetailsPreviewData.member, "details")

    @Test
    @Config(qualifiers = "+h1800dp")
    fun memberNoGame() = compose.captureDetails("group-details-member-no-game", GroupDetailsPreviewData.memberNoGame, "details")

    @Test
    fun loading() = compose.captureDetails("group-details-loading", GroupDetailsState(), "details")

    @Test
    @Config(qualifiers = "+h2400dp")
    fun photoFailed() =
        compose.captureDetails("group-details-photo-failed", GroupDetailsPreviewData.adminNoGame, "details", photoFailed = true)
}
```

## Gates

Rodar da raiz do worktree, nesta ordem:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.ui.details.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :compose-app:iosSimulatorArm64Test --tests "br.com.saqz.composeapp.navigation.*"
```

O último roda o `SaqzNavHostViewModelScopeTest`, que clica `group-details-leave` com `performScrollTo` e tem de continuar verde.

Depois do `recordRoborazzi`, **abrir e olhar** estes PNGs em `mobile/features/groups/presentation/screenshots/`: `details/group-details-admin.png`, `details/group-details-member.png`, `details/group-details-admin-no-game.png`, `details/group-details-member-no-game.png`, `own-debt/group-details-own-charges.png`, e os três de `onboarding/`.

## Prints obrigatórios no corpo do PR

Empurrar para a branch órfã `screenshots` em `vul-XXX/` e embutir o raw: `group-details-admin.png`, `group-details-admin-no-game.png`, `group-details-member.png`, `group-details-member-no-game.png`, `group-details-loading.png`, `group-details-photo-failed.png`, `group-details-own-charges.png`. Legenda única no corpo: "Sem redesenho: só reordenação, respiro 24/12 e o botão Avisar fora do card de contadores."

## Critérios de aceite

- [ ] `GroupDetailsScreen.kt` não contém mais tags, preview data, banner nem card da quadra — só a composição e as três previews.
- [ ] Os quatro blocos vazios (`GroupToastBlock`, `GroupOwnChargesSettledBlock`, `GroupAgendaBlock`, `GroupHomeCourtBlock`) e o `GroupTopBar` são os únicos com `@Suppress("UnusedParameter")`, todos com o KDoc "Andaime (T)".
- [ ] Nenhuma tag existente mudou de valor (`git diff` de strings `"group-…"`: só adições).
- [ ] Todos os testes que existiam em `GroupDetailsScreenTest` continuam existindo em algum dos cinco arquivos novos, com a mesma asserção (exceções deliberadas: `ViewGame` e `Notice` saíram da tabela "só membro"; `NotifyPending` saiu da tabela "só admin" e virou `GroupWaitingBlockTest`; os cliques em nós fora da dobra ganharam `performScrollTo`).
- [ ] `GroupDetailsPreviewData.admin` tem `nextGame` e `attendance` juntos; existe `adminNoGame` e `memberNoGame`.
- [ ] `AthleteOnboardingCardTest`, `GroupOnboardingCardTest`, `GroupLeaveSheetTest`, `GroupDetailsRootTest`, `GroupOnboardingScreenshotTest`, `AthleteOnboardingScreenshotTest` verdes **sem edição**.
- [ ] Nenhum arquivo de `details/`, `ui/home/`, `ui/components/`, `composeResources/` ou `android-app/src/e2e` no diff.
- [ ] Diff ≤ 1500 linhas (estimativa: ~1350, a maior parte é movimentação).

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-andaime-detalhe-grupo origin/main` (XXX = número deste ticket).
2. Commits pequenos em PT-BR no padrão do repo (`refactor(groups): …`, `test(groups): …`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo.
4. PR contra `main`, aberto como ready (não draft), título `refactor(groups): detalhe do grupo em um arquivo por bloco (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
