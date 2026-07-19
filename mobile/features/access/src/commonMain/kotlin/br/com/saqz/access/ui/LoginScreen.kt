package br.com.saqz.access.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_email_in_use
import br.com.saqz.access.resources.auth_error_invalid_credentials
import br.com.saqz.access.resources.auth_error_method_conflict
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.auth_error_provider
import br.com.saqz.access.resources.auth_error_unknown
import br.com.saqz.access.resources.auth_error_weak_password
import br.com.saqz.access.resources.google_g
import br.com.saqz.access.resources.login_continue_with
import br.com.saqz.access.resources.login_email
import br.com.saqz.access.resources.login_google
import br.com.saqz.access.resources.login_headline_emphasis
import br.com.saqz.access.resources.login_headline_first
import br.com.saqz.access.resources.login_headline_second
import br.com.saqz.access.resources.login_no_account
import br.com.saqz.access.resources.login_password
import br.com.saqz.access.resources.login_register
import br.com.saqz.access.resources.login_reset
import br.com.saqz.access.resources.login_submit
import br.com.saqz.access.resources.login_supporting_text
import br.com.saqz.access.resources.material_arrow_forward
import br.com.saqz.access.resources.material_chevron_right
import br.com.saqz.access.resources.material_lock
import br.com.saqz.access.resources.material_mail
import br.com.saqz.access.resources.material_sports_volleyball
import br.com.saqz.access.resources.saqz_symbol_foreground
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzInputKind
import br.com.saqz.designsystem.resources.Res as DesignRes
import br.com.saqz.designsystem.resources.saqz_lettering
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
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
    onIntent: (AuthenticationIntent) -> Unit,
) {
    val metrics = SaqzTheme.metrics
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp)
                .padding(top = 48.dp, bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SaqzBrandMark(Modifier.size(110.dp))

            Spacer(Modifier.height(14.dp))
            Image(
                painter = painterResource(DesignRes.drawable.saqz_lettering),
                contentDescription = null,
                modifier = Modifier.size(width = 108.dp, height = 32.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.login_headline_first))
                    append('\n')
                    append(stringResource(Res.string.login_headline_second))
                    append(' ')
                    pushStyle(SpanStyle(color = colors.primary))
                    append(stringResource(Res.string.login_headline_emphasis))
                    pop()
                },
                style = SaqzTheme.typography.lead.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(metrics.grid))
            Text(
                text = stringResource(Res.string.login_supporting_text),
                style = SaqzTheme.typography.navigation.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 272.dp),
            )
            Spacer(Modifier.height(30.dp))
            SaqzInput(
                value = TextFieldValue(state.email),
                onValueChange = { onIntent(AuthenticationIntent.UpdateEmail(it.text)) },
                label = stringResource(Res.string.login_email),
                kind = SaqzInputKind.Email,
                enabled = !state.isLoading,
                inlineLabel = true,
                leadingContent = { MaterialIcon(Res.drawable.material_mail, colors.primary) },
                modifier = Modifier.testTag(LoginTags.Email),
            )
            Spacer(Modifier.height(10.dp))
            SaqzInput(
                value = TextFieldValue(state.password),
                onValueChange = { onIntent(AuthenticationIntent.UpdatePassword(it.text)) },
                label = stringResource(Res.string.login_password),
                kind = SaqzInputKind.Password,
                enabled = !state.isLoading,
                inlineLabel = true,
                leadingContent = { MaterialIcon(Res.drawable.material_lock, colors.primary) },
                modifier = Modifier.testTag(LoginTags.Password),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SaqzButton(
                    label = stringResource(Res.string.login_reset),
                    onClick = { onIntent(AuthenticationIntent.ShowPasswordReset) },
                    variant = SaqzButtonVariant.Ghost,
                    enabled = !state.isLoading,
                    labelStyle = SaqzTheme.typography.navigation.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
            state.error?.let { error ->
                Text(
                    text = stringResource(error.resource()),
                    style = SaqzTheme.typography.caption,
                    color = colors.errorForeground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = metrics.grid),
                )
            }
            LoginPrimaryAction(
                label = stringResource(Res.string.login_submit),
                onClick = { onIntent(AuthenticationIntent.SubmitPasswordLogin) },
                enabled = !state.isLoading,
                loading = state.isLoading,
            )
            Spacer(Modifier.height(18.dp))
            LoginDivider(label = stringResource(Res.string.login_continue_with))
            Spacer(Modifier.height(14.dp))
            GoogleAction(
                label = stringResource(Res.string.login_google),
                onClick = { onIntent(AuthenticationIntent.SubmitGoogleLogin) },
                enabled = !state.isLoading,
            )
            Spacer(Modifier.weight(0.5f))
            Text(
                text = stringResource(Res.string.login_no_account),
                style = SaqzTheme.typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            SaqzButton(
                label = stringResource(Res.string.login_register),
                onClick = { onIntent(AuthenticationIntent.ShowRegistration) },
                variant = SaqzButtonVariant.Ghost,
                enabled = !state.isLoading,
                labelStyle = SaqzTheme.typography.caption.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
                trailingContent = { color ->
                    MaterialIcon(
                        resource = Res.drawable.material_chevron_right,
                        color = color,
                        size = 16.dp,
                    )
                },
            )
        }
    }
}

