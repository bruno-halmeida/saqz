package br.com.saqz.groups.presentation.monthlypayments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.details.GroupOwnChargesSection
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.monthly_payments_title
import br.com.saqz.groups.resources.monthly_payments_empty
import br.com.saqz.groups.resources.monthly_payments_open_group
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OwnMonthlyPaymentsRoot(onBack: () -> Unit, onOpenGroup: (String) -> Unit) {
    val vm: OwnMonthlyPaymentsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    ObserveAsEvents(vm.effects) { effect ->
        when (effect) {
            is OwnMonthlyPaymentsEffect.OpenGroup -> onOpenGroup(effect.groupId)
        }
    }
    OwnMonthlyPaymentsScreen(state, onBack, vm::onIntent)
}

@Composable
internal fun OwnMonthlyPaymentsScreen(
    state: OwnMonthlyPaymentsState,
    onBack: () -> Unit,
    onIntent: (OwnMonthlyPaymentsIntent) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag("own-monthly-payments")) {
        SaqzTopAppBar(title = stringResource(Res.string.monthly_payments_title), onBack = onBack)
        when {
            state.loading -> SaqzSpinner()
            state.error != null -> GroupLoadFailure(state.error, { onIntent(OwnMonthlyPaymentsIntent.Retry) })
            else -> LazyColumn(
                modifier = Modifier.padding(horizontal = SaqzTheme.metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            ) {
                if (state.groups.isEmpty()) item {
                    Text(stringResource(Res.string.monthly_payments_empty), modifier = Modifier.testTag("monthly-payments-empty"))
                }
                items(state.groups, key = { it.id }) { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
                        Text(group.name, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                        if (!group.charges.failed && group.charges.pending.isEmpty() && group.charges.history.isEmpty()) {
                            Text(stringResource(Res.string.monthly_payments_empty))
                        } else {
                            GroupOwnChargesSection(group.charges, onIntent = { intent ->
                                if (intent == GroupDetailsIntent.RetryOwnCharges) onIntent(OwnMonthlyPaymentsIntent.Retry)
                            })
                        }
                        SaqzButton(
                            label = stringResource(Res.string.monthly_payments_open_group),
                            onClick = { onIntent(OwnMonthlyPaymentsIntent.OpenGroup(group.id)) },
                            modifier = Modifier.testTag("monthly-payments-group-${group.id}"),
                        )
                    }
                }
            }
        }
    }
}
