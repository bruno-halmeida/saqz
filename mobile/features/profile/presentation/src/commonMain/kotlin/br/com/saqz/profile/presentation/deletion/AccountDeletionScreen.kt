package br.com.saqz.profile.presentation.deletion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.profile.domain.AccountDeletionMethod
import br.com.saqz.profile.resources.*
import org.jetbrains.compose.resources.stringResource

object AccountDeletionTags {
    const val Confirm = "account-deletion-confirm"
    const val Password = "account-deletion-password-field"
    const val Error = "account-deletion-error"
    fun delete(method: AccountDeletionMethod) = "account-deletion-${method.name.lowercase()}"
}

@Composable
fun AccountDeletionScreen(
    state: AccountDeletionState,
    onIntent: (AccountDeletionIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background).navigationBarsPadding().imePadding()) {
        SaqzTopAppBar(title = stringResource(Res.string.profile_delete_title), onBack = { if (!state.isBusy) onBack() })
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            when {
                state.isLoading -> SaqzSpinner()
                state.loadFailed -> SaqzEmptyState(
                    title = stringResource(Res.string.profile_edit_load_error),
                    action = stringResource(Res.string.profile_edit_retry), onAction = { onIntent(AccountDeletionIntent.Retry) },
                )
                else -> {
                    state.email?.let { Text(it, color = SaqzTheme.colors.textPrimary, style = SaqzTheme.typography.label) }
                    Text(
                        stringResource(Res.string.profile_delete_body),
                        color = SaqzTheme.colors.textPrimary, style = SaqzTheme.typography.body,
                    )
                    Text(
                        stringResource(Res.string.profile_delete_retention),
                        color = SaqzTheme.colors.textSecondary, style = SaqzTheme.typography.support,
                    )
                    Row(
                        Modifier.fillMaxWidth().testTag(AccountDeletionTags.Confirm).toggleable(
                            value = state.confirmed, enabled = !state.isBusy, role = Role.Checkbox,
                            onValueChange = { onIntent(AccountDeletionIntent.Confirm(it)) },
                        ), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = state.confirmed, onCheckedChange = null, enabled = !state.isBusy)
                        Text(
                            stringResource(Res.string.profile_delete_confirm),
                            style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary,
                        )
                    }
                    Text(
                        stringResource(Res.string.profile_delete_identity),
                        style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary,
                    )
                    SaqzInput(
                        state.password, { onIntent(AccountDeletionIntent.Password(it)) },
                        stringResource(Res.string.profile_delete_password), kind = SaqzInputKind.Password,
                        enabled = !state.isBusy, modifier = Modifier.testTag(AccountDeletionTags.Password),
                    )
                    val methods = if (state.supportsApple) AccountDeletionMethod.entries
                    else listOf(AccountDeletionMethod.PASSWORD, AccountDeletionMethod.GOOGLE)
                    methods.forEach { method ->
                        val label = when (method) {
                            AccountDeletionMethod.PASSWORD -> Res.string.profile_delete_with_password
                            AccountDeletionMethod.GOOGLE -> Res.string.profile_delete_with_google
                            AccountDeletionMethod.APPLE -> Res.string.profile_delete_with_apple
                        }
                        SaqzButton(stringResource(label), { onIntent(AccountDeletionIntent.Delete(method)) },
                            variant = SaqzButtonVariant.Danger, fullWidth = true,
                            enabled = state.canDelete && (method != AccountDeletionMethod.PASSWORD || state.password.isNotEmpty()),
                            modifier = Modifier.testTag(AccountDeletionTags.delete(method)))
                    }
                    if (state.isBusy) Text(stringResource(Res.string.profile_delete_busy), color = SaqzTheme.colors.textSecondary)
                    state.error?.let { error ->
                        val message = if (error == AccountDeletionError.AUTHENTICATION) Res.string.profile_delete_auth_error
                        else Res.string.profile_delete_request_error
                        Text(
                            stringResource(message), color = SaqzTheme.colors.errorForeground,
                            modifier = Modifier.testTag(AccountDeletionTags.Error),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun AccountDeletionPreview() = SaqzTheme {
    AccountDeletionScreen(
        AccountDeletionState(userId = "user", email = "ana@example.test", isLoading = false, supportsApple = true), {}, {},
    )
}
