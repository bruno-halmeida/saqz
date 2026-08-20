package br.com.saqz.subscriptions.presentation.ui.changeplan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ChangePlanRoot(
    onBack: () -> Unit,
    viewModel: ChangePlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    ChangePlanScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        onCopyPix = { clipboard.setText(AnnotatedString(it)) },
        onOpenInvoice = { runCatching { uriHandler.openUri(it) } },
    )
}
