package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.saqzInitials
import br.com.saqz.designsystem.resources.Res as DsRes
import br.com.saqz.designsystem.resources.saqz_mark
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeToast
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_attendance_cancel
import br.com.saqz.groups.resources.home_attendance_change
import br.com.saqz.groups.resources.home_error_message
import br.com.saqz.groups.resources.home_error_title
import br.com.saqz.groups.resources.home_game_next
import br.com.saqz.groups.resources.home_admin_group_chip
import br.com.saqz.groups.resources.home_groups_title
import br.com.saqz.groups.resources.home_groups_view_all
import br.com.saqz.groups.resources.home_greeting
import br.com.saqz.groups.resources.home_no_game_action
import br.com.saqz.groups.resources.home_no_game_description
import br.com.saqz.groups.resources.home_no_game_hero_title
import br.com.saqz.groups.resources.home_notifications_cd
import br.com.saqz.groups.resources.home_response_error
import br.com.saqz.groups.resources.home_response_no
import br.com.saqz.groups.resources.home_response_yes
import br.com.saqz.groups.resources.home_retry
import br.com.saqz.groups.resources.home_spots_left
import br.com.saqz.groups.resources.home_status_confirmed
import br.com.saqz.groups.resources.home_status_declined
import br.com.saqz.groups.resources.home_toast_confirmed
import br.com.saqz.groups.resources.home_toast_declined
import br.com.saqz.groups.resources.home_toast_pix_copied
import br.com.saqz.groups.resources.home_toast_waitlisted
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object HomeTags {
    const val Content = "home-content"
    const val Error = "home-error"
    const val Loading = "home-loading"
    const val Retry = "home-retry"
    const val NextGame = "home-next-game"
    const val Empty = "home-empty"
    const val Groups = "home-groups"
    const val Notifications = "home-notifications"
    const val ResponseYes = "home-response-yes"
    const val ResponseNo = "home-response-no"
    const val ResponseChange = "home-response-change"
    const val ResponseCancel = "home-response-cancel"
    const val ResponseError = "home-response-error"
    const val Toast = "home-toast"
    const val OwnCharges = "home-own-charges"
    const val OwnChargesBanner = "home-own-charges-banner"

    fun group(id: String) = "home-group-$id"

    fun ownCharge(id: String) = "home-own-charge-$id"

    fun ownChargePixCopy(id: String) = "home-own-charge-pix-copy-$id"
}

@Composable
fun HomeScreen(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> HomeSkeleton(modifier)
        state.loadFailed -> HomeFailure(
            onRetry = { onIntent(HomeIntent.Retry) },
            modifier = modifier,
        )
        else -> HomeContent(state, onIntent, modifier)
    }
}

@Composable
private fun HomeSkeleton(modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    // Espelha o layout real (greeting, hero com as duas pílulas do RSVP, lista de
    // grupos): quanto mais o skeleton parece o conteúdo, menor o salto na troca.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .padding(metrics.horizontalPadding)
            .testTag(HomeTags.Loading),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSkeleton(width = metrics.buttonHeight * 4, height = metrics.buttonHeight / 2)
        SaqzSkeleton(width = metrics.buttonHeight * 6, height = metrics.buttonHeight / 3)
        SaqzCard {
            SaqzSkeleton(width = metrics.buttonHeight * 5, height = metrics.buttonHeight / 2)
            SaqzSkeleton(height = metrics.buttonHeight / 3)
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                SaqzSkeleton(height = metrics.buttonHeight, modifier = Modifier.weight(1f))
                SaqzSkeleton(height = metrics.buttonHeight, modifier = Modifier.weight(1f))
            }
            SaqzSkeleton(height = metrics.avatarSize)
        }
        SaqzSkeleton(width = metrics.buttonHeight * 3, height = metrics.buttonHeight / 3)
        SaqzCard {
            SaqzSkeleton(height = metrics.avatarSize)
            SaqzSkeleton(height = metrics.avatarSize)
        }
    }
}

