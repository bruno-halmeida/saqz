package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.receivables.presentation.ReceivablesState

internal fun SessionAccessState.canOpenReceipts(receipts: ReceivablesState): Boolean =
    isAccountOwner() && receipts.configurationEntryAvailable
