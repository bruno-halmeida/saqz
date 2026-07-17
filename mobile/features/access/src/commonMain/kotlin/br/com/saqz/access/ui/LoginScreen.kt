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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.component.SaqzInputKind
import br.com.saqz.designsystem.resources.Res as DesignRes
import br.com.saqz.designsystem.resources.saqz_symbol
import br.com.saqz.designsystem.theme.SaqzTheme
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
        LoginBackdrop()
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 420.dp)
                .fillMaxHeight()
                .fillMaxWidth()
                .imePadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 20.dp)
                .padding(bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(DesignRes.drawable.saqz_symbol),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = "saqz",
                style = SaqzTheme.typography.displayLarge.copy(
                    color = colors.primary,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                ),
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
                style = SaqzTheme.typography.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 272.dp),
            )
            Spacer(Modifier.height(24.dp))
            SaqzInput(
                value = TextFieldValue(state.email),
                onValueChange = { onIntent(AuthenticationIntent.UpdateEmail(it.text)) },
                label = stringResource(Res.string.login_email),
                kind = SaqzInputKind.Email,
                enabled = !state.isLoading,
                inlineLabel = true,
                leadingContent = { EmailIcon(colors.primary) },
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
                leadingContent = { LockIcon(colors.primary) },
                modifier = Modifier.testTag(LoginTags.Password),
            )
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                SaqzButton(
                    label = stringResource(Res.string.login_reset),
                    onClick = { onIntent(AuthenticationIntent.ShowPasswordReset) },
                    variant = SaqzButtonVariant.Ghost,
                    enabled = !state.isLoading,
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
            Spacer(Modifier.height(18.dp))
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
            )
        }
    }
}

@Composable
private fun BoxScope.LoginBackdrop() {
    val colors = SaqzTheme.colors
    Image(
        painter = painterResource(DesignRes.drawable.saqz_symbol),
        contentDescription = null,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 72.dp, y = (-44).dp)
            .size(220.dp)
            .alpha(0.035f),
    )
    Canvas(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(94.dp)
            .clearAndSetSemantics {},
    ) {
        val softWave = Path().apply {
            moveTo(0f, size.height * 0.18f)
            cubicTo(
                size.width * 0.25f,
                size.height * 0.78f,
                size.width * 0.72f,
                -size.height * 0.08f,
                size.width,
                size.height * 0.28f,
            )
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(softWave, color = Color(0xFF90B5FF))
        val primaryWave = Path().apply {
            moveTo(0f, size.height * 0.44f)
            cubicTo(
                size.width * 0.30f,
                size.height * 1.02f,
                size.width * 0.72f,
                size.height * 0.24f,
                size.width,
                size.height * 0.56f,
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
    Box(modifier = Modifier.fillMaxWidth()) {
        SaqzButton(
            label = label,
            onClick = onClick,
            enabled = enabled,
            loading = loading,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .testTag(LoginTags.Submit),
        )
        if (!loading) {
            ArrowIcon(
                color = SaqzTheme.colors.onPrimary,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
            )
        }
    }
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
    Box(modifier = Modifier.fillMaxWidth()) {
        SaqzButton(
            label = label,
            onClick = onClick,
            variant = SaqzButtonVariant.Secondary,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag(LoginTags.Google),
        )
        GoogleIcon(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp),
        )
    }
}

@Composable
private fun EmailIcon(color: Color) {
    Canvas(Modifier.size(20.dp).clearAndSetSemantics {}) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx()), style = stroke)
        drawLine(color, Offset(1.dp.toPx(), 3.dp.toPx()), center, stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width - 1.dp.toPx(), 3.dp.toPx()), center, stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun LockIcon(color: Color) {
    Canvas(Modifier.size(20.dp).clearAndSetSemantics {}) {
        val strokeWidth = 1.6.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(3.dp.toPx(), 8.dp.toPx()),
            size = Size(14.dp.toPx(), 11.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(5.dp.toPx(), 1.dp.toPx()),
            size = Size(10.dp.toPx(), 13.dp.toPx()),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun ArrowIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp).clearAndSetSemantics {}) {
        val strokeWidth = 1.8.dp.toPx()
        drawLine(color, Offset(3.dp.toPx(), center.y), Offset(14.dp.toPx(), center.y), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(10.dp.toPx(), 5.dp.toPx()), Offset(14.dp.toPx(), center.y), strokeWidth, StrokeCap.Round)
        drawLine(color, Offset(14.dp.toPx(), center.y), Offset(10.dp.toPx(), 13.dp.toPx()), strokeWidth, StrokeCap.Round)
    }
}

@Composable
private fun GoogleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp).clearAndSetSemantics {}) {
        val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Butt)
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = Offset(stroke.width / 2f, stroke.width / 2f)
        drawArc(Color(0xFFEA4335), -45f, 90f, false, topLeft, arcSize, style = stroke)
        drawArc(Color(0xFFFBBC05), 45f, 90f, false, topLeft, arcSize, style = stroke)
        drawArc(Color(0xFF34A853), 135f, 90f, false, topLeft, arcSize, style = stroke)
        drawArc(Color(0xFF4285F4), 225f, 100f, false, topLeft, arcSize, style = stroke)
        drawLine(Color(0xFF4285F4), center, Offset(size.width - 1.dp.toPx(), center.y), stroke.width, StrokeCap.Square)
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