@Composable
private fun HomeFailure(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(HomeTags.Error),
        contentAlignment = Alignment.Center,
    ) {
        SaqzEmptyState(
            title = stringResource(Res.string.home_error_title),
            description = stringResource(Res.string.home_error_message),
            action = stringResource(Res.string.home_retry),
            onAction = onRetry,
            modifier = Modifier.testTag(HomeTags.Retry),
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val member = state.member
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(HomeTags.Content),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
        ) {
            HomeHeader(displayName = state.displayName, subtitle = member?.adminSubtitle, onIntent = onIntent)
            if (member == null) {
                Text(
                    text = stringResource(Res.string.home_error_message),
                    style = SaqzTheme.typography.body,
                    color = SaqzTheme.colors.textSecondary,
                )
            } else {
                member.nextGame?.let {
                    if (isAdminOfNextGame(it, member.admin)) {
                        HomeAdminHero(
                            game = it,
                            responding = state.responding,
                            responseFailed = state.responseFailed,
                            onIntent = onIntent,
                        )
                    } else {
                        HomeHero(
                            game = it,
                            responding = state.responding,
                            responseFailed = state.responseFailed,
                            onIntent = onIntent,
                        )
                    }
                    // Fora do if: quem está na reserva vê a fila, admin ou não.
                    HomeWaitlistExtras(game = it)
                } ?: member.admin?.let { admin -> HomeAdminNoGame(admin = admin, onIntent = onIntent) } ?: HomeNoGame(onIntent)
                // Dívida vencida é tão pessoal quanto o RSVP: vem logo abaixo do hero (VUL-220).
                state.ownCharges?.takeIf { it.overdueGroups.isNotEmpty() }?.let {
                    HomeOwnChargesSection(ownCharges = it, pixCopiedGroupId = state.pixCopiedGroupId, onIntent = onIntent)
                }
                // Para o admin, o que o grupo espera dele é tão pessoal quanto o que ele
                // deve — "Esperando você" sobe para logo abaixo do hero, antes das
                // seções individuais (VUL-202) e do histórico.
                member.admin?.let { admin ->
                    HomeAdminWaitingSection(admin = admin, onIntent = onIntent)
                    // Sem jogo marcado os dois verbos já estão no hero (HomeAdminNoGame).
                    if (member.nextGame != null) {
                        HomeAdminShortcuts(admin = admin, nextGame = member.nextGame, onIntent = onIntent)
                    }
                }
                HomeUpcomingSection(games = member.upcomingGames, onIntent = onIntent)
                HomeGroups(member.groups, onIntent)
            }
        }
        state.toast?.let { toast ->
            SaqzToast(
                visible = true,
                onDismiss = { onIntent(HomeIntent.DismissToast) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(metrics.horizontalPadding)
                    .testTag(HomeTags.Toast),
            ) {
                SaqzToastText(
                    text = stringResource(
                        when (toast) {
                            HomeToast.Confirmed -> Res.string.home_toast_confirmed
                            HomeToast.Declined -> Res.string.home_toast_declined
                            HomeToast.Waitlisted -> Res.string.home_toast_waitlisted
                            HomeToast.PixCopied -> Res.string.home_toast_pix_copied
                        },
                    ),
                )
            }
        }
    }
}

/**
 * Marca, saudação e sino (VUL-219). O sino abre Notificações — hoje só se chega lá pelo
 * Perfil. Sem ponto de não lidas: não existe contagem no agregado. O subtítulo é só o do
 * gestor ("N grupos · M coisas esperando você"); o do atleta saiu porque repetia o hero.
 */
