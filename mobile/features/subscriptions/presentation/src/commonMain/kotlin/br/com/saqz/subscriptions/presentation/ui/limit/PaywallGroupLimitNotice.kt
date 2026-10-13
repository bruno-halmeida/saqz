package br.com.saqz.subscriptions.presentation.ui.limit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.limit.PaywallGroupLimit
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.paywall_limit_hint
import br.com.saqz.subscriptions.resources.paywall_limit_title
import br.com.saqz.subscriptions.resources.paywall_limit_title_one
import br.com.saqz.subscriptions.resources.paywall_limit_web_cta
import br.com.saqz.subscriptions.resources.paywall_title
import org.jetbrains.compose.resources.stringResource

object PaywallGroupLimitTags {
    const val Notice = "paywall-limit-notice"
    const val Screen = "paywall-limit-screen"
    const val Plans = "paywall-limit-plans"
}

/** O porquê da assinatura ter aberto: "Você chegou ao limite de N grupos do plano X." */
@Composable
fun PaywallGroupLimitNotice(limit: PaywallGroupLimit, modifier: Modifier = Modifier) {
    SaqzCard(tone = SaqzCardTone.Soft, modifier = modifier.testTag(PaywallGroupLimitTags.Notice)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = if (limit.groupsLimit == 1) {
                    stringResource(Res.string.paywall_limit_title_one, limit.planName)
                } else {
                    stringResource(Res.string.paywall_limit_title, limit.groupsLimit, limit.planName)
                },
                style = SaqzTheme.typography.body,
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                text = stringResource(Res.string.paywall_limit_hint),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * No web app não há loja: a mensagem do limite vem antes de abrir o checkout do site
 * (saqz.app/assinar/, em outra aba), com o botão que o abre.
 */
@Composable
fun PaywallGroupLimitWebScreen(
    limit: PaywallGroupLimit,
    onOpenPlans: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(PaywallGroupLimitTags.Screen),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.paywall_title), onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.sectionGap),
        ) {
            PaywallGroupLimitNotice(limit)
            SaqzButton(
                label = stringResource(Res.string.paywall_limit_web_cta),
                onClick = onOpenPlans,
                fullWidth = true,
                modifier = Modifier.testTag(PaywallGroupLimitTags.Plans),
            )
        }
    }
}
