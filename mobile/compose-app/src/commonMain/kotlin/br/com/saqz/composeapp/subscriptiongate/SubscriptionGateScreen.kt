package br.com.saqz.composeapp.subscriptiongate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.composeapp.resources.Res
import br.com.saqz.composeapp.resources.subscription_gate_authorized_message
import br.com.saqz.composeapp.resources.subscription_gate_authorized_title
import br.com.saqz.composeapp.resources.subscription_gate_authorization_failure_message
import br.com.saqz.composeapp.resources.subscription_gate_email
import br.com.saqz.composeapp.resources.subscription_gate_initial_message
import br.com.saqz.composeapp.resources.subscription_gate_not_authorized_message
import br.com.saqz.composeapp.resources.subscription_gate_not_authorized_title
import br.com.saqz.composeapp.resources.subscription_gate_purchase_failure_message
import br.com.saqz.composeapp.resources.subscription_gate_refresh
import br.com.saqz.composeapp.resources.subscription_gate_request
import br.com.saqz.composeapp.resources.subscription_gate_retry
import br.com.saqz.composeapp.resources.subscription_gate_sent_message
import br.com.saqz.composeapp.resources.subscription_gate_sent_title
import br.com.saqz.composeapp.resources.subscription_gate_sending_message
import br.com.saqz.composeapp.resources.subscription_gate_sending_title
import br.com.saqz.composeapp.resources.subscription_gate_title
import br.com.saqz.composeapp.resources.subscription_gate_verifying_message
import br.com.saqz.composeapp.resources.subscription_gate_verifying_title
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

object SubscriptionGateTags {
    const val Root = "subscription-gate-root"
    const val Header = "subscription-gate-header"
    const val Content = "subscription-gate-content"
    const val Title = "subscription-gate-title"
    const val Status = "subscription-gate-status"
    const val Progress = "subscription-gate-progress"
    const val Email = "subscription-gate-email"
    const val Request = "subscription-gate-request"
    const val Refresh = "subscription-gate-refresh"
}

@Composable
fun SubscriptionGateScreen(
    state: SubscriptionGateState,
    onIntent: (SubscriptionGateIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(SubscriptionGateTags.Root),
    ) {
        SaqzTopAppBar(
            title = stringResource(Res.string.subscription_gate_title),
            onBack = onBack,
            modifier = Modifier.testTag(SubscriptionGateTags.Header),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.sectionGap)
                .testTag(SubscriptionGateTags.Content),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SubscriptionGateBody(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun SubscriptionGateBody(
    state: SubscriptionGateState,
    onIntent: (SubscriptionGateIntent) -> Unit,
) {
    val status = state.status
    val copy = statusCopy(state)
    val isBusy = status == SubscriptionGateStatus.Sending || status == SubscriptionGateStatus.Verifying
    val isFinished = status == SubscriptionGateStatus.Authorized
    val requestLabel = when {
        status == SubscriptionGateStatus.Failed && state.failure == SubscriptionGateFailure.PurchaseInformation ->
            stringResource(Res.string.subscription_gate_retry)
        else -> stringResource(Res.string.subscription_gate_request)
    }
    val refreshLabel = if (status == SubscriptionGateStatus.Failed && state.failure == SubscriptionGateFailure.Authorization) {
        stringResource(Res.string.subscription_gate_retry)
    } else {
        stringResource(Res.string.subscription_gate_refresh)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        if (isBusy || isFinished) {
            SaqzSpinner(
                modifier = Modifier.testTag(SubscriptionGateTags.Progress),
                onDark = false,
            )
        }
        Text(
            text = copy.title,
            style = SaqzTheme.typography.title,
            color = SaqzTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(SubscriptionGateTags.Title),
        )
        Text(
            text = copy.message,
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(SubscriptionGateTags.Status)
                .semantics(mergeDescendants = true) {
                    stateDescription = copy.accessibilityLabel
                    liveRegion = LiveRegionMode.Polite
                },
        )
        state.maskedEmail?.let { email ->
            Text(
                text = stringResource(Res.string.subscription_gate_email, email),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag(SubscriptionGateTags.Email),
            )
        }
        if (!isFinished) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = SaqzTheme.metrics.subGrid),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            ) {
                SaqzButton(
                    label = requestLabel,
                    onClick = { onIntent(SubscriptionGateIntent.RequestPurchaseInformation) },
                    modifier = Modifier.testTag(SubscriptionGateTags.Request),
                    fullWidth = true,
                    enabled = !isBusy,
                    loading = status == SubscriptionGateStatus.Sending,
                )
                SaqzButton(
                    label = refreshLabel,
                    onClick = { onIntent(SubscriptionGateIntent.RefreshAuthorization) },
                    modifier = Modifier.testTag(SubscriptionGateTags.Refresh),
                    variant = SaqzButtonVariant.Secondary,
                    fullWidth = true,
                    enabled = !isBusy,
                )
            }
        }
    }
}