@Composable
private fun HomeHeader(
    displayName: String?,
    subtitle: String?,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HeaderMarkGap),
        ) {
            Image(
                painter = painterResource(DsRes.drawable.saqz_mark),
                contentDescription = null,
                modifier = Modifier.size(HeaderMarkSize),
            )
            if (displayName != null) {
                Text(
                    text = stringResource(Res.string.home_greeting, displayName),
                    style = SaqzTheme.typography.title.copy(fontWeight = FontWeight(800)),
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Box(modifier = Modifier.weight(1f))
            }
            SaqzIconButton(
                onClick = { onIntent(HomeIntent.OpenNotifications) },
                contentDescription = stringResource(Res.string.home_notifications_cd),
                modifier = Modifier.testTag(HomeTags.Notifications),
            ) {
                SaqzIcon(SaqzIcons.Bell)
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = HeaderMarkSize + HeaderMarkGap),
            )
        }
    }
}

private val HeaderMarkSize = 30.dp
private val HeaderMarkGap = 10.dp

@Composable
private fun HomeHero(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = game.display,
        meta = game.meta,
        trailing = { SaqzStatusChip(text = game.groupName, tone = SaqzChipTone.Inverse) },
        modifier = Modifier.testTag(HomeTags.NextGame),
    ) {
        HomeDeadlineLine(text = game.deadline, open = game.confirmationOpen)
        HomeAttendanceControls(
            game = game,
            responding = responding,
            responseFailed = responseFailed,
            onIntent = onIntent,
        )
        // Em espera, as seções abaixo do hero já mostram o roster: a linha some.
        if (game.ownAttendance != AttendanceStatus.Waitlisted) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                SaqzAvatarStack(
                    names = game.rosterNames,
                    ring = colors.primary,
                    overflowContainer = colors.accent,
                    overflowContent = colors.textPrimary,
                )
                Text(
                    text = game.confirmedSummary,
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onPrimary.copy(alpha = HeroSummaryAlpha),
                    modifier = Modifier.weight(1f),
                )
                val spotsLeft = game.capacity - game.confirmedCount
                if (spotsLeft in 1..SpotsLeftMax) {
                    SaqzStatusChip(
                        text = stringResource(Res.string.home_spots_left, spotsLeft),
                        tone = SaqzChipTone.Inverse,
                        dot = true,
                    )
                }
            }
        }
    }
}

