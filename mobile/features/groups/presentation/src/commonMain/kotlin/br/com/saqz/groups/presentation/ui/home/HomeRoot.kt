package br.com.saqz.groups.presentation.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.home.HomeEffect
import br.com.saqz.groups.presentation.home.HomeViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeRoot(
    onOpenGroup: (String) -> Unit = {},
    onOpenGroups: () -> Unit = {},
    onOpenGame: (String, String) -> Unit = { _, _ -> },
    onOpenMembers: (String) -> Unit = {},
    onOpenCashbox: (String) -> Unit = {},
    onOpenGameSettlement: (String, String) -> Unit = { _, _ -> },
    onOpenGameEditor: (String) -> Unit = {},
    onOpenInvite: (String) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            HomeEffect.OpenGroups -> onOpenGroups()
            is HomeEffect.OpenGroup -> onOpenGroup(effect.groupId)
            is HomeEffect.OpenGame -> onOpenGame(effect.groupId, effect.gameId)
            is HomeEffect.OpenMembers -> onOpenMembers(effect.groupId)
            is HomeEffect.OpenCashbox -> onOpenCashbox(effect.groupId)
            is HomeEffect.OpenGameSettlement -> onOpenGameSettlement(effect.groupId, effect.gameId)
            is HomeEffect.OpenGameEditor -> onOpenGameEditor(effect.groupId)
            is HomeEffect.OpenInvite -> onOpenInvite(effect.groupId)
            // VUL-202: mesma ligação do 2f e do caixa — copiar é área de transferência,
            // não navegação, e morre aqui.
            is HomeEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
        }
    }
    HomeScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * O aviso permanente de cobrança em aberto (VUL-202), montado pelo shell acima do conteúdo
 * e visível em qualquer aba.
 *
 * É um Root de verdade — resolve a **mesma** [HomeViewModel] da aba Início, porque as duas
 * composições vivem sob o `ViewModelStoreOwner` do destino do shell. Uma instância, uma
 * chamada a `GET /api/me/home`: o aviso não tem carregador próprio e não duplica requisição.
 * Ele nasce antes da Home quando a pessoa entra por outra aba — e é exatamente o ponto,
 * porque a faixa precisa existir em todas elas.
 *
 * Não desenha nada quando não há pendência: `ownCharges` nulo é a ausência de dívida, e é
 * assim que a faixa some sozinha quando o admin baixa a cobrança.
 */
@Composable
fun HomeOwnChargesBannerRoot(
    onOpenHome: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    state.ownCharges?.let { HomeOwnChargesBanner(charges = it, onClick = onOpenHome) }
}
