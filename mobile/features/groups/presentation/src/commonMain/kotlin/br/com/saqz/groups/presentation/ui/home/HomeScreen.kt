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
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzButton
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
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeGroupUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeMemberUi
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.home.HomeState
import br.com.saqz.groups.presentation.home.HomeToast
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceControls
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTags
import br.com.saqz.groups.presentation.ui.components.HeroAttendanceTexts
import br.com.saqz.groups.presentation.ui.components.HeroDeadlineLine
import br.com.saqz.groups.presentation.ui.components.HeroOutlineAlpha
import br.com.saqz.groups.presentation.ui.components.HeroRosterRow
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
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = game.display,
        meta = game.meta,
        trailing = { SaqzStatusChip(text = game.groupName, tone = SaqzChipTone.Inverse) },
        modifier = Modifier.testTag(HomeTags.NextGame),
    ) {
        HeroDeadlineLine(text = game.deadline, open = game.confirmationOpen)
        HomeAttendanceControls(
            game = game,
            responding = responding,
            responseFailed = responseFailed,
            onIntent = onIntent,
        )
        // Em espera, as seções abaixo do hero já mostram o roster: a linha some.
        if (game.ownAttendance != AttendanceStatus.Waitlisted) {
            val spotsLeft = game.capacity - game.confirmedCount
            HeroRosterRow(
                names = game.rosterNames,
                summary = game.confirmedSummary,
                spotsChip = if (spotsLeft in 1..SpotsLeftMax) {
                    stringResource(Res.string.home_spots_left, spotsLeft)
                } else {
                    null
                },
            )
        }
    }
}

/** Escassez que muda comportamento: abaixo disso o "9 de 12" vira "restam N". */
private const val SpotsLeftMax = 3

/**
 * Seletor de presença do hero: o adaptador da Início para o `HeroAttendanceControls` — traduz
 * `HomeNextGameUi`/`HomeIntent` e passa os textos e as tags desta tela. Vive fora do `HomeHero`
 * porque o hero do admin (VUL-192) usa o mesmo bloco: dono e admin são atletas do grupo e
 * respondem presença no mesmo lugar que todo mundo.
 */
@Composable
internal fun ColumnScope.HomeAttendanceControls(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    HeroAttendanceControls(
        status = game.ownAttendance,
        confirmationOpen = game.confirmationOpen,
        responding = responding,
        responseFailed = responseFailed,
        waitlistKind = game.waitlistKind,
        waitlistPosition = game.waitlistPosition,
        onRespond = { onIntent(HomeIntent.Respond(it)) },
        onViewGame = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
        texts = HeroAttendanceTexts(
            yes = stringResource(Res.string.home_response_yes),
            no = stringResource(Res.string.home_response_no),
            confirmed = stringResource(Res.string.home_status_confirmed),
            declined = stringResource(Res.string.home_status_declined),
            change = stringResource(Res.string.home_attendance_change),
            cancel = stringResource(Res.string.home_attendance_cancel),
            error = stringResource(Res.string.home_response_error),
        ),
        tags = HomeHeroAttendanceTags,
    )
}

private val HomeHeroAttendanceTags = HeroAttendanceTags(
    yes = HomeTags.ResponseYes,
    no = HomeTags.ResponseNo,
    change = HomeTags.ResponseChange,
    cancel = HomeTags.ResponseCancel,
    error = HomeTags.ResponseError,
)

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