/** Linha do prazo do RSVP: relógio + texto; com o prazo encerrado fica mais apagada. */
@Composable
internal fun HomeDeadlineLine(text: String, open: Boolean) {
    val color = SaqzTheme.colors.onPrimary.copy(alpha = if (open) HeroTextAlpha else HeroMutedAlpha)
    Row(
        horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.Clock, tint = color, size = HeroLineIconSize)
        Text(
            text = text,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

/** Escassez que muda comportamento: abaixo disso o "9 de 12" vira "restam N". */
private const val SpotsLeftMax = 3

// Opacidades do branco sobre o azul do hero (VUL-218). Só `onPrimary` com alfa: não há
// token de branco translúcido no contrato, e derivar do sólido é o padrão dos chips.
private const val HeroTextAlpha = 0.9f
private const val HeroMutedAlpha = 0.7f
private const val HeroSummaryAlpha = 0.88f
private const val HeroPanelAlpha = 0.12f
private const val HeroDotMutedAlpha = 0.45f
internal const val HeroOutlineAlpha = 0.45f
private val HeroIconGap = 6.dp
private val HeroLineIconSize = 14.dp
private val HeroInlineIconSize = 16.dp
private val HeroCheckSize = 18.dp
private val HeroStatusDot = 10.dp

/**
 * Seletor de presença do hero. Vive fora do `HomeHero` porque o hero do admin
 * (VUL-192) usa o mesmo bloco: dono e admin são atletas do grupo e respondem
 * presença no mesmo lugar que todo mundo.
 */
@Composable
internal fun ColumnScope.HomeAttendanceControls(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    when (game.ownAttendance) {
        AttendanceStatus.Waitlisted -> {
            val kind = game.waitlistKind ?: HomeWaitlistKind.Reserva
            HomeWaitlistChip(kind = kind, position = game.waitlistPosition)
            HomeWaitlistInfoBox(kind = kind)
            HomeWaitlistActions(
                kind = kind,
                responding = responding,
                confirmationOpen = game.confirmationOpen,
                onLeave = { onIntent(HomeIntent.Respond(AttendanceIntent.Decline)) },
                onViewGame = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
            )
        }
        else -> {
            // Pendente é o momento do toque: botões grandes, pergunta aberta.
            // Depois de respondido, os botões somem — dois botões habilitados num jogo
            // já confirmado pareciam uma pergunta sem resposta e confundiam o atleta.
            val answered = game.ownAttendance
            if (answered == null) {
                HomeResponseRow(
                    ownAttendance = answered,
                    confirmationOpen = game.confirmationOpen,
                    responding = responding,
                    size = SaqzButtonSize.Md,
                    onIntent = onIntent,
                )
            } else {
                HomeAnsweredStatus(
                    status = answered,
                    responding = responding,
                    changeEnabled = game.confirmationOpen,
                    onIntent = onIntent,
                )
            }
        }
    }
    if (responseFailed) {
        // Não há token de erro legível sobre azul: o aviso é branco com o ícone de alerta.
        val color = colors.onPrimary.copy(alpha = HeroTextAlpha)
        Row(
            modifier = Modifier.testTag(HomeTags.ResponseError),
            horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzIcon(SaqzIcons.CircleAlert, tint = color, size = HeroInlineIconSize)
            Text(
                text = stringResource(Res.string.home_response_error),
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Medium),
                color = color,
            )
        }
    }
}

/**
 * "Vou" é sempre o CTA lima; "Não vou" é contorno branco. Com resposta marcada (modo
 * alterar) a marcada leva o check: "Vou" continua lima, "Não vou" marcado vira branco sólido
 * e o outro cai para contorno.
 */
@Composable
internal fun HomeResponseRow(
    ownAttendance: AttendanceStatus?,
    confirmationOpen: Boolean,
    responding: Boolean,
    size: SaqzButtonSize,
    onIntent: (HomeIntent) -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val declined = ownAttendance == AttendanceStatus.Declined
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        HomeResponseButton(
            label = stringResource(Res.string.home_response_yes),
            variant = if (declined) HomeResponseVariant.Outline else HomeResponseVariant.Accent,
            selected = ownAttendance == AttendanceStatus.Confirmed,
            loading = responding && ownAttendance == AttendanceStatus.Confirmed,
            enabled = confirmationOpen && !responding,
            size = size,
            modifier = Modifier.weight(1f).testTag(HomeTags.ResponseYes),
            onClick = { onIntent(HomeIntent.Respond(AttendanceIntent.Confirm)) },
        )
        HomeResponseButton(
            label = stringResource(Res.string.home_response_no),
            variant = if (declined) HomeResponseVariant.Inverse else HomeResponseVariant.Outline,
            selected = declined,
            loading = responding && declined,
            enabled = confirmationOpen && !responding,
            size = size,
            modifier = Modifier.weight(1f).testTag(HomeTags.ResponseNo),
            onClick = { onIntent(HomeIntent.Respond(AttendanceIntent.Decline)) },
        )
    }
}

private enum class HomeResponseVariant { Accent, Inverse, Outline }

/**
 * Estado de quem já respondeu: painel único com a resposta e "Alterar" — os botões só
 * reaparecem a pedido (`editing`), porque dois botões habilitados num jogo já
 * confirmado não dizem que a resposta foi registrada e parecem exigir nova ação.
 * `editing` vive na composição e morre quando o status muda: responder de novo
 * (com sucesso) volta para o painel automaticamente.
 */
