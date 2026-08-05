package br.com.saqz.groups.presentation.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.home.HomeEffect
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeOwnChargesUi
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
 * É um Root — resolve a **mesma** [HomeViewModel] da aba Início, então são uma instância e
 * uma chamada a `GET /api/me/home`: o aviso não tem carregador próprio e não duplica
 * requisição. O que faz as duas composições caírem na mesma instância é o
 * `LocalViewModelStoreOwner`, e desde o VUL-204 ele é o **destino do shell** — a faixa e a
 * aba Início são compostas dentro do mesmo `entry<SaqzShellDestination>`, logo no mesmo
 * `ViewModelStore`. Antes o owner era a Activity: o compartilhamento acontecia igual, mas a
 * instância **não** morria quando o shell saía do stack, e um logout sem recriar a Activity
 * entregava a Home da conta anterior para quem entrasse depois. Agora o store morre com a
 * entrada, e a sessão nova começa com ViewModel nova.
 *
 * O recarregamento na volta é o que torna o aviso **permanente e vivo**: sem ele a faixa
 * só se atualizaria no `init` da instância, e continuaria cobrando depois de o admin dar
 * baixa — o mesmo motivo do `LifecycleResumeEffect` da faixa de e-mail (VUL-91). É o
 * `softRefresh`: sem esqueleto e sem tela de erro, porque isto acontece por baixo.
 *
 * [emailBanner] é a outra faixa (VUL-91), e o desempate entre as duas mora aqui porque
 * depende de `overdue`, que só existe neste estado. Regra: **vencida ganha de tudo**; no
 * prazo, o e-mail passa na frente. Justificativa no `SaqzNavHost`, onde a faixa de e-mail é
 * construída.
 *
 * Sem pendência não desenha nada seu: `ownCharges` nulo é a ausência de dívida, e é assim
 * que a faixa some sozinha quando o admin baixa a cobrança.
 */
@Composable
fun HomeOwnChargesBannerRoot(
    onOpenHome: () -> Unit,
    emailBanner: (@Composable () -> Unit)? = null,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(HomeIntent.Refresh)
        onPauseOrDispose {}
    }
    val charges = state.ownCharges
    if (ownChargesBannerWins(charges, hasEmailBanner = emailBanner != null)) {
        HomeOwnChargesBanner(charges = checkNotNull(charges), onClick = onOpenHome)
    } else {
        emailBanner?.invoke()
    }
}

/**
 * O desempate entre as duas faixas do shell, fora do `@Composable` para ser testável sem
 * montar o Root inteiro: vencida ganha do e-mail, no prazo perde, e sem faixa de e-mail
 * (`hasEmailBanner == false`, e-mail já confirmado) não há disputa.
 */
internal fun ownChargesBannerWins(charges: HomeOwnChargesUi?, hasEmailBanner: Boolean): Boolean =
    charges != null && (charges.overdue || !hasEmailBanner)
