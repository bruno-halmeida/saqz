# C1 · Hero do próximo jogo + toast

**Onda 2 · depende de: S, B, T, V1 · bloqueia: D · paralelo com: V2, C2, C3**

## Objetivo

Trocar o corpo do `GroupHeroBlock` (hoje: guia do gestor + card do jogo + "Você vai jogar?" + contadores + card da quadra) pelo **hero azul** da Início, e ligar o `GroupToastBlock` ao `state.toast` do V1.

Depois deste PR o bloco emite UM `Column(spacedBy(blockGap))` com, nesta ordem:

1. o hero (`SaqzHeroCard`) — com jogo, sem jogo (membro), sem jogo (gestor), primeiro jogo (gestor com `GroupOnboarding.CreateGame`);
2. a linha de confirmação automática (fora do hero: o `SaqzSwitch` não tem variante sobre azul);
3. a intro do atleta (`AthleteOnboardingCard`, como está);
4. o guia do gestor (`GroupOnboardingCard`, como está, só `InviteAthletes` e `ReviewFinances`);
5. os extras de espera (`HomeWaitlist*`, como estão).

Fatos do código que sustentam o desenho (já conferidos, não reabrir):

- `GroupOnboarding.CreateGame` só existe quando o grupo não tem NENHUM jogo (`groupOnboarding(...)`: `games.isEmpty()`), então "primeiro jogo" é sempre um estado sem `nextGame`. O botão do hero emite `CreateNextGame`; no ViewModel ele e o `OnboardingAction` desse guia emitem o mesmo `OpenCreateGame(groupId)`.
- O e2e `attendance`: o atleta é MENSALISTA num grupo com `mensalista_priority = false` ⇒ espera do tipo `Reserva` ⇒ o chip final é "Lista de espera · 1º" (`home_waitlist_reserva_chip`); o dono chega CONFIRMADO pelo fixture ⇒ vê o painel com "Alterar", não os dois botões.
- O placar (`AttendanceScoreBoard`) exige `tags: AttendanceScoreBoardTags` e o `GroupDetailsTags` (fechado) não tem tags de placar: este ticket reusa `HomeAdminTags.ScoreGoing/ScoreOut/ScorePending`, pelo mesmo motivo que o B deu para `HomeWaitlistTags` — a Início e o detalhe nunca estão na mesma árvore de semântica.
- Enquanto o C2 não mergear, o card de cabeçalho antigo (`GroupHeaderCard`, do C2) ainda desenha um botão com a tag `CreateNextGame` para o gestor. Por isso TODO teste deste ticket que procura `CreateNextGame`/`HeroInvite` procura **dentro do hero** (`hasAnyAncestor(hasTestTag(Hero))`): fica verde nas duas ordens de merge.

## Fora do escopo

- **PROIBIDO tocar:** `DET/GroupDetailsScreen.kt`, `DET/GroupDetailsSections.kt`, `DET/GroupGameResponseSection.kt` (este ticket só PARA de chamá-los; quem apaga é o D), `DET/GroupDetailsTags.kt`, `DET/GroupDetailsPreviewData.kt`, `DET/GroupOnboardingCard.kt`, `DET/AthleteOnboardingCard.kt`, `PRES/details/*` (Contract/ViewModel), `composeResources/`, `PRES/ui/home/*`, `PRES/ui/components/*`, `mobile/core/design-system`.
- O botão "Avisar quem falta" NÃO mora no hero (é do C5). A quadra padrão sem jogo é do C2 (`GroupHomeCourtBlock`).
- O KDoc do `SaqzHeroCard` (que ainda diz que o detalhe do grupo usa o `SaqzGameSummaryCard`) é atualizado no D.
- `E2E/GroupLeaveE2eTest.kt` **NÃO muda**: o dono do cenário `leave` não tem resposta, então continua vendo `group-game-response-going` (os dois botões grandes do estado pendente).

## Arquivos

Base: `DET`, `TDET`, `SDET`, `E2E` do CONTRATO.

| Ação | Arquivo |
|---|---|
| editar (reescrito) | `DET/GroupHeroBlock.kt` |
| editar (reescrito) | `DET/GroupToastBlock.kt` |
| criar | `DET/GroupHeroPreviewData.kt` |
| editar (reescrito) | `TDET/GroupHeroBlockTest.kt` |
| criar | `SDET/GroupHeroScreenshotTest.kt` |
| editar | `E2E/AttendanceE2eTest.kt` |
| editar | `E2E/AttendanceOrderE2eTest.kt` |

**Tocar em arquivo fora desta lista = parar e avisar o orquestrador.**

## Passo a passo

### 0. Conferência antes de começar

Na branch nova, os quatro comandos abaixo têm de imprimir pelo menos uma linha cada. Se algum não imprimir, a onda 1 não está inteira na `main`: parar e avisar o orquestrador.

```sh
grep -n "val toast: GroupDetailsToast" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/details/GroupDetailsContract.kt
grep -n "fun ColumnScope.HeroAttendanceControls" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/components/HeroAttendance.kt
grep -n "Andaime (T)" mobile/features/groups/presentation/src/commonMain/kotlin/br/com/saqz/groups/presentation/ui/details/GroupHeroBlock.kt
grep -n "group_details_hero_first_title" mobile/features/groups/presentation/src/commonMain/composeResources/values/strings_group_details.xml
```

### 1. Reescrever `DET/GroupHeroBlock.kt` (arquivo completo)

Âncora: o arquivo que o T criou começa com o KDoc `* Andaime (T): tudo que é do próximo jogo, ainda com as peças antigas` e termina com `private fun GroupVenueCard(`. Substituir o arquivo INTEIRO por:

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupOnboarding
import br.com.saqz.groups.presentation.details.NextGameUi
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoard
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoardTags
import br.com.saqz.groups.presentation.ui.components.HeroAlertLine
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceControls
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTags
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTexts
import br.com.saqz.groups.presentation.ui.components.HeroDeadlineLine
import br.com.saqz.groups.presentation.ui.components.HeroIconGap
import br.com.saqz.groups.presentation.ui.components.HeroInlineIconSize
import br.com.saqz.groups.presentation.ui.components.HeroLineIconSize
import br.com.saqz.groups.presentation.ui.components.HeroOutlineAlpha
import br.com.saqz.groups.presentation.ui.components.HeroRosterRow
import br.com.saqz.groups.presentation.ui.home.HomeAdminTags
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistBellCard
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistConfirmedSection
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistPositionLine
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistQueueSection
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistUpsellCard
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_response_auto_confirmation
import br.com.saqz.groups.resources.game_response_auto_confirmation_failed
import br.com.saqz.groups.resources.game_response_confirmed
import br.com.saqz.groups.resources.game_response_confirmed_summary
import br.com.saqz.groups.resources.game_response_day_member_fee
import br.com.saqz.groups.resources.game_response_deadline_closed
import br.com.saqz.groups.resources.game_response_declined
import br.com.saqz.groups.resources.game_response_no
import br.com.saqz.groups.resources.game_response_request_failed
import br.com.saqz.groups.resources.game_response_retry_roster
import br.com.saqz.groups.resources.game_response_roster_stale
import br.com.saqz.groups.resources.game_response_yes
import br.com.saqz.groups.resources.group_details_hero_empty_admin
import br.com.saqz.groups.resources.group_details_hero_first_body
import br.com.saqz.groups.resources.group_details_hero_first_title
import br.com.saqz.groups.resources.group_details_map_failure
import br.com.saqz.groups.resources.group_details_venue_map
import br.com.saqz.groups.resources.group_details_view_game
import br.com.saqz.groups.resources.home_admin_shortcuts_create_game
import br.com.saqz.groups.resources.home_admin_shortcuts_invite
import br.com.saqz.groups.resources.home_attendance_cancel
import br.com.saqz.groups.resources.home_attendance_change
import br.com.saqz.groups.resources.home_game_next
import br.com.saqz.groups.resources.home_no_game_description
import br.com.saqz.groups.resources.home_no_game_hero_title
import br.com.saqz.groups.resources.home_spots_left
import br.com.saqz.groups.resources.onboarding_create_action
import org.jetbrains.compose.resources.stringResource

