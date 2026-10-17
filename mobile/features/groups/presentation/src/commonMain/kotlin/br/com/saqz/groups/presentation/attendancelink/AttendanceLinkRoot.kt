package br.com.saqz.groups.presentation.attendancelink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
}

@Composable
fun AttendanceLinkRoot(
    code: String,
    decline: Boolean,
    onBack: () -> Unit,
    onRegister: (String) -> Unit,
    onOpenGame: (AttendanceLinkDestination) -> Unit,
) {
    val vm: AttendanceLinkViewModel = koinViewModel(
        key = "attendance-link/$code/$decline",
        parameters = { parametersOf(code, decline) },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    ObserveAsEvents(vm.effects) { effect -> when (effect) { is AttendanceLinkEffect.Register -> onRegister(effect.groupId) } }
    AttendanceLinkScreen(state, onBack, vm::onIntent, onOpenGame, vm.appDownloadAvailable)
}

@Composable
internal fun AttendanceLinkScreen(
    state: AttendanceLinkState, onBack: () -> Unit, onIntent: (AttendanceLinkIntent) -> Unit,
    onOpenGame: (AttendanceLinkDestination) -> Unit,
    appDownloadAvailable: Boolean = false,
) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzTopAppBar(title = stringResource(Res.string.attendance_link_title), onBack = onBack)
            Column(
                Modifier.padding(SaqzTheme.metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            ) {
                if (state.phase == AttendanceLinkPhase.Loading) SaqzSpinner()
                Text(stringResource(when (state.phase) {
                    AttendanceLinkPhase.Loading -> Res.string.attendance_link_loading
                    AttendanceLinkPhase.Confirmed -> Res.string.attendance_link_confirmed
                    AttendanceLinkPhase.Waitlisted -> Res.string.attendance_link_waitlisted
                    // Antes do toque nada foi gravado: o texto de fundo não pode dizer que o grupo foi avisado.
                    AttendanceLinkPhase.DeclineSheet -> Res.string.attendance_link_decline_pending
                    AttendanceLinkPhase.Declined -> Res.string.attendance_link_declined
                    AttendanceLinkPhase.Invalid -> Res.string.attendance_link_invalid
                    AttendanceLinkPhase.NotReceiving -> Res.string.attendance_link_not_receiving
                    AttendanceLinkPhase.Failed -> Res.string.communication_failure
                    AttendanceLinkPhase.Registration -> Res.string.attendance_link_registration
                    AttendanceLinkPhase.Pending -> Res.string.attendance_link_pending
                }))
                if (state.phase in listOf(AttendanceLinkPhase.Failed, AttendanceLinkPhase.Pending)) {
                    SaqzButton(stringResource(Res.string.communication_refresh), { onIntent(AttendanceLinkIntent.Retry) })
                }
                state.destination?.let { destination ->
                    SaqzButton(stringResource(Res.string.attendance_link_open_game), { onOpenGame(destination) })
                }
                // Depois da primeira resposta, o web app oferece o app para o lembrete no celular.
                if (appDownloadAvailable && state.phase in listOf(AttendanceLinkPhase.Confirmed, AttendanceLinkPhase.Waitlisted)) {
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
        }
        // O aviso do "não vou": fecha pela alça, pelo scrim ou pelo back, e nenhum deles
        // responde nada — só o botão do rodapé escreve no servidor.
        SaqzBottomSheet(
            open = state.phase == AttendanceLinkPhase.DeclineSheet,
            onClose = onBack,
            title = stringResource(Res.string.attendance_link_decline_sheet_title),
            description = stringResource(Res.string.attendance_link_decline_sheet_description),
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
