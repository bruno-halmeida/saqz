package br.com.saqz.access.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionIntent
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.identity_add_photo
import br.com.saqz.access.resources.identity_headline
import br.com.saqz.access.resources.identity_headline_emphasis
import br.com.saqz.access.resources.identity_phone_privacy
import br.com.saqz.access.resources.identity_photo_failed
import br.com.saqz.access.resources.identity_submit
import br.com.saqz.access.resources.identity_supporting_text
import br.com.saqz.access.resources.register_error_name
import br.com.saqz.access.resources.register_error_phone
import br.com.saqz.access.resources.register_name_placeholder
import br.com.saqz.access.resources.register_phone_placeholder
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.PhoneVisualTransformation
import br.com.saqz.designsystem.rememberSaqzFormScope
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object Identity1cTags {
    const val Back = "identity-back"
    const val Photo = "identity-photo"
    const val Name = "identity-name"
    const val Phone = "identity-phone"
    const val Submit = "identity-submit"
    const val PhotoAlert = "identity-photo-alert"
    const val Error = "identity-error"
}

// SPEC_DEVIATION: `dp` e `sp` crus em `features/*/src/commonMain`, que a seção 5 do
// mobile/AGENTS.md proíbe por convenção.
// Reason: são as medidas do seletor de foto da 1c, que só esta tela desenha. Ficam no
// arquivo pelo mesmo motivo que as do `SaqzCodeInput` (VUL-77) e as do `SaqzInlineAlert`
// (VUL-78) ficaram nos deles: o `AccessMetrics` é compartilhado pelos sete tickets da
// onda e cada ticket que escreve lá conflita com os outros seis. Sobem para lá quando o
// segundo consumidor aparecer — e o `ui-contract.json` não versiona este bloco, porque o
// export descreve o seletor em prosa e não em tabela.
private val PHOTO_SIZE = 92.dp
private val PHOTO_ICON = 34.dp
private val PHOTO_BADGE = 32.dp
private val PHOTO_BADGE_ICON = 17.dp

// `right:-2px;bottom:-2px` do export. O sinal inverte na tradução: no CSS o negativo
// empurra para fora da caixa, e aqui quem empurra para fora do canto inferior direito é o
// positivo.
private val PHOTO_BADGE_OFFSET = 2.dp
private val PHOTO_BADGE_SHADOW = 3.dp
private val PHOTO_LABEL_GAP = 10.dp
private val PHOTO_LABEL_SIZE = 14.sp
private const val PHOTO_LABEL_WEIGHT = 600

private val BACK_GAP = 24.dp
private val HEADER_GAP = 24.dp
private val FIELDS_GAP = 24.dp

// `gapDosCampos.padrao` do contrato.
private val FIELD_GAP = 12.dp

/**
 * 1c — completar cadastro: foto, nome e telefone numa tela só, como o export desenha.
 *
 * A tela **não** sabe em que momento está. O `SessionAccessState.CompletingIdentity` chega
 * sem sessão (antes do bootstrap, de quem entrou por um provedor que não deu nome) ou com
 * ela (depois, de quem ainda deve o telefone), e os dois pedem exatamente estes campos —
 * quem escolhe o que fazer com eles é a máquina de sessão.
 *
 * A foto é opcional em toda parte: "Concluir cadastro" não a espera, e o aviso de envio
 * recusado ([IdentityCompletionState.photoFailed]) não desabilita nada.
 */