/**
 * Tudo que é do próximo jogo DESTE grupo: o hero azul da Início (segundo uso do `SaqzHeroCard`),
 * a confirmação automática, a intro do atleta, o guia do gestor e — em espera — a fila.
 *
 * O bloco sempre emite: sem jogo o hero vira o convite a marcar (gestor) ou o aviso de que
 * ainda não há jogo (membro). "Avisar quem falta" mora no [GroupWaitingBlock].
 */
@Composable
internal fun GroupHeroBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = state.nextGame
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        if (game != null) {
            GroupGameHero(state = state, game = game, onIntent = onIntent)
        } else {
            GroupNoGameHero(state = state, onIntent = onIntent)
        }
        if (game != null && state.autoConfirmationVisible) {
            GroupAutoConfirmationCard(state = state, onIntent = onIntent)
        }
        if (state.athleteIntroVisible && !state.isAdmin && !state.responding) {
            AthleteOnboardingCard(state.athleteShareFailed, onIntent)
        }
        // O guia "marcar o primeiro jogo" não vira card: ele É o hero sem jogo do gestor.
        if (state.isAdmin) {
            state.onboarding?.takeIf { it != GroupOnboarding.CreateGame }?.let { GroupOnboardingCard(it, onIntent) }
        }
        if (game != null) GroupWaitlistExtras(state = state, game = game)
    }
}

@Composable
private fun GroupGameHero(
    state: GroupDetailsState,
    game: NextGameUi,
    onIntent: (GroupDetailsIntent) -> Unit,
) {
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = game.display,
        meta = game.meta,
        trailing = { HeroViewGameLink(onClick = { onIntent(GroupDetailsIntent.ViewGame) }) },
        modifier = Modifier.testTag(GroupDetailsTags.Hero),
    ) {
        if (game.address.isNotEmpty()) {
            HeroAddress(
                address = game.address,
                mapFailed = state.mapFailed,
                onOpenMap = { onIntent(GroupDetailsIntent.OpenVenueMap) },
            )
        }
        val attendance = state.attendance
        if (state.isAdmin && attendance != null) {
            AttendanceScoreBoard(
                going = attendance.going,
                out = attendance.notGoing,
                pending = attendance.pending,
                onClick = { onIntent(GroupDetailsIntent.ViewGame) },
                tags = GroupScoreBoardTags,
            )
        }
        HeroDeadlineLine(
            text = if (game.confirmationOpen) game.deadlineLine else stringResource(Res.string.game_response_deadline_closed),
            open = game.confirmationOpen,
        )
        // Dono e admin respondem presença no mesmo lugar que o atleta: o papel administrativo
        // muda o que ele gerencia, não o fato de que ele joga.
        HeroAttendanceControls(
            status = state.memberResponse?.status?.toAttendanceStatus(),
            confirmationOpen = game.confirmationOpen && !state.rosterRefreshing,
            responding = state.responding,
            responseFailed = state.responseFailed,
            waitlistKind = state.waitlist?.kind,
            waitlistPosition = state.memberResponse?.waitlistPosition,
            onRespond = { onIntent(GroupDetailsIntent.Respond(it)) },
            onViewGame = { onIntent(GroupDetailsIntent.ViewGame) },
            texts = HeroAttendanceTexts(
                yes = stringResource(Res.string.game_response_yes),
                no = stringResource(Res.string.game_response_no),
                confirmed = stringResource(Res.string.game_response_confirmed),
                declined = stringResource(Res.string.game_response_declined),
                change = stringResource(Res.string.home_attendance_change),
                cancel = stringResource(Res.string.home_attendance_cancel),
                error = stringResource(Res.string.game_response_request_failed),
            ),
            tags = GroupHeroAttendanceTags,
        )
        if (state.membershipType == AthleteMembershipType.AVULSO && game.hasGameFee) HeroFeeNote()
        if (state.rosterStale) {
            HeroRosterStale(
                refreshing = state.rosterRefreshing,
                enabled = !state.rosterRefreshing && !state.responding,
                onRetry = { onIntent(GroupDetailsIntent.RetryRoster) },
            )
        }
        // O gestor já tem o placar; em espera, as seções abaixo do hero já mostram o roster.
        if (!state.isAdmin && state.waitlist == null) {
            val scarce = game.availableSpots in 1..SpotsLeftMax && state.memberResponse == null && game.confirmationOpen
            HeroRosterRow(
                names = game.confirmedNames,
                summary = stringResource(Res.string.game_response_confirmed_summary, game.confirmedCount, game.capacity),
                spotsChip = if (scarce) stringResource(Res.string.home_spots_left, game.availableSpots) else null,
            )
        }
    }
}

/** "Ver jogo ›" no canto do kicker: o atalho para a tela do jogo, branco sobre o azul. */
@Composable
private fun HeroViewGameLink(onClick: () -> Unit) {
    SaqzButton(
        label = stringResource(Res.string.group_details_view_game),
        onClick = onClick,
        modifier = Modifier.testTag(GroupDetailsTags.ViewGame),
        variant = SaqzButtonVariant.Ghost,
        size = SaqzButtonSize.Sm,
        contentColor = SaqzTheme.colors.onPrimary,
        trailingContent = { tint -> SaqzIcon(SaqzIcons.ChevronRight, tint = tint, size = HeroInlineIconSize) },
    )
}

/**
 * O endereço DO JOGO (não o da quadra padrão) com "Ver no mapa". A falha de abrir o mapa fica
 * colada na linha que a causou.
 */
@Composable
private fun HeroAddress(
    address: String,
    mapFailed: Boolean,
    onOpenMap: () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val muted = colors.onPrimary.copy(alpha = HeroAddressAlpha)
    val mapLabel = stringResource(Res.string.group_details_venue_map)
    Column(verticalArrangement = Arrangement.spacedBy(HeroIconGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzIcon(SaqzIcons.Pin, tint = muted, size = HeroLineIconSize)
            Text(
                text = address,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = muted,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .testTag(GroupDetailsTags.HeroMap)
                    .clickable(onClickLabel = mapLabel, role = Role.Button, onClick = onOpenMap)
                    .heightIn(min = metrics.minimumTouchTarget)
                    .padding(start = metrics.grid),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mapLabel,
                    style = SaqzTheme.typography.support.copy(
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                    ),
                    color = colors.onPrimary,
                )
            }
        }
        if (mapFailed) {
            HeroAlertLine(
                text = stringResource(Res.string.group_details_map_failure),
                tag = GroupDetailsTags.HeroMapFailure,
            )
        }
    }
}

