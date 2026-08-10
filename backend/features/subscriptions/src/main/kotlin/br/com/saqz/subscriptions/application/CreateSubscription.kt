package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.adapter.output.asaas.CardDeclinedException
import br.com.saqz.subscriptions.application.SubscriptionPricing.discountedPriceCents
import br.com.saqz.subscriptions.application.SubscriptionPricing.initialPeriodEnd
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.Subscription
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.subscriptions.domain.SubscriptionStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateSubscriptionCommand(
    val ownerUserId: UUID,
    val requestId: UUID,
    val plan: Plan,
    val cycle: SubscriptionCycle,
    val billingType: AsaasBillingType,
    val name: String,
    val email: String,
    val cpfCnpj: String,
    val couponCode: String? = null,
    /** Obrigatórios (validados em [CreateSubscription.execute]) quando billingType é CREDIT_CARD. */
    val creditCard: CreditCardDetails? = null,
    val creditCardHolderInfo: CreditCardHolderInfo? = null,
    /** IP do pagador — resolvido pelo controller a partir da request, nunca enviado pelo cliente. */
    val remoteIp: String? = null,
)

sealed interface CreateSubscriptionResult {
    data class Success(
        val subscription: Subscription,
        val billingType: AsaasBillingType,
        val pixCopyPaste: String?,
        val pixQrCodeBase64: String?,
        val invoiceUrl: String?,
    ) : CreateSubscriptionResult

    data object AlreadySubscribed : CreateSubscriptionResult
    data object CouponNotFound : CreateSubscriptionResult
    data object CouponExpired : CreateSubscriptionResult
    data object CouponAlreadyRedeemed : CreateSubscriptionResult
    data object InvalidCustomerDetails : CreateSubscriptionResult
    data class InvalidCreditCardDetails(val fieldErrors: Map<String, List<String>>) : CreateSubscriptionResult

    /**
     * Cartão recusado pela Asaas. `reason` é o código de recusa da Asaas repassado como veio
     * (ex.: "invalid_creditCard") — contrato pinado com o mobile (VUL-196), não inventar um
     * enum próprio aqui. `asaasDescription` já vem em PT-BR da Asaas.
     */
    data class CardDeclined(val reason: String, val asaasDescription: String) : CreateSubscriptionResult

    /** An unconfirmed subscription is pending for a DIFFERENT plan/cycle than this request. */
    data object PendingCheckoutMismatch : CreateSubscriptionResult
}