@Composable
private fun HomeAnsweredStatus(
    status: AttendanceStatus,
    responding: Boolean,
    changeEnabled: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    var editing by remember(status) { mutableStateOf(false) }
    if (editing) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            HomeResponseRow(
                ownAttendance = status,
                confirmationOpen = changeEnabled,
                responding = responding,
                size = SaqzButtonSize.Sm,
                onIntent = onIntent,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaqzButton(
                    label = stringResource(Res.string.home_attendance_cancel),
                    onClick = { editing = false },
                    variant = SaqzButtonVariant.Ghost,
                    contentColor = colors.onPrimary,
                    size = SaqzButtonSize.Sm,
                    enabled = !responding,
                    modifier = Modifier.testTag(HomeTags.ResponseCancel),
                )
            }
        }
    } else {
        val (color, text) = when (status) {
            AttendanceStatus.Confirmed -> colors.success to stringResource(Res.string.home_status_confirmed)
            else -> colors.onPrimary.copy(alpha = HeroDotMutedAlpha) to stringResource(Res.string.home_status_declined)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.onPrimary.copy(alpha = HeroPanelAlpha), RoundedCornerShape(metrics.inputRadius))
                .padding(
                    start = metrics.blockGap,
                    end = metrics.subGrid,
                    top = metrics.subGrid,
                    bottom = metrics.subGrid,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid * 2),
        ) {
            Box(
                modifier = Modifier
                    .size(HeroStatusDot)
                    .background(color, CircleShape),
            )
            Text(
                text = text,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.home_attendance_change),
                onClick = { editing = true },
                variant = SaqzButtonVariant.Ghost,
                contentColor = colors.onPrimary,
                size = SaqzButtonSize.Sm,
                enabled = changeEnabled && !responding,
                loading = responding,
                modifier = Modifier.testTag(HomeTags.ResponseChange),
            )
        }
    }
}

@Composable
private fun HomeResponseButton(
    label: String,
    variant: HomeResponseVariant,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    size: SaqzButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val saqzVariant = when (variant) {
        HomeResponseVariant.Accent -> SaqzButtonVariant.Accent
        HomeResponseVariant.Inverse -> SaqzButtonVariant.Inverse
        HomeResponseVariant.Outline -> SaqzButtonVariant.Ghost
    }
    val outline = variant == HomeResponseVariant.Outline
    SaqzButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        variant = saqzVariant,
        size = size,
        fullWidth = true,
        enabled = enabled,
        loading = loading,
        contentColor = if (outline) colors.onPrimary else null,
        borderColor = if (outline) colors.onPrimary.copy(alpha = HeroOutlineAlpha) else null,
        leadingContent = if (selected) {
            { tint -> SaqzIcon(SaqzIcons.Check, tint = tint, size = HeroCheckSize) }
        } else {
            null
        },
    )
}

/**
 * Seções extras que aparecem abaixo do hero só quando o membro está em espera:
 * reserva (6b) mostra os confirmados e o card sino; lista do avulso (6e) mostra a
 * linha de posição, a fila e o upsell. Decomposto em `HomeWaitlistSections.kt`
 * para o VUL-192 (home do admin) reutilizar as peças.
 */
@Composable
private fun HomeWaitlistExtras(
    game: HomeNextGameUi,
) {
    if (game.ownAttendance != AttendanceStatus.Waitlisted) return
    val kind = game.waitlistKind ?: HomeWaitlistKind.Reserva
    when (kind) {
        HomeWaitlistKind.Reserva -> {
            HomeWaitlistConfirmedSection(
                confirmedRoster = game.confirmedRoster,
                confirmedCount = game.confirmedCount,
                capacity = game.capacity,
            )
            if (game.deadlineBellLabel.isNotEmpty()) {
                HomeWaitlistBellCard(label = game.deadlineBellLabel)
            }
        }
        HomeWaitlistKind.AvulsoList -> {
            game.waitlistPosition?.let { position ->
                HomeWaitlistPositionLine(
                    position = position,
                    confirmedCount = game.confirmedCountTotal,
                )
            }
            HomeWaitlistQueueSection(rows = game.waitlistedRoster)
            HomeWaitlistUpsellCard()
        }
    }
}