/** Avulso em jogo com taxa: confirmar gera cobrança, e ele precisa saber ANTES de tocar. */
@Composable
private fun HeroFeeNote() {
    val color = SaqzTheme.colors.onPrimary.copy(alpha = HeroAddressAlpha)
    Row(
        modifier = Modifier.testTag(GroupDetailsTags.HeroFeeNote),
        horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.CreditCard, tint = color, size = HeroLineIconSize)
        Text(
            text = stringResource(Res.string.game_response_day_member_fee),
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

@Composable
private fun HeroRosterStale(
    refreshing: Boolean,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    HeroAlertLine(
        text = stringResource(Res.string.game_response_roster_stale),
        tag = GroupDetailsTags.HeroRosterStale,
        action = {
            SaqzButton(
                label = stringResource(Res.string.game_response_retry_roster),
                onClick = onRetry,
                modifier = Modifier.testTag(GroupDetailsTags.HeroRosterRetry),
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                enabled = enabled,
                loading = refreshing,
                contentColor = SaqzTheme.colors.onPrimary,
            )
        },
    )
}

/**
 * Sem jogo: o membro só é avisado; o gestor ganha os dois verbos dele dentro do hero, como na
 * Início. No grupo recém-criado ([GroupOnboarding.CreateGame]) o hero É o guia: um botão só.
 */
@Composable
private fun GroupNoGameHero(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
) {
    val first = state.isAdmin && state.onboarding == GroupOnboarding.CreateGame
    val title = if (first) Res.string.group_details_hero_first_title else Res.string.home_no_game_hero_title
    val meta = when {
        first -> Res.string.group_details_hero_first_body
        state.isAdmin -> Res.string.group_details_hero_empty_admin
        else -> Res.string.home_no_game_description
    }
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = stringResource(title),
        meta = stringResource(meta),
        modifier = Modifier.testTag(GroupDetailsTags.Hero),
        content = if (state.isAdmin) {
            { HeroNoGameActions(first = first, onIntent = onIntent) }
        } else {
            null
        },
    )
}

@Composable
private fun HeroNoGameActions(
    first: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        SaqzButton(
            label = stringResource(
                if (first) Res.string.onboarding_create_action else Res.string.home_admin_shortcuts_create_game,
            ),
            onClick = { onIntent(GroupDetailsIntent.CreateNextGame) },
            modifier = Modifier.weight(1f).testTag(GroupDetailsTags.CreateNextGame),
            variant = SaqzButtonVariant.Accent,
            fullWidth = true,
        )
        if (!first) {
            SaqzButton(
                label = stringResource(Res.string.home_admin_shortcuts_invite),
                onClick = { onIntent(GroupDetailsIntent.InviteByLink) },
                modifier = Modifier.weight(1f).testTag(GroupDetailsTags.HeroInvite),
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                contentColor = colors.onPrimary,
                borderColor = colors.onPrimary.copy(alpha = HeroOutlineAlpha),
            )
        }
    }
}

/**
 * Confirmação automática: é do GRUPO e só do mensalista. Fica fora do hero porque o
 * `SaqzSwitch` não tem variante sobre o azul.
 */
@Composable
private fun GroupAutoConfirmationCard(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
) {
    SaqzCard(tone = SaqzCardTone.Soft) {
        SaqzSwitch(
            checked = state.autoConfirmationEnabled,
            onCheckedChange = { onIntent(GroupDetailsIntent.ToggleAutoConfirmation(it)) },
            modifier = Modifier.fillMaxWidth().testTag(GroupGameResponseTags.AutoConfirmation),
            label = stringResource(Res.string.game_response_auto_confirmation),
            enabled = !state.autoConfirmationUpdating,
        )
        if (state.autoConfirmationFailed) {
            Text(
                text = stringResource(Res.string.game_response_auto_confirmation_failed),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
            )
        }
    }
}

/**
 * Em espera, abaixo de tudo: reserva mostra os confirmados e o sino; lista do avulso mostra a
 * posição, a fila e o upsell. As peças são as da Início, como estão. É extensão de
 * `ColumnScope` porque emite irmãos direto na coluna do bloco.
 */
@Composable
private fun ColumnScope.GroupWaitlistExtras(state: GroupDetailsState, game: NextGameUi) {
    val waitlist = state.waitlist ?: return
    when (waitlist.kind) {
        HomeWaitlistKind.Reserva -> {
            HomeWaitlistConfirmedSection(
                confirmedRoster = game.confirmedNames,
                confirmedCount = game.confirmedCount,
                capacity = game.capacity,
            )
            if (game.bellLabel.isNotEmpty()) HomeWaitlistBellCard(label = game.bellLabel)
        }
        HomeWaitlistKind.AvulsoList -> {
            state.memberResponse?.waitlistPosition?.let { position ->
                HomeWaitlistPositionLine(position = position, confirmedCount = game.confirmedCount)
            }
            HomeWaitlistQueueSection(rows = waitlist.rows)
            HomeWaitlistUpsellCard()
        }
    }
}

private fun GroupDetailsResponseStatus.toAttendanceStatus() = when (this) {
    GroupDetailsResponseStatus.Confirmed -> AttendanceStatus.Confirmed
    GroupDetailsResponseStatus.Declined -> AttendanceStatus.Declined
    GroupDetailsResponseStatus.Waitlisted -> AttendanceStatus.Waitlisted
}

private val GroupHeroAttendanceTags = HeroAttendanceTags(
    yes = GroupGameResponseTags.Going,
    no = GroupGameResponseTags.NotGoing,
    change = GroupGameResponseTags.Change,
    cancel = GroupGameResponseTags.Cancel,
    error = GroupGameResponseTags.Error,
)

// As tags do placar são as da Início, como as de `HomeWaitlistTags`: as duas telas nunca estão
// na mesma árvore de semântica, e o inventário de tags do detalhe não tem placar.
private val GroupScoreBoardTags = AttendanceScoreBoardTags(
    going = HomeAdminTags.ScoreGoing,
    out = HomeAdminTags.ScoreOut,
    pending = HomeAdminTags.ScorePending,
)

/** Escassez que muda comportamento: abaixo disso aparece "Restam N vagas" (mesmo corte da Início). */
private const val SpotsLeftMax = 3

/** Endereço e nota da taxa: branco a 82%, um degrau abaixo do prazo (90%). */
private const val HeroAddressAlpha = 0.82f

@Preview
@Composable
private fun GroupHeroPendingPreview() = SaqzTheme {
    GroupHeroBlock(state = GroupHeroPreviewData.pending, onIntent = {})
}

