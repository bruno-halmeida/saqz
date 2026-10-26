package br.com.saqz.receivables.presentation

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizerReceivablesViewModelTest {
    @BeforeTest fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun teardown() { Dispatchers.resetMain() }

    @Test fun activationPreviewAlwaysCarriesPixAndCard() = runTest {
        val fake = ReceiptFake()
        val vm = ReceiptConfigurationViewModel("group", fake, object : ReceivablesAvailabilityGateway {
            override suspend fun get() = SaqzResult.Success(ReceivablesAvailability(true, true))
        }, ReceivablesSessionContext { "session" }, SavedStateHandle(), ReceivablesRecoveryIdentity { "user" })
        vm.onIntent(ReceiptConfigurationIntent.SelectAccount("account"))
        vm.onIntent(ReceiptConfigurationIntent.PreviewAllMethods)
        vm.onIntent(ReceiptConfigurationIntent.Accept(true))
        vm.onIntent(ReceiptConfigurationIntent.Activate)
        assertEquals(setOf(ReceiptMethod.PIX, ReceiptMethod.CARD), fake.activations.single().methods)
    }

    @Test fun feePreviewAbsorbAndPass() {
        val quote = FeeQuote(FeeQuoteSide(2000, 1951, 1880), FeeQuoteSide(2128, 2077, 2000), "R$ 0,99", "4,99% + R$ 0,49")
        assertEquals("Atleta paga R$ 20,00 · você recebe R$ 20,00 − taxas", feePreviewText(FeePolicy.ABSORB, 2000, quote).nbsp())
        assertEquals("Atleta paga R$ 21,28 · você recebe R$ 20,00", feePreviewText(FeePolicy.PASS, 2000, quote).nbsp())
        // Sem resposta do servidor ainda: o valor base, nunca uma taxa inventada.
        assertEquals("Atleta paga R$ 20,00 · você recebe R$ 20,00", feePreviewText(FeePolicy.PASS, 2000, null).nbsp())
    }

    @Test fun groupStatePicksTheActiveAccountAndAsksTheQuoteOnce() = runTest {
        val payments = GroupPaymentsFake()
        val vm = GroupReceivablesViewModel("group", ReceiptFake().apply { enabled = true }, payments, WalletOnly(),
            AutopayNotYet(), ReceivablesSessionContext { "session" })
        assertTrue(vm.state.value.active)
        assertEquals("account", vm.state.value.accountId)
        assertEquals("R$ 860,00", vm.state.value.walletLabel?.nbsp())
        assertEquals(1, vm.state.value.reviews.size)
        assertNull(vm.state.value.autopay, "sem a rota da F3, a linha de automático some")
        vm.onIntent(GroupReceivablesIntent.Quote(2000))
        vm.onIntent(GroupReceivablesIntent.Quote(2000))
        assertEquals(1, payments.quotes)
        assertNotNull(vm.state.value.quotes[2000])
    }

    @Test fun groupWithoutAccountShowsNothing() = runTest {
        val vm = GroupReceivablesViewModel("group", ReceiptFake().apply { listed = emptyList() }, GroupPaymentsFake(), WalletOnly(),
            AutopayNotYet(), ReceivablesSessionContext { "session" })
        assertFalse(vm.state.value.active)
        assertNull(vm.state.value.accountId)
        assertFalse(vm.state.value.loading)
    }

    @Test fun feePolicyLoadsTheCurrentAndSavesOnlyAChange() = runTest {
        val payments = GroupPaymentsFake()
        val vm = FeePolicyViewModel(FeePolicyArgs("group", "account"), ReceiptFake(), payments, ReceivablesSessionContext { "session" })
        assertEquals(FeePolicy.ABSORB, vm.state.value.current)
        assertFalse(vm.state.value.canSave)
        assertEquals("R$ 0,99", vm.state.value.pixFee)
        vm.onIntent(FeePolicyIntent.Select(FeePolicy.PASS))
        val effect = async { vm.effects.first() }
        vm.onIntent(FeePolicyIntent.Save)
        assertEquals(FeePolicyEffect.Saved, effect.await())
        assertEquals(listOf(FeePolicy.PASS), payments.policies)
    }

    @Test fun reviewSuggestsTheOldestChargesAndTheOrganizerCanUnmark() = runTest {
        val payments = GroupPaymentsFake()
        val vm = PaymentReviewViewModel(PaymentReviewArgs("group", "account", "r1"), payments, Debtors(),
            ReceivablesSessionContext { "session" })
        assertEquals("João P. Silva", vm.state.value.payerName)
        vm.onIntent(PaymentReviewIntent.StartAssign)
        assertEquals(listOf("felipe", "carla"), vm.state.value.debtors.map { it.memberUserId })
        assertEquals("2 em aberto · R$ 40,00", vm.state.value.debtors.first().support?.nbsp())
        vm.onIntent(PaymentReviewIntent.SelectMember("carla"))
        assertNull(vm.state.value.member, "quem não deve não pode ser escolhido")
        vm.onIntent(PaymentReviewIntent.SelectMember("felipe"))
        assertEquals(listOf(true, true), vm.state.value.charges.map { it.checked })
        assertFalse(vm.state.value.canAssign, "R$ 40,00 marcados num Pix de R$ 20,00")
        vm.onIntent(PaymentReviewIntent.ToggleCharge("c2"))
        assertTrue(vm.state.value.canAssign)
        val effect = async { vm.effects.first() }
        vm.onIntent(PaymentReviewIntent.ConfirmAssign)
        assertEquals(PaymentReviewEffect.Resolved, effect.await())
        assertEquals(listOf("felipe" to listOf("c1")), payments.assigned)
    }

    @Test fun notChargeResolvesTheReview() = runTest {
        val payments = GroupPaymentsFake()
        val vm = PaymentReviewViewModel(PaymentReviewArgs("group", "account", "r1"), payments, Debtors(),
            ReceivablesSessionContext { "session" })
        val effect = async { vm.effects.first() }
        vm.onIntent(PaymentReviewIntent.NotCharge)
        assertEquals(PaymentReviewEffect.Resolved, effect.await())
        assertEquals(1, payments.notCharges)
    }

    private fun String.nbsp() = replace(' ', ' ')

    private class Debtors : GroupDebtorsGateway {
        override suspend fun debtors(groupId: String) = SaqzResult.Success(listOf(
            GroupDebtor("felipe", "Felipe Nunes", 2, 4000), GroupDebtor("carla", "Carla Mendes", 0, 0)))
    }

    private class WalletOnly : ReceiptWalletGateway {
        override suspend fun balance(accountId: String) = SaqzResult.Success(ReceiptWalletBalance(accountId, 86000, 0, "now"))
        override suspend fun statement(accountId: String, cursor: String?) = TODO()
        override suspend fun destinations(accountId: String) = TODO()
        override suspend fun saveDestination(accountId: String, requestId: String, details: ReceiptBankDetails) = TODO()
        override suspend fun recoverDestination(accountId: String, requestId: String) = TODO()
        override suspend fun withdraw(accountId: String, requestId: String, destinationId: String, amountCents: Long) = TODO()
        override suspend fun recoverWithdrawal(accountId: String, requestId: String) = TODO()
    }

    private class AutopayNotYet : AutopayGateway {
        override suspend fun start(groupId: String, requestId: String) = SaqzResult.Failure(PaymentError.UNAVAILABLE)
        override suspend fun cancel(groupId: String, requestId: String, reason: AutopayCancelReason) =
            SaqzResult.Failure(PaymentError.UNAVAILABLE)
        override suspend fun changeCard(groupId: String, requestId: String) = SaqzResult.Failure(PaymentError.UNAVAILABLE)
        override suspend fun members(groupId: String, accountId: String) = SaqzResult.Failure(PaymentError.UNAVAILABLE)
    }
}

