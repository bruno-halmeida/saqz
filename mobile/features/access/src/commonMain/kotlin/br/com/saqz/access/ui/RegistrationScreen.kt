package br.com.saqz.access.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.isValidEmail
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_brand
import br.com.saqz.access.resources.auth_error_email_in_use
import br.com.saqz.access.resources.auth_error_method_conflict
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_provider
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.auth_error_weak_password
import br.com.saqz.access.resources.google_g
import br.com.saqz.access.resources.login_continue_with
import br.com.saqz.access.resources.material_lock
import br.com.saqz.access.resources.material_mail
import br.com.saqz.access.resources.material_person
import br.com.saqz.access.resources.registration_back
import br.com.saqz.access.resources.registration_body
import br.com.saqz.access.resources.registration_email
import br.com.saqz.access.resources.registration_email_invalid
import br.com.saqz.access.resources.registration_google
import br.com.saqz.access.resources.registration_name
import br.com.saqz.access.resources.registration_name_required
import br.com.saqz.access.resources.registration_password
import br.com.saqz.access.resources.registration_submit
import br.com.saqz.access.resources.registration_title
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzInputKind
import br.com.saqz.designsystem.resources.Res as DesignRes
import br.com.saqz.designsystem.resources.saqz_lettering
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object RegistrationTags {
    const val Title = "registration-title"
    const val Name = "registration-name"
    const val Email = "registration-email"
    const val Password = "registration-password"
    const val Submit = "registration-submit"
    const val Google = "registration-google"
    const val Back = "registration-back"
}

internal data class RegistrationSavedState(val name: String, val email: String) {
    fun restore() = AuthenticationState(screen = AuthScreen.REGISTRATION, name = name, email = email)
}

internal fun AuthenticationState.registrationSavedState() = RegistrationSavedState(name = name, email = email)

@Composable
fun RegistrationScreen(
    state: AuthenticationState,
    onIntent: (AuthenticationIntent) -> Unit,
) {
    val validName = state.name.isNotBlank()
    val validEmail = state.email.isValidEmail()
    val colors = SaqzTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        AccessBackdrop()
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 420.dp)
                .fillMaxHeight()
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp)
                .padding(top = 40.dp, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SaqzBrandMark(Modifier.size(68.dp))
            Spacer(Modifier.height(4.dp))
            Image(
                painter = painterResource(DesignRes.drawable.saqz_lettering),
                contentDescription = stringResource(Res.string.access_brand),
                modifier = Modifier.size(width = 96.dp, height = 29.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(Res.string.registration_title),
                style = SaqzTheme.typography.lead.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textPrimary,
                modifier = Modifier.testTag(RegistrationTags.Title),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.registration_body),
                style = SaqzTheme.typography.navigation.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp),
            )
            Spacer(Modifier.height(24.dp))
            SaqzInput(
                value = TextFieldValue(state.name),
                onValueChange = { onIntent(AuthenticationIntent.UpdateName(it.text)) },
                label = stringResource(Res.string.registration_name),
                inlineLabel = true,
                leadingContent = { MaterialIcon(Res.drawable.material_person, colors.primary) },
                errorText = if (state.validationAttempted && !validName) {
                    stringResource(Res.string.registration_name_required)
                } else null,
                enabled = !state.isLoading,
                modifier = Modifier.testTag(RegistrationTags.Name),
            )
            Spacer(Modifier.height(10.dp))
            SaqzInput(
                value = TextFieldValue(state.email),
                onValueChange = { onIntent(AuthenticationIntent.UpdateEmail(it.text)) },
                label = stringResource(Res.string.registration_email),
                kind = SaqzInputKind.Email,
                inlineLabel = true,
                leadingContent = { MaterialIcon(Res.drawable.material_mail, colors.primary) },
                errorText = when {
                    state.error == AuthUiError.EMAIL_IN_USE -> stringResource(Res.string.auth_error_email_in_use)
                    state.validationAttempted && !validEmail -> stringResource(Res.string.registration_email_invalid)
                    else -> null
                },
                enabled = !state.isLoading,
                modifier = Modifier.testTag(RegistrationTags.Email),
            )
            Spacer(Modifier.height(10.dp))
            SaqzInput(
                value = TextFieldValue(state.password),
                onValueChange = { onIntent(AuthenticationIntent.UpdatePassword(it.text)) },
                label = stringResource(Res.string.registration_password),
                kind = SaqzInputKind.Password,
                inlineLabel = true,
                leadingContent = { MaterialIcon(Res.drawable.material_lock, colors.primary) },
                errorText = if (state.error == AuthUiError.WEAK_PASSWORD) {
                    stringResource(Res.string.auth_error_weak_password)
                } else null,
                enabled = !state.isLoading,
                modifier = Modifier.testTag(RegistrationTags.Password),
            )
            state.registrationGlobalError()?.let { message ->
                Text(
                    text = stringResource(message),
                    style = SaqzTheme.typography.caption,
                    color = colors.errorForeground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            SaqzButton(
                label = stringResource(Res.string.registration_submit),
                onClick = { onIntent(AuthenticationIntent.SubmitRegistration) },
                loading = state.isLoading,
                labelStyle = SaqzTheme.typography.navigation.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.fillMaxWidth().clip(CircleShape).testTag(RegistrationTags.Submit),
            )
            Spacer(Modifier.height(18.dp))
            RegistrationDivider(stringResource(Res.string.login_continue_with))
            Spacer(Modifier.height(14.dp))
            SaqzButton(
                label = stringResource(Res.string.registration_google),
                onClick = { onIntent(AuthenticationIntent.SubmitGoogleLogin) },
                variant = SaqzButtonVariant.Secondary,
                enabled = !state.isLoading,
                labelStyle = SaqzTheme.typography.navigation.copy(
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
                contentColor = colors.textPrimary,
                leadingContent = {
                    Image(
                        painter = painterResource(Res.drawable.google_g),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).clearAndSetSemantics {},
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag(RegistrationTags.Google),
            )
            Spacer(Modifier.height(10.dp))
            SaqzButton(
                label = stringResource(Res.string.registration_back),
                onClick = { onIntent(AuthenticationIntent.ShowLogin) },
                variant = SaqzButtonVariant.Ghost,
                enabled = !state.isLoading,
                labelStyle = SaqzTheme.typography.navigation.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.testTag(RegistrationTags.Back),
            )
        }
    }
}

@Composable
private fun RegistrationDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(SaqzTheme.colors.hairline))
        Text(label, style = SaqzTheme.typography.navigation, color = SaqzTheme.colors.textSecondary)
        Box(Modifier.weight(1f).height(1.dp).background(SaqzTheme.colors.hairline))
    }
}

private fun AuthenticationState.registrationGlobalError() = when (error) {
    AuthUiError.AUTH_METHOD_CONFLICT -> Res.string.auth_error_method_conflict
    AuthUiError.NETWORK_UNAVAILABLE -> Res.string.auth_error_network
    AuthUiError.PROVIDER_UNAVAILABLE -> Res.string.auth_error_provider
    AuthUiError.UNKNOWN, AuthUiError.INVALID_CREDENTIALS -> Res.string.auth_error_unknown
    AuthUiError.EMAIL_IN_USE, AuthUiError.WEAK_PASSWORD, null -> null
}

@Preview
@Composable
private fun RegistrationScreenPreview() = SaqzTheme {
    RegistrationScreen(AuthenticationState(screen = AuthScreen.REGISTRATION, name = "Ana", email = "ana@exemplo.com"), {})
}
