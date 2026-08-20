package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Par `imeAction` + `keyboardActions` pronto para passar ao [SaqzInput] de um campo dentro
 * de um [SaqzForm]. Constrói-se só via [SaqzFormScope.imeNext] e [SaqzFormScope.imeDone].
 *
 * Sem anotação de estabilidade de propósito: o Compose já infere este tipo como estável, porque
 * `KeyboardActions` é `@Stable` na foundation e `ImeAction` é `value class` sobre `Int`.
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
 * SaqzForm(onSubmit = { onIntent(Submit) }) {
 *     SaqzInput(..., imeNext())
 *     SaqzInput(..., imeNext())
 *     SaqzInput(..., imeDone())  // último campo: fecha o teclado e envia
 * }
 * ```
 *
 * Fora de um [SaqzForm] — tela que já tem o próprio layout — o mesmo scope vem de
 * [rememberSaqzFormScope].
 *
 * `@Immutable` é o que mantém quem recebe este scope skippable. Receiver conta como parâmetro
 * para o Compose, e a inferência marcaria esta classe como instável por dois motivos que não dá
 * para remover: [FocusManager] é interface sem `@Stable`, e os delegates `by lazy` viram campos
 * `Lazy`, que também é interface. Mesmo motivo pelo qual a foundation anota `ColumnScope` e
 * `RowScope`. A promessa se sustenta: não há propriedade pública, e o que os campos privados
 * guardam nunca muda depois da construção.
 */
@Immutable
class SaqzFormScope internal constructor(
    private val focusManager: FocusManager,
    private val onSubmit: () -> Unit,
) {
    /**
     * `Next` e não `Down`: `Down` é busca geométrica e, numa linha com dois campos lado a lado
     * (validade e CVV do cartão, cada um com `weight(1f)`), pula o vizinho e cai no campo de
     * baixo. `Next` segue a ordem de foco declarada.
     *
     * Instâncias fixas por scope: os campos chamam isto inline na composição, e um formulário de
     * dez campos criaria dez objetos novos por recomposição — cada um trocando de identidade e
     * empurrando `KeyboardOptions`/`KeyboardActions` novos para dentro do `BasicTextField`.
     */
    private val next by lazy {
        SaqzFormIme(
            imeAction = ImeAction.Next,
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
        )
    }

    private val done by lazy {
        SaqzFormIme(
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onSubmit()
                },
            ),
        )
    }

    /**
     * Para todos os campos exceto o último: tecla de ação = "Próximo", avança o foco na ordem
     * declarada dos campos.
     */
    fun imeNext(): SaqzFormIme = next

    /**
     * Para o último campo do formulário: tecla de ação = "Concluído". Fecha o teclado e,
     * quando o [SaqzForm] (ou o [rememberSaqzFormScope]) recebeu [onSubmit], dispara o envio
     * — o mesmo gesto do botão primário.
     */
    fun imeDone(): SaqzFormIme = done
}

/**
 * Scope de IME para tela que **não** usa o container [SaqzForm] — login, cadastro e as
 * demais que já têm o próprio layout. [onSubmit] é o envio do último campo; sem ele o
 * Done só fecha o teclado.
 *
 * [onSubmit] passa por `rememberUpdatedState`: o scope é estável (os [SaqzFormIme] não
 * recriam a cada recomposição) e a lambda lida é sempre a da composição atual.
 */
@Composable
fun rememberSaqzFormScope(onSubmit: (() -> Unit)? = null): SaqzFormScope {
    val focusManager = LocalFocusManager.current
    val currentOnSubmit = rememberUpdatedState(onSubmit)
    return remember(focusManager) {
        SaqzFormScope(focusManager) { currentOnSubmit.value?.invoke() }
    }
}

/**
 * Tira o foco do campo e fecha o IME. Overlay na mesma janela (sheet, picker) não
 * faz isso sozinho — um Dialog nativo faria. Sem delay: quem chama segue no mesmo
 * clique, testes não esperam animação de teclado.
 */
@Composable
fun rememberSaqzDismissKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
}

/**
 * Container de formulário: aplica `imePadding` (teclado não sobrepõe campos) + scroll vertical
 * + espaçamento padrão entre itens. Cada campo dentro de [content] declara seu IME via
 * [SaqzFormScope.imeNext] / [SaqzFormScope.imeDone].
 *
 * [onSubmit] é o que o último campo dispara no Done, além de fechar o teclado. Sem ele o
 * Done só limpa o foco — o comportamento antigo, para o caso em que o envio ainda não existe.
 *
 * Substitui o pattern manual de `Column(verticalScroll(...).imePadding())` que era replicado
 * por tela e que deixava o teclado cobrir os campos de baixo.
 */
@Composable
fun SaqzForm(
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
    horizontalPadding: Dp = SaqzTheme.metrics.horizontalPadding,
    verticalPadding: Dp = SaqzTheme.metrics.grid,
    itemSpacing: Dp = SaqzTheme.metrics.subGrid,
    content: @Composable SaqzFormScope.() -> Unit,
) {
    val scope = rememberSaqzFormScope(onSubmit)
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
