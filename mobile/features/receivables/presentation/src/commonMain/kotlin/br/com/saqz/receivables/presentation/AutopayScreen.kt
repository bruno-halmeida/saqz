package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.AutopayCancelReason
import br.com.saqz.receivables.domain.RecurrenceStatus
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object AutopayTags {
    const val Screen = "autopay"
    const val Register = "autopay-register"
    const val ChangeCard = "autopay-change-card"
    const val Cancel = "autopay-cancel"
    const val ConfirmCancel = "autopay-confirm-cancel"
    const val Keep = "autopay-keep"
    const val Unavailable = "autopay-unavailable"
    fun reason(reason: AutopayCancelReason) = "autopay-reason-${reason.name}"
}

@Composable
fun AutopayRoot(
    route: AutopayRoute,
    onBack: () -> Unit,
    viewModel: AutopayViewModel = koinViewModel(key = "autopay/${route.groupId}",
        parameters = { parametersOf(GroupArgs(route.groupId, route.groupName)) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) { is AutopayEffect.OpenUrl -> runCatching { uri.openUri(effect.url) } }
    }
    // Volta da página do Asaas: a adesão ou a troca do cartão aparecem ao reler o `/me`.
    RefreshWhenVisible { viewModel.onIntent(AutopayIntent.Resumed) }
    AutopayScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun AutopayScreen(state: AutopayState, onIntent: (AutopayIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val back = if (state.cancelling) ({ onIntent(AutopayIntent.KeepAutopay) }) else onBack
    if (state.cancelling) {
        CancelScreen(state, onIntent, back, modifier)
        return
    }
    val metrics = SaqzTheme.metrics
    val padded = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding)
    ReceivablesPage(stringResource(Res.string.netflix_title), AutopayTags.Screen, back, modifier, footer = {
        when (state.status) {
            RecurrenceStatus.NONE -> {
                Text(stringResource(Res.string.netflix_card_note), style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary)
                SaqzButton(stringResource(Res.string.netflix_register), { onIntent(AutopayIntent.Start) }, fullWidth = true,
                    loading = state.busy, modifier = Modifier.testTag(AutopayTags.Register))
            }
            RecurrenceStatus.ACTIVE, RecurrenceStatus.REFUSED ->
                SaqzButton(stringResource(Res.string.netflix_cancel), { onIntent(AutopayIntent.RequestCancel) }, fullWidth = true,
                    variant = SaqzButtonVariant.Ghost, contentColor = SaqzTheme.colors.errorForeground,
                    modifier = Modifier.testTag(AutopayTags.Cancel))
            null -> Unit
        }
    }) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(metrics.sectionGap)) { SaqzSpinner(Modifier.align(Alignment.Center)) }
            state.error != null && state.status == null -> PaymentFailure(state.error, { onIntent(AutopayIntent.Retry) }, padded)
            state.status == null -> SaqzEmptyState(title = stringResource(Res.string.netflix_unavailable),
                modifier = padded.testTag(AutopayTags.Unavailable))
            else -> {
                ReceivablesHero(
                    kicker = state.groupName,
                    amount = stringResource(Res.string.netflix_headline),
                    support = if (state.status == RecurrenceStatus.NONE) stringResource(Res.string.netflix_tagline) else null,
                ) {
                    if (state.status != RecurrenceStatus.NONE) SaqzStatusChip(
                        stringResource(
                            if (state.status == RecurrenceStatus.ACTIVE) Res.string.netflix_active else Res.string.netflix_refused,
                        ),
                        tone = if (state.status == RecurrenceStatus.ACTIVE) SaqzChipTone.Inverse else SaqzChipTone.WarningInverse)
                }
                state.error?.let { Text(paymentErrorText(it), padded.padding(top = metrics.blockGap),
                    style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground) }
                if (state.cancelled) ReceivablesNote(stringResource(Res.string.netflix_cancelled))
                if (state.status == RecurrenceStatus.NONE) Adhesion(state) else Manage(state, onIntent)
            }
        }
    }
}

@Composable
private fun Adhesion(state: AutopayState) {
    Benefit(stringResource(Res.string.netflix_amount, state.amountLabel), stringResource(Res.string.netflix_amount_support))
    Benefit(state.dueDay?.let { stringResource(Res.string.netflix_day, it) } ?: stringResource(Res.string.netflix_day_unknown),
        state.openMonth?.let { stringResource(Res.string.netflix_open_month, it) } ?: stringResource(Res.string.netflix_day_support))
    Benefit(stringResource(Res.string.netflix_change), stringResource(Res.string.netflix_change_support))
    Benefit(stringResource(Res.string.netflix_cancel_anytime), stringResource(Res.string.netflix_cancel_anytime_support))
}

@Composable
private fun Benefit(title: String, support: String) {
    ReceivablesRow(title, support)
    SaqzDivider()
}