@Composable
fun IdentityCompletionScreen(
    state: IdentityCompletionState,
    onIntent: (IdentityCompletionIntent) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) = AccessScaffold(modifier) {
    SaqzIconButton(
        onClick = { onIntent(IdentityCompletionIntent.Back) },
        contentDescription = stringResource(Res.string.action_back),
        outlined = true,
        modifier = Modifier.align(Alignment.Start).testTag(Identity1cTags.Back),
    ) {
        SaqzIcon(SaqzIcons.ChevronLeft)
    }
    Spacer(Modifier.height(BACK_GAP))
    PhotoPicker(photo = state.photo, onPick = onPickPhoto)
    Spacer(Modifier.height(HEADER_GAP))
    AccessHeader(
        title = stringResource(Res.string.identity_headline),
        emphasis = stringResource(Res.string.identity_headline_emphasis),
        subtitle = stringResource(Res.string.identity_supporting_text),
    )
    Spacer(Modifier.height(FIELDS_GAP))
    state.error?.let { error ->
        SaqzInlineAlert(
            text = error.asString(),
            tone = SaqzInlineAlertTone.Error,
            modifier = Modifier.testTag(Identity1cTags.Error),
        )
        Spacer(Modifier.height(FIELD_GAP))
    }
    if (state.photoFailed) {
        SaqzInlineAlert(
            text = stringResource(Res.string.identity_photo_failed),
            tone = SaqzInlineAlertTone.Warning,
            modifier = Modifier.testTag(Identity1cTags.PhotoAlert),
        )
        Spacer(Modifier.height(FIELD_GAP))
    }
    val form = rememberSaqzFormScope(onSubmit = { onIntent(IdentityCompletionIntent.Submit) })
    SaqzInput(
        value = state.name,
        onValueChange = { onIntent(IdentityCompletionIntent.UpdateName(it)) },
        label = stringResource(Res.string.register_name_placeholder),
        enabled = !state.isLoading,
        inlineLabel = true,
        errorText = stringResource(Res.string.register_error_name).takeIf { state.invalidName },
        leadingContent = { SaqzIcon(SaqzIcons.User, tint = SaqzTheme.colors.primary) },
        ime = form.imeNext(),
        modifier = Modifier.testTag(Identity1cTags.Name),
    )
    Spacer(Modifier.height(FIELD_GAP))
    SaqzInput(
        value = state.phone,
        onValueChange = { onIntent(IdentityCompletionIntent.UpdatePhone(it)) },
        label = stringResource(Res.string.register_phone_placeholder),
        kind = SaqzInputKind.Phone,
        visualTransformation = PhoneVisualTransformation(),
        enabled = !state.isLoading,
        inlineLabel = true,
        helperText = stringResource(Res.string.identity_phone_privacy),
        errorText = stringResource(Res.string.register_error_phone).takeIf { state.invalidPhone },
        leadingContent = { SaqzIcon(SaqzIcons.Phone, tint = SaqzTheme.colors.primary) },
        ime = form.imeDone(),
        modifier = Modifier.testTag(Identity1cTags.Phone),
    )
    Spacer(Modifier.height(FIELD_GAP))
    SaqzButton(
        label = stringResource(Res.string.identity_submit),
        onClick = { onIntent(IdentityCompletionIntent.Submit) },
        loading = state.isLoading,
        fullWidth = true,
        trailingContent = { color -> SaqzIcon(SaqzIcons.ArrowRight, tint = color) },
        modifier = Modifier.testTag(Identity1cTags.Submit),
    )
}

/**
 * O círculo ice de 92 com o anel por dentro, o badge azul da câmera no canto e o rótulo
 * azul abaixo.
 *
 * O círculo é o [SaqzAvatar] do 10k: o anel de 1px que o VUL-62 acertou lá é o mesmo
 * `box-shadow: inset` que o export pede aqui, e o slot de foto dele aceita tanto a imagem
 * escolhida quanto o glifo de quem ainda não escolheu.
 *
 * O clique é da coluna inteira, rótulo incluído — "Adicionar foto" é o alvo mais óbvio da
 * tela, e é ele que dá nome ao botão para o leitor de tela.
 */
@Composable
private fun PhotoPicker(photo: ImageBitmap?, onPick: () -> Unit) {
    val colors = SaqzTheme.colors
    val label = stringResource(Res.string.identity_add_photo)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClickLabel = label, role = Role.Button, onClick = onPick)
            .testTag(Identity1cTags.Photo),
    ) {
        Box {
            SaqzAvatar(name = "", size = PHOTO_SIZE, background = colors.surfaceSoft) {
                if (photo == null) {
                    SaqzIcon(SaqzIcons.User, tint = colors.primary, size = PHOTO_ICON)
                } else {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = PHOTO_BADGE_OFFSET, y = PHOTO_BADGE_OFFSET)
                    .size(PHOTO_BADGE)
                    .shadow(PHOTO_BADGE_SHADOW, CircleShape)
                    .background(colors.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SaqzIcon(SaqzIcons.Camera, tint = colors.onPrimary, size = PHOTO_BADGE_ICON)
            }
        }
        Spacer(Modifier.height(PHOTO_LABEL_GAP))
        Text(
            text = label,
            style = SaqzTheme.typography.support.copy(
                fontSize = PHOTO_LABEL_SIZE,
                fontWeight = FontWeight(PHOTO_LABEL_WEIGHT),
            ),
            color = colors.primary,
        )
    }
}

@Preview(name = "1c — completar cadastro", widthDp = 390, heightDp = 844)
@Composable
private fun IdentityCompletionScreenPreview() = SaqzTheme {
    IdentityCompletionScreen(IdentityCompletionState(name = "Ana Costa"), {}, {})
}

@Preview(name = "1c — a foto não subiu", widthDp = 390, heightDp = 844)
@Composable
private fun IdentityCompletionPhotoFailedPreview() = SaqzTheme {
    IdentityCompletionScreen(
        IdentityCompletionState(name = "Ana Costa", phone = "(11) 99999-0000", photoFailed = true),
        {},
        {},
    )
}