@Composable
private fun HomeNoGame(onIntent: (HomeIntent) -> Unit) {
    val colors = SaqzTheme.colors
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = stringResource(Res.string.home_no_game_hero_title),
        meta = stringResource(Res.string.home_no_game_description),
        modifier = Modifier.testTag(HomeTags.Empty),
    ) {
        SaqzButton(
            label = stringResource(Res.string.home_no_game_action),
            onClick = { onIntent(HomeIntent.OpenGroups) },
            variant = SaqzButtonVariant.Ghost,
            contentColor = colors.onPrimary,
            borderColor = colors.onPrimary.copy(alpha = HeroOutlineAlpha),
            fullWidth = true,
        )
    }
}

@Composable
private fun HomeGroups(groups: List<HomeGroupUi>, onIntent: (HomeIntent) -> Unit) {
    Column(
        modifier = Modifier.testTag(HomeTags.Groups),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(
            title = stringResource(Res.string.home_groups_title),
            action = stringResource(Res.string.home_groups_view_all),
            onAction = { onIntent(HomeIntent.OpenGroups) },
        )
        if (groups.isNotEmpty()) {
            SaqzCard(padded = false) {
                groups.forEachIndexed { index, group ->
                    HomeGroupRow(group, onClick = { onIntent(HomeIntent.OpenGroup(group.id)) })
                    if (index < groups.lastIndex) SaqzDivider()
                }
            }
        }
    }
}

@Composable
private fun HomeGroupRow(group: HomeGroupUi, onClick: () -> Unit) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .clickable(onClickLabel = group.name, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = group.name }
            .testTag(HomeTags.group(group.id))
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.iconButtonSize - metrics.subGrid / 2)
                .clip(RoundedCornerShape(metrics.inputRadius + metrics.subGrid / 2))
                .background(SaqzTheme.colors.surfaceSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = saqzInitials(group.name),
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.primary,
            )
        }
        // Chip ao lado do nome, meta embaixo: no fim da linha ele espremia a coluna e a
        // meta quebrava em duas linhas.
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.grid),
            ) {
                Text(
                    text = group.name,
                    style = SaqzTheme.typography.compactTitle,
                    color = SaqzTheme.colors.textPrimary,
                )
                if (group.isAdmin) {
                    SaqzStatusChip(
                        text = stringResource(Res.string.home_admin_group_chip),
                        tone = SaqzChipTone.Brand,
                    )
                }
            }
            Text(
                text = group.meta,
                style = SaqzTheme.typography.compactMeta,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
    }
}

@Preview(name = "Home carregando", widthDp = 390, heightDp = 844)
@Composable
private fun HomeLoadingPreview() = SaqzTheme {
    HomeScreen(HomeState(), onIntent = {})
}

@Preview(name = "Home erro", widthDp = 390, heightDp = 844)
@Composable
private fun HomeFailurePreview() = SaqzTheme {
    HomeScreen(HomeState(isLoading = false, loadFailed = true), onIntent = {})
}

@Preview(name = "Home com próximo jogo", widthDp = 390, heightDp = 844)
@Composable
private fun HomeContentPreview() = SaqzTheme {
    HomeScreen(previewState(), onIntent = {})
}

@Preview(name = "Home com cobrança no prazo", widthDp = 390, heightDp = 1000)
@Composable
private fun HomeOwnChargesPreview() = SaqzTheme {
    HomeScreen(previewState().copy(ownCharges = previewOwnCharges()), onIntent = {})
}

@Preview(name = "Home com cobrança vencida", widthDp = 390, heightDp = 1200)
@Composable
private fun HomeOwnChargesOverduePreview() = SaqzTheme {
    HomeScreen(previewState().copy(ownCharges = previewOwnChargesOverdue()), onIntent = {})
}

@Preview(name = "Home sem jogo", widthDp = 390, heightDp = 844)
@Composable
private fun HomeEmptyPreview() = SaqzTheme {
    HomeScreen(previewState(nextGame = null), onIntent = {})
}

@Preview(name = "Home reserva (6b)", widthDp = 390, heightDp = 1200)
@Composable
private fun HomeReservaPreview() = SaqzTheme {
    HomeScreen(previewState(nextGame = reservaPreviewGame()), onIntent = {})
}

