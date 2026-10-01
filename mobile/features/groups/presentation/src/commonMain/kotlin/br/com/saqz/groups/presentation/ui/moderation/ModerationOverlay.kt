package br.com.saqz.groups.presentation.ui.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.moderation.ContentReport
import br.com.saqz.groups.domain.moderation.ReportReason
import br.com.saqz.groups.domain.moderation.ReportTargetType
import br.com.saqz.groups.presentation.moderation.BlockPromptUi
import br.com.saqz.groups.presentation.moderation.ModerationFeedback
import br.com.saqz.groups.presentation.moderation.ModerationIntent
import br.com.saqz.groups.presentation.moderation.ModerationState
import br.com.saqz.groups.presentation.moderation.ReportDraftUi
import br.com.saqz.groups.presentation.moderation.ReportTargetUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.moderation_block_body
import br.com.saqz.groups.resources.moderation_block_cancel
import br.com.saqz.groups.resources.moderation_block_confirm
import br.com.saqz.groups.resources.moderation_block_failure
import br.com.saqz.groups.resources.moderation_block_title
import br.com.saqz.groups.resources.moderation_blocked
import br.com.saqz.groups.resources.moderation_reason_harassment
import br.com.saqz.groups.resources.moderation_reason_offensive
import br.com.saqz.groups.resources.moderation_reason_other
import br.com.saqz.groups.resources.moderation_reason_spam
import br.com.saqz.groups.resources.moderation_report_details
import br.com.saqz.groups.resources.moderation_report_details_count
import br.com.saqz.groups.resources.moderation_report_failure
import br.com.saqz.groups.resources.moderation_report_reason_label
import br.com.saqz.groups.resources.moderation_report_send
import br.com.saqz.groups.resources.moderation_report_sent
import br.com.saqz.groups.resources.moderation_report_subject_group
import br.com.saqz.groups.resources.moderation_report_subject_message
import br.com.saqz.groups.resources.moderation_report_title
import br.com.saqz.groups.resources.moderation_unblock_failure
import br.com.saqz.groups.resources.moderation_unblocked
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

object ModerationTags {
    const val ReportSheet = "moderation-report-sheet"
    const val Details = "moderation-report-details"
    const val Submit = "moderation-report-submit"
    const val ReportError = "moderation-report-error"
    const val BlockConfirm = "moderation-block-confirm"
    const val BlockCancel = "moderation-block-cancel"
    const val BlockError = "moderation-block-error"
    const val Toast = "moderation-toast"

    fun reason(reason: ReportReason) = "moderation-reason-${reason.name}"
}

/**
 * Os sheets de denúncia e de bloqueio e o toast de retorno. Quem hospeda coloca este overlay
 * como último filho de um `Box` que ocupa a tela, por cima da própria tela — o mesmo contrato
 * do `SaqzBottomSheet`.
 */
@Composable
fun ModerationOverlay(
    state: ModerationState,
    onIntent: (ModerationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `imePadding`: o campo de detalhes abre o teclado, e o painel sobe junto em vez de ficar atrás dele.
    Box(modifier = modifier.fillMaxSize().imePadding()) {
        ReportSheet(draft = state.report, onIntent = onIntent)
        BlockSheet(prompt = state.block, onIntent = onIntent)
        val feedback = state.feedback
        if (feedback != null) {
            SaqzToast(
                visible = true,
                onDismiss = { onIntent(ModerationIntent.DismissFeedback) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(SaqzTheme.metrics.horizontalPadding)
                    .testTag(ModerationTags.Toast),
            ) {
                SaqzToastText(stringResource(feedback.message()))
            }
        }
    }
}

/**
 * O sheet continua compondo durante a animação de saída, quando o estado já voltou a `null`:
 * guardar o último valor evita o título piscar vazio enquanto o painel desce.
 */
@Composable
private fun <T : Any> rememberLastShown(value: T?): T? {
    // Holder simples, não `State`: quem dispara a recomposição é o próprio parâmetro.
    val last = remember { LastShown(value) }
    if (value != null) last.value = value
    return last.value
}

private class LastShown<T : Any>(var value: T?)

@Composable
private fun ReportSheet(draft: ReportDraftUi?, onIntent: (ModerationIntent) -> Unit) {
    val shown = rememberLastShown(draft)
    SaqzBottomSheet(
        open = draft != null,
        onClose = { onIntent(ModerationIntent.DismissReport) },
        title = stringResource(Res.string.moderation_report_title),
        description = shown?.target?.let { reportSubject(it) },
        footer = {
            SaqzButton(
                label = stringResource(Res.string.moderation_report_send),
                onClick = { onIntent(ModerationIntent.SubmitReport) },
                enabled = shown?.reason != null,
                loading = shown?.sending == true,
                fullWidth = true,
                modifier = Modifier.testTag(ModerationTags.Submit),
            )
        },
    ) {
        val current = shown ?: return@SaqzBottomSheet
        Text(
            text = stringResource(Res.string.moderation_report_reason_label),
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.testTag(ModerationTags.ReportSheet),
        )
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            ReportReason.entries.forEachIndexed { index, reason ->
                if (index > 0) SaqzDivider()
                ReasonRow(
                    label = stringResource(reason.label()),
                    selected = current.reason == reason,
                    enabled = !current.sending,
                    onClick = { onIntent(ModerationIntent.SelectReason(reason)) },
                    modifier = Modifier.testTag(ModerationTags.reason(reason)),
                )
            }
        }
        SaqzInput(
            value = current.details,
            onValueChange = { onIntent(ModerationIntent.UpdateDetails(it)) },
            label = stringResource(Res.string.moderation_report_details),
            enabled = !current.sending,
            singleLine = false,
            minLines = 3,
            helperText = stringResource(
                Res.string.moderation_report_details_count,
                current.details.length,
                ContentReport.MAX_DETAILS_LENGTH,
            ),
            modifier = Modifier.testTag(ModerationTags.Details),
        )
        if (current.failed) {
            Text(
                text = stringResource(Res.string.moderation_report_failure),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(ModerationTags.ReportError),
            )
        }
    }
}

@Composable
private fun reportSubject(target: ReportTargetUi): String = when (target.type) {
    ReportTargetType.MESSAGE -> stringResource(Res.string.moderation_report_subject_message, target.name)
    ReportTargetType.GROUP -> stringResource(Res.string.moderation_report_subject_group, target.name)
    ReportTargetType.USER -> target.name
}

/** Rádio na linguagem das listas de opção do app: linha inteira tocável, check à direita. */
@Composable
private fun ReasonRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.minimumTouchTarget)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = metrics.subGrid, vertical = metrics.grid),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.primary)
    }
}

