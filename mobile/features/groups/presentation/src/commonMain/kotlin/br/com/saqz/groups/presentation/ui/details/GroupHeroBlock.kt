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
