package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_invite_sheet_action
import br.com.saqz.groups.resources.home_invite_sheet_body
import br.com.saqz.groups.resources.home_invite_sheet_field
import br.com.saqz.groups.resources.home_invite_sheet_invalid
import br.com.saqz.groups.resources.home_invite_sheet_placeholder
import br.com.saqz.groups.resources.home_invite_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * Folha "Tenho um convite", a mesma na Início sem grupo e na aba Grupos vazia. O convite
 * normal chega por deep link; esta folha existe para quem recebeu o link mas abriu o app
 * por conta própria. O texto colado vira código na ViewModel de quem a hospeda
 * (`InviteLinkParser`) e segue o mesmo caminho do deep link.
 */
@Composable
internal fun InviteLinkSheet(
    open: Boolean,
    link: String,
    invalid: Boolean,
    onLinkChange: (String) -> Unit,
    onClose: () -> Unit,
    onSubmit: () -> Unit,
    fieldTag: String,
    submitTag: String,
) {
    SaqzBottomSheet(
        open = open,
        title = stringResource(Res.string.home_invite_sheet_title),
        description = stringResource(Res.string.home_invite_sheet_body),
        onClose = onClose,
        footer = {
            SaqzButton(
                label = stringResource(Res.string.home_invite_sheet_action),
                onClick = onSubmit,
                enabled = link.isNotBlank(),
                fullWidth = true,
                modifier = Modifier.testTag(submitTag),
            )
        },
    ) {
        SaqzInput(
            value = link,
            onValueChange = onLinkChange,
            label = stringResource(Res.string.home_invite_sheet_field),
            placeholder = stringResource(Res.string.home_invite_sheet_placeholder),
            invalid = invalid,
            errorText = stringResource(Res.string.home_invite_sheet_invalid).takeIf { invalid },
            keyboardType = KeyboardType.Uri,
            modifier = Modifier.testTag(fieldTag),
        )
    }
}
