package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboardManager
import br.com.saqz.groups.domain.map.GroupMapPort
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.details.GroupDetailsEffect
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.port.NativeInviteSharePort
import br.com.saqz.groups.port.InviteNativeOperationResult
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * A tela não navega: o efeito sobe para quem registrou o destino no `NavDisplay`
 * (AGENTS.md §6). [onEffect] é o callback único; quem o liga é o ticket de navegação.
 */
@Composable
fun GroupDetailsRoot(
    groupId: String,
    onBack: () -> Unit,
    onEffect: (GroupDetailsEffect) -> Unit,
    // VUL-204: o `groupId` entra na chave do store. Quem separa o grupo A do B é o escopo
    // por destino do `NavDisplay` (cada `GroupsRoute.Details` é uma entrada com store
    // próprio); a chave é a segunda tranca, e é ela que mantém o Root correto sozinho —
    // montado fora de um `NavEntry`, sem ela A e B cairiam na mesma instância.
    //
    // VUL-205: o prefixo é obrigatório, e vale para toda `key` do app. No
    // `koin-core-viewmodel` a `key` **substitui** o nome da classe em vez de compor com ele
    // (`getViewModelKey`: `key != null -> key`), e `ViewModelStore.put` limpa quem estava
    // naquela chave. Sem "details/", `"grupo-a"` nomearia oito ViewModels diferentes, e no
    // dia em que duas delas dividirem uma entrada — uma tela de grupo com abas, que é o que
    // o shell já faz com quatro Roots — elas se limpariam sem erro nenhum.
    viewModel: GroupDetailsViewModel = koinViewModel(
        key = "details/$groupId",
        parameters = { parametersOf(groupId) },
    ),
    refreshVersion: Int = 0,
    photoFailed: Boolean = false,
    mapPort: GroupMapPort = koinInject(),
    sharePort: NativeInviteSharePort = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    // VUL-205: só recarrega se o contador mudou desde que esta ViewModel nasceu. O contador é
    // do host e sobrevive ao pop da entrada (`SaqzNavHost`), então `> 0` fazia a entrada
    // reempilhada somar o `Retry` ao `init { load() }` da ViewModel nova — duas cargas no
    // mesmo quadro, e `groupDetailsRefreshVersion` é global, não por grupo.
    val loadedVersion = rememberSaveable(viewModel) { refreshVersion }
    LaunchedEffect(viewModel, refreshVersion) {
        if (refreshVersion != loadedVersion) viewModel.onIntent(GroupDetailsIntent.Retry)
    }
    // Copiar o Pix (VUL-203) é área de transferência, não destino: morre aqui em vez de
    // subir para o `NavDisplay` como um efeito de navegação que ninguém navega.
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupDetailsEffect.ShareSaqz -> sharePort.shareText(effect.message) { result ->
                if (result is InviteNativeOperationResult.Failure) {
                    viewModel.onIntent(GroupDetailsIntent.AthleteShareFailed)
                }
            }
            is GroupDetailsEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
            is GroupDetailsEffect.OpenMap -> {
                mapPort.open(venueMapUrl(effect.address)) { opened ->
                    if (!opened) viewModel.onIntent(GroupDetailsIntent.MapOpenFailed)
                }
            }
            else -> onEffect(effect)
        }
    }
    GroupDetailsScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        photoFailed = photoFailed,
    )
}

internal fun venueMapUrl(address: String): String {
    val query = buildString {
        address.encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and 255
            if (value.toChar() in 'a'..'z' || value.toChar() in 'A'..'Z' ||
                value.toChar() in '0'..'9' || value.toChar() in "-._~"
            ) {
                append(value.toChar())
            } else {
                append('%')
                append("0123456789ABCDEF"[value ushr 4])
                append("0123456789ABCDEF"[value and 15])
            }
        }
    }
    return "https://www.google.com/maps/search/?api=1&query=$query"
}