@Preview
@Composable
private fun GroupHeroAdminNoGamePreview() = SaqzTheme {
    GroupHeroBlock(state = GroupHeroPreviewData.adminNoGame, onIntent = {})
}
```

Notas de compilação (fatos): `ColumnScope` é usado só pelo receiver de `GroupWaitlistExtras`; `SaqzHeroCard(kicker, title, modifier, trailing, meta, content)` — todos os argumentos vão nomeados; `HeroAlertLine(text, tag, modifier, action)`; `SaqzButton(label, onClick, modifier, variant, size, fullWidth, enabled, loading, labelStyle, contentColor, borderColor, leadingContent, trailingContent)`; `SaqzIcon(icon, modifier, tint, size)`.

A linha do endereço fica com 48dp de altura por causa do alvo de toque de "Ver no mapa" (regra da casa, mesmo padrão do `GroupVenueRow`). No mock ela tem 18: a diferença é esperada e vale a receita.

### 2. Reescrever `DET/GroupToastBlock.kt` (arquivo completo)

Âncora: o arquivo do T tem o KDoc `/** Andaime (T): o toast chega no ticket C1, junto com `state.toast` do V1. */`, o `@Suppress("UnusedParameter")` e o corpo `= Unit`. Substituir o arquivo INTEIRO por:

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupDetailsToast
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_toast_confirmed
import br.com.saqz.groups.resources.home_toast_declined
import br.com.saqz.groups.resources.home_toast_pix_copied
import br.com.saqz.groups.resources.home_toast_waitlisted
import org.jetbrains.compose.resources.stringResource

/**
 * O retorno da ação recém-concluída (resposta de presença, chave Pix copiada), com os textos da
 * Início. Sem toast o bloco não emite nada; o `SaqzToast` some sozinho e devolve `DismissToast`.
 */
@Composable
internal fun GroupToastBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val toast = state.toast ?: return
    SaqzToast(
        visible = true,
        onDismiss = { onIntent(GroupDetailsIntent.DismissToast) },
        modifier = modifier
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(GroupDetailsTags.Toast),
    ) {
        SaqzToastText(
            text = stringResource(
                when (toast) {
                    GroupDetailsToast.Confirmed -> Res.string.home_toast_confirmed
                    GroupDetailsToast.Declined -> Res.string.home_toast_declined
                    GroupDetailsToast.Waitlisted -> Res.string.home_toast_waitlisted
                    GroupDetailsToast.PixCopied -> Res.string.home_toast_pix_copied
                },
            ),
        )
    }
}
```

### 3. Criar `DET/GroupHeroPreviewData.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.details.AttendanceSummaryUi
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import br.com.saqz.groups.presentation.details.GroupDetailsToast
import br.com.saqz.groups.presentation.details.GroupOnboarding
import br.com.saqz.groups.presentation.details.GroupWaitlistUi
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi

/**
 * Os estados do hero, um por prancha do mock (`_mock-grupo/png`). Tudo deriva de
 * [GroupDetailsPreviewData] com `.copy(...)` e só descreve o que o `GroupDetailsViewModel`
 * produz — por isso "respondendo" já traz a resposta otimista: o ViewModel troca a resposta
 * ANTES de ligar `responding`.
 */
internal object GroupHeroPreviewData {
    val game = GroupDetailsPreviewData.nextGame.copy(
        display = "Terça, 19h30",
        meta = "28 de julho · CERET — Quadra 2",
        address = "R. Canuto Abreu, s/n · Tatuapé",
        deadlineLine = "As confirmações encerram hoje às 18h00.",
        deadlineShort = "Encerra 28/07 · 18h00",
        bellLabel = "Avisamos você se abrir vaga até 18h00 de 28/07.",
    )
    private val closedGame = game.copy(confirmationOpen = false)
    private val fullGame = game.copy(confirmedCount = 12, availableSpots = 0)
    private val confirmedResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Confirmed)

    // Membro
    val confirmed = GroupDetailsPreviewData.member.copy(nextGame = game, memberResponse = confirmedResponse)
    val pending = confirmed.copy(memberResponse = null, autoConfirmationEnabled = false)
    val confirmedToast = confirmed.copy(toast = GroupDetailsToast.Confirmed)
    val responding = confirmed.copy(responding = true, autoConfirmationEnabled = false)
    val declined = pending.copy(memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Declined))
    val responseFailed = pending.copy(responseFailed = true)
    val closed = confirmed.copy(nextGame = closedGame)
    val closedPending = pending.copy(nextGame = closedGame)
    val noAddress = pending.copy(nextGame = game.copy(address = ""))
    val dayMemberFee = pending.copy(
        membershipType = AthleteMembershipType.AVULSO,
        autoConfirmationVisible = false,
        nextGame = game.copy(hasGameFee = true),
    )
    val dayMemberNoFee = dayMemberFee.copy(nextGame = game)
    val rosterStale = confirmed.copy(rosterStale = true)
    val rosterRefreshing = rosterStale.copy(rosterRefreshing = true)
    val mapFailed = confirmed.copy(mapFailed = true)
    val autoConfirmationFailed = confirmed.copy(autoConfirmationEnabled = false, autoConfirmationFailed = true)
    val autoConfirmationUpdating = confirmed.copy(autoConfirmationUpdating = true)
    val athleteIntro = confirmed.copy(athleteIntroVisible = true, autoConfirmationVisible = false)
    val noGame = GroupDetailsPreviewData.memberNoGame

    val reserve = confirmed.copy(
        nextGame = fullGame,
        memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Waitlisted, waitlistPosition = 1),
        waitlist = GroupWaitlistUi(
            kind = HomeWaitlistKind.Reserva,
            rows = listOf(HomeWaitlistRowUi("Bruna Silva", 1, isSelf = true)),
        ),
        autoConfirmationVisible = false,
    )
    val dayMemberList = reserve.copy(
        nextGame = game,
        membershipType = AthleteMembershipType.AVULSO,
        memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Waitlisted, waitlistPosition = 2),
        waitlist = GroupWaitlistUi(
            kind = HomeWaitlistKind.AvulsoList,
            rows = listOf(
                HomeWaitlistRowUi("Lucas Pereira", 1, isSelf = false),
                HomeWaitlistRowUi("Bruna Silva", 2, isSelf = true),
                HomeWaitlistRowUi("Tiago Moraes", 3, isSelf = false),
            ),
        ),
    )

    // Gestor
    val adminPending = GroupDetailsPreviewData.admin.copy(nextGame = game)
    val adminConfirmed = adminPending.copy(
        memberResponse = confirmedResponse,
        attendance = AttendanceSummaryUi(confirmedCount = 10, capacity = 12, going = 10, notGoing = 1, pending = 1, availableSpots = 2),
        membershipType = AthleteMembershipType.MENSALISTA,
        autoConfirmationVisible = true,
        autoConfirmationEnabled = true,
    )
    val adminClosed = adminPending.copy(
        nextGame = closedGame,
        memberResponse = confirmedResponse,
        attendance = AttendanceSummaryUi(confirmedCount = 10, capacity = 12, going = 10, notGoing = 2, pending = 0, availableSpots = 2),
    )
    val adminNoGame = GroupDetailsPreviewData.adminNoGame
    val adminFirstGame = adminNoGame.copy(onboarding = GroupOnboarding.CreateGame)
    val adminInviteGuide = adminPending.copy(
        onboarding = GroupOnboarding.InviteAthletes("game-1"),
        attendance = AttendanceSummaryUi(confirmedCount = 0, capacity = 12, going = 0, notGoing = 0, pending = 1, availableSpots = 12),
    )
    val adminFinanceGuide = adminNoGame.copy(onboarding = GroupOnboarding.ReviewFinances("game-0"))
}
```

### 4. Reescrever `TDET/GroupHeroBlockTest.kt` (arquivo completo)

Âncora: o arquivo do T tem 7 testes, o primeiro é `adminWithNextGameStillGetsTheAttendanceSelector`. Substituir o arquivo INTEIRO pelo conteúdo da seção **Testes**.

### 5. Criar `SDET/GroupHeroScreenshotTest.kt` (arquivo completo)

Conteúdo na seção **Cenas de screenshot e prints do PR**.

### 6. Editar `E2E/AttendanceE2eTest.kt`

6.1. Trecho antigo (atleta):

```kotlin
        click("group-game-response-going", scroll = true)
        waitText("Você está em 1º na lista de espera.")
        waitEnabled("group-game-response-going")
        ui.onNodeWithText("Você está em 1º na lista de espera.").performScrollTo().assertIsDisplayed()