@Composable
private fun Manage(state: AutopayState, onIntent: (AutopayIntent) -> Unit) {
    state.nextCharge?.let { ReceivablesRow(stringResource(Res.string.netflix_next), it); SaqzDivider() }
    ReceivablesRow(stringResource(Res.string.netflix_card), stringResource(Res.string.netflix_card_support)) {
        SaqzButton(stringResource(Res.string.netflix_change_card), { onIntent(AutopayIntent.ChangeCard) }, size = SaqzButtonSize.Sm,
            variant = SaqzButtonVariant.Secondary, loading = state.busy, modifier = Modifier.testTag(AutopayTags.ChangeCard))
    }
    if (state.history.isNotEmpty()) {
        ReceivablesBand(stringResource(Res.string.netflix_history))
        state.history.forEach {
            ReceivablesRow(it.title, it.support) { SaqzStatusChip(stringResource(Res.string.charges_paid), tone = SaqzChipTone.Success) }
            SaqzDivider()
        }
    }
}

private val reasonText: Map<AutopayCancelReason, StringResource> = mapOf(
    AutopayCancelReason.STOP_PLAYING to Res.string.cancel_STOP_PLAYING,
    AutopayCancelReason.PREFER_PIX to Res.string.cancel_PREFER_PIX,
    AutopayCancelReason.CARD_PROBLEM to Res.string.cancel_CARD_PROBLEM,
    AutopayCancelReason.TOO_EXPENSIVE to Res.string.cancel_TOO_EXPENSIVE,
    AutopayCancelReason.OTHER to Res.string.cancel_OTHER,
    AutopayCancelReason.PREFER_NOT_SAY to Res.string.cancel_PREFER_NOT_SAY,
)

/** Netflix-Cancelar: a lista curta e obrigatória, num segmentado vertical (seis opções, uma escolha). */
@Composable
private fun CancelScreen(state: AutopayState, onIntent: (AutopayIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    val reasons = AutopayCancelReason.entries
    ReceivablesPage(stringResource(Res.string.netflix_title), AutopayTags.Screen, onBack, modifier, footer = {
        SaqzButton(stringResource(Res.string.netflix_cancel), { onIntent(AutopayIntent.ConfirmCancel) }, fullWidth = true,
            variant = SaqzButtonVariant.Danger, enabled = state.canConfirmCancel, loading = state.busy,
            modifier = Modifier.testTag(AutopayTags.ConfirmCancel))
        SaqzButton(stringResource(Res.string.cancel_keep), { onIntent(AutopayIntent.KeepAutopay) }, fullWidth = true,
            variant = SaqzButtonVariant.Secondary, enabled = !state.busy, modifier = Modifier.testTag(AutopayTags.Keep))
    }) {
        Column(Modifier.fillMaxWidth().padding(metrics.horizontalPadding), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            Text(stringResource(Res.string.cancel_title), style = SaqzTheme.typography.headline, color = SaqzTheme.colors.textPrimary)
            Text(stringResource(Res.string.cancel_support), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
            SaqzSegmented(
                options = reasons.map { stringResource(reasonText.getValue(it)) },
                selected = state.reason?.let(reasons::indexOf) ?: -1,
                onSelect = { onIntent(AutopayIntent.SelectReason(reasons[it])) },
                vertical = true,
                optionTags = reasons.map(AutopayTags::reason),
            )
            state.error?.let { Text(paymentErrorText(it), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground) }
            Text(stringResource(Res.string.cancel_note), style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = SaqzTheme.colors.textSecondary)
        }
    }
}

private val previewAdhesion = AutopayState(groupName = "Vôlei do CERET", loading = false, status = RecurrenceStatus.NONE,
    amountLabel = "R$ 100,00", dueDay = "10", openMonth = "Agosto")

@Preview(name = "Modo Netflix · aderir", widthDp = 390)
@Composable
private fun AutopayAdhesionPreview() = SaqzTheme { AutopayScreen(previewAdhesion, {}, {}) }

@Preview(name = "Modo Netflix · gerenciar", widthDp = 390)
@Composable
private fun AutopayManagePreview() = SaqzTheme {
    AutopayScreen(previewAdhesion.copy(status = RecurrenceStatus.ACTIVE, nextCharge = "R$ 100,00 em 10/09", history = listOf(
        AutopayHistoryUi("Agosto", "R$ 100,00 · cobrado em 10/08"),
        AutopayHistoryUi("Julho", "R$ 100,00 · cobrado na adesão, 22/07"))), {}, {})
}

@Preview(name = "Modo Netflix · cancelar com motivo", widthDp = 390)
@Composable
private fun AutopayCancelPreview() = SaqzTheme {
    AutopayScreen(previewAdhesion.copy(status = RecurrenceStatus.ACTIVE, cancelling = true,
        reason = AutopayCancelReason.PREFER_PIX), {}, {})
}