@Composable
internal fun BoxScope.AccessBackdrop() {
    val colors = SaqzTheme.colors
    Image(
        painter = painterResource(Res.drawable.material_sports_volleyball),
        contentDescription = null,
        colorFilter = ColorFilter.tint(colors.primary),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 50.dp, y = (-16).dp)
            .size(250.dp)
            .alpha(0.025f),
    )
    Canvas(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(104.dp)
            .clearAndSetSemantics {},
    ) {
        val softWave = Path().apply {
            moveTo(0f, size.height * 0.14f)
            cubicTo(
                size.width * 0.25f,
                size.height * 0.80f,
                size.width * 0.60f,
                size.height * 0.80f,
                size.width,
                size.height * 0.20f,
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(softWave, color = Color(0xFF90B5FF))
        val primaryWave = Path().apply {
            moveTo(0f, size.height * 0.28f)
            cubicTo(
                size.width * 0.30f,
                size.height * 0.88f,
                size.width * 0.65f,
                size.height * 1.02f,
                size.width,
                size.height * 0.52f,
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(primaryWave, color = colors.primary)
    }
}

@Composable
private fun LoginPrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean, loading: Boolean) {
    SaqzButton(
        label = label,
        onClick = onClick,
        enabled = enabled,
        loading = loading,
        labelStyle = SaqzTheme.typography.caption.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        ),
        trailingContent = { color ->
            MaterialIcon(
                resource = Res.drawable.material_arrow_forward,
                color = color,
                size = 16.dp,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .testTag(LoginTags.Submit),
    )
}

@Composable
private fun LoginDivider(label: String) {
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

@Composable
private fun GoogleAction(label: String, onClick: () -> Unit, enabled: Boolean) {
    SaqzButton(
        label = label,
        onClick = onClick,
        variant = SaqzButtonVariant.Secondary,
        enabled = enabled,
        labelStyle = SaqzTheme.typography.navigation.copy(
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        ),
        contentColor = SaqzTheme.colors.textPrimary,
        leadingContent = { GoogleIcon() },
        modifier = Modifier.fillMaxWidth().testTag(LoginTags.Google),
    )
}

@Composable
internal fun SaqzBrandMark(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF0B55F2), Color(0xFF0638DF), Color(0xFF002CB8)),
                ),
                shape = shape,
            )
            .clearAndSetSemantics {},
    ) {
        Image(
            painter = painterResource(Res.drawable.saqz_symbol_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun MaterialIcon(
    resource: DrawableResource,
    color: Color,
    size: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(size).clearAndSetSemantics {},
    )
}

@Composable
private fun GoogleIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(Res.drawable.google_g),
        contentDescription = null,
        modifier = modifier.size(20.dp).clearAndSetSemantics {},
    )
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

@Preview(
    name = "Login — compacto",
    widthDp = 354,
    heightDp = 796,
    showBackground = true,
    backgroundColor = 0xFFF5F5F7,
)
@Composable
private fun LoginScreenPreview() = SaqzTheme {
    LoginScreen(AuthenticationState(email = "ana@exemplo.com"), {})
}
