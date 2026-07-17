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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_brand
import br.com.saqz.access.resources.auth_error_email_in_use
import br.com.saqz.access.resources.auth_error_method_conflict
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_provider
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.auth_error_weak_password
import br.com.saqz.access.resources.registration_back
import br.com.saqz.access.resources.registration_email
import br.com.saqz.access.resources.registration_email_invalid
import br.com.saqz.access.resources.registration_name
import br.com.saqz.access.resources.registration_name_required
import br.com.saqz.access.resources.registration_password
import br.com.saqz.access.resources.registration_submit
import br.com.saqz.access.resources.registration_title
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzInputKind
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object RegistrationTags {
    const val Name = "registration-name"
    const val Email = "registration-email"
    const val Password = "registration-password"
    const val Submit = "registration-submit"
    const val Back = "registration-back"
}

internal data class RegistrationSavedState(val name: String, val email: String) {
    fun restore() = AuthenticationState(screen = AuthScreen.REGISTRATION, name = name, email = email)
}

internal fun AuthenticationState.registrationSavedState() = RegistrationSavedState(name = name, email = email)

@Composable
fun RegistrationScreen(
    state: AuthenticationState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    var validationAttempted by remember { mutableStateOf(false) }
    val validName = state.name.isNotBlank()
    val validEmail = state.email.isValidEmail()
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
        Text(stringResource(Res.string.registration_title), style = SaqzTheme.typography.lead, color = colors.textPrimary)
        Spacer(Modifier.height(metrics.grid))
        SaqzInput(
            value = TextFieldValue(state.name),
            onValueChange = { onNameChange(it.text) },
            label = stringResource(Res.string.registration_name),
            errorText = if (validationAttempted && !validName) {
                stringResource(Res.string.registration_name_required)
            } else null,
            enabled = !state.isLoading,
            modifier = Modifier.testTag(RegistrationTags.Name),
        )
        SaqzInput(
            value = TextFieldValue(state.email),
            onValueChange = { onEmailChange(it.text) },
            label = stringResource(Res.string.registration_email),
            kind = SaqzInputKind.Email,
            errorText = when {
                state.error == AuthUiError.EMAIL_IN_USE -> stringResource(Res.string.auth_error_email_in_use)
                validationAttempted && !validEmail -> stringResource(Res.string.registration_email_invalid)
                else -> null
            },
            enabled = !state.isLoading,
            modifier = Modifier.testTag(RegistrationTags.Email),
        )
        SaqzInput(
            value = TextFieldValue(state.password),
            onValueChange = { onPasswordChange(it.text) },
            label = stringResource(Res.string.registration_password),
            kind = SaqzInputKind.Password,
            errorText = if (state.error == AuthUiError.WEAK_PASSWORD) {
                stringResource(Res.string.auth_error_weak_password)
            } else null,
            enabled = !state.isLoading,
            modifier = Modifier.testTag(RegistrationTags.Password),
        )
        state.registrationGlobalError()?.let { message ->
            Text(stringResource(message), style = SaqzTheme.typography.caption, color = colors.errorForeground)
        }
        SaqzButton(
            label = stringResource(Res.string.registration_submit),
            onClick = {
                validationAttempted = true
                if (validName && validEmail) onSubmit()
            },
            loading = state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(RegistrationTags.Submit),
        )
        SaqzButton(
            label = stringResource(Res.string.registration_back),
            onClick = onBack,
            variant = SaqzButtonVariant.Ghost,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().testTag(RegistrationTags.Back),
        )
    }
}

private fun String.isValidEmail(): Boolean {
    val at = indexOf('@')
    return at > 0 && at < lastIndex && substring(at + 1).contains('.')
}

private fun AuthenticationState.registrationGlobalError() = when (error) {
    AuthUiError.AUTH_METHOD_CONFLICT -> Res.string.auth_error_method_conflict
    AuthUiError.NETWORK_UNAVAILABLE -> Res.string.auth_error_network
    AuthUiError.PROVIDER_UNAVAILABLE -> Res.string.auth_error_provider
    AuthUiError.UNKNOWN, AuthUiError.INVALID_CREDENTIALS -> Res.string.auth_error_unknown
    AuthUiError.EMAIL_IN_USE, AuthUiError.WEAK_PASSWORD, null -> null
}
