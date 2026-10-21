package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzScoreTab
import br.com.saqz.designsystem.SaqzScoreTabTone
import br.com.saqz.designsystem.SaqzScoreTabs
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.GameDetailHeaderUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailStatusTone
import br.com.saqz.groups.presentation.gamedetail.GameDetailTab
import br.com.saqz.groups.presentation.gamedetail.GameLimeLine
import br.com.saqz.groups.presentation.ui.components.HeroAlertLine
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceControls
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTags
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTexts
import br.com.saqz.groups.presentation.ui.components.HeroFeeAlpha
import br.com.saqz.groups.presentation.ui.components.HeroFeeNote
import br.com.saqz.groups.presentation.ui.components.HeroResponseLine
import br.com.saqz.groups.presentation.ui.components.lateNoticeText
import br.com.saqz.groups.presentation.ui.pluralLabel
import br.com.saqz.groups.presentation.ui.singularLabel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.communication_failure
import br.com.saqz.groups.resources.communication_remind_refused
import br.com.saqz.groups.resources.communication_reminded
import br.com.saqz.groups.resources.game_detail_confirmation_deadline
import br.com.saqz.groups.resources.game_detail_duration
import br.com.saqz.groups.resources.game_detail_duration_hours
import br.com.saqz.groups.resources.game_detail_duration_minutes
import br.com.saqz.groups.resources.game_detail_publish
import br.com.saqz.groups.resources.game_detail_publish_after_plan_end
import br.com.saqz.groups.resources.game_detail_publish_failed
import br.com.saqz.groups.resources.game_detail_publish_hint
import br.com.saqz.groups.resources.game_hero_until
import br.com.saqz.groups.resources.game_kicker_cancelled
import br.com.saqz.groups.resources.game_kicker_completed
import br.com.saqz.groups.resources.game_kicker_deadline
import br.com.saqz.groups.resources.game_kicker_draft
import br.com.saqz.groups.resources.game_kicker_full
import br.com.saqz.groups.resources.game_kicker_open
import br.com.saqz.groups.resources.game_kicker_started
import br.com.saqz.groups.resources.game_lime_and
import br.com.saqz.groups.resources.game_lime_missing
import br.com.saqz.groups.resources.game_lime_open_positions
import br.com.saqz.groups.resources.game_pending_people
import br.com.saqz.groups.resources.game_response_confirmed
import br.com.saqz.groups.resources.game_response_declined
import br.com.saqz.groups.resources.game_response_no
import br.com.saqz.groups.resources.game_response_request_failed
import br.com.saqz.groups.resources.game_response_yes
import br.com.saqz.groups.resources.game_settlement_open
import br.com.saqz.groups.resources.game_tab_capacity
import br.com.saqz.groups.resources.game_tab_not_played
import br.com.saqz.groups.resources.game_tab_played
import br.com.saqz.groups.resources.group_details_map_failure
import br.com.saqz.groups.resources.group_details_venue_map
import br.com.saqz.groups.resources.group_details_waiting_notify
import br.com.saqz.groups.resources.home_admin_score_going
import br.com.saqz.groups.resources.home_admin_score_out
import br.com.saqz.groups.resources.home_admin_score_waiting
import br.com.saqz.groups.resources.home_attendance_cancel
import br.com.saqz.groups.resources.home_attendance_change
import br.com.saqz.groups.resources.home_spots_left
import br.com.saqz.groups.resources.subscription_paused_athlete
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GameDetailAttendanceTags {
    const val Going = "game-detail-attendance-going"
    const val NotGoing = "game-detail-attendance-not-going"
    const val Change = "game-detail-attendance-change"
    const val Cancel = "game-detail-attendance-cancel"
    const val Error = "game-detail-attendance-error"
}

/**
 * O cabeçalho azul de borda a borda (DEB-116): kicker do estado, "Terça, 19h30", data, duração e quadra, a
 * resposta de quem olha (o mesmo `HeroAttendanceControls` da Início e do grupo), o que falta, o Avisar do
 * gestor e, colado no fim, o placar que troca a aba da lista.
 */