```

Trecho novo:

```kotlin
        click("group-game-response-going", scroll = true)
        // Em espera os dois botões somem: ficam o chip com a posição e "Sair da lista de espera".
        waitText("Lista de espera · 1º")
        waitEnabled("home-reserva-leave")
        ui.onNodeWithText("Lista de espera · 1º").performScrollTo().assertIsDisplayed()
```

(`home-reserva-leave` é `HomeWaitlistTags.ReservaLeave`, em `PRES/ui/home/HomeWaitlistSections.kt`; o botão só habilita com `confirmationOpen && !responding`, então é o mesmo sinal de "resposta assentada" que o `waitEnabled` antigo dava. Durante o otimista o chip diz só "Lista de espera", sem posição — o `waitText` exato espera o roster.)

6.2. Trecho antigo (dono):

```kotlin
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        waitEnabled("group-game-response-not-going")
        ui.onNodeWithText("Você não vai jogar.").performScrollTo().assertIsDisplayed()
        assertEquals("DECLINED", api("owner", attendancePath).getJSONObject("ownAttendance").getString("status"))
```

Trecho novo:

```kotlin
        // O fixture entrega o dono CONFIRMADO: o hero mostra o painel, e os botões só voltam em "Alterar".
        click("group-game-response-change", scroll = true)
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        waitEnabled("group-game-response-change")
        ui.onNodeWithText("Você não vai jogar.").performScrollTo().assertIsDisplayed()
        assertEquals("DECLINED", api("owner", attendancePath).getJSONObject("ownAttendance").getString("status"))
```

O resto do arquivo (imports inclusive) não muda. O trecho final do atleta (`waitText("Sua presença está confirmada.")`) continua valendo: é o texto do painel.

### 7. Editar `E2E/AttendanceOrderE2eTest.kt`

Trecho antigo:

```kotlin
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        waitEnabled("group-game-response-not-going")
        ui.onNodeWithText("Você não vai jogar.").performScrollTo().assertIsDisplayed()
```

Trecho novo:

```kotlin
        // O fixture entrega o dono CONFIRMADO: o hero mostra o painel, e os botões só voltam em "Alterar".
        click("group-game-response-change", scroll = true)
        click("group-game-response-not-going", scroll = true)
        waitText("Você não vai jogar.")
        waitEnabled("group-game-response-change")
        ui.onNodeWithText("Você não vai jogar.").performScrollTo().assertIsDisplayed()
