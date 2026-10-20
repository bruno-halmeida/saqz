package br.com.saqz.profile.presentation.own.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import br.com.saqz.domain.AppVersion
import br.com.saqz.profile.presentation.own.OwnProfileEffect
import br.com.saqz.profile.presentation.own.OwnProfileIntent
import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.mp.KoinPlatformTools

internal const val PrivacyUrl = "https://saqz.app/privacidade/"
internal const val TermsUrl = "https://saqz.app/termos/"

/** Contato publicado no app: abre o e-mail com o assunto preenchido. */
internal const val HelpMailto = "mailto:contato@egysis.com?subject=Saqz%20-%20Ajuda"

@Composable
fun OwnProfileRoot(
    onOpenEditor: () -> Unit,
    onOpenPasswordRecovery: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAccountDeletion: () -> Unit = {},
    onOpenMyPlan: () -> Unit = {},
    onOpenAthleteProfile: (String) -> Unit = {},
    onOpenMonthlyPayments: () -> Unit = {},
    onOpenReceipts: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    isPlanOwner: Boolean = false,
    refreshVersion: Int = 0,
    topBarWindowInsets: WindowInsets = WindowInsets.statusBars,
    viewModel: OwnProfileViewModel = koinViewModel(),
    imageLoader: ImageLoader = koinInject(),
    // Sem Koin (testes e prévias) não há versão: o rodapé simplesmente não aparece.
    appVersion: AppVersion? = remember { KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<AppVersion>() },
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Toda vez que a aba aparece (e quando o contador do host muda, VUL-205: edição salva), recarrega
    // por baixo. A ViewModel guarda o pedido que chega com uma carga em voo e recarrega quando ela termina.
    RefreshWhenVisible(refreshVersion) { viewModel.onIntent(OwnProfileIntent.Appeared) }
    ObserveAsEvents(viewModel.effects, key = onOpenReceipts != null) { effect ->
        when (effect) {
            OwnProfileEffect.OpenEditor -> onOpenEditor()
            OwnProfileEffect.OpenPasswordRecovery -> onOpenPasswordRecovery()
            OwnProfileEffect.OpenMyPlan -> onOpenMyPlan()
            is OwnProfileEffect.OpenAthleteProfile -> onOpenAthleteProfile(effect.groupId)
            OwnProfileEffect.OpenReceipts -> onOpenReceipts?.invoke()
            OwnProfileEffect.OpenMonthlyPayments -> onOpenMonthlyPayments()
            OwnProfileEffect.OpenPrivacy -> uriHandler.openUri(PrivacyUrl)
            OwnProfileEffect.OpenTerms -> uriHandler.openUri(TermsUrl)
            OwnProfileEffect.OpenHelp -> uriHandler.openUri(HelpMailto)
            OwnProfileEffect.OpenSettings -> onOpenSettings()
            OwnProfileEffect.OpenNotifications -> onOpenNotifications()
            OwnProfileEffect.OpenAccountDeletion -> onOpenAccountDeletion()
            OwnProfileEffect.SignedOut -> onSignOut()
        }
    }
    OwnProfileScreen(
        state = state,
        onIntent = viewModel::onIntent,
        imageLoader = imageLoader,
        topBarWindowInsets = topBarWindowInsets,
        isPlanOwner = isPlanOwner,
        receiptsVisible = onOpenReceipts != null,
        appVersion = appVersion?.label,
    )
}