@Composable
internal fun GameDetailHero(
    state: GameDetailState,
    selectedTab: GameDetailTab,
    tabs: List<GameDetailTab>,
    onSelectTab: (GameDetailTab) -> Unit,
    onIntent: (GameDetailIntent) -> Unit,
) {
    val header = state.header ?: return
    val scoreTabs = state.scoreTabs(tabs)
    SaqzHeroCard(
        kicker = stringResource(state.kickerResource(header)),
        title = state.hero?.title ?: header.dateTime,
        shape = RectangleShape,
        kickerMuted = header.statusTone != GameDetailStatusTone.Published,
        modifier = Modifier.testTag(GameDetailTags.Hero),
        footer = if (scoreTabs.isEmpty()) {
            null
        } else {
            {
                SaqzScoreTabs(
                    tabs = scoreTabs,
                    selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0),
                    onSelect = { onSelectTab(tabs[it]) },
                )
            }
        },
    ) {
        GameHeroInfo(state, header, onIntent)
        when (header.statusTone) {
            GameDetailStatusTone.Published -> GamePublishedBlock(state, header, onIntent)
            GameDetailStatusTone.Draft -> if (state.isAdmin) GamePublishBlock(state, onIntent)
            GameDetailStatusTone.Completed -> if (state.isAdmin) {
                SaqzButton(
                    label = stringResource(Res.string.game_settlement_open),
                    onClick = { onIntent(GameDetailIntent.OpenSettlement) },
                    modifier = Modifier.testTag(GameDetailTags.Settle),
                    variant = SaqzButtonVariant.Accent,
                    fullWidth = true,
                )
            }
            GameDetailStatusTone.Cancelled -> Unit
        }
    }
}

private fun GameDetailState.kickerResource(header: GameDetailHeaderUi) = when (header.statusTone) {
    GameDetailStatusTone.Draft -> Res.string.game_kicker_draft
    GameDetailStatusTone.Completed -> Res.string.game_kicker_completed
    GameDetailStatusTone.Cancelled -> Res.string.game_kicker_cancelled
    GameDetailStatusTone.Published -> when {
        header.gameStarted -> Res.string.game_kicker_started
        header.closedByDeadline -> Res.string.game_kicker_deadline
        (attendance?.availableSpots ?: header.availableSpots) <= 0 -> Res.string.game_kicker_full
        else -> Res.string.game_kicker_open
    }
}

@Composable
private fun GameDetailState.scoreTabs(tabs: List<GameDetailTab>): List<SaqzScoreTab> {
    val completed = header?.statusTone == GameDetailStatusTone.Completed
    val confirmed = attendance?.confirmed ?: confirmedRoster.size
    return tabs.map { tab ->
        when (tab) {
            GameDetailTab.Going -> SaqzScoreTab(
                number = "$confirmed",
                label = stringResource(if (completed) Res.string.game_tab_played else Res.string.home_admin_score_going),
                suffix = attendance?.capacity?.let { stringResource(Res.string.game_tab_capacity, it) },
                tone = SaqzScoreTabTone.Accent,
                testTag = GameDetailTags.TabGoing,
            )
            GameDetailTab.Waiting -> SaqzScoreTab(
                number = "${waitlist.size}",
                label = stringResource(Res.string.home_admin_score_waiting),
                testTag = GameDetailTags.TabWaiting,
            )
            GameDetailTab.Declined -> SaqzScoreTab(
                number = "$declinedCount",
                label = stringResource(if (completed) Res.string.game_tab_not_played else Res.string.home_admin_score_out),
                tone = SaqzScoreTabTone.Muted,
                testTag = GameDetailTags.TabDeclined,
            )
        }
    }
}

/** Data, duração com o fim e a quadra com "Ver no mapa" e o endereço embaixo; uma linha por informação. */
@Composable
private fun GameHeroInfo(state: GameDetailState, header: GameDetailHeaderUi, onIntent: (GameDetailIntent) -> Unit) {
    val colors = SaqzTheme.colors
    val muted = colors.onPrimary.copy(alpha = HeroFeeAlpha)
    val hero = state.hero
    val address = hero?.address.orEmpty()
    Column(
        modifier = Modifier.padding(vertical = InfoBlockPadding),
        verticalArrangement = Arrangement.spacedBy(InfoGap),
    ) {
        HeroInfoLine(SaqzIcons.Calendar, hero?.date ?: header.dateTime)
        HeroInfoLine(
            SaqzIcons.Timer,
            stringResource(Res.string.game_detail_duration, durationLabel(header.durationMinutes)),
            extra = hero?.endTime?.let { stringResource(Res.string.game_hero_until, it) },
        )
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InfoIconGap)) {
                SaqzIcon(SaqzIcons.Pin, tint = muted, size = InfoIconSize)
                Text(text = header.venue, style = infoStyle(), color = colors.onPrimary, modifier = Modifier.weight(1f))
                if (address.isNotEmpty()) {
                    SaqzButton(
                        label = stringResource(Res.string.group_details_venue_map),
                        onClick = { onIntent(GameDetailIntent.OpenVenueMap) },
                        modifier = Modifier.testTag(GameDetailTags.Map),
                        variant = SaqzButtonVariant.Ghost,
                        size = SaqzButtonSize.Sm,
                        contentColor = colors.onPrimary,
                    )
                }
            }
            if (address.isNotEmpty()) {
                Text(
                    text = address,
                    style = SaqzTheme.typography.support,
                    color = muted,
                    modifier = Modifier.padding(start = InfoIconSize + InfoIconGap),
                )
            }
        }
        if (state.mapFailed) {
            HeroAlertLine(text = stringResource(Res.string.group_details_map_failure), tag = GameDetailTags.MapFailure)
        }
    }
}

