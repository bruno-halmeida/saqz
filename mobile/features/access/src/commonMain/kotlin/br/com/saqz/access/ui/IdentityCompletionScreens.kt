package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.access.port.NativeUser
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.isValidEmail
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_method_conflict
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_provider
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.name_body
import br.com.saqz.access.resources.name_invalid
import br.com.saqz.access.resources.name_label
import br.com.saqz.access.resources.name_submit
import br.com.saqz.access.resources.name_title
import br.com.saqz.access.resources.registration_email_invalid
import br.com.saqz.access.resources.reset_back
import br.com.saqz.access.resources.reset_body
import br.com.saqz.access.resources.reset_confirmation
import br.com.saqz.access.resources.reset_email
import br.com.saqz.access.resources.reset_submit
import br.com.saqz.access.resources.reset_title
import br.com.saqz.access.resources.verification_body
import br.com.saqz.access.resources.verification_confirm
import br.com.saqz.access.resources.verification_resend
import br.com.saqz.access.resources.verification_sent
import br.com.saqz.access.resources.verification_title
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzInputKind
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object IdentityTags {
    const val Verify = "identity-verify"
    const val Resend = "identity-resend"
    const val NameSubmit = "identity-name-submit"
    const val ResetSubmit = "identity-reset-submit"
    const val ResetBack = "identity-reset-back"
}

@Composable
fun VerificationScreen(
    state: SessionAccessState.AwaitingVerification,
    onIntent: (SessionIntent) -> Unit,
) = IdentityColumn {
    IdentityHeading(stringResource(Res.string.verification_title))
    Text(stringResource(Res.string.verification_body), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary)
    Text(state.user.email.orEmpty(), style = SaqzTheme.typography.bodyStrong, color = SaqzTheme.colors.textPrimary)
    if (state.verificationSent) {
        Text(stringResource(Res.string.verification_sent), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.accent)
    }
    state.error?.let {
        Text(stringResource(it.identityError()), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground)
    }
    SaqzButton(
        label = stringResource(Res.string.verification_confirm),
        onClick = { onIntent(SessionIntent.ConfirmVerification) },
        loading = state.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(IdentityTags.Verify),
    )
    SaqzButton(
        label = stringResource(Res.string.verification_resend),
        onClick = { onIntent(SessionIntent.ResendVerification) },
        variant = SaqzButtonVariant.Secondary,
        enabled = !state.isLoading && !state.verificationSent,
        modifier = Modifier.fillMaxWidth().testTag(IdentityTags.Resend),
    )
}

@Composable
fun NameCompletionScreen(
    state: SessionAccessState.CompletingName,
    onIntent: (SessionIntent) -> Unit,
) = IdentityColumn {
    IdentityHeading(stringResource(Res.string.name_title))
    Text(stringResource(Res.string.name_body), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary)
    SaqzInput(
        value = TextFieldValue(state.name),
        onValueChange = { onIntent(SessionIntent.UpdateName(it.text)) },
        label = stringResource(Res.string.name_label),
        errorText = if (state.invalidName) stringResource(Res.string.name_invalid) else null,
        enabled = !state.isLoading,
    )
    state.error?.let {
        Text(stringResource(it.identityError()), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground)
    }
    SaqzButton(
        label = stringResource(Res.string.name_submit),
        onClick = { onIntent(SessionIntent.CompleteName) },
        loading = state.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(IdentityTags.NameSubmit),
    )
}

@Composable
fun PasswordResetScreen(
    state: AuthenticationState,
    onIntent: (AuthenticationIntent) -> Unit,
) {
    IdentityColumn {
        IdentityHeading(stringResource(Res.string.reset_title))
        if (state.resetConfirmation) {
            Text(
                stringResource(Res.string.reset_confirmation),
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textPrimary,
            )
        } else {
            Text(stringResource(Res.string.reset_body), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary)
            SaqzInput(
                value = TextFieldValue(state.email),
                onValueChange = { onIntent(AuthenticationIntent.UpdateEmail(it.text)) },
                label = stringResource(Res.string.reset_email),
                kind = SaqzInputKind.Email,
                errorText = if (state.validationAttempted && !state.email.isValidEmail()) {
                    stringResource(Res.string.registration_email_invalid)
                } else null,
                enabled = !state.isLoading,
            )
            SaqzButton(
                label = stringResource(Res.string.reset_submit),
                onClick = { onIntent(AuthenticationIntent.SubmitPasswordReset) },
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth().testTag(IdentityTags.ResetSubmit),
            )
        }
        SaqzButton(
            label = stringResource(Res.string.reset_back),
            onClick = { onIntent(AuthenticationIntent.ShowLogin) },
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(IdentityTags.ResetBack),
        )
    }
}

@Composable
private fun IdentityColumn(content: @Composable () -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) { content() }
}

@Composable
private fun IdentityHeading(text: String) {
    Text(text, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
}

private fun AuthUiError.identityError(): StringResource = when (this) {
    AuthUiError.NETWORK_UNAVAILABLE -> Res.string.auth_error_network
    AuthUiError.PROVIDER_UNAVAILABLE -> Res.string.auth_error_provider
    AuthUiError.AUTH_METHOD_CONFLICT -> Res.string.auth_error_method_conflict
    else -> Res.string.auth_error_unknown
}

private val previewIdentityUser = NativeUser("preview-user", "ana@exemplo.com", false, "Ana")

@Preview
@Composable
private fun VerificationScreenPreview() = SaqzTheme {
    VerificationScreen(SessionAccessState.AwaitingVerification(previewIdentityUser, verificationSent = true), {})
}

@Preview
@Composable
private fun NameCompletionScreenPreview() = SaqzTheme {
    NameCompletionScreen(SessionAccessState.CompletingName(previewIdentityUser, name = "Ana"), {})
}

@Preview
@Composable
private fun PasswordResetScreenPreview() = SaqzTheme {
    PasswordResetScreen(AuthenticationState(email = "ana@exemplo.com"), {})
}