@Preview(name = "Home lista de espera do avulso (6e)", widthDp = 390, heightDp = 1200)
@Composable
private fun HomeAvulsoListPreview() = SaqzTheme {
    HomeScreen(previewState(nextGame = avulsoListPreviewGame()), onIntent = {})
}

private fun previewState(
    nextGame: HomeNextGameUi? = HomeNextGameUi(
        groupId = "ceret",
        gameId = "game-1",
        groupName = "Vôlei do CERET",
        dateTime = "Ter, 28/07 · 19h30",
        local = "CERET — Quadra 2 · Tatuapé",
        deadline = "As confirmações encerram hoje às 18h.",
        confirmedSummary = "9 de 12 confirmados",
        confirmedCount = 9,
        capacity = 12,
        rosterNames = listOf("Ana Souza", "Bruna Lima", "Caio", "Duda"),
        ownAttendance = null,
        weekday = "terça",
        time = "19h30",
        display = "Terça, 19h30",
        meta = "28 de julho · CERET — Quadra 2 · Tatuapé",
    ),
) = HomeState(
    isLoading = false,
    displayName = "Bruna",
    member = HomeMemberUi(
        nextGame = nextGame,
        groups = listOf(
            HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos", isAdmin = true),
            HomeGroupUi("pacaembu", "Vôlei Pacaembu", "14 pessoas · 6 jogos"),
        ),
    ),
)

private fun reservaPreviewGame() = HomeNextGameUi(
    groupId = "ceret",
    gameId = "game-1",
    groupName = "Vôlei do CERET",
    dateTime = "Ter, 28/07 · 19h30",
    local = "CERET — Quadra 2 · Tatuapé",
    deadline = "As confirmações encerram hoje às 18h.",
    confirmedSummary = "12 de 12 confirmados",
    confirmedCount = 12,
    capacity = 12,
    rosterNames = listOf("Ana Souza", "Bruna Lima", "Caio", "Duda"),
    ownAttendance = AttendanceStatus.Waitlisted,
    weekday = "terça",
    time = "19h30",
    display = "Terça, 19h30",
    meta = "28 de julho · CERET — Quadra 2 · Tatuapé",
    confirmationOpen = true,
    waitlistKind = HomeWaitlistKind.Reserva,
    waitlistPosition = 1,
    confirmedRoster = listOf("Ana Souza", "Bruna Lima", "Caio", "Duda", "Eva", "Tiago"),
    deadlineBellLabel = "Avisamos você se abrir vaga até 18h00 de 28/07.",
)

private fun avulsoListPreviewGame() = HomeNextGameUi(
    groupId = "ceret",
    gameId = "game-1",
    groupName = "Vôlei do CERET",
    dateTime = "Ter, 28/07 · 19h30",
    local = "CERET — Quadra 2 · Tatuapé",
    deadline = "As confirmações encerram hoje às 18h.",
    confirmedSummary = "9 de 12 confirmados",
    confirmedCount = 9,
    capacity = 12,
    rosterNames = listOf("Ana Souza", "Bruna Lima", "Caio"),
    ownAttendance = AttendanceStatus.Waitlisted,
    weekday = "terça",
    time = "19h30",
    display = "Terça, 19h30",
    meta = "28 de julho · CERET — Quadra 2 · Tatuapé",
    confirmationOpen = true,
    waitlistKind = HomeWaitlistKind.AvulsoList,
    waitlistPosition = 2,
    confirmedRoster = listOf("Ana Souza", "Bruna Lima", "Caio"),
    waitlistedRoster = listOf(
        br.com.saqz.groups.presentation.home.HomeWaitlistRowUi(name = "Lucas Pereira", position = 1, isSelf = false),
        br.com.saqz.groups.presentation.home.HomeWaitlistRowUi(name = "Bruna Silva", position = 2, isSelf = true),
        br.com.saqz.groups.presentation.home.HomeWaitlistRowUi(name = "Tiago Moraes", position = 3, isSelf = false),
    ),
    confirmedCountTotal = 9,
)
