package br.com.saqz.receivables.presentation

import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PaymentViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun payAllCoversEveryOpenChargeInOneCode() = runTest {
        val fake = PaymentsFake()
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        assertEquals(listOf(ReceiptMethod.PIX to listOf("c1", "c2", "c3")), fake.created)
        assertEquals("R$ 60,00", vm.state.value.amountLabel)
        assertEquals("000201pix", vm.state.value.pixPayload)
        assertEquals("Marcos Lima", vm.state.value.payeeName)
        assertEquals(PaymentSubject.MANY, vm.state.value.subject)
    }

    @Test fun gameTargetCoversTheGuestOfThatGameAndOnlyIt() = runTest {
        val fake = PaymentsFake()
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET", gameId = "g2"))
        assertEquals(listOf("c2", "c3"), fake.created.single().second)
        assertEquals(PaymentSubject.GAME, vm.state.value.subject)
        assertEquals(listOf("Sua diária", "Convidado: Rafa"), vm.state.value.lines.map { it.label })
        assertEquals("Vôlei do CERET · terça, 28/07", vm.state.value.kicker)
    }

    @Test fun nothingOpenForTheTargetShowsTheEmptyMessage() = runTest {
        val fake = PaymentsFake()
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET", gameId = "other"))
        assertTrue(vm.state.value.nothingOpen)
        assertTrue(fake.created.isEmpty())
    }

    @Test fun pollsTheCodeUntilPaidAndConfirms() = runTest {
        val fake = PaymentsFake()
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET", gameId = "g2"))
        advanceTimeBy(PaymentViewModel.POLL_MILLIS + 1)
        assertNull(vm.state.value.confirmed)
        assertEquals(1, fake.polls)
        fake.status = PaymentCodeStatus.PAID
        advanceTimeBy(PaymentViewModel.POLL_MILLIS + 1)
        assertEquals("R$ 40,00 · jogo de terça", vm.state.value.confirmed?.summary)
        val polls = fake.polls
        advanceTimeBy(PaymentViewModel.POLL_MILLIS * 3)
        assertEquals(polls, fake.polls, "para de consultar depois do PAID")
    }

    @Test fun expiredCodeAsksForANewOne() = runTest {
        val fake = PaymentsFake()
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        fake.status = PaymentCodeStatus.EXPIRED
        advanceTimeBy(PaymentViewModel.POLL_MILLIS + 1)
        assertTrue(vm.state.value.codeExpired)
        fake.status = PaymentCodeStatus.PENDING
        vm.onIntent(PaymentIntent.NewCode)
        assertFalse(vm.state.value.codeExpired)
        assertEquals(2, fake.created.size)
    }

    @Test fun chargeNotPayableOnCreationAlsoAsksForANewCode() = runTest {
        val fake = PaymentsFake().apply { createError = PaymentError.CHARGE_NOT_PAYABLE }
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        assertTrue(vm.state.value.codeExpired)
    }

    @Test fun pixNotReadyShowsOnlyTheCard() = runTest {
        val fake = PaymentsFake().apply { createError = PaymentError.PIX_NOT_READY }
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        assertFalse(vm.state.value.pixAvailable)
        assertEquals(PayTab.CARD, vm.state.value.tab)
        assertNull(vm.state.value.error)
        vm.onIntent(PaymentIntent.SelectTab(PayTab.PIX))
        assertEquals(PayTab.CARD, vm.state.value.tab)
    }

    @Test fun pixNotReadyFlagInTheMeSkipsThePixCodeEntirely() = runTest {
        val fake = PaymentsFake().apply { me = me.copy(pixReady = false) }
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        assertEquals(PayTab.CARD, vm.state.value.tab)
        assertTrue(fake.created.isEmpty())
    }

    @Test fun cardAsksTheCpfOnceThenOpensTheHostedCheckout() = runTest {
        val fake = PaymentsFake()
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        vm.onIntent(PaymentIntent.SelectTab(PayTab.CARD))
        assertTrue(vm.state.value.needsDocument)
        vm.onIntent(PaymentIntent.CpfChanged("529.982.247-24"))
        vm.onIntent(PaymentIntent.ContinueCard)
        assertTrue(vm.state.value.cpfInvalid)
        assertTrue(fake.documents.isEmpty())
        val effect = async { vm.effects.first() }
        vm.onIntent(PaymentIntent.CpfChanged("529.982.247-25"))
        vm.onIntent(PaymentIntent.ContinueCard)
        assertEquals(listOf("52998224725"), fake.documents)
        assertFalse(vm.state.value.needsDocument)
        assertEquals(PaymentEffect.OpenUrl("https://sandbox.asaas.com/i/abc"), effect.await())
        assertTrue(vm.state.value.waitingCard)
        assertEquals(ReceiptMethod.CARD, fake.created.last().first)
    }

    @Test fun documentRequiredFromTheServerLeadsToTheCpf() = runTest {
        val fake = PaymentsFake().apply { me = me.copy(hasDocument = true) }
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        vm.onIntent(PaymentIntent.SelectTab(PayTab.CARD))
        assertFalse(vm.state.value.needsDocument)
        fake.createError = PaymentError.DOCUMENT_REQUIRED
        vm.onIntent(PaymentIntent.ContinueCard)
        assertTrue(vm.state.value.needsDocument)
    }

    @Test fun monthlyPaidByPixOffersAutopayAtMostOncePerMonth() = runTest {
        val memory = OfferMemory()
        val fake = PaymentsFake().apply { me = monthlyMe; status = PaymentCodeStatus.PAID }
        val first = model(fake, PaymentArgs("ceret", "Vôlei do CERET", billingMonth = "2026-08"), memory)
        assertEquals(true, first.state.value.confirmed?.offer)
        assertEquals("10", first.state.value.confirmed?.dueDay)
        assertEquals(setOf("autopay-offer:ceret:2026-08"), memory.keys)
        val second = model(fake, PaymentArgs("ceret", "Vôlei do CERET", billingMonth = "2026-08"), memory)
        assertEquals(false, second.state.value.confirmed?.offer)
    }

    @Test fun athleteWithoutRecurrenceNeverSeesTheOffer() = runTest {
        val fake = PaymentsFake().apply { me = monthlyMe.copy(recurrence = null); status = PaymentCodeStatus.PAID }
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"), OfferMemory())
        assertEquals(false, vm.state.value.confirmed?.offer)
    }

    @Test fun routeThatDoesNotExistYetFailsShort() = runTest {
        val fake = PaymentsFake().apply { meError = PaymentError.UNAVAILABLE }
        val vm = model(fake, PaymentArgs("ceret", "Vôlei do CERET"))
        assertEquals(PaymentError.UNAVAILABLE, vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    private fun model(fake: PaymentsFake, args: PaymentArgs, memory: OfferMemory = OfferMemory()) =
        PaymentViewModel(args, fake, memory, FixedClock, ReceivablesSessionContext { "session" })

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.parse("2026-08-10T15:00:00Z")
    }
}

