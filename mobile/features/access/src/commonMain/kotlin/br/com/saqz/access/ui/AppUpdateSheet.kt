package br.com.saqz.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.access.domain.appupdate.AppUpdatePrompt
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.app_update_not_now
import br.com.saqz.access.resources.app_update_recommended_body
import br.com.saqz.access.resources.app_update_recommended_title
import br.com.saqz.access.resources.app_update_required_body
import br.com.saqz.access.resources.app_update_required_title
import br.com.saqz.access.resources.app_update_store_app_store
import br.com.saqz.access.resources.app_update_store_google_play
import br.com.saqz.access.resources.app_update_update
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/**
 * A loja do aparelho, com a página do Saqz. São os IDs de produção também no build dev (os mesmos de
 * `links-page/index.html`). [appUri] abre o app da loja; [webUri] é a saída quando ele não existe.
 */
enum class AppUpdateStore(val appUri: String, val webUri: String) {
    GooglePlay("market://details?id=app.saqz", "https://play.google.com/store/apps/details?id=app.saqz"),
    AppStore("itms-apps://apps.apple.com/app/id6812743525", "https://apps.apple.com/br/app/id6812743525"),
}

object AppUpdateTags {
    const val Sheet = "app-update"
    const val Store = "app-update-store"
    const val NotNow = "app-update-not-now"
    const val Update = "app-update-update"
}

/**
 * O pop-up de atualização (FLG-007). Obrigatória: não fecha, um botão da loja que não fecha a folha (quem
 * volta da loja sem atualizar continua bloqueado). Recomendada: fecha por X, toque fora, voltar, "Agora
 * não" ou "Atualizar", e todos contam como dispensada ([onDismiss]). Último filho do Box que ocupa a tela.
 */
@Composable
fun AppUpdateSheet(
    prompt: AppUpdatePrompt,
    store: AppUpdateStore,
    onOpenStore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (prompt) {
        AppUpdatePrompt.None -> Unit
        AppUpdatePrompt.Required -> SaqzBottomSheet(
            open = true,
            onClose = {},
            modifier = modifier.testTag(AppUpdateTags.Sheet),
            title = stringResource(Res.string.app_update_required_title),
            description = stringResource(Res.string.app_update_required_body),
            dismissible = false,
            footer = {
                SaqzButton(
                    stringResource(
                        when (store) {
                            AppUpdateStore.AppStore -> Res.string.app_update_store_app_store
                            AppUpdateStore.GooglePlay -> Res.string.app_update_store_google_play
                        },
                    ),
                    onOpenStore,
                    Modifier.fillMaxWidth().testTag(AppUpdateTags.Store),
                )
            },
        )
        is AppUpdatePrompt.Recommended -> SaqzBottomSheet(
            open = true,
            onClose = onDismiss,
            modifier = modifier.testTag(AppUpdateTags.Sheet),
            title = stringResource(Res.string.app_update_recommended_title),
            description = stringResource(Res.string.app_update_recommended_body),
            splitFooter = {
                SaqzButton(
                    stringResource(Res.string.app_update_not_now),
                    onDismiss,
                    Modifier.weight(1f).testTag(AppUpdateTags.NotNow),
                    SaqzButtonVariant.Secondary,
                )
                SaqzButton(
                    stringResource(Res.string.app_update_update),
                    {
                        onOpenStore()
                        onDismiss()
                    },
                    Modifier.weight(1f).testTag(AppUpdateTags.Update),
                )
            },
        )
    }
}

@Preview
@Composable
private fun AppUpdateSheetRequiredPreview() = SaqzTheme {
    Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        AppUpdateSheet(AppUpdatePrompt.Required, AppUpdateStore.GooglePlay, onOpenStore = {}, onDismiss = {})
    }
}

@Preview
@Composable
private fun AppUpdateSheetRecommendedPreview() = SaqzTheme {
    Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        AppUpdateSheet(AppUpdatePrompt.Recommended("1.0.16"), AppUpdateStore.AppStore, onOpenStore = {}, onDismiss = {})
    }
}
