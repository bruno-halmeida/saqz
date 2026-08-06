package br.com.saqz.groups.presentation.ui.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzGameSummaryCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.saqzInitials
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeLastCompletedGameUi
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeToast
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_error_message
import br.com.saqz.groups.resources.home_error_title
import br.com.saqz.groups.resources.home_game_next
import br.com.saqz.groups.resources.home_admin_group_chip
import br.com.saqz.groups.resources.home_groups_title
import br.com.saqz.groups.resources.home_groups_view_all
import br.com.saqz.groups.resources.home_greeting
import br.com.saqz.groups.resources.home_last_game_section
import br.com.saqz.groups.resources.home_no_game_action
import br.com.saqz.groups.resources.home_no_game_description
import br.com.saqz.groups.resources.home_no_game_title
import br.com.saqz.groups.resources.home_response_error
import br.com.saqz.groups.resources.home_response_no
import br.com.saqz.groups.resources.home_response_yes
import br.com.saqz.groups.resources.home_retry
import br.com.saqz.groups.resources.home_status_confirmed
import br.com.saqz.groups.resources.home_status_declined
import br.com.saqz.groups.resources.home_status_pending
import br.com.saqz.groups.resources.home_toast_confirmed
import br.com.saqz.groups.resources.home_toast_declined
import br.com.saqz.groups.resources.home_toast_waitlisted
import org.jetbrains.compose.resources.stringResource

internal object HomeTags {
    const val Content = "home-content"
    const val Error = "home-error"
    const val Loading = "home-loading"
    const val Retry = "home-retry"
    const val NextGame = "home-next-game"
    const val Empty = "home-empty"
    const val LastGame = "home-last-game"
    const val Groups = "home-groups"
    const val ResponseYes = "home-response-yes"
    const val ResponseNo = "home-response-no"
    const val ResponseError = "home-response-error"
    const val Toast = "home-toast"
    const val OwnCharges = "home-own-charges"
    const val OwnChargesBanner = "home-own-charges-banner"

    fun group(id: String) = "home-group-$id"

    fun ownCharge(id: String) = "home-own-charge-$id"

    fun ownChargePix(id: String) = "home-own-charge-pix-$id"

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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .padding(metrics.horizontalPadding)
            .testTag(HomeTags.Loading),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSkeleton(width = metrics.avatarSize, height = metrics.avatarSize, circle = true)
        SaqzSkeleton(width = metrics.buttonHeight * 4, height = metrics.buttonHeight / 2)
        SaqzSkeleton(height = metrics.avatarSize * 2)
        SaqzSkeleton(height = metrics.avatarSize * 2)
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
            HomeHeader(state.displayName, member?.adminSubtitle ?: member?.subtitle)
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
                } ?: HomeNoGame(onIntent)
                // VUL-202: abaixo do hero e acima de "Seus grupos". Antes do bloco do admin
                // de propósito — o que a pessoa deve vem antes do que ela administra.
                state.ownCharges?.let { HomeOwnChargesSection(ownCharges = it, onIntent = onIntent) }
                member.lastCompletedGame?.let { HomeLastGame(it) }
                member.admin?.let { admin ->
                    HomeAdminWaitingSection(admin = admin, onIntent = onIntent)
                    HomeAdminShortcuts(admin = admin, nextGame = member.nextGame, onIntent = onIntent)
                }
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
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(displayName: String?, subtitle: String?) {
    val colors = SaqzTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        if (displayName != null) {
            Text(
                text = stringResource(Res.string.home_greeting, displayName),
                style = SaqzTheme.typography.title.copy(fontWeight = FontWeight(800)),
                color = colors.textPrimary,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HomeHero(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzGameSummaryCard(
        eyebrow = stringResource(Res.string.home_game_next),
        title = game.dateTime,
        trailingEyebrow = game.groupName,
        tone = SaqzCardTone.Soft,
        cornerRadius = metrics.blockRadius,
        modifier = Modifier.testTag(HomeTags.NextGame),
    ) {
        Text(
            text = game.local,
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
        )
        HomeAttendanceControls(
            game = game,
            responding = responding,
            responseFailed = responseFailed,
            onIntent = onIntent,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            SaqzAvatarStack(names = game.rosterNames)
            Text(
                text = game.confirmedSummary,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
            )
        }
        Text(
            text = game.deadline,
            style = SaqzTheme.typography.caption,
            color = colors.textSecondary,
        )
    }
}

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
    val metrics = SaqzTheme.metrics
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                HomeResponseButton(
                    label = stringResource(Res.string.home_response_yes),
                    danger = false,
                    selected = game.ownAttendance == AttendanceStatus.Confirmed,
                    loading = responding && game.ownAttendance == AttendanceStatus.Confirmed,
                    enabled = game.confirmationOpen && !responding,
                    modifier = Modifier.weight(1f).testTag(HomeTags.ResponseYes),
                    onClick = { onIntent(HomeIntent.Respond(AttendanceIntent.Confirm)) },
                )
                HomeResponseButton(
                    label = stringResource(Res.string.home_response_no),
                    danger = true,
                    selected = game.ownAttendance == AttendanceStatus.Declined,
                    loading = responding && game.ownAttendance == AttendanceStatus.Declined,
                    enabled = game.confirmationOpen && !responding,
                    modifier = Modifier.weight(1f).testTag(HomeTags.ResponseNo),
                    onClick = { onIntent(HomeIntent.Respond(AttendanceIntent.Decline)) },
                )
            }
            HomeStatus(game.ownAttendance)
        }
    }
    if (responseFailed) {
        Text(
            text = stringResource(Res.string.home_response_error),
            style = SaqzTheme.typography.support,
            color = colors.errorForeground,
            modifier = Modifier.testTag(HomeTags.ResponseError),
        )
    }
}

