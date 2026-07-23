package br.com.saqz.access.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.message
import br.com.saqz.designsystem.text.asString
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.isValidEmail
import br.com.saqz.access.presentation.passwordreset.PasswordResetIntent
import br.com.saqz.access.presentation.passwordreset.PasswordResetState
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
import br.com.saqz.access.resources.material_mail
import br.com.saqz.access.resources.material_shield_lock
import br.com.saqz.access.resources.registration_email_invalid
import br.com.saqz.access.resources.reset_back
import br.com.saqz.access.resources.reset_body
import br.com.saqz.access.resources.reset_confirmation
import br.com.saqz.access.resources.reset_email
import br.com.saqz.access.resources.reset_security_hint
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
import br.com.saqz.designsystem.resources.Res as DesignRes
import br.com.saqz.designsystem.resources.saqz_lettering
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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
        Text(it.message().asString(), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground)
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
        Text(it.message().asString(), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground)
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
    state: PasswordResetState,
    onIntent: (PasswordResetIntent) -> Unit,
) {
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
                .padding(top = 48.dp, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SaqzBrandMark(Modifier.size(68.dp))
            Spacer(Modifier.height(4.dp))
            Image(
                painter = painterResource(DesignRes.drawable.saqz_lettering),
                contentDescription = null,
                modifier = Modifier.size(width = 96.dp, height = 29.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(Res.string.reset_title),
                style = SaqzTheme.typography.lead.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (state.resetConfirmation) Res.string.reset_confirmation else Res.string.reset_body,
                ),
                style = SaqzTheme.typography.navigation.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = if (state.resetConfirmation) colors.textPrimary else colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp),
            )
            if (!state.resetConfirmation) {
                Spacer(Modifier.height(28.dp))
                SaqzInput(
                    value = TextFieldValue(state.email),
                    onValueChange = { onIntent(PasswordResetIntent.UpdateEmail(it.text)) },
                    label = stringResource(Res.string.reset_email),
                    kind = SaqzInputKind.Email,
                    inlineLabel = true,
                    leadingContent = { MaterialIcon(Res.drawable.material_mail, colors.primary) },
                    errorText = if (state.validationAttempted && !state.email.isValidEmail()) {
                        stringResource(Res.string.registration_email_invalid)
                    } else null,
                    enabled = !state.isLoading,
                )
                Spacer(Modifier.height(28.dp))
                SaqzButton(
                    label = stringResource(Res.string.reset_submit),
                    onClick = { onIntent(PasswordResetIntent.SubmitPasswordReset) },
                    loading = state.isLoading,
                    labelStyle = SaqzTheme.typography.navigation.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .testTag(IdentityTags.ResetSubmit),
                )
            }
            Spacer(Modifier.height(10.dp))
            SaqzButton(
                label = stringResource(Res.string.reset_back),
                onClick = { onIntent(PasswordResetIntent.ShowLogin) },
                variant = SaqzButtonVariant.Ghost,
                enabled = !state.isLoading,
                labelStyle = SaqzTheme.typography.navigation.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.testTag(IdentityTags.ResetBack),
            )
            if (!state.resetConfirmation) {
                Spacer(Modifier.height(14.dp))
                MaterialIcon(Res.drawable.material_shield_lock, colors.primary, size = 20.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.reset_security_hint),
                    style = SaqzTheme.typography.navigation.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    ),
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
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
    PasswordResetScreen(PasswordResetState(email = "ana@exemplo.com"), {})
}
