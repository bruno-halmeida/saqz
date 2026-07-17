package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_brand
import br.com.saqz.access.resources.auth_error_email_in_use
import br.com.saqz.access.resources.auth_error_invalid_credentials
import br.com.saqz.access.resources.auth_error_method_conflict
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_provider
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.auth_error_weak_password
import br.com.saqz.access.resources.login_email
import br.com.saqz.access.resources.login_google
import br.com.saqz.access.resources.login_password
import br.com.saqz.access.resources.login_register
import br.com.saqz.access.resources.login_reset
import br.com.saqz.access.resources.login_submit
import br.com.saqz.access.resources.login_title
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzInputKind
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object LoginTags {
    const val Email = "login-email"
    const val Password = "login-password"
    const val Submit = "login-submit"
    const val Google = "login-google"
}

@Composable
fun LoginScreen(
    state: AuthenticationState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogle: () -> Unit,
    onRegister: () -> Unit,
    onReset: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val colors = SaqzTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        Text(stringResource(Res.string.access_brand), style = SaqzTheme.typography.displayMedium, color = colors.primary)
        Text(stringResource(Res.string.login_title), style = SaqzTheme.typography.lead, color = colors.textPrimary)
        Spacer(Modifier.height(metrics.grid))
        SaqzInput(
            value = TextFieldValue(state.email),
            onValueChange = { onEmailChange(it.text) },
            label = stringResource(Res.string.login_email),
            kind = SaqzInputKind.Email,
            enabled = !state.isLoading,
            modifier = Modifier.testTag(LoginTags.Email),
        )
        SaqzInput(
            value = TextFieldValue(state.password),
            onValueChange = { onPasswordChange(it.text) },
            label = stringResource(Res.string.login_password),
            kind = SaqzInputKind.Password,
            enabled = !state.isLoading,
            modifier = Modifier.testTag(LoginTags.Password),
        )
        state.error?.let { error ->
            Text(
                text = stringResource(error.resource()),
                style = SaqzTheme.typography.caption,
                color = colors.errorForeground,
            )
        }
        SaqzButton(
            label = stringResource(Res.string.login_submit),
            onClick = onSubmit,
            loading = state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(LoginTags.Submit),
        )
        SaqzButton(
            label = stringResource(Res.string.login_google),
            onClick = onGoogle,
            variant = SaqzButtonVariant.Secondary,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(LoginTags.Google),
        )
        SaqzButton(
            label = stringResource(Res.string.login_reset),
            onClick = onReset,
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
        SaqzButton(
            label = stringResource(Res.string.login_register),
            onClick = onRegister,
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun AuthUiError.resource(): StringResource = when (this) {
    AuthUiError.INVALID_CREDENTIALS -> Res.string.auth_error_invalid_credentials
    AuthUiError.EMAIL_IN_USE -> Res.string.auth_error_email_in_use
    AuthUiError.WEAK_PASSWORD -> Res.string.auth_error_weak_password
    AuthUiError.AUTH_METHOD_CONFLICT -> Res.string.auth_error_method_conflict
    AuthUiError.NETWORK_UNAVAILABLE -> Res.string.auth_error_network
    AuthUiError.PROVIDER_UNAVAILABLE -> Res.string.auth_error_provider
    AuthUiError.UNKNOWN -> Res.string.auth_error_unknown
}