internal class OfferMemory : AutopayOfferMemory {
    val keys = mutableSetOf<String>()
    override fun wasOffered(key: String, done: (Boolean) -> Unit) = done(key in keys)
    override fun markOffered(key: String) { keys += key }
}

internal val openMe = AthleteReceivables(
    active = true, payeeName = "Marcos Lima", methods = setOf(ReceiptMethod.PIX, ReceiptMethod.CARD), pixReady = true,
    hasDocument = false,
    openCharges = listOf(
        OpenCharge("c1", OpenChargeKind.GAME, "g1", null, null, "2026-07-21", 2000, true),
        OpenCharge("c2", OpenChargeKind.GAME, "g2", null, null, "2026-07-28", 2000, false),
        OpenCharge("c3", OpenChargeKind.GAME, "g2", null, "Rafa", "2026-07-28", 2000, false),
    ),
    totalCents = 6000, recurrence = null,
)

internal val monthlyMe = openMe.copy(
    openCharges = listOf(OpenCharge("m8", OpenChargeKind.MONTHLY, null, "2026-08", null, "2026-08-10", 10000, false)),
    totalCents = 10000,
    recurrence = AthleteRecurrence(RecurrenceStatus.NONE, 10000, null, null, emptyList()),
)

internal class PaymentsFake : AthletePaymentsGateway {
    var me = openMe
    var meError: PaymentError? = null
    var createError: PaymentError? = null
    var status = PaymentCodeStatus.PENDING
    val created = mutableListOf<Pair<ReceiptMethod, List<String>>>()
    val documents = mutableListOf<String>()
    var polls = 0
    private var last: PaymentCode? = null

    override suspend fun me(groupId: String) = meError?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(me)

    override suspend fun createCode(groupId: String, requestId: String, method: ReceiptMethod, chargeIds: List<String>):
        SaqzResult<PaymentCode, PaymentError> {
        createError?.let { return SaqzResult.Failure(it) }
        created += method to chargeIds
        val amount = me.openCharges.filter { it.chargeId in chargeIds }.sumOf { it.payableCents }
        return SaqzResult.Success(PaymentCode("k${created.size}", method, amount, chargeIds, status,
            if (method == ReceiptMethod.PIX) "000201pix" else null, null,
            if (method == ReceiptMethod.CARD) "https://sandbox.asaas.com/i/abc" else null).also { last = it })
    }

    override suspend fun code(codeId: String): SaqzResult<PaymentCode, PaymentError> {
        polls++
        return SaqzResult.Success(last!!.copy(status = status))
    }

    override suspend fun saveDocument(requestId: String, cpf: String): SaqzResult<Unit, PaymentError> {
        if (!cpf.isValidCpf()) return SaqzResult.Failure(PaymentError.INVALID_DOCUMENT)
        documents += cpf
        me = me.copy(hasDocument = true)
        return SaqzResult.Success(Unit)
    }
}
