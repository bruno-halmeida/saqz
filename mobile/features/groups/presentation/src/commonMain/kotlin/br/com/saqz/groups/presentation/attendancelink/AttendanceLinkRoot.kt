package br.com.saqz.groups.presentation.attendancelink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.share.AttendanceLinkDestination
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AttendanceLinkRoot(
    code: String, onBack: () -> Unit, onRegister: (String) -> Unit, onOpenGame: (AttendanceLinkDestination) -> Unit,
) {
    val vm: AttendanceLinkViewModel = koinViewModel(key = "attendance-link/$code", parameters = { parametersOf(code) })
    val state by vm.state.collectAsStateWithLifecycle()
    ObserveAsEvents(vm.effects) { effect -> when (effect) { is AttendanceLinkEffect.Register -> onRegister(effect.groupId) } }
    AttendanceLinkScreen(state, onBack, vm::retry, onOpenGame)
}

@Composable
internal fun AttendanceLinkScreen(
    state: AttendanceLinkState, onBack: () -> Unit, onRetry: () -> Unit, onOpenGame: (AttendanceLinkDestination) -> Unit,
) {
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
                AttendanceLinkPhase.Invalid -> Res.string.attendance_link_invalid
                AttendanceLinkPhase.Failed -> Res.string.communication_failure
                AttendanceLinkPhase.Registration -> Res.string.attendance_link_registration
                AttendanceLinkPhase.Pending -> Res.string.attendance_link_pending
            }))
            if (state.phase in listOf(AttendanceLinkPhase.Failed, AttendanceLinkPhase.Pending)) {
                SaqzButton(stringResource(Res.string.communication_refresh), onRetry)
            }
            state.destination?.let { destination ->
                SaqzButton(stringResource(Res.string.attendance_link_open_game), { onOpenGame(destination) })
            }
        }
    }
}
