package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.receivables.presentation.ReceivablesState

/** Cadastro financeiro e ativação: só a conta dona do plano, e só com a disponibilidade do servidor (ou conta já criada). */
internal fun SessionAccessState.canOpenReceipts(receipts: ReceivablesState): Boolean =
    isAccountOwner() && receipts.configurationEntryAvailable