```

O resto do arquivo não muda.

### 8. `E2E/GroupLeaveE2eTest.kt` — NÃO editar

`waitTag("group-game-response-going")` continua correto: no cenário `leave` o dono não respondeu, e sem resposta o hero mostra os dois botões grandes com as mesmas tags. Rodar o cenário `leave` nos gates é a prova.

## Testes

### `TDET/GroupHeroBlockTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.ui.home.HomeAdminTags
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistTags
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupHeroBlockTest {
    // Enquanto o card de cabeçalho antigo existir (C2), `CreateNextGame` aparece duas vezes para o
    // gestor: o que é do hero se procura DENTRO do hero.
    private fun ComposeUiTest.inHero(tag: String) =
        onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(GroupDetailsTags.Hero)))

    @Test
    fun pendingShowsBothButtonsAndGoingAsksToConfirm() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.pending) { intents += it }

        onNodeWithTag(GroupGameResponseTags.NotGoing).assertExists()
        onNodeWithText("As confirmações encerram hoje às 18h00.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Going).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Confirm), intents.single())
        onAllNodesWithText("Talvez").assertCountEquals(0)
    }

    @Test
    fun confirmedShowsThePanelWithChangeAndNoButtons() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.confirmed)

        onNodeWithText("Sua presença está confirmada.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Change).assertExists()
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
        onAllNodesWithTag(GroupGameResponseTags.NotGoing).assertCountEquals(0)
    }

    @Test
    fun changeBringsTheButtonsBackWithCancel() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithTag(GroupGameResponseTags.Change).performScrollTo().performClick()

        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(GroupGameResponseTags.Cancel).assertExists()
        onNodeWithTag(GroupGameResponseTags.NotGoing).performClick()
        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Decline), intents.single())
    }

    @Test
    fun declinedShowsTheDeclinedPanel() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.declined)

        onNodeWithText("Você não vai jogar.").assertExists()
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
    }

    @Test
    fun closedDeadlineSaysSoAndDisablesTheAnswer() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closedPending)

        onNodeWithText("As confirmações estão encerradas.").assertExists()
        onAllNodesWithText("As confirmações encerram hoje às 18h00.").assertCountEquals(0)
        onNodeWithTag(GroupGameResponseTags.Going).assertIsNotEnabled()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertIsNotEnabled()
    }

    @Test
    fun closedDeadlineDisablesChangeForWhoAnswered() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.closed)

        onNodeWithText("Sua presença está confirmada.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Change).assertIsNotEnabled()
    }

    @Test
    fun rosterRefreshLocksTheAnswer() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.pending.copy(rosterRefreshing = true))

        onNodeWithTag(GroupGameResponseTags.Going).assertIsNotEnabled()
    }

    @Test
    fun failedResponseShowsTheErrorLine() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.responseFailed)

        onNodeWithTag(GroupGameResponseTags.Error).assertExists()
        onNodeWithText("Não foi possível salvar sua resposta. Tente novamente.").assertExists()
    }

    @Test
    fun dayMemberSeesTheFeeNoteOnlyWhenTheGameHasAFee() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberFee)

        onNodeWithTag(GroupDetailsTags.HeroFeeNote).assertExists()
        onNodeWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertExists()
    }

    @Test
    fun feeNoteIsHiddenWithoutFeeAndForMonthlyMembers() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberNoFee)
        onAllNodesWithTag(GroupDetailsTags.HeroFeeNote).assertCountEquals(0)
    }

    @Test
    fun monthlyMemberNeverSeesTheFeeNote() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.pending.copy(nextGame = GroupHeroPreviewData.game.copy(hasGameFee = true)))
        onAllNodesWithTag(GroupDetailsTags.HeroFeeNote).assertCountEquals(0)
    }

    @Test
    fun adminSeesTheScoreBoardAndNoRosterRow() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminPending) { intents += it }

        onAllNodesWithText("9 de 12 confirmados").assertCountEquals(0)
        // Dono e admin também jogam: o seletor é o mesmo do atleta.
        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(HomeAdminTags.ScorePending, useUnmergedTree = true).assertExists()
        onNodeWithTag(HomeAdminTags.ScoreGoing, useUnmergedTree = true).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.ViewGame, intents.single())
    }

    @Test
    fun memberSeesTheRosterRowWithScarcityAndNoScoreBoard() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.pending)

        onNodeWithText("9 de 12 confirmados").assertExists()
        onNodeWithText("Restam 3 vagas").assertExists()
        onAllNodesWithTag(HomeAdminTags.ScoreGoing, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun scarcityChipIsOnlyForWhoHasNotAnswered() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.confirmed)

        onNodeWithText("9 de 12 confirmados").assertExists()
        onAllNodesWithText("Restam 3 vagas").assertCountEquals(0)
    }

    @Test
    fun viewGameLinkOpensTheGame() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithTag(GroupDetailsTags.ViewGame).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.ViewGame, intents.single())
    }

    @Test
    fun mapLinkOpensTheGameAddress() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithText("R. Canuto Abreu, s/n · Tatuapé").assertExists()
        onNodeWithTag(GroupDetailsTags.HeroMap).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.OpenVenueMap, intents.single())
        onAllNodesWithTag(GroupDetailsTags.HeroMapFailure).assertCountEquals(0)
    }

    @Test
    fun emptyAddressHidesTheAddressLine() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.noAddress)

        onNodeWithTag(GroupDetailsTags.Hero).assertExists()
        onAllNodesWithTag(GroupDetailsTags.HeroMap).assertCountEquals(0)
    }

    @Test
    fun mapFailureIsShownUnderTheAddress() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.mapFailed)

        onNodeWithTag(GroupDetailsTags.HeroMapFailure).assertExists()
    }

    @Test
    fun staleRosterOffersARetry() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.rosterStale) { intents += it }

        onNodeWithTag(GroupDetailsTags.HeroRosterStale).assertExists()
        onNodeWithTag(GroupDetailsTags.HeroRosterRetry).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.RetryRoster, intents.single())
    }

    @Test
    fun retryIsLockedWhileTheRosterRefreshes() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.rosterRefreshing)

        onNodeWithTag(GroupDetailsTags.HeroRosterRetry).assertIsNotEnabled()
    }

    @Test
    fun reserveShowsTheQueuePiecesAndNoGoingButton() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.reserve) { intents += it }

        onNodeWithText("Lista de espera · 1º").assertExists()
        onNodeWithTag(HomeWaitlistTags.ConfirmedSection).assertExists()
        onNodeWithText("Avisamos você se abrir vaga até 18h00 de 28/07.").assertExists()
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
        onAllNodesWithText("12 de 12 confirmados").assertCountEquals(0)
        onNodeWithTag(HomeWaitlistTags.ReservaLeave).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Decline), intents.single())
    }

    @Test
    fun dayMemberListShowsTheQueueAndTheUpsell() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberList)

        onNodeWithTag(HomeWaitlistTags.AvulsoChip).assertExists()
        onNodeWithTag(HomeWaitlistTags.QueueSection).assertExists()
        onNodeWithTag(HomeWaitlistTags.queueRow(2)).assertExists()
        onAllNodesWithTag(HomeWaitlistTags.ConfirmedSection).assertCountEquals(0)
    }

    @Test
    fun memberWithoutGameGetsTheEmptyHeroWithoutActions() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.noGame)

        onNodeWithTag(GroupDetailsTags.Hero).assertExists()
        onNodeWithText("Sem jogo marcado").assertExists()
        inHero(GroupDetailsTags.CreateNextGame).assertCountEquals(0)
        inHero(GroupDetailsTags.HeroInvite).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.ViewGame).assertCountEquals(0)
        onAllNodesWithTag(GroupGameResponseTags.Going).assertCountEquals(0)
        onAllNodesWithTag(GroupGameResponseTags.AutoConfirmation).assertCountEquals(0)
    }

    @Test
    fun adminWithoutGameCanCreateAndInviteFromTheHero() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminNoGame) { intents += it }

        onNodeWithText("Marque o próximo e a galera confirma em um toque.").assertExists()
        inHero(GroupDetailsTags.CreateNextGame).assertCountEquals(1)
        inHero(GroupDetailsTags.CreateNextGame)[0].performScrollTo().performClick()
        inHero(GroupDetailsTags.HeroInvite)[0].performScrollTo().performClick()

        assertEquals(listOf(GroupDetailsIntent.CreateNextGame, GroupDetailsIntent.InviteByLink), intents)
    }

    @Test
    fun firstGameHeroReplacesTheCreateGuide() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.adminFirstGame) { intents += it }

        onNodeWithText("Grupo criado!").assertExists()
        onNodeWithText("Marcar primeiro jogo").assertExists()
        inHero(GroupDetailsTags.HeroInvite).assertCountEquals(0)
        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
        inHero(GroupDetailsTags.CreateNextGame)[0].performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.CreateNextGame, intents.single())
    }

    @Test
    fun inviteAndFinanceGuidesStayAsCardsForTheAdminOnly() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminInviteGuide)
        onNodeWithTag(GroupOnboardingTags.Card).assertExists()
        onNodeWithTag(GroupOnboardingTags.Responses).assertExists()
    }

    @Test
    fun guideNeverShowsForAMember() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.adminFinanceGuide.copy(isAdmin = false, isOwner = false))
        onAllNodesWithTag(GroupOnboardingTags.Card).assertCountEquals(0)
    }

    @Test
    fun autoConfirmationSwitchAsksToToggle() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmed) { intents += it }

        onNodeWithTag(GroupGameResponseTags.AutoConfirmation).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.ToggleAutoConfirmation(false), intents.single())
    }

    @Test
    fun autoConfirmationFailureIsExplainedAndUpdatingLocksTheSwitch() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.autoConfirmationFailed.copy(autoConfirmationUpdating = true))

        onNodeWithText("Não foi possível atualizar a confirmação automática. Tente novamente.").assertExists()
        onNodeWithTag(GroupGameResponseTags.AutoConfirmation).assertIsNotEnabled()
    }

    @Test
    fun dayMemberHasNoAutoConfirmation() = runComposeUiTest {
        setDetailsScreen(GroupHeroPreviewData.dayMemberFee)
        onAllNodesWithTag(GroupGameResponseTags.AutoConfirmation).assertCountEquals(0)
    }

    @Test
    fun toastFollowsTheStateValue() = runComposeUiTest {
        var state by mutableStateOf(GroupHeroPreviewData.confirmedToast)
        setContent { SaqzTheme { GroupDetailsScreen(state = state, onBack = {}, onIntent = {}) } }

        onNodeWithTag(GroupDetailsTags.Toast).assertExists()
        onNodeWithText("Presença confirmada. Bom jogo!").assertExists()

        runOnIdle { state = state.copy(toast = null) }
        onAllNodesWithTag(GroupDetailsTags.Toast).assertCountEquals(0)
    }

    @Test
    fun toastAsksToBeDismissedAfterItsDwell() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupHeroPreviewData.confirmedToast) { intents += it }

        mainClock.advanceTimeBy(10_000)

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.DismissToast), intents)
    }
}
```

Fatos de arranjo: os textos literais vêm das chaves `game_response_*`, `home_*`, `onboarding_create_action` ("Marcar primeiro jogo"), `group_details_hero_*`; o texto da falha da confirmação automática é o valor literal de `game_response_auto_confirmation_failed` (`strings_game_response.xml:16`), o do mapa é `group_details_map_failure` (`strings.xml:3`). As colunas do placar ficam dentro de um nó `mergeDescendants`, por isso `useUnmergedTree = true`. `motion.toastDwellMillis` é 2 600 ms (`SaqzMotionPolicy.kt:53`); `advanceTimeBy(10_000)` cobre o dwell inteiro.

Detekt (`TooManyFunctions` dispara com 11 funções NÃO privadas por arquivo; privadas e `@Composable` não contam): `GroupHeroBlock.kt` tem 1 função não privada, `GroupToastBlock.kt` 1, `GroupHeroPreviewData.kt` nenhuma. As suítes de teste não entram na regra — `HomeScreenshotTest` (23 testes) e `HomeScreenTest` (27) estão na `main` sem baseline.

### Suítes que continuam verdes SEM edição (conferido lendo cada uma)

- `TDET/AthleteOnboardingCardTest.kt`: monta `GroupDetailsScreen` com `member.copy(athleteIntroVisible = true)` e confere o mesmo gate (`athleteIntroVisible && !isAdmin && !responding`) — preservado literalmente no passo 1.
- `TDET/GroupOnboardingCardTest.kt`: monta o `GroupOnboardingCard` direto (inclusive `CreateGame`) — o componente não muda.
- `SDET/GroupOnboardingScreenshotTest.kt`: só grava imagem, não tem asserção. A cena `create` MUDA de conteúdo (passa a mostrar o hero "Grupo criado!" no lugar do card) — é esperado; dizer no corpo do PR. `invite` e `finance` continuam com o card.
- `SDET/AthleteOnboardingScreenshotTest.kt`: monta o card direto.
- `TDET/GroupDetailsScreenTest.kt`, `GroupShellBlocksTest`, `GroupOwnDebtBlockTest`, `GroupWaitingBlockTest`: nenhuma asserção depende de nó do hero antigo (`Section`, `Venue`, "Editar" da quadra saíram junto com o `GroupHeroBlockTest` antigo).

## Cenas de screenshot e prints do PR

### `SDET/GroupHeroScreenshotTest.kt` (arquivo completo)

```kotlin
package br.com.saqz.groups.presentation.ui.details

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsState
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Os estados do hero, um por prancha do mock. Estado fora da cena é estado não conferido. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupHeroScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, state: GroupDetailsState) = compose.captureDetails(name, state, "hero")

    @Test fun pending() = capture("group-hero-pending", GroupHeroPreviewData.pending)

    @Test fun confirmedWithToast() = capture("group-hero-confirmed-toast", GroupHeroPreviewData.confirmedToast)

    @Test fun responding() = capture("group-hero-responding", GroupHeroPreviewData.responding)

    @Test fun declined() = capture("group-hero-declined", GroupHeroPreviewData.declined)

    // "Alterar" é estado de composição (`remember`), não do ViewModel: a cena toca o botão.
    @Test
    fun changing() {
        compose.setContent {
            SaqzTheme {
                Box(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
                    GroupDetailsScreen(state = GroupHeroPreviewData.confirmed, onBack = {}, onIntent = {})
                }
            }
        }
        compose.onNodeWithTag(GroupGameResponseTags.Change).performClick()
        compose.onRoot().captureRoboImage("screenshots/hero/group-hero-changing.png")
    }

    @Test fun responseFailed() = capture("group-hero-response-failed", GroupHeroPreviewData.responseFailed)

    @Test fun closed() = capture("group-hero-closed", GroupHeroPreviewData.closed)

    @Test fun closedPending() = capture("group-hero-closed-pending", GroupHeroPreviewData.closedPending)

    @Test fun dayMemberFee() = capture("group-hero-day-member-fee", GroupHeroPreviewData.dayMemberFee)

    @Test fun rosterStale() = capture("group-hero-roster-stale", GroupHeroPreviewData.rosterStale)

    @Test fun rosterRefreshing() = capture("group-hero-roster-refreshing", GroupHeroPreviewData.rosterRefreshing)

    @Test fun mapFailed() = capture("group-hero-map-failed", GroupHeroPreviewData.mapFailed)

    @Test fun noAddress() = capture("group-hero-no-address", GroupHeroPreviewData.noAddress)

    @Test fun autoConfirmationFailed() = capture("group-hero-auto-failed", GroupHeroPreviewData.autoConfirmationFailed)

    @Test fun autoConfirmationUpdating() = capture("group-hero-auto-updating", GroupHeroPreviewData.autoConfirmationUpdating)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun athleteIntro() = capture("group-hero-athlete-intro", GroupHeroPreviewData.athleteIntro)

    @Test fun noGame() = capture("group-hero-no-game", GroupHeroPreviewData.noGame)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun reserve() = capture("group-hero-reserve", GroupHeroPreviewData.reserve)

    @Test
    @Config(qualifiers = "+h1600dp")
    fun dayMemberList() = capture("group-hero-day-member-list", GroupHeroPreviewData.dayMemberList)

    @Test fun adminPending() = capture("group-hero-admin-pending", GroupHeroPreviewData.adminPending)

    @Test fun adminConfirmed() = capture("group-hero-admin-confirmed", GroupHeroPreviewData.adminConfirmed)

    @Test fun adminClosed() = capture("group-hero-admin-closed", GroupHeroPreviewData.adminClosed)

    @Test fun adminNoGame() = capture("group-hero-admin-no-game", GroupHeroPreviewData.adminNoGame)

    @Test fun adminFirstGame() = capture("group-hero-admin-first-game", GroupHeroPreviewData.adminFirstGame)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun adminInviteGuide() = capture("group-hero-admin-invite-guide", GroupHeroPreviewData.adminInviteGuide)

    @Test
    @Config(qualifiers = "+h1400dp")
    fun adminFinanceGuide() = capture("group-hero-admin-finance-guide", GroupHeroPreviewData.adminFinanceGuide)
}
```

Mapa cena → prancha do mock (`_mock-grupo/png`):

| Cena (`screenshots/hero/…png`) | Prancha |
|---|---|
| `group-hero-pending` | `Main` |
| `group-hero-confirmed-toast` | `MembroConfirmado` |
| `group-hero-responding` | `MembroRespondendo` (divergência esperada: o ViewModel já trocou a resposta pelo otimista, então a cena mostra o painel com "Alterar" em loading, não os dois botões) |
| `group-hero-declined` | `MembroNaoVou` |
| `group-hero-changing` | `MembroAlterando` |
| `group-hero-response-failed` | `MembroErroResposta` |
| `group-hero-closed` / `-closed-pending` | `MembroEncerradas` / `MembroEncerradasSemResposta` |
| `group-hero-day-member-fee` | `MembroAvulsoTaxa` |
| `group-hero-roster-stale` / `-roster-refreshing` | `MembroListaDesatualizada` (+ o retry em loading, sem prancha) |
| `group-hero-map-failed` / `-no-address` | `MembroMapaFalhou` (+ jogo sem endereço, sem prancha) |
| `group-hero-auto-failed` / `-auto-updating` | `MembroAutoFalha` (+ switch travado, sem prancha) |
| `group-hero-athlete-intro` | `MembroIntro` |
| `group-hero-no-game` | `MembroSemJogo` |
| `group-hero-reserve` / `-day-member-list` | `MembroReserva` / `MembroListaAvulso` |
| `group-hero-admin-pending` / `-admin-confirmed` / `-admin-closed` | `GestorPendencias` / `GestorConfirmado` / `GestorEncerradas` |
| `group-hero-admin-no-game` / `-admin-first-game` | `GestorSemJogo` / `GestorGrupoNovo` |
| `group-hero-admin-invite-guide` / `-admin-finance-guide` | `GestorOnboardingConvite` / `GestorOnboardingAcerto` |

Os blocos abaixo do hero aparecem nas capturas no estado em que a `main` estiver (andaime ou já redesenhados por C2/C3): **só o hero, a linha de confirmação automática, os cards de guia/intro, os extras de espera e o toast são deste ticket**.

**Prints obrigatórios no corpo do PR** (branch órfã `screenshots`, pasta `vul-XXX/`): as 26 cenas acima + `onboarding/create.png` (mudou de conteúdo). Legendas: o nome da prancha do mock ao lado de cada uma.

**Registrar no corpo do PR, literalmente:** "2º uso do `SaqzHeroCard` fora do export oficial: decisão do usuário em 2026-09-17, no mesmo redesenho que levou o hero à Início (VUL-217). O KDoc do componente é atualizado no ticket D. As tags do placar dentro do hero do detalhe são as de `HomeAdminTags` (compartilhadas, como `HomeWaitlistTags`)."

## Gates

Rodar da raiz do worktree, nesta ordem:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:detektAll
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test --tests "br.com.saqz.groups.presentation.ui.details.*"
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:iosSimulatorArm64Test
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :features:groups:presentation:recordRoborazziAndroidHostTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:testDevDebugUnitTest
JAVA_HOME=$(/usr/libexec/java_home -v 21) mobile/gradlew -p mobile :android-app:compileDevDebugAndroidTestKotlin -Psaqz.e2e=true
```