private data class SubscriptionGateCopy(
    val title: String,
    val message: String,
    val accessibilityLabel: String,
)

@Composable
private fun statusCopy(state: SubscriptionGateState): SubscriptionGateCopy = when (state.status) {
    SubscriptionGateStatus.Initial,
    SubscriptionGateStatus.NotAuthorized -> SubscriptionGateCopy(
        title = stringResource(
            if (state.status == SubscriptionGateStatus.Initial) {
                Res.string.subscription_gate_title
            } else {
                Res.string.subscription_gate_not_authorized_title
            },
        ),
        message = stringResource(
            if (state.status == SubscriptionGateStatus.Initial) {
                Res.string.subscription_gate_initial_message
            } else {
                Res.string.subscription_gate_not_authorized_message
            },
        ),
        accessibilityLabel = stringResource(Res.string.subscription_gate_not_authorized_title),
    )
    SubscriptionGateStatus.Sending -> SubscriptionGateCopy(
        title = stringResource(Res.string.subscription_gate_sending_title),
        message = stringResource(Res.string.subscription_gate_sending_message),
        accessibilityLabel = stringResource(Res.string.subscription_gate_sending_title),
    )
    SubscriptionGateStatus.Sent -> SubscriptionGateCopy(
        title = stringResource(Res.string.subscription_gate_sent_title),
        message = stringResource(Res.string.subscription_gate_sent_message),
        accessibilityLabel = stringResource(Res.string.subscription_gate_sent_title),
    )
    SubscriptionGateStatus.Verifying -> SubscriptionGateCopy(
        title = stringResource(Res.string.subscription_gate_verifying_title),
        message = stringResource(Res.string.subscription_gate_verifying_message),
        accessibilityLabel = stringResource(Res.string.subscription_gate_verifying_title),
    )
    SubscriptionGateStatus.Authorized -> SubscriptionGateCopy(
        title = stringResource(Res.string.subscription_gate_authorized_title),
        message = stringResource(Res.string.subscription_gate_authorized_message),
        accessibilityLabel = stringResource(Res.string.subscription_gate_authorized_title),
    )
    SubscriptionGateStatus.Failed -> if (state.failure == SubscriptionGateFailure.PurchaseInformation) {
        SubscriptionGateCopy(
            title = stringResource(Res.string.subscription_gate_sending_title),
            message = stringResource(Res.string.subscription_gate_purchase_failure_message),
            accessibilityLabel = stringResource(Res.string.subscription_gate_purchase_failure_message),
        )
    } else {
        SubscriptionGateCopy(
            title = stringResource(Res.string.subscription_gate_verifying_title),
            message = stringResource(Res.string.subscription_gate_authorization_failure_message),
            accessibilityLabel = stringResource(Res.string.subscription_gate_authorization_failure_message),
        )
    }
}

@Preview(name = "Inicial")
@Composable
private fun SubscriptionGateInitialPreview() = SubscriptionGatePreview(SubscriptionGateState())

@Preview(name = "Enviando")
@Composable
private fun SubscriptionGateSendingPreview() = SubscriptionGatePreview(
    SubscriptionGateState(status = SubscriptionGateStatus.Sending),
)

@Preview(name = "Enviado")
@Composable
private fun SubscriptionGateSentPreview() = SubscriptionGatePreview(
    SubscriptionGateState(status = SubscriptionGateStatus.Sent, maskedEmail = "a***a@exemplo.com"),
)

@Preview(name = "Falha no envio")
@Composable
private fun SubscriptionGatePurchaseFailurePreview() = SubscriptionGatePreview(
    SubscriptionGateState(
        status = SubscriptionGateStatus.Failed,
        failure = SubscriptionGateFailure.PurchaseInformation,
    ),
)

@Preview(name = "Falha na verificação")
@Composable
private fun SubscriptionGateAuthorizationFailurePreview() = SubscriptionGatePreview(
    SubscriptionGateState(
        status = SubscriptionGateStatus.Failed,
        failure = SubscriptionGateFailure.Authorization,
    ),
)

@Preview(name = "Verificando")
@Composable
private fun SubscriptionGateVerifyingPreview() = SubscriptionGatePreview(
    SubscriptionGateState(status = SubscriptionGateStatus.Verifying),
)

@Preview(name = "Sem autorização")
@Composable
private fun SubscriptionGateNotAuthorizedPreview() = SubscriptionGatePreview(
    SubscriptionGateState(status = SubscriptionGateStatus.NotAuthorized),
)

@Preview(name = "Autorizado")
@Composable
private fun SubscriptionGateAuthorizedPreview() = SubscriptionGatePreview(
    SubscriptionGateState(status = SubscriptionGateStatus.Authorized),
)

@Composable
private fun SubscriptionGatePreview(state: SubscriptionGateState) = SaqzTheme {
    SubscriptionGateScreen(state = state, onIntent = {}, onBack = {})
}
