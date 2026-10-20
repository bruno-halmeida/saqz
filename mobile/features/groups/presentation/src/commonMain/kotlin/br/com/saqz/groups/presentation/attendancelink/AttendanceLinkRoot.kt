package br.com.saqz.groups.presentation.attendancelink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal object AttendanceLinkTags {
    const val Download = "attendance-link-download"
    const val GameCard = "attendance-link-game-card"
    const val Undo = "attendance-link-undo"
    const val ResultCard = "attendance-link-result-card"
    const val Spots = "attendance-link-spots"
    const val WaitlistTurn = "attendance-link-waitlist-turn"
    const val PendingCard = "attendance-link-pending-card"
    const val RegistrationDone = "attendance-link-registration-done"
    const val AdvanceRegistration = "attendance-link-advance-registration"
    const val Refresh = "attendance-link-refresh"
    const val OpenGame = "attendance-link-open-game"
    const val Footer = "attendance-link-footer"
    const val RequestAgain = "attendance-link-request-again"
}

@Composable
fun AttendanceLinkRoot(
    code: String,
    decline: Boolean,
    onBack: () -> Unit,
    onRegister: (String) -> Unit,
    onAdvanceRegistration: (groupId: String) -> Unit,
    onOpenGame: (AttendanceLinkDestination) -> Unit,
) {
    val vm: AttendanceLinkViewModel = koinViewModel(
        key = "attendance-link/$code/$decline",
        parameters = { parametersOf(code, decline) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    ObserveAsEvents(vm.effects) { effect -> when (effect) { is AttendanceLinkEffect.Register -> onRegister(effect.groupId) } }
    // Volta do formulário de cadastro (ou do app em segundo plano): o pedido pode ter sido aprovado nesse meio tempo.
    RefreshWhenVisible { vm.onIntent(AttendanceLinkIntent.Refresh) }
    AttendanceLinkScreen(state, onBack, vm::onIntent, onOpenGame, onAdvanceRegistration, vm.appDownloadAvailable)
}

@Composable
internal fun AttendanceLinkScreen(
    state: AttendanceLinkState, onBack: () -> Unit, onIntent: (AttendanceLinkIntent) -> Unit,
    onOpenGame: (AttendanceLinkDestination) -> Unit,
    onAdvanceRegistration: (groupId: String) -> Unit = {},
    appDownloadAvailable: Boolean = false,
) {
    val phase = state.phase
    val answered = phase in listOf(
        AttendanceLinkPhase.Confirmed, AttendanceLinkPhase.Waitlisted, AttendanceLinkPhase.Declined, AttendanceLinkPhase.RequestClosed,
    )
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzTopAppBar(
                title = stringResource(
                    if (phase == AttendanceLinkPhase.Pending) Res.string.attendance_link_pending_title
                    else Res.string.attendance_link_title,
                ),
                onBack = onBack,
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = SaqzTheme.metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            ) {
                if (answered) AttendanceResultCard(state)
                state.game?.let { game ->
                    val pending = phase == AttendanceLinkPhase.Pending
                    SaqzGameSummaryCard(
                        eyebrow = stringResource(
                            if (pending || game.groupNext) Res.string.invite_landing_next_game else Res.string.attendance_link_game_eyebrow,
                        ),
                        title = game.title(),
                        // O local de quem aguarda aprovação não é liberado: o texto do backend fica de fora.
                        venue = if (pending) stringResource(Res.string.attendance_link_pending_venue) else game.venueName,
                        venueLocked = pending,
                        address = if (pending) null else game.court,
                        modifier = Modifier.testTag(AttendanceLinkTags.GameCard),
                    )
                }
                // Como no Main.dc.html: o jogo em cima, o andamento do pedido embaixo.
                if (phase == AttendanceLinkPhase.Pending) AttendancePendingCard(state)
                if (phase == AttendanceLinkPhase.Loading) SaqzSpinner()
                val message = when (phase) {
                    AttendanceLinkPhase.Loading -> Res.string.attendance_link_loading
                    AttendanceLinkPhase.Invalid -> Res.string.attendance_link_invalid
                    AttendanceLinkPhase.NotReceiving -> Res.string.attendance_link_not_receiving
                    AttendanceLinkPhase.GameStarted -> Res.string.attendance_link_game_started
                    AttendanceLinkPhase.GameCancelled -> Res.string.attendance_link_game_cancelled
                    AttendanceLinkPhase.GameUnpublished -> Res.string.attendance_link_game_unpublished
                    AttendanceLinkPhase.Failed -> Res.string.communication_failure
                    AttendanceLinkPhase.Registration -> Res.string.attendance_link_registration
                    else -> null
                }
                if (message != null) Text(stringResource(message))
                // Depois da primeira resposta, o web app oferece o app para o lembrete no celular.
                if (appDownloadAvailable && phase in listOf(AttendanceLinkPhase.Confirmed, AttendanceLinkPhase.Waitlisted)) {
                    Text(stringResource(Res.string.app_download_offer),
                        style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
                    SaqzButton(
                        stringResource(Res.string.app_download_action),
                        { onIntent(AttendanceLinkIntent.OpenAppDownload) },
                        variant = SaqzButtonVariant.Secondary,
                        modifier = Modifier.testTag(AttendanceLinkTags.Download),
                    )
                }
            }
            AttendanceLinkFooter(state, onIntent, onOpenGame, onAdvanceRegistration)
        }
        // O aviso do "não vou": fecha pela alça, pelo scrim ou pelo back, e nenhum deles
        // responde nada — só o botão do rodapé escreve no servidor.
        SaqzBottomSheet(
            open = phase == AttendanceLinkPhase.DeclineSheet,
            onClose = onBack,
            title = stringResource(Res.string.attendance_link_decline_sheet_title),
            description = state.game?.let { stringResource(Res.string.attendance_link_decline_sheet_description_game, it.weekdayAndTime()) }
                ?: stringResource(Res.string.attendance_link_decline_sheet_description),
            splitFooter = {
                SaqzButton(
                    label = stringResource(Res.string.attendance_link_decline_sheet_back),
                    onClick = onBack,
                    variant = SaqzButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
                SaqzButton(
                    label = stringResource(Res.string.attendance_link_decline_sheet_confirm),
                    onClick = { onIntent(AttendanceLinkIntent.Decline) },
                    variant = SaqzButtonVariant.Danger,
                    enabled = !state.declining,
                    loading = state.declining,
                    modifier = Modifier.weight(1f),
                )
            },
        ) {
            Text(
                text = stringResource(Res.string.attendance_link_decline_sheet_body),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

/** O rodapé fixo, fora da rolagem: a ação principal da fase e, abaixo dela, uma ação de texto. */
@Composable
private fun AttendanceLinkFooter(
    state: AttendanceLinkState,
    onIntent: (AttendanceLinkIntent) -> Unit,
    onOpenGame: (AttendanceLinkDestination) -> Unit,
    onAdvanceRegistration: (groupId: String) -> Unit,
) {
    val destination = state.destination
    val groupId = state.pendingGroupId
    // O aviso do "não vou" só deixa o cartão do jogo atrás da folha: sem "Ver jogo".
    val openGame = destination != null && state.phase != AttendanceLinkPhase.DeclineSheet
    val hasAction = when (state.phase) {
        AttendanceLinkPhase.Pending, AttendanceLinkPhase.Failed, AttendanceLinkPhase.RequestClosed -> true
        else -> openGame
    }
    if (!hasAction) return
    Column(
        Modifier.fillMaxWidth().testTag(AttendanceLinkTags.Footer)
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        when (state.phase) {
            AttendanceLinkPhase.Pending -> {
                if (groupId != null) {
                    SaqzButton(
                        stringResource(
                            if (state.pendingRegistrationSaved) Res.string.attendance_link_review_registration
                            else Res.string.attendance_link_advance_registration,
                        ),
                        { onAdvanceRegistration(groupId) },
                        fullWidth = true,
                        modifier = Modifier.testTag(AttendanceLinkTags.AdvanceRegistration),
                    )
                }
                SaqzButton(
                    stringResource(Res.string.communication_refresh),
                    { onIntent(AttendanceLinkIntent.Refresh) },
                    variant = SaqzButtonVariant.Ghost,
                    fullWidth = true,
                    enabled = !state.refreshing,
                    loading = state.refreshing,
                    modifier = Modifier.testTag(AttendanceLinkTags.Refresh),
                )
            }
            AttendanceLinkPhase.RequestClosed -> SaqzButton(
                stringResource(Res.string.attendance_link_closed_action),
                { onIntent(AttendanceLinkIntent.Retry) },
                fullWidth = true,
                modifier = Modifier.testTag(AttendanceLinkTags.RequestAgain),
            )
            AttendanceLinkPhase.Failed -> {
                SaqzButton(
                    stringResource(Res.string.communication_refresh), { onIntent(AttendanceLinkIntent.Retry) }, fullWidth = true,
                )
                if (destination != null) OpenGameButton(destination, onOpenGame)
            }
            else -> if (destination != null) {
                OpenGameButton(destination, onOpenGame)
                // Quem chegou por um link pode ter tocado sem querer: a resposta se desfaz num toque.
                val undoLabel = when (state.phase) {
                    AttendanceLinkPhase.Confirmed -> Res.string.attendance_link_undo_confirmed
                    AttendanceLinkPhase.Waitlisted -> Res.string.attendance_link_undo_waitlisted
                    else -> null
                }
                if (undoLabel != null) {
                    SaqzButton(
                        stringResource(undoLabel),
                        { onIntent(AttendanceLinkIntent.Undo) },
                        variant = SaqzButtonVariant.Ghost,
                        fullWidth = true,
                        enabled = !state.declining,
                        loading = state.declining,
                        modifier = Modifier.testTag(AttendanceLinkTags.Undo),
                    )
                }
            }
        }
    }
}

@Composable
private fun OpenGameButton(destination: AttendanceLinkDestination, onOpenGame: (AttendanceLinkDestination) -> Unit) {
    SaqzButton(
        stringResource(Res.string.attendance_link_open_game),
        { onOpenGame(destination) },
        fullWidth = true,
        modifier = Modifier.testTag(AttendanceLinkTags.OpenGame),
    )
}

@Composable
internal fun AttendanceLinkGame.title(): String {
    val dateTime = if (time.isBlank()) date else "$date · $time"
    val weekday = when (weekdayCode) {
        "SUNDAY" -> Res.string.invite_landing_weekday_short_sunday
        "MONDAY" -> Res.string.invite_landing_weekday_short_monday
        "TUESDAY" -> Res.string.invite_landing_weekday_short_tuesday
        "WEDNESDAY" -> Res.string.invite_landing_weekday_short_wednesday
        "THURSDAY" -> Res.string.invite_landing_weekday_short_thursday
        "FRIDAY" -> Res.string.invite_landing_weekday_short_friday
        "SATURDAY" -> Res.string.invite_landing_weekday_short_saturday
        else -> null
    }?.let { stringResource(it) }
    return if (weekday == null) dateTime else "$weekday, $dateTime"
}