@Composable
private fun BlockSheet(prompt: BlockPromptUi?, onIntent: (ModerationIntent) -> Unit) {
    val shown = rememberLastShown(prompt)
    SaqzBottomSheet(
        open = prompt != null,
        onClose = { onIntent(ModerationIntent.DismissBlock) },
        title = stringResource(Res.string.moderation_block_title, shown?.name.orEmpty()),
        description = stringResource(Res.string.moderation_block_body),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
                SaqzButton(
                    label = stringResource(Res.string.moderation_block_confirm),
                    onClick = { onIntent(ModerationIntent.ConfirmBlock) },
                    variant = SaqzButtonVariant.Danger,
                    loading = shown?.blocking == true,
                    fullWidth = true,
                    modifier = Modifier.testTag(ModerationTags.BlockConfirm),
                )
                SaqzButton(
                    label = stringResource(Res.string.moderation_block_cancel),
                    onClick = { onIntent(ModerationIntent.DismissBlock) },
                    variant = SaqzButtonVariant.Ghost,
                    enabled = shown?.blocking != true,
                    fullWidth = true,
                    modifier = Modifier.testTag(ModerationTags.BlockCancel),
                )
            }
        },
    ) {
        if (shown?.failed == true) {
            Text(
                text = stringResource(Res.string.moderation_block_failure),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(ModerationTags.BlockError),
            )
        }
    }
}

/**
 * Linha de ação de denúncia/bloqueio fora da lista de membros (o sheet de opções do aviso):
 * mesma altura e hierarquia da linha do sheet de membro, com o destrutivo em `errorForeground`.
 */
@Composable
internal fun ModerationActionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val foreground = if (destructive) colors.errorForeground else colors.textPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .heightIn(min = metrics.grid * 7)
            .padding(horizontal = metrics.subGrid),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(icon, tint = if (destructive) colors.errorForeground else colors.primary)
        Text(
            text = title,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(600)),
            color = foreground,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun ReportReason.label(): StringResource = when (this) {
    ReportReason.SPAM -> Res.string.moderation_reason_spam
    ReportReason.OFFENSIVE -> Res.string.moderation_reason_offensive
    ReportReason.HARASSMENT -> Res.string.moderation_reason_harassment
    ReportReason.OTHER -> Res.string.moderation_reason_other
}

private fun ModerationFeedback.message(): StringResource = when (this) {
    ModerationFeedback.ReportSent -> Res.string.moderation_report_sent
    ModerationFeedback.Blocked -> Res.string.moderation_blocked
    ModerationFeedback.Unblocked -> Res.string.moderation_unblocked
    ModerationFeedback.UnblockFailed -> Res.string.moderation_unblock_failure
}

private val previewTarget = ReportTargetUi("group", ReportTargetType.MESSAGE, "message", "Bia Souza")

@Preview(name = "Denunciar — motivo escolhido", widthDp = 390, heightDp = 844)
@Composable
private fun ReportSheetPreview() = SaqzTheme {
    ModerationOverlay(
        state = ModerationState(
            report = ReportDraftUi(previewTarget, reason = ReportReason.OFFENSIVE, details = "Xingou a galera no aviso."),
        ),
        onIntent = {},
    )
}

@Preview(name = "Bloquear — confirmação", widthDp = 390, heightDp = 844)
@Composable
private fun BlockSheetPreview() = SaqzTheme {
    ModerationOverlay(state = ModerationState(block = BlockPromptUi("group", "bia", "Bia Souza")), onIntent = {})
}
