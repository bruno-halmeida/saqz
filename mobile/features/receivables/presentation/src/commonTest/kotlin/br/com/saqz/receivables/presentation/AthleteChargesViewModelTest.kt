package br.com.saqz.receivables.presentation

import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AthleteChargesViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun avulsoHeroSumsTheOpenChargesAndPayAllCoversThemAll() = runTest {
        val vm = model(PaymentsFake())
        val state = vm.state.value
        assertEquals("R$ 60,00", state.totalLabel)
        assertEquals("3 cobranças", state.summaryLabel)
        assertEquals("1 vencida", state.overdueLabel)
        assertNull(state.monthlyKicker)
        assertEquals(listOf("Jogo de terça · 21/07", "Jogo de terça · 28/07", "Convidado: Rafa"), state.open.map { it.title })
        assertEquals("Venceu em 21/07", state.open.first().support)
        assertEquals("Jogo de terça · 28/07", state.open.last().support)
        val effect = async { vm.effects.first() }
        vm.onIntent(AthleteChargesIntent.PayAll)
        assertEquals(AthleteChargesEffect.OpenPayment(listOf("c1", "c2", "c3")), effect.await())
    }

    @Test fun historySaysHowEachChargeWasPaid() = runTest {
        val vm = model(PaymentsFake(), History(listOf(
            PaidCharge("p1", OpenChargeKind.GAME, null, null, "2026-07-16", 2000, PaidVia.APP_PIX),
            PaidCharge("p2", OpenChargeKind.GAME, null, null, "2026-07-14", 2000, PaidVia.CASH),
            PaidCharge("p3", OpenChargeKind.MONTHLY, "2026-07", null, "2026-07-10", 10000, PaidVia.APP_CARD),
        )))
        assertEquals(listOf("R$ 20,00 · pelo app, no Pix", "R$ 20,00 · em dinheiro", "R$ 100,00 · pelo app · cartão"),
            vm.state.value.history.map { it.support })
        assertEquals("Jogo de quinta · 16/07", vm.state.value.history.first().title)
        assertEquals("Mensalidade · julho", vm.state.value.history.last().title)
    }

    @Test fun monthlyOnlyBecomesTheMensalistaHeroWithTheAutopayOffer() = runTest {
        val vm = model(PaymentsFake().apply { me = monthlyMe })
        val state = vm.state.value
        assertEquals("mensalidade de agosto", state.monthlyKicker)
        assertEquals("Vence em 10/08", state.summaryLabel)
        assertEquals(AutopayCardUi.Offer("10"), state.autopay)
    }

    @Test fun withoutRecurrenceInTheMeTheOfferDoesNotAppear() = runTest {
        val vm = model(PaymentsFake().apply { me = monthlyMe.copy(recurrence = null) })
        assertNull(vm.state.value.autopay)
    }

    @Test fun refusedCardKeepsTheMonthOpenAndOffersPixOrNewCard() = runTest {
        val refused = monthlyMe.copy(recurrence = AthleteRecurrence(RecurrenceStatus.REFUSED, 10000, "2026-10-10", "m8", emptyList()))
        val vm = model(PaymentsFake().apply { me = refused })
        val autopay = assertIs<AutopayCardUi.Refused>(vm.state.value.autopay)
        assertEquals("agosto", autopay.monthLabel)
        assertEquals("10/10", autopay.nextChargeLabel)
        assertEquals("Vence em 10/08 · cartão recusado", vm.state.value.open.single().support)
    }

    @Test fun failingHistoryDoesNotHideWhatIsOpen() = runTest {
        val vm = model(PaymentsFake(), OwnChargeHistoryGateway { SaqzResult.Failure(PaymentError.NETWORK) })
        assertEquals(3, vm.state.value.open.size)
        assertTrue(vm.state.value.historyFailed)
    }

    private fun model(fake: PaymentsFake, history: OwnChargeHistoryGateway = History(emptyList())) =
        AthleteChargesViewModel(GroupArgs("ceret", "Vôlei do CERET"), fake, history, ReceivablesSessionContext { "session" })

    private class History(val paid: List<PaidCharge>) : OwnChargeHistoryGateway {
        override suspend fun paid(groupId: String) = SaqzResult.Success(paid)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutopayViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun cancellingRequiresAReason() = runTest {
        val autopay = AutopayFake()
        val fake = PaymentsFake().apply { me = monthlyMe.copy(recurrence = active) }
        val vm = AutopayViewModel(GroupArgs("ceret", "Vôlei do CERET"), fake, autopay, ReceivablesSessionContext { "session" })
        vm.onIntent(AutopayIntent.RequestCancel)
        assertTrue(vm.state.value.cancelling)
        vm.onIntent(AutopayIntent.ConfirmCancel)
        assertTrue(autopay.cancelled.isEmpty(), "sem motivo, nada sai")
        vm.onIntent(AutopayIntent.SelectReason(AutopayCancelReason.PREFER_NOT_SAY))
        vm.onIntent(AutopayIntent.ConfirmCancel)
        assertEquals(listOf(AutopayCancelReason.PREFER_NOT_SAY), autopay.cancelled)
        assertFalse(vm.state.value.cancelling)
        assertTrue(vm.state.value.cancelled)
    }

    @Test fun adhesionOpensTheAsaasCheckoutAndShowsTheDayAndTheOpenMonth() = runTest {
        val vm = AutopayViewModel(GroupArgs("ceret", "Vôlei do CERET"), PaymentsFake().apply { me = monthlyMe }, AutopayFake(),
            ReceivablesSessionContext { "session" })
        assertEquals(RecurrenceStatus.NONE, vm.state.value.status)
        assertEquals("10", vm.state.value.dueDay)
        assertEquals("Agosto", vm.state.value.openMonth)
        assertEquals("R$ 100,00", vm.state.value.amountLabel)
        val effect = async { vm.effects.first() }
        vm.onIntent(AutopayIntent.Start)
        assertEquals(AutopayEffect.OpenUrl("https://sandbox.asaas.com/c/abc"), effect.await())
    }

    @Test fun avulsoOrServerBeforeF3HasNoAutopay() = runTest {
        val vm = AutopayViewModel(GroupArgs("ceret", "Vôlei do CERET"), PaymentsFake(), AutopayFake(),
            ReceivablesSessionContext { "session" })
        assertNull(vm.state.value.status)
        vm.onIntent(AutopayIntent.Start)
        vm.onIntent(AutopayIntent.RequestCancel)
        assertFalse(vm.state.value.cancelling)
    }

    private val active = AthleteRecurrence(RecurrenceStatus.ACTIVE, 10000, "2026-09-10", null,
        listOf(RecurrenceCharge("2026-08", 10000, "2026-08-10", false)))

    private class AutopayFake : AutopayGateway {
        val cancelled = mutableListOf<AutopayCancelReason>()
        override suspend fun start(groupId: String, requestId: String) = SaqzResult.Success("https://sandbox.asaas.com/c/abc")
        override suspend fun cancel(groupId: String, requestId: String, reason: AutopayCancelReason): SaqzResult<Unit, PaymentError> {
            cancelled += reason
            return SaqzResult.Success(Unit)
        }
        override suspend fun changeCard(groupId: String, requestId: String) = SaqzResult.Success("https://sandbox.asaas.com/c/new")
        override suspend fun members(groupId: String, accountId: String) = SaqzResult.Success(emptyList<MemberAutopay>())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettleLinkViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun resolvedCodeOpensThePaymentOfThatGame() = runTest {
        val vm = SettleLinkViewModel("AbC123", { SaqzResult.Success(SettleTarget("ceret", "g2", null)) },
            ReceivablesSessionContext { "session" })
        assertEquals(SettleLinkEffect.Open(SettleTarget("ceret", "g2", null)), vm.effects.first())
    }

    @Test fun unknownCodeEndsInNothingOpenNotAHang() = runTest {
        val vm = SettleLinkViewModel("AbC123", { SaqzResult.Failure(PaymentError.UNAVAILABLE) }, ReceivablesSessionContext { "session" })
        assertFalse(vm.state.value.loading)
        assertEquals(PaymentError.UNAVAILABLE, vm.state.value.error)
    }

    @Test fun codeShape() {
        assertTrue(isSettleCode("AbC_12-3x"))
        assertFalse(isSettleCode("a/b"))
        assertFalse(isSettleCode("abc"))
        assertFalse(isSettleCode(null))
    }
}
