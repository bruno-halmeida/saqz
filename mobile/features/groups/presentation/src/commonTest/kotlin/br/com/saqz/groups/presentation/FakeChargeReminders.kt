package br.com.saqz.groups.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.communication.ChargeReminderGateway
import br.com.saqz.groups.domain.communication.ChargeReminderReceipt
import br.com.saqz.groups.presentation.ui.finance.sheets.ChargeReminderViewModel

fun fakeChargeReminders() = ChargeReminderViewModel("group-1", ChargeReminderGateway { _, _, ids ->
    SaqzResult.Success(ChargeReminderReceipt(ids.size))
}, SavedStateHandle())
