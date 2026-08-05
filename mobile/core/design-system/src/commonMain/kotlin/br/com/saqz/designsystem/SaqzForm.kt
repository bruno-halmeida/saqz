package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Par `imeAction` + `keyboardActions` pronto para passar ao [SaqzInput] de um campo dentro
 * de um [SaqzForm]. Constrói-se só via [SaqzFormScope.imeNext] e [SaqzFormScope.imeDone].
 */
class SaqzFormIme internal constructor(
    val imeAction: ImeAction,
    val keyboardActions: KeyboardActions,
)

/**
 * Receiver do bloco `content` de [SaqzForm]. Provê [imeNext] e [imeDone] para que cada campo
 * declare explicitamente seu papel no fluxo de teclado — sem auto-detecção frágil de
 * "último campo" (campos condicionais, `Row` com dois campos, botões no meio tornariam isso
 * instável).
 *
 * Uso:
 * ```
 * SaqzForm {
 *     SaqzInput(..., imeNext())
 *     SaqzInput(..., imeNext())
 *     SaqzInput(..., imeDone())  // último campo: fecha o teclado
 * }
 * ```
 */
class SaqzFormScope internal constructor() {
    internal var focusManager: FocusManager? = null

    /**
     * Para todos os campos exceto o último: tecla de ação = "Próximo", move o foco para baixo.
     */
    fun imeNext(): SaqzFormIme = SaqzFormIme(
        imeAction = ImeAction.Next,
        keyboardActions = KeyboardActions(onNext = { focusManager?.moveFocus(FocusDirection.Down) }),
    )

    /**
     * Para o último campo do formulário: tecla de ação = "Concluído", limpa o foco e fecha o
     * teclado.
     */
    fun imeDone(): SaqzFormIme = SaqzFormIme(
        imeAction = ImeAction.Done,
        keyboardActions = KeyboardActions(onDone = { focusManager?.clearFocus() }),
    )
}

/**
 * Container de formulário: aplica `imePadding` (teclado não sobrepõe campos) + scroll vertical
 * + espaçamento padrão entre itens. Cada campo dentro de [content] declara seu IME via
 * [SaqzFormScope.imeNext] / [SaqzFormScope.imeDone].
 *
 * Substitui o pattern manual de `Column(verticalScroll(...).imePadding())` que era replicado
 * por tela e que deixava o teclado cobrir os campos de baixo.
 */
@Composable
fun SaqzForm(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = SaqzTheme.metrics.horizontalPadding,
    verticalPadding: Dp = SaqzTheme.metrics.grid,
    itemSpacing: Dp = SaqzTheme.metrics.subGrid,
    content: @Composable SaqzFormScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scope = remember { SaqzFormScope() }
    scope.focusManager = focusManager
    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        scope.content()
    }
}