class CreateSubscription(
    private val subscriptions: SubscriptionRepository,
    private val coupons: CouponRepository,
    private val asaasGateway: AsaasGateway,
    private val transaction: SubscriptionsTransactionRunner,
    private val clock: Clock,
    private val creditCardTokens: CreditCardTokenStore = CreditCardTokenStore { _, _, _, _ -> },
) {
    fun execute(command: CreateSubscriptionCommand): CreateSubscriptionResult {
        val name = command.name.trim()
        val email = command.email.trim()
        val cpfDigits = command.cpfCnpj.filter { it.isDigit() }
        if (name.isBlank() || !isValidEmail(email) || !isValidCpfCnpj(cpfDigits)) {
            return CreateSubscriptionResult.InvalidCustomerDetails
        }
        if (command.billingType == AsaasBillingType.CREDIT_CARD) {
            val fieldErrors = validateCreditCard(command)
            if (fieldErrors.isNotEmpty()) return CreateSubscriptionResult.InvalidCreditCardDetails(fieldErrors)
        }

        val now = clock.instant()

        // Commit local row first (Asaas side effects + insert/reactivate). Checkout is best-effort after.
        // Coupon resolution only runs for a genuinely new Asaas subscription (new or reactivated) — an
        // unconfirmed subscription being recovered must not re-check redemption, since it was already
        // recorded on the first attempt and would wrongly fail the retry.
        val outcome = try {
            transaction.inTransaction {
                subscriptions.lockOwner(command.ownerUserId)
                val existing = subscriptions.findByOwnerUserIdForUpdate(command.ownerUserId)
                when {
                    existing == null -> newSubscriptionOutcome(command, now) { c, v ->
                        createNew(command, name, email, cpfDigits, c, v, now)
                    }
                    existing.status == SubscriptionStatus.CANCELED ->
                        newSubscriptionOutcome(command, now) { c, v ->
                            reactivate(existing, command, name, email, cpfDigits, c, v, now)
                        }
                    existing.firstConfirmedAt == null ->
                        // Legacy rows predating billingType have it null — only enforce the match once
                        // we actually know what the existing charge's billing type was.
                        if (existing.plan == command.plan &&
                            existing.cycle == command.cycle &&
                            (existing.billingType == null || existing.billingType == command.billingType)
                        ) {
                            CommitOutcome.Committed(existing)
                        } else {
                            CommitOutcome.Rejected(CreateSubscriptionResult.PendingCheckoutMismatch)
                        }
                    else -> CommitOutcome.Committed(existing) // confirmed active/past_due — AlreadySubscribed after commit
                }
            }
        } catch (ex: CardDeclinedException) {
            return CreateSubscriptionResult.CardDeclined(reason = ex.asaasCode, asaasDescription = ex.asaasDescription)
        }

        val committed = when (outcome) {
            is CommitOutcome.Rejected -> return outcome.result
            is CommitOutcome.Committed -> outcome.subscription
        }

        if (committed.status != SubscriptionStatus.CANCELED &&
            committed.firstConfirmedAt == null
        ) {
            val checkout = resolveCheckout(committed, now)
            return CreateSubscriptionResult.Success(
                // Assinatura ja paga volta ACTIVE e sem checkout: e assim que o app sabe que
                // nao deve oferecer pagamento de novo (nasce sempre PAST_DUE em blankSubscription).
                subscription = checkout.subscription,
                billingType = command.billingType,
                pixCopyPaste = checkout.pixCopyPaste,
                pixQrCodeBase64 = checkout.pixQrCodeBase64,
                invoiceUrl = checkout.invoiceUrl,
            )
        }

        return CreateSubscriptionResult.AlreadySubscribed
    }

    private fun newSubscriptionOutcome(
        command: CreateSubscriptionCommand,
        now: Instant,
        create: (Coupon?, Long) -> Subscription,
    ): CommitOutcome {
        val couponOutcome = resolveCoupon(command.couponCode, command.ownerUserId, now)
        if (couponOutcome is CouponOutcome.Failure) return CommitOutcome.Rejected(couponOutcome.result)
        val coupon = (couponOutcome as CouponOutcome.Ok).coupon
        val valueCents = discountedPriceCents(command.plan, command.cycle, coupon)
        return CommitOutcome.Committed(create(coupon, valueCents))
    }

    private sealed interface CommitOutcome {
        data class Committed(val subscription: Subscription) : CommitOutcome
        data class Rejected(val result: CreateSubscriptionResult) : CommitOutcome
    }

    private fun createNew(
        command: CreateSubscriptionCommand,
        name: String,
        email: String,
        cpfDigits: String,
        coupon: Coupon?,
        valueCents: Long,
        now: Instant,
    ): Subscription {
        val customerId = asaasGateway.createCustomer(command.ownerUserId, name, email, cpfDigits)
        val creation = createAsaasSubscription(command, customerId, valueCents)
        val subscription = blankSubscription(
            ownerUserId = command.ownerUserId,
            plan = command.plan,
            cycle = command.cycle,
            customerId = customerId,
            asaasSubscriptionId = creation.asaasSubscriptionId,
            billingType = command.billingType,
            now = now,
            coupon = coupon,
        )
        subscriptions.insert(subscription)
        persistCreditCardToken(creation)
        if (coupon != null) {
            coupons.saveRedemption(CouponRedemption(coupon.id, command.ownerUserId, now))
        }
        return subscription
    }

    private fun createAsaasSubscription(
        command: CreateSubscriptionCommand,
        customerId: String,
        valueCents: Long,
    ): AsaasSubscriptionCreation =
        asaasGateway.createSubscription(
            asaasCustomerId = customerId,
            plan = command.plan,
            cycle = command.cycle,
            valueCents = valueCents,
            billingType = command.billingType,
            idempotencyKey = "subscription-create:${command.ownerUserId}:${command.requestId}",
            creditCard = command.creditCard,
            creditCardHolderInfo = command.creditCardHolderInfo,
            remoteIp = command.remoteIp,
        )

    /**
     * Roda em toda criação/reativação, com ou sem cartão: a linha de `subscriptions` é reaproveitada
     * por owner, então uma reativação em PIX depois de uma assinatura de cartão cancelada precisa
     * limpar o token/last4/brand antigos — senão ficam órfãos, apontando para um cartão que não tem
     * mais nenhuma relação com o `asaasSubscriptionId` atual (achado do Codex no PR #179).
     */
    private fun persistCreditCardToken(creation: AsaasSubscriptionCreation) {
        val card = creation.creditCard
        creditCardTokens.save(creation.asaasSubscriptionId, card?.token, card?.lastFourDigits, card?.brand)
    }

    private fun reactivate(
        existing: Subscription,
        command: CreateSubscriptionCommand,
        name: String,
        email: String,
        cpfDigits: String,
        coupon: Coupon?,
        valueCents: Long,
        now: Instant,
    ): Subscription {
        val customerId = asaasGateway.createCustomer(command.ownerUserId, name, email, cpfDigits)
        val creation = createAsaasSubscription(command, customerId, valueCents)
        val reactivated = blankSubscription(
            ownerUserId = command.ownerUserId,
            plan = command.plan,
            cycle = command.cycle,
            customerId = customerId,
            asaasSubscriptionId = creation.asaasSubscriptionId,
            billingType = command.billingType,
            now = now,
            coupon = coupon,
        )
        subscriptions.save(reactivated)
        persistCreditCardToken(creation)
        if (coupon != null && !coupons.hasRedemption(coupon.id, command.ownerUserId)) {
            coupons.saveRedemption(CouponRedemption(coupon.id, command.ownerUserId, now))
        }
        return reactivated
    }

    private fun blankSubscription(
        ownerUserId: UUID,
        plan: Plan,
        cycle: SubscriptionCycle,
        customerId: String,
        asaasSubscriptionId: String,
        billingType: AsaasBillingType,
        now: Instant,
        coupon: Coupon?,
    ) = Subscription(
        ownerUserId = ownerUserId,
        plan = plan,
        cycle = cycle,
        asaasCustomerId = customerId,
        asaasSubscriptionId = asaasSubscriptionId,
        billingType = billingType,
        currentPeriodEnd = initialPeriodEnd(now, cycle),
        status = SubscriptionStatus.PAST_DUE,
        pastDueSince = now,
        canceledAt = null,
        pendingPlan = null,
        pendingPlanEffectiveAt = null,
        couponId = coupon?.id,
        couponCyclesRemaining = coupon?.durationCycles,
        firstConfirmedAt = null,
        pendingUpgradePlan = null,
        pendingUpgradeChargeId = null,
    )

    private sealed interface CouponOutcome {
        data class Ok(val coupon: Coupon?) : CouponOutcome
        data class Failure(val result: CreateSubscriptionResult) : CouponOutcome
    }

    private fun resolveCoupon(code: String?, ownerUserId: UUID, now: Instant): CouponOutcome {
        if (code.isNullOrBlank()) return CouponOutcome.Ok(null)
        val coupon = coupons.findByCode(code.trim())
            ?: return CouponOutcome.Failure(CreateSubscriptionResult.CouponNotFound)
        val validUntil = coupon.validUntil
        if (validUntil != null && validUntil.isBefore(now)) {
            return CouponOutcome.Failure(CreateSubscriptionResult.CouponExpired)
        }
        if (coupons.hasRedemption(coupon.id, ownerUserId)) {
            return CouponOutcome.Failure(CreateSubscriptionResult.CouponAlreadyRedeemed)
        }
        return CouponOutcome.Ok(coupon)
    }

    private data class Checkout(
        val subscription: Subscription,
        val pixCopyPaste: String?,
        val pixQrCodeBase64: String?,
        val invoiceUrl: String?,
    )

    /**
     * Recupera a cobranca que ja existe no Asaas para uma assinatura ainda nao confirmada.
     *
     * O webhook e push e acontece uma vez so: se a entrega se perdeu, ninguem mais avisa que a
     * cobranca foi paga. Por isso aqui se pergunta o estado direto ao Asaas antes de oferecer
     * pagamento — sem isso o usuario e convidado a pagar de novo algo que ja pagou.
     *
     * Enriquecimento continua best-effort (falha de rede nao pode derrubar um create que ja
     * comitou), mas o `invoiceUrl` agora sai do mesmo GET do status: se o Pix nao puder ser
     * regerado, a cobranca ainda chega ao usuario pelo boleto/fatura em vez de sumir.
     */
    private fun resolveCheckout(committed: Subscription, now: Instant): Checkout {
        val paymentId = runCatching {
            asaasGateway.findLatestPaymentIdForSubscription(committed.asaasSubscriptionId)
        }.getOrNull() ?: return Checkout(committed, null, null, null)

        val payment = runCatching { asaasGateway.findPayment(paymentId) }.getOrNull()
        if (PaymentConfirmation.isPaid(payment?.status)) {
            return Checkout(confirmPaidCharge(committed, paymentId, now), null, null, null)
        }

        val pix = runCatching { asaasGateway.regeneratePixPayload(paymentId) }.getOrNull()
        val invoice = payment?.invoiceUrl
            ?: runCatching { asaasGateway.findPaymentInvoiceUrl(paymentId) }.getOrNull()
        return Checkout(
            committed,
            pixCopyPaste = pix?.payload,
            pixQrCodeBase64 = pix?.encodedImage,
            invoiceUrl = invoice,
        )
    }

    /**
     * Mesma confirmacao do webhook ([PaymentConfirmation]), sob lock, para os dois caminhos
     * nao divergirem. Se o webhook chegou primeiro (ou chegar depois), `lastConfirmedPaymentId`
     * faz o segundo virar no-op em vez de avancar `currentPeriodEnd` um ciclo de graca.
     */
    private fun confirmPaidCharge(committed: Subscription, paymentId: String, now: Instant): Subscription =
        transaction.inTransaction {
            val current = subscriptions.findByOwnerUserIdForUpdate(committed.ownerUserId) ?: committed
            if (current.firstConfirmedAt != null ||
                PaymentConfirmation.isAlreadyConfirmed(current, paymentId)
            ) {
                return@inTransaction current
            }
            val outcome = PaymentConfirmation.confirm(current, paymentId, now)
            subscriptions.save(outcome.subscription)
            outcome.fullPriceCentsToPush?.let { cents ->
                runCatching { asaasGateway.updateSubscriptionValue(current.asaasSubscriptionId, cents) }
            }
            outcome.subscription
        }

    private fun isValidEmail(email: String): Boolean =
        email.length in 3..254 && email.contains('@') && email.indexOf('@') in 1 until email.lastIndex

    private fun isValidCpfCnpj(digits: String): Boolean =
        digits.length == 11 || digits.length == 14

    /**
     * Validação de presença/formato na borda — 400 com o campo, nunca 500 por dado faltando lá
     * na frente na chamada à Asaas. Não valida Luhn do número: isso é da tela de captura (mobile,
     * ticket seguinte); aqui é só sanidade de formato para não estourar na Asaas.
     */
    private fun validateCreditCard(command: CreateSubscriptionCommand): Map<String, List<String>> {
        val errors = mutableMapOf<String, MutableList<String>>()
        fun fail(field: String, message: String) {
            errors.getOrPut(field) { mutableListOf() }.add(message)
        }

        val card = command.creditCard
        if (card == null) {
            fail("creditCard", "is required for CREDIT_CARD billing")
        } else {
            if (card.holderName.isBlank()) fail("creditCard.holderName", "is required")
            if (card.number.filter { it.isDigit() }.length !in 13..19) {
                fail("creditCard.number", "must have 13 to 19 digits")
            }
            if (!card.expiryMonth.matches(EXPIRY_MONTH_PATTERN)) {
                fail("creditCard.expiryMonth", "must be 2 digits between 01 and 12")
            }
            if (!card.expiryYear.matches(FOUR_DIGITS_PATTERN)) fail("creditCard.expiryYear", "must have 4 digits")
            if (!card.ccv.matches(CCV_PATTERN)) fail("creditCard.ccv", "must have 3 or 4 digits")
        }

        val holder = command.creditCardHolderInfo
        if (holder == null) {
            fail("creditCardHolderInfo", "is required for CREDIT_CARD billing")
        } else {
            if (holder.name.isBlank()) fail("creditCardHolderInfo.name", "is required")
            if (!isValidEmail(holder.email.trim())) fail("creditCardHolderInfo.email", "must be a valid email")
            if (!isValidCpfCnpj(holder.cpfCnpj.filter { it.isDigit() })) {
                fail("creditCardHolderInfo.cpfCnpj", "must have 11 (CPF) or 14 (CNPJ) digits")
            }
            if (holder.postalCode.filter { it.isDigit() }.length != 8) {
                fail("creditCardHolderInfo.postalCode", "must have 8 digits")
            }
            if (holder.addressNumber.isBlank()) fail("creditCardHolderInfo.addressNumber", "is required")
            if (holder.phone.filter { it.isDigit() }.length < 10) {
                fail("creditCardHolderInfo.phone", "must be a valid phone number")
            }
        }

        if (command.remoteIp.isNullOrBlank()) fail("remoteIp", "is required for CREDIT_CARD billing")

        return errors
    }

    private companion object {
        val EXPIRY_MONTH_PATTERN = Regex("^(0[1-9]|1[0-2])$")
        val FOUR_DIGITS_PATTERN = Regex("^\\d{4}$")
        val CCV_PATTERN = Regex("^\\d{3,4}$")
    }
}
