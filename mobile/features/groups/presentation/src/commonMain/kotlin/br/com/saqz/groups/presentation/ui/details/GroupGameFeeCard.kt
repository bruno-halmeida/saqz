package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.groups.presentation.details.GameFeeUndefinedUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_fee_card_action
import br.com.saqz.groups.resources.game_fee_card_body
import br.com.saqz.groups.resources.game_fee_card_body_suggestion
import br.com.saqz.groups.resources.game_fee_card_kicker
import br.com.saqz.groups.resources.game_fee_card_title
import org.jetbrains.compose.resources.stringResource

/**
 * DEB-146 · JOGO-008 (prancha Aviso-grupo): o grupo ainda não tem valor por jogo. Só dono e admins veem; "Definir
 * valor" abre o "Valor por jogo" de Editar grupo com a sugestão preenchida. Azul da marca com botão branco.
 */
@Composable
internal fun GroupGameFeeCard(notice: GameFeeUndefinedUi, onDefine: () -> Unit, modifier: Modifier = Modifier) {
    SaqzHeroCard(
        kicker = stringResource(Res.string.game_fee_card_kicker),
        title = stringResource(Res.string.game_fee_card_title),
        meta = notice.suggestion?.let { stringResource(Res.string.game_fee_card_body_suggestion, it) }
            ?: stringResource(Res.string.game_fee_card_body),
        modifier = modifier.testTag(GroupDetailsTags.GameFeeCard),
    ) {
        SaqzButton(
            label = stringResource(Res.string.game_fee_card_action),
            onClick = onDefine,
            variant = SaqzButtonVariant.Inverse,
            modifier = Modifier.testTag(GroupDetailsTags.GameFeeCardAction),
        )
    }
}
