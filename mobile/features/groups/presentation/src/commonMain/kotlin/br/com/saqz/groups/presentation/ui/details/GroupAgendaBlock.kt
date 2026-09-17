package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/** Andaime (T): "Próximos jogos" chega no ticket C4, junto com `state.agenda` do V2. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupAgendaBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