O último compila o e2e: `mobile/android-app/build.gradle.kts` só acrescenta `src/e2e/kotlin` ao source set `androidTest` com `-Psaqz.e2e=true` (não existe source set `e2e` próprio).

Depois, com o emulador ligado (ler `tests/e2e/android/README.md` para o `SAQZ_E2E_FIREBASE_BIN`), os três cenários — `leave` é a prova de que o `GroupLeaveE2eTest` não precisava mudar:

```sh
node tests/e2e/android/run.mjs --serial <serial> --scenario attendance
node tests/e2e/android/run.mjs --serial <serial> --scenario attendance-order
node tests/e2e/android/run.mjs --serial <serial> --scenario leave
```

Depois do `recordRoborazzi`, **abrir e olhar** os 26 PNGs de `mobile/features/groups/presentation/screenshots/hero/` e `onboarding/create.png`, comparando cada um com a prancha do mapa acima. Conferir em especial: texto branco legível sobre o azul em TODOS os estados desabilitados (`closed-pending`, `roster-refreshing`, `admin-closed`); o "Ver jogo ›" não quebra o kicker em duas linhas; "Tentar novamente" não espreme o aviso em mais de 3 linhas.

## Critérios de aceite

- [ ] `GroupHeroBlock.kt` e `GroupToastBlock.kt` não têm mais `@Suppress` nem KDoc "Andaime (T)".
- [ ] `GroupHeroBlock.kt` não chama mais `GroupNextGameCard`, `GroupGameResponseSection`, `GroupAttendanceStats` nem `GroupVenueRow`; esses arquivos NÃO aparecem no diff.
- [ ] As tags `group-game-response-going` e `group-game-response-not-going` existem no máximo uma vez na árvore em qualquer estado.
- [ ] Os textos "Sua presença está confirmada." e "Você não vai jogar." aparecem exatamente uma vez quando respondido (o toast usa outros textos).
- [ ] Gestor com `GroupOnboarding.CreateGame`: um botão só no hero, nenhum `GroupOnboardingTags.Card`.
- [ ] Nenhum `dp` cru, nenhuma string hardcoded, nenhuma tag fora de `GroupDetailsTags`/`GroupGameResponseTags`/`HomeWaitlistTags`/`HomeAdminTags`.
- [ ] 32 testes de `GroupHeroBlockTest` verdes; `AthleteOnboardingCardTest`, `GroupOnboardingCardTest`, `GroupOnboardingScreenshotTest`, `AthleteOnboardingScreenshotTest` verdes sem edição.
- [ ] Cenários e2e `attendance`, `attendance-order` e `leave` verdes; `GroupLeaveE2eTest.kt` fora do diff.
- [ ] Nenhum arquivo de `details/` (Contract/ViewModel), `ui/home/`, `ui/components/`, `composeResources/` ou design system no diff.
- [ ] Corpo do PR registra o 2º uso do `SaqzHeroCard` e o reuso de `HomeAdminTags`.
- [ ] Diff ≤ 1500 linhas (estimativa: ~1250 — hero +335/−80, toast +45/−15, preview data +110, testes +330/−105, capturas +125, e2e +12/−8).

## Protocolo do worker

1. Branch a partir de `origin/main`: `git fetch origin && git switch -c vul-XXX-hero-detalhe-grupo origin/main` (XXX = número do ticket no Linear).
2. Commits pequenos em PT-BR no padrão do repo (`feat(groups): …`, `test(groups): …`).
3. Rodar TODOS os gates antes de abrir o PR; colar a saída resumida no corpo do PR. Ticket de UI: **abrir e olhar os PNGs gravados** antes de abrir o PR e embutir os prints obrigatórios.
4. PR contra `main`, aberto como ready (não draft), título `feat(groups): hero do próximo jogo e toast no detalhe do grupo (VUL-XXX)`. Teto: 2000 linhas de adições+remoções.
5. `gh` desta máquina: prefixar `GH_TOKEN=$(gh auth token --user bruno-halmeida)` nos comandos `gh`.
6. Depois de abrir o PR: PARAR. Não fazer triagem de review. Só executar mensagens do orquestrador que comecem com `CORRECAO:`.
