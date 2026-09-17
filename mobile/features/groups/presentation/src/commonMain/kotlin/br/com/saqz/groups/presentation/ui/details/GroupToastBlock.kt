package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/** Andaime (T): o toast chega no ticket C1, junto com `state.toast` do V1. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupToastBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit
