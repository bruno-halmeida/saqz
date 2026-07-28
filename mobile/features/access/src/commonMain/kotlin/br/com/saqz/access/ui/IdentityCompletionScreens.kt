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
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.access.presentation.message
import br.com.saqz.access.presentation.verification.VerificationIntent
import br.com.saqz.access.presentation.verification.VerificationState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.verification_body
import br.com.saqz.access.resources.verification_confirm
import br.com.saqz.access.resources.verification_resend
import br.com.saqz.access.resources.verification_sent
import br.com.saqz.access.resources.verification_title
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/**
 * O que sobrou da tela de verificação de e-mail: órfã desde o VUL-84 — nenhuma rota a
 * alcança e a [VerificationViewModel] que a alimentava ficou inerte. O VUL-91 apaga o
 * conjunto quando entregar a faixa de e-mail não confirmado que o substitui.
 *
 * As telas de nome e telefone que dividiam este arquivo saíram no VUL-84: o export as une
 * na 1c, e o estado que as alimentava virou `SessionAccessState.CompletingIdentity`.
 */
internal object IdentityTags {
    const val Verify = "identity-verify"
    const val Resend = "identity-resend"
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