@Composable
private fun HeroInfoLine(icon: ImageVector, text: String, extra: String? = null) {
    val colors = SaqzTheme.colors
    val muted = colors.onPrimary.copy(alpha = HeroFeeAlpha)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(InfoIconGap)) {
        SaqzIcon(icon, tint = muted, size = InfoIconSize)
        Text(
            text = buildAnnotatedString {
                append(text)
                if (extra != null) {
                    withStyle(SpanStyle(color = muted, fontWeight = FontWeight.Normal, fontSize = 14.sp)) { append(" · $extra") }
                }
            },
            style = infoStyle(),
            color = colors.onPrimary,
        )
    }
}

@Composable
private fun infoStyle() = SaqzTheme.typography.body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

/** Publicado: prazo e resposta como hoje, a nota da cobrança do avulso, o Avisar do gestor e o que falta. */
@Composable
private fun ColumnScope.GamePublishedBlock(
    state: GameDetailState,
    header: GameDetailHeaderUi,
    onIntent: (GameDetailIntent) -> Unit,
) {
    val response = state.response
    if (response.visible) {
        HeroResponseLine(
            confirmationOpen = header.confirmationOpen,
            responseOpen = header.responseOpen,
            closedByDeadline = header.closedByDeadline,
            openText = stringResource(Res.string.game_detail_confirmation_deadline, header.deadlineLabel()),
        )
        HeroAttendanceControls(
            status = response.status,
            confirmationOpen = header.canRespond,
            lateNotice = if (!header.confirmationOpen) lateNoticeText(state.promotionMode) else null,
            responding = response.responding,
            responseFailed = response.failed || response.planPaused,
            responseOffline = response.offline,
            waitlistKind = response.waitlistKind,
            spotsOpen = (state.attendance?.availableSpots ?: 0) > 0,
            waitlistPosition = response.waitlistPosition,
            onRespond = { onIntent(GameDetailIntent.Respond(it)) },
            onViewGame = null,
            texts = HeroAttendanceTexts(
                yes = stringResource(Res.string.game_response_yes),
                no = stringResource(Res.string.game_response_no),
                confirmed = stringResource(Res.string.game_response_confirmed),
                declined = stringResource(Res.string.game_response_declined),
                change = stringResource(Res.string.home_attendance_change),
                cancel = stringResource(Res.string.home_attendance_cancel),
                error = stringResource(
                    if (response.planPaused) Res.string.subscription_paused_athlete else Res.string.game_response_request_failed,
                ),
            ),
            tags = GameDetailHeroAttendanceTags,
        )
    }
    if (state.showsFeeNote && header.canRespond) HeroFeeNote(GameDetailTags.FeeNote)
    if (state.canNotify) GameNotifyPanel(state, onIntent)
    state.limeLine?.let { GameLimeLineRow(it) }
}

