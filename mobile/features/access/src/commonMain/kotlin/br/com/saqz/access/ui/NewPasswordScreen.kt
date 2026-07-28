package br.com.saqz.access.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.access.presentation.newpassword.NewPasswordIntent
import br.com.saqz.access.presentation.newpassword.NewPasswordState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_password_hint
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.new_password_confirm_field
import br.com.saqz.access.resources.new_password_field
import br.com.saqz.access.resources.new_password_headline
import br.com.saqz.access.resources.new_password_headline_emphasis
import br.com.saqz.access.resources.new_password_submit
import br.com.saqz.access.resources.new_password_supporting_text
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object NewPasswordTags {
    const val Back = "new-password-back"
    const val Password = "new-password-field"
    const val Confirmation = "new-password-confirmation"
    const val Submit = "new-password-submit"
}

/**
 * O 1g: o par de campos de senha entre o cabeçalho do fluxo e o botão de salvar.
 *
 * **O olho existe no primeiro campo e não no segundo, de propósito.** É o export quem
 * decide: confirmar é digitar de novo, não conferir o que já se leu. Acrescentar o olho
 * ao "Confirmar nova senha" por simetria é regressão de desenho.
 *
 * O "Mínimo de 8 caracteres." entra pelo `helperText` do segundo campo, e não como um
 * `Text` solto: é o mesmo slot que a recusa ocupa quando as senhas não conferem, então a
 * troca de dica por erro acontece sem a coluna pular de altura.
 */
@Composable
fun NewPasswordScreen(
    state: NewPasswordState,
    onIntent: (NewPasswordIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessScaffold(modifier = modifier) {
        SaqzIconButton(
            onClick = onBack,
            contentDescription = stringResource(Res.string.action_back),
            outlined = true,
            modifier = Modifier.align(Alignment.Start).testTag(NewPasswordTags.Back),
        ) {
            SaqzIcon(SaqzIcons.ChevronLeft, tint = SaqzTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(BACK_TO_BRAND))
        AccessBrandMark()
        Spacer(Modifier.height(BRAND_TO_HEADER))
        AccessHeader(
            title = stringResource(Res.string.new_password_headline),
            emphasis = stringResource(Res.string.new_password_headline_emphasis),
            subtitle = stringResource(Res.string.new_password_supporting_text),
        )
        state.alert?.let { alert ->
            Spacer(Modifier.height(HEADER_TO_FIELDS))
            SaqzInlineAlert(text = alert.asString(), tone = SaqzInlineAlertTone.Error)
        }
        Spacer(Modifier.height(HEADER_TO_FIELDS))
        SaqzInput(
            value = state.password,
            onValueChange = { onIntent(NewPasswordIntent.UpdatePassword(it)) },
            label = stringResource(Res.string.new_password_field),
            kind = SaqzInputKind.Password,
            enabled = !state.isSaving,
            inlineLabel = true,
            errorText = state.passwordError?.asString(),
            leadingContent = { SaqzIcon(SaqzIcons.Lock, tint = SaqzTheme.colors.primary) },
            modifier = Modifier.testTag(NewPasswordTags.Password),
        )
        Spacer(Modifier.height(NEW_PASSWORD_FIELD_GAP))
        SaqzInput(
            value = state.confirmation,
            onValueChange = { onIntent(NewPasswordIntent.UpdateConfirmation(it)) },
            label = stringResource(Res.string.new_password_confirm_field),
            kind = SaqzInputKind.Password,
            enabled = !state.isSaving,
            inlineLabel = true,
            revealable = false,
            helperText = stringResource(Res.string.access_password_hint),
            errorText = state.confirmationError?.asString(),
            leadingContent = { SaqzIcon(SaqzIcons.Lock, tint = SaqzTheme.colors.primary) },
            modifier = Modifier.testTag(NewPasswordTags.Confirmation),
        )
        Spacer(Modifier.height(NEW_PASSWORD_FIELD_GAP))
        // Sem seta: o 1g é o fim do formulário, não uma passagem. A seta é do 1a e do 1h.
        SaqzButton(
            label = stringResource(Res.string.new_password_submit),
            onClick = { onIntent(NewPasswordIntent.Submit) },
            fullWidth = true,
            enabled = !state.isSaving,
            loading = state.isSaving,
            modifier = Modifier.testTag(NewPasswordTags.Submit),
        )
    }
}

// SPEC_DEVIATION: `dp` cru em `features/*/src/commonMain` (mobile/AGENTS.md §5).
// Reason: são os afastamentos desta tela do export, medidos sobre o frame de 390×844, e
// não tokens do fluxo 10 — o AD-031 mantém no design system só o que o fluxo 10 lista.
// Ficam neste arquivo pelo mesmo motivo que os do `SaqzInlineAlert` ficam no dele: subir
// para o `AccessMetrics` compartilhado o que ainda não se repetiu em duas telas é a
// generalização que o reset apagou. `NEW_PASSWORD_FIELD_GAP` é o único com número no contrato
// (`fluxo1.gapDosCampos.ampliado`), e o `NewPasswordScreenTest` o amarra lá.
//
// O `SaqzInput` reserva 3dp de halo de foco em volta da moldura, então o vão *visto*
// entre dois campos sai 6 maior que o `Spacer`. O export mede a moldura; o número aqui é
// o do export, como o VUL-79 já fez na cena do 1a.
private val BACK_TO_BRAND = 26.dp
private val BRAND_TO_HEADER = 18.dp
private val HEADER_TO_FIELDS = 20.dp
internal val NEW_PASSWORD_FIELD_GAP = 14.dp

@Preview(name = "1g — nova senha", widthDp = 390, heightDp = 844)
@Composable
private fun NewPasswordScreenPreview() = SaqzTheme {
    NewPasswordScreen(state = NewPasswordState(), onIntent = {}, onBack = {})
}
