package br.com.saqz.composeapp.access

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.appupdate.AppUpdateCheck
import br.com.saqz.access.ui.AppUpdateSheet
import br.com.saqz.access.ui.AppUpdateStore
import org.koin.compose.koinInject

/**
 * O pop-up de atualização (DEB-117) por cima de tudo, inclusive splash e login. Checa ao abrir e a cada
 * volta ao primeiro plano, nunca no meio do uso. Último filho do Box do `SaqzApp`.
 */
@Composable
internal fun AppUpdateHost(
    check: AppUpdateCheck = koinInject(),
    store: AppUpdateStore = koinInject(),
) {
    LifecycleResumeEffect(check) {
        check.check()
        onPauseOrDispose { }
    }
    val prompt by check.prompt.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    AppUpdateSheet(
        prompt = prompt,
        store = store,
        onOpenStore = {
            // Sem o app da loja (aparelho sem Play Store), o `openUri` lança: cai na página web.
            runCatching { uriHandler.openUri(store.appUri) }
                .onFailure { runCatching { uriHandler.openUri(store.webUri) } }
        },
        onDismiss = check::dismissRecommended,
    )
}