@Composable
private fun HomeResponseButton(
    label: String,
    danger: Boolean,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        variant = when {
            selected && danger -> SaqzButtonVariant.Danger
            selected -> SaqzButtonVariant.Primary
            else -> SaqzButtonVariant.Secondary
        },
        size = SaqzButtonSize.Sm,
        fullWidth = true,
        enabled = enabled,
        loading = loading,
    )
}

@Composable
private fun HomeStatus(status: AttendanceStatus?) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val (color, text) = when (status) {
        AttendanceStatus.Confirmed -> colors.success to stringResource(Res.string.home_status_confirmed)
        AttendanceStatus.Declined -> colors.textSecondary to stringResource(Res.string.home_status_declined)
        AttendanceStatus.Waitlisted -> return
        null -> colors.primary to stringResource(Res.string.home_status_pending)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(metrics.inputRadius))
            .padding(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.grid + metrics.subGrid / 2)
                .background(color, CircleShape),
        )
        Text(
            text = text,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
    }
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
    SaqzEmptyState(
        title = stringResource(Res.string.home_no_game_title),
        description = stringResource(Res.string.home_no_game_description),
        icon = SaqzIcons.Calendar,
        action = stringResource(Res.string.home_no_game_action),
        actionVariant = SaqzButtonVariant.Secondary,
        actionFullWidth = true,
        iconBadgeSize = SaqzTheme.metrics.avatarSize,
        onAction = { onIntent(HomeIntent.OpenGroups) },
        modifier = Modifier.testTag(HomeTags.Empty),
    )
}

@Composable
private fun HomeLastGame(game: HomeLastCompletedGameUi) {
    SaqzCard(modifier = Modifier.testTag(HomeTags.LastGame)) {
        Text(
            text = stringResource(Res.string.home_last_game_section),
            style = SaqzTheme.typography.subtitle,
            color = SaqzTheme.colors.textPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            Column(
                modifier = Modifier
                    .size(SaqzTheme.metrics.avatarSize)
                    .background(SaqzTheme.colors.surfaceSoft, RoundedCornerShape(SaqzTheme.metrics.inputRadius))
                    .padding(SaqzTheme.metrics.subGrid),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = game.day,
                    style = SaqzTheme.typography.dateDay,
                    color = SaqzTheme.colors.textPrimary,
                )
                Text(
                    text = game.month,
                    style = SaqzTheme.typography.dateMonth,
                    color = SaqzTheme.colors.primary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(
                    text = game.title,
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = SaqzTheme.colors.textPrimary,
                )
                Text(
                    text = game.summary,
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = SaqzTheme.typography.compactTitle,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = group.meta,
                style = SaqzTheme.typography.compactMeta,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        if (group.isAdmin) {
            SaqzStatusChip(
                text = stringResource(Res.string.home_admin_group_chip),
                tone = SaqzChipTone.Brand,
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
    ),
) = HomeState(
    isLoading = false,
    displayName = "Bruna",
    member = HomeMemberUi(
        subtitle = if (nextGame == null) "Semana sem jogo por aqui." else "Terça tem jogo. Confirma?",
        nextGame = nextGame,
        lastCompletedGame = HomeLastCompletedGameUi(
            day = "21",
            month = "JUL",
            title = "Vôlei do CERET · 19h30",
            summary = "Você jogou · 12 confirmados",
        ),
        groups = listOf(
            HomeGroupUi("ceret", "Vôlei do CERET", "26 pessoas · 18 jogos"),
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
