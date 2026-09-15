package br.com.saqz.groups.presentation.whatsappbinding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.whatsapp_binding_saved
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Liga a ViewModel à tela. O efeito [WhatsAppBindingEffect.Saved] é a confirmação
 * transitória do gestor; o nome do grupo confirmado vive no estado e a tela o desenha.
 */
@Composable
fun WhatsAppBindingRoot(
    groupId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WhatsAppBindingViewModel = koinViewModel(
        key = "whatsapp-binding/$groupId",
        parameters = { parametersOf(groupId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var toastMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val savedMessage = stringResource(Res.string.whatsapp_binding_saved)
    ObserveAsEvents(viewModel.effects) { toastMessage = savedMessage }
    Box(modifier.fillMaxSize()) {
        WhatsAppBindingScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
        SaqzToast(
            visible = toastMessage != null,
            onDismiss = { toastMessage = null },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(SaqzTheme.metrics.horizontalPadding),
        ) {
            SaqzToastText(toastMessage.orEmpty())
        }
    }
}
