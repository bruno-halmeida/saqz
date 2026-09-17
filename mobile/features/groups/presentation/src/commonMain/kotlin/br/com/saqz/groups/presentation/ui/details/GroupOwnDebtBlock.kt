package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/**
 * Andaime (T): a seção antiga inteira (pendentes + histórico + Pix), já na posição final —
 * logo abaixo do jogo. O C3 troca pelo ticket enxuto. `GroupOwnChargesSection` continua
 * existindo: a tela Perfil → Mensalidades a reutiliza.
 */
@Composable
internal fun GroupOwnDebtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ownCharges = state.ownCharges ?: return
    GroupOwnChargesSection(ownCharges = ownCharges, onIntent = onIntent, modifier = modifier)
}

/** Andaime (T): a linha "Tudo em dia" do fim da tela chega no ticket C3. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupOwnChargesSettledBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
