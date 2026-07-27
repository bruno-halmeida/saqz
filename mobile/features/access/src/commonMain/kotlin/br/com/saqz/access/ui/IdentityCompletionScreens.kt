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
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.asString
import br.com.saqz.access.presentation.message
import br.com.saqz.access.presentation.namecompletion.NameCompletionIntent
import br.com.saqz.access.presentation.namecompletion.NameCompletionState
import br.com.saqz.access.presentation.phonecompletion.PhoneCompletionIntent
import br.com.saqz.access.presentation.phonecompletion.PhoneCompletionState
import br.com.saqz.access.presentation.verification.VerificationIntent
import br.com.saqz.access.presentation.verification.VerificationState
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
import br.com.saqz.access.resources.phone_body
import br.com.saqz.access.resources.phone_invalid
import br.com.saqz.access.resources.phone_label
import br.com.saqz.access.resources.phone_submit
import br.com.saqz.access.resources.phone_title
import br.com.saqz.access.resources.verification_body
import br.com.saqz.access.resources.verification_confirm
import br.com.saqz.access.resources.verification_resend
import br.com.saqz.access.resources.verification_sent
import br.com.saqz.access.resources.verification_title
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object IdentityTags {
    const val Verify = "identity-verify"
    const val Resend = "identity-resend"
    const val NameSubmit = "identity-name-submit"
    const val PhoneSubmit = "identity-phone-submit"
}

@Composable
fun VerificationScreen(
    state: VerificationState,
    onIntent: (VerificationIntent) -> Unit,
    modifier: Modifier = Modifier,
) = IdentityColumn(modifier) {
    IdentityHeading(stringResource(Res.string.verification_title))
    Text(stringResource(Res.string.verification_body), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary)
    Text(state.email, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
    if (state.verificationSent) {
        Text(stringResource(Res.string.verification_sent), style = SaqzTheme.typography.support, color = SaqzTheme.colors.accent)
    }
    state.error?.let {
        Text(it.message().asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
    }
    SaqzButton(
        label = stringResource(Res.string.verification_confirm),
        onClick = { onIntent(VerificationIntent.Confirm) },
        loading = state.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(IdentityTags.Verify),
    )
    SaqzButton(
        label = stringResource(Res.string.verification_resend),
        onClick = { onIntent(VerificationIntent.Resend) },
        variant = SaqzButtonVariant.Secondary,
        enabled = !state.isLoading && !state.verificationSent,
        modifier = Modifier.fillMaxWidth().testTag(IdentityTags.Resend),
    )
}

@Composable
fun NameCompletionScreen(
    state: NameCompletionState,
    onIntent: (NameCompletionIntent) -> Unit,
    modifier: Modifier = Modifier,
) = IdentityColumn(modifier) {
    IdentityHeading(stringResource(Res.string.name_title))
    Text(stringResource(Res.string.name_body), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary)
    SaqzInput(
        value = TextFieldValue(state.name),
        onValueChange = { onIntent(NameCompletionIntent.UpdateName(it.text)) },
        label = stringResource(Res.string.name_label),
        errorText = if (state.invalidName) stringResource(Res.string.name_invalid) else null,
        enabled = !state.isLoading,
    )
    state.error?.let {
        Text(it.message().asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
    }
    SaqzButton(
        label = stringResource(Res.string.name_submit),
        onClick = { onIntent(NameCompletionIntent.Complete) },
        loading = state.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(IdentityTags.NameSubmit),
    )
}

@Composable
fun PhoneCompletionScreen(
    state: PhoneCompletionState,
    onIntent: (PhoneCompletionIntent) -> Unit,
    modifier: Modifier = Modifier,
) = IdentityColumn(modifier) {
    IdentityHeading(stringResource(Res.string.phone_title))
    Text(stringResource(Res.string.phone_body), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary)
    SaqzInput(
        value = TextFieldValue(state.phone),
        onValueChange = { onIntent(PhoneCompletionIntent.UpdatePhone(it.text)) },
        label = stringResource(Res.string.phone_label),
        kind = SaqzInputKind.Phone,
        errorText = if (state.invalidPhone) stringResource(Res.string.phone_invalid) else null,
        enabled = !state.isLoading,
    )
    state.error?.let {
        Text(it.message().asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
    }
    SaqzButton(
        label = stringResource(Res.string.phone_submit),
        onClick = { onIntent(PhoneCompletionIntent.Complete) },
        loading = state.isLoading,
        modifier = Modifier.fillMaxWidth().testTag(IdentityTags.PhoneSubmit),
    )
}

@Composable
private fun IdentityColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) { content() }
}

@Composable
private fun IdentityHeading(text: String) {
    Text(text, style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
}

@Preview
@Composable
private fun VerificationScreenPreview() = SaqzTheme {
    VerificationScreen(VerificationState(email = "ana@exemplo.com", verificationSent = true), {})
}

@Preview
@Composable
private fun NameCompletionScreenPreview() = SaqzTheme {
    NameCompletionScreen(NameCompletionState(name = "Ana"), {})
}

