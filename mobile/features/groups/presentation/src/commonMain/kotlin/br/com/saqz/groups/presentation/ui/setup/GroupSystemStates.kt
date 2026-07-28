package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzOfflineBanner
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_error_banner
import br.com.saqz.groups.resources.group_setup_error_banner_one
import br.com.saqz.groups.resources.group_system_creating
import br.com.saqz.groups.resources.group_system_offline
import br.com.saqz.groups.resources.group_system_offline_title
import br.com.saqz.groups.resources.group_system_retry
import br.com.saqz.groups.resources.group_system_save_draft
import br.com.saqz.groups.resources.group_system_save_failure
import br.com.saqz.groups.resources.group_system_save_failure_body
import br.com.saqz.groups.resources.group_system_save_failure_title
import br.com.saqz.groups.resources.group_system_sending_title
import br.com.saqz.groups.resources.group_system_session_expired
import br.com.saqz.groups.resources.group_system_session_expired_body
import br.com.saqz.groups.resources.group_system_session_expired_title
import br.com.saqz.groups.resources.group_system_toast
import br.com.saqz.groups.resources.group_system_toast_title
import br.com.saqz.groups.resources.group_system_try
import org.jetbrains.compose.resources.stringResource

private const val SoftTintAlpha = 0.1f

/** `2g` — quantas informações faltam. O plural vira singular com um só erro. */
@Composable
internal fun GroupErrorBanner(count: Int, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.errorForeground.copy(alpha = SoftTintAlpha))
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(GroupSetupTags.ErrorBanner),
        horizontalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = colors.errorForeground)
        Text(
            text = if (count == 1) {
                stringResource(Res.string.group_setup_error_banner_one, count)
            } else {
                stringResource(Res.string.group_setup_error_banner, count)
            },
            style = SaqzTheme.typography.support,
            color = colors.errorForeground,
        )
    }
}

/** `2h` — falha ao salvar: o formulário continua na tela, com as duas saídas. */
@Composable
internal fun GroupSaveFailureCard(
    onRetry: () -> Unit,
    onSaveDraft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzCard(modifier = modifier.testTag(GroupSetupTags.SaveFailure)) {
        GroupStatusRow(
            icon = SaqzIcons.CircleAlert,
            tint = SaqzTheme.colors.errorForeground,
            title = stringResource(Res.string.group_system_save_failure),
            body = stringResource(Res.string.group_system_save_failure_body),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        ) {
            SaqzButton(
                label = stringResource(Res.string.group_system_retry),
                onClick = onRetry,
                size = SaqzButtonSize.Sm,
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(GroupSetupTags.Retry),
            )
            SaqzButton(
                label = stringResource(Res.string.group_system_save_draft),
                onClick = onSaveDraft,
                variant = SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(GroupSetupTags.SaveDraft),
            )
        }
    }
}

/**
 * `2h` — sessão expirada.
 *
 * ponytail: sem gateway não há 401, então nada no estado a acende ainda. Ela existe
 * pintada e conferida na captura; quando o gateway entrar, é um campo a mais no estado
 * e uma linha no `GroupSetupScreen`.
 */
@Composable
internal fun GroupSessionExpiredCard(modifier: Modifier = Modifier) {
    SaqzCard(modifier = modifier) {
        GroupStatusRow(
            icon = SaqzIcons.Lock,
            tint = SaqzTheme.colors.warningForeground,
            title = stringResource(Res.string.group_system_session_expired),
            body = stringResource(Res.string.group_system_session_expired_body),
        )
    }
}

/** `2h` — toast de erro com ação. Mesmo motivo do card acima: pintado, ainda sem gatilho. */
@Composable
internal fun GroupErrorToast(
    visible: Boolean,
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzToast(visible = visible, onDismiss = onDismiss, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzToastText(message, modifier = Modifier.weight(1f))
            val retryLabel = stringResource(Res.string.group_system_try)
            Text(
                text = retryLabel,
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(SaqzTheme.metrics.inputRadius))
                    .clickable(onClickLabel = retryLabel, role = Role.Button, onClick = onRetry)
                    .padding(horizontal = SaqzTheme.metrics.subGrid)
                    .testTag(GroupSetupTags.ToastAction),
            )
        }
    }
}

@Composable
private fun GroupStatusRow(icon: ImageVector, tint: Color, title: String, body: String) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(metrics.iconButtonSize)
                .clip(CircleShape)
                .background(tint.copy(alpha = SoftTintAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(icon, tint = tint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
            Text(body, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        }
    }
}

/** O catálogo do `2h` inteiro, na ordem do export. */
@Preview
@Composable
private fun GroupSystemStatesPreview() = SaqzTheme {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SaqzTheme.colors.background)
            .padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.horizontalPadding),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_system_offline_title))
        SaqzOfflineBanner(message = stringResource(Res.string.group_system_offline))
        SaqzSectionHeader(title = stringResource(Res.string.group_system_save_failure_title))
        GroupSaveFailureCard(onRetry = {}, onSaveDraft = {})
        SaqzSectionHeader(title = stringResource(Res.string.group_system_sending_title))
        SaqzButton(
            label = stringResource(Res.string.group_system_creating),
            onClick = {},
            loading = true,
            fullWidth = true,
        )
        SaqzSectionHeader(title = stringResource(Res.string.group_system_session_expired_title))
        GroupSessionExpiredCard()
        SaqzSectionHeader(title = stringResource(Res.string.group_system_toast_title))
        GroupErrorToast(
            visible = true,
            message = stringResource(Res.string.group_system_toast),
            onRetry = {},
            onDismiss = {},
        )
    }
}
