package br.com.saqz.groups.presentation.ui.finance.sheets

import br.com.saqz.groups.presentation.fakeChargeReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChargeReminderViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @AfterTest fun close() { Dispatchers.resetMain() }

    @Test fun remindedChargesStayMarkedAfterTheSheetCloses() = runTest {
        val viewModel = fakeChargeReminders()
        viewModel.onIntent(ChargeReminderIntent.Send(listOf("c1", "c2")))
        assertEquals(setOf("c1", "c2"), viewModel.state.value.remindedChargeIds)

        viewModel.onIntent(ChargeReminderIntent.Reset)

        assertEquals(null, viewModel.state.value.sent)
        assertEquals(setOf("c1", "c2"), viewModel.state.value.remindedChargeIds)
    }
}