/** Rascunho (gestor): o "Publicar jogo" lima no lugar do "Vou", com o apoio ou o erro de hoje. */
@Composable
private fun GamePublishBlock(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) = Column(
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzButton(
        label = stringResource(Res.string.game_detail_publish),
        onClick = { onIntent(GameDetailIntent.Publish) },
        modifier = Modifier.testTag(GameDetailTags.Publish),
        variant = SaqzButtonVariant.Accent,
        fullWidth = true,
        enabled = !state.publishing,
        loading = state.publishing,
    )
    Text(
        text = stringResource(
            when {
                state.publishAfterPlanEnd -> Res.string.game_detail_publish_after_plan_end
                state.publishFailed -> Res.string.game_detail_publish_failed
                else -> Res.string.game_detail_publish_hint
            },
        ),
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.onPrimary.copy(alpha = HeroFeeAlpha),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "4 pessoas sem resposta" + "Avisar" branco; depois do envio, o check e quantos receberam. */
@Composable
private fun GameNotifyPanel(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) {
    val colors = SaqzTheme.colors
    val reminder = state.reminder
    val notified = reminder.notifiedCount
    val pending = state.attendance?.pending ?: 0
    val meta = when {
        reminder.refused -> stringResource(Res.string.communication_remind_refused)
        reminder.failed -> stringResource(Res.string.communication_failure)
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.onPrimary.copy(alpha = PanelAlpha), RoundedCornerShape(PanelRadius))
            .heightIn(min = PanelMinHeight)
            .padding(start = PanelStartPadding, end = PanelEndPadding, top = PanelEndPadding, bottom = PanelEndPadding)
            .testTag(GameDetailTags.NotifyPanel),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(if (notified != null) SaqzIcons.Check else SaqzIcons.Megaphone, tint = colors.onPrimary, size = PanelIconSize)
        Column(Modifier.weight(1f)) {
            Text(
                text = if (notified != null) {
                    pluralStringResource(Res.plurals.communication_reminded, notified, notified)
                } else {
                    pluralStringResource(Res.plurals.game_pending_people, pending, pending)
                },
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
            )
            if (meta != null) {
                Text(text = meta, style = SaqzTheme.typography.support, color = colors.onPrimary.copy(alpha = HeroFeeAlpha))
            }
        }
        if (notified == null) {
            SaqzButton(
                label = stringResource(Res.string.group_details_waiting_notify),
                onClick = { onIntent(GameDetailIntent.NotifyPending) },
                modifier = Modifier.testTag(GameDetailTags.NotifyPending),
                variant = SaqzButtonVariant.Inverse,
                size = SaqzButtonSize.Sm,
                enabled = !reminder.sending,
                loading = reminder.sending,
            )
        }
    }
}

/** A linha verde-limão: ponto e o que falta. */
@Composable
private fun GameLimeLineRow(line: GameLimeLine) {
    val accent = SaqzTheme.colors.accent
    val and = stringResource(Res.string.game_lime_and)
    val text = when (line) {
        is GameLimeLine.SpotsLeft -> pluralStringResource(Res.plurals.home_spots_left, line.spots, line.spots)
        is GameLimeLine.OpenPositions -> stringResource(
            Res.string.game_lime_open_positions,
            line.positions.map { it.singularLabel().lowercase() }.joinPt(and),
        )
        is GameLimeLine.Missing -> {
            val total = line.missing.sumOf { it.second }
            val items = line.missing.map { (position, count) ->
                "$count ${if (count == 1) position.singularLabel().lowercase() else position.pluralLabel()}"
            }
            pluralStringResource(Res.plurals.game_lime_missing, total, items.joinPt(and))
        }
    }
    Row(
        modifier = Modifier.testTag(GameDetailTags.LimeLine),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(LimeDotSize).background(accent, CircleShape))
        Text(text = text, style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold), color = accent)
    }
}

/** "central", "central e oposto(a)", "ponteiro(a), central e oposto(a)". */
internal fun List<String>.joinPt(and: String): String = when (size) {
    0 -> ""
    1 -> first()
    else -> dropLast(1).joinToString(", ") + " $and " + last()
}

@Composable
private fun durationLabel(minutes: Int) = when {
    minutes % MINUTES_IN_HOUR == 0 -> stringResource(Res.string.game_detail_duration_hours, minutes / MINUTES_IN_HOUR)
    else -> stringResource(Res.string.game_detail_duration_minutes, minutes)
}

private val GameDetailHeroAttendanceTags = HeroAttendanceTags(
    yes = GameDetailAttendanceTags.Going,
    no = GameDetailAttendanceTags.NotGoing,
    change = GameDetailAttendanceTags.Change,
    cancel = GameDetailAttendanceTags.Cancel,
    error = GameDetailAttendanceTags.Error,
)

private const val MINUTES_IN_HOUR = 60
private const val PanelAlpha = 0.12f
private val PanelRadius = 10.dp
private val PanelMinHeight = 52.dp
private val PanelStartPadding = 14.dp
private val PanelEndPadding = 6.dp
private val PanelIconSize = 20.dp
private val InfoBlockPadding = 2.dp
private val InfoGap = 10.dp
private val InfoIconGap = 8.dp
private val InfoIconSize = 18.dp
private val LimeDotSize = 6.dp