internal class GroupPaymentsFake : GroupPaymentsGateway {
    var quotes = 0
    var notCharges = 0
    val policies = mutableListOf<FeePolicy>()
    val assigned = mutableListOf<Pair<String, List<String>>>()
    override suspend fun setFeePolicy(groupId: String, requestId: String, accountId: String, policy: FeePolicy):
        SaqzResult<ReceiptStatus, ReceiptError> {
        policies += policy
        return SaqzResult.Success(ReceiptStatus(ReceiptConfiguration(accountId, groupId, true, true, true, policy), emptyMap()))
    }
    override suspend fun quote(groupId: String, accountId: String, baseCents: Long): SaqzResult<FeeQuote, ReceiptError> {
        quotes++
        return SaqzResult.Success(FeeQuote(FeeQuoteSide(baseCents, baseCents - 49, baseCents - 120),
            FeeQuoteSide(baseCents + 128, baseCents + 77, baseCents), "R$ 0,99", "4,99% + R$ 0,49"))
    }
    override suspend fun reviews(groupId: String, accountId: String) =
        SaqzResult.Success(listOf(PaymentReview("r1", "João P. Silva", 2000, "2026-07-28T23:04:00Z")))
    override suspend fun suggestion(reviewId: String, memberUserId: String) = SaqzResult.Success(ReviewSuggestion(listOf(
        SuggestedCharge("c1", "Diária · 21/07", "2026-07-21", 2000, true),
        SuggestedCharge("c2", "Diária · 28/07", "2026-07-28", 2000, false)), 2000))
    override suspend fun assign(reviewId: String, requestId: String, memberUserId: String, chargeIds: List<String>):
        SaqzResult<ReviewAssignment, ReceiptError> {
        assigned += memberUserId to chargeIds
        return SaqzResult.Success(ReviewAssignment(chargeIds, 0))
    }
    override suspend fun notCharge(reviewId: String, requestId: String): SaqzResult<String, ReceiptError> {
        notCharges++
        return SaqzResult.Success("e1")
    }
}
