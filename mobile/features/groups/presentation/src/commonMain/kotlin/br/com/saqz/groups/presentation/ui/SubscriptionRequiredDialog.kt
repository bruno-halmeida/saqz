package br.com.saqz.groups.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.subscription_required_body
import br.com.saqz.groups.resources.subscription_required_not_now
import br.com.saqz.groups.resources.subscription_required_title_inactive
import br.com.saqz.groups.resources.subscription_required_title_trial_ended
import br.com.saqz.groups.resources.subscription_required_view_plans
import org.jetbrains.compose.resources.stringResource

/**
 * Abre a tela de assinatura (a mesma de Perfil → Meu plano → "Continuar com o Organizador"). Quem
 * hospeda a navegação a provê; a apresentação de grupos só a invoca, sem conhecer `subscriptions`.
 */
val LocalOpenSubscriptionPlans = staticCompositionLocalOf<() -> Unit> { {} }

object SubscriptionRequiredTags {
    const val Dialog = "subscription-required-dialog"
    const val ViewPlans = "subscription-required-view-plans"
    const val NotNow = "subscription-required-not-now"
}

/**
 * O backend respondeu 403 `SUBSCRIPTION_REQUIRED` a uma ação de gestão. Tentar de novo nunca
 * resolve, então o organizador recebe a oferta da assinatura; "Agora não" fecha e mantém o
 * formulário preenchido. Colocar como último filho de um Box que ocupa a tela.
 */
@Composable
fun SubscriptionRequiredDialog(
    trialEnded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openPlans = LocalOpenSubscriptionPlans.current
    SaqzBottomSheet(
        open = true,
        onClose = onDismiss,
        modifier = modifier.testTag(SubscriptionRequiredTags.Dialog),
        title = stringResource(
            if (trialEnded) {
                Res.string.subscription_required_title_trial_ended
            } else {
                Res.string.subscription_required_title_inactive
            },
        ),
        description = stringResource(Res.string.subscription_required_body),
        splitFooter = {
            SaqzButton(
                stringResource(Res.string.subscription_required_not_now),
                onDismiss,
                Modifier.weight(1f).testTag(SubscriptionRequiredTags.NotNow),
                SaqzButtonVariant.Secondary,
            )
            SaqzButton(
                stringResource(Res.string.subscription_required_view_plans),
                {
                    onDismiss()
                    openPlans()
                },
                Modifier.weight(1f).testTag(SubscriptionRequiredTags.ViewPlans),
            )
        },
    )
}
