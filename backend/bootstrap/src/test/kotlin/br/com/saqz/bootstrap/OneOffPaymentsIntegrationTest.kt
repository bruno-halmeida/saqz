package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.finance.*
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcGroupAdministrationDirectory
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.charge.ChargeManagement
import br.com.saqz.groups.application.finance.charge.ChargeStatusResult
import br.com.saqz.groups.domain.finance.charge.ChargeStatus
import br.com.saqz.groups.domain.finance.charge.ChargeStatusCommand
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.input.http.*
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasPayments
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class OneOffPaymentsIntegrationTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val mapper = ObjectMapper()
    private val payerData = PaymentPayer("Pagador de teste", "12345678909")

    @Test fun `PIX CARD and both preserve approved methods and real monthly competence`() {
        for (methods in listOf(setOf(PaymentMethod.PIX), setOf(PaymentMethod.CARD), PaymentMethod.entries.toSet())) fixture(methods) { f ->
            val review = f.review()
            assertEquals(methods, review.quotes.map { it.method }.toSet())
            assertEquals("2026-08-01", review.billingMonth.toString())
            val order = f.approve(review)
            assertEquals("2026-08-01", f.string("SELECT billing_month::text FROM receivable_orders"))
            assertEquals(order.id, f.approve(review).id)
            assertEquals(1, f.count("receivable_orders"))
            if (methods.size == 1) assertEquals(FinancialError.INVALID_INPUT,
                assertIs<FinancialResult.Failure>(f.service.instrument(order.id, f.payerRequest(), PaymentMethod.entries.first { it !in methods }, order.fingerprint, true, payerData)).error)
            assertEquals(FinancialError.INVALID_INPUT,
                assertIs<FinancialResult.Failure>(f.service.instrument(order.id, f.payerRequest(), methods.first(), order.fingerprint, false, payerData)).error)
            val instrument = f.instrument(order, methods.first())
            assertEquals("ACTIVE", instrument.status)
            if (methods.first() == PaymentMethod.PIX) assertEquals("pix-copy", instrument.pixPayload)
            else assertEquals("https://asaas.com/i/pay_local", instrument.checkoutUrl)
            assertEquals("106.58", f.remote.lastPayment!!["value"].asText())
            assertEquals(0, java.math.BigDecimal("3.00").compareTo(f.remote.lastPayment!!["split"][0]["fixedValue"].decimalValue()))
            assertFalse(f.remote.lastPayment!!.has("creditCard"))
            assertFalse(f.remote.lastPayment!!.has("installmentCount"))
            assertEquals(if (methods.first() == PaymentMethod.PIX) "PIX" else "CREDIT_CARD", f.remote.lastPayment!!["billingType"].asText())
            assertFalse(f.string("SELECT payload_encrypted FROM receivable_instruments").contains("pix-copy"))
            assertFalse(f.string("SELECT payer_data_encrypted FROM receivable_customers").contains(payerData.cpfCnpj))
        }
    }

    @Test fun `snapshots survive methods changed OFF cutoff payer exit owner transfer and soft deletion`() = fixture { f ->
        val order = f.approve()
        f.sql("UPDATE receivable_group_links SET pix_enabled=false,card_enabled=true,enabled=false")
        f.sql("UPDATE receivable_rollout SET backend_mode='OFF'")
        f.eligible = false
        f.sql("DELETE FROM group_memberships WHERE user_id='${f.payer}'")
        f.sql("UPDATE access_groups SET owner_user_id='${f.payer}',deleted_at=now()")
        assertIs<FinancialResult.Success<*>>(f.service.get(order.id, f.request()))
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.service.get(order.id, FinancialRequest(UUID.randomUUID(), UUID.randomUUID()))).error)
        val instrument = f.instrument(order)
        assertEquals(PaymentMethod.PIX, instrument.quote.method)
        f.remote.paymentStatus = "RECEIVED"
        f.execution.reconcile(instrument.id)
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges"))
        assertEquals(1, f.count("group_charge_payment_effects"))
    }

    @Test fun `owner transfer prohibits new business from the previous financial owner`() = fixture { f ->
        f.sql("UPDATE access_groups SET owner_user_id='${f.payer}'")
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.service.preview(f.charge, f.account, f.request())).error)
    }

    @Test fun `customer and payment timeout recover without another POST or concurrent instrument`() = fixture { f ->
        val order = f.approve()
        f.remote.disconnectCustomer = true
        val request = f.payerRequest()
        val first = assertIs<FinancialResult.Success<PaymentInstrument>>(f.service.instrument(order.id, request, PaymentMethod.PIX, order.fingerprint, true, payerData)).value
        assertEquals("UNKNOWN", first.status)
        assertEquals(1, f.remote.customerPosts)
        assertEquals(0, f.remote.paymentPosts)
        f.remote.disconnectCustomer = false; f.remote.disconnectPayment = true
        f.execution.create(first.id)
        assertEquals(1, f.remote.customerPosts)
        assertEquals(1, f.remote.paymentPosts)
        assertEquals("UNKNOWN", f.store.instrument(first.id)!!.status)
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(f.service.instrument(order.id, f.payerRequest(), PaymentMethod.CARD, order.fingerprint, true, payerData)).error)
        f.remote.disconnectPayment = false
        f.execution.reconcile(first.id)
        assertEquals("ACTIVE", f.store.instrument(first.id)!!.status)
        assertEquals(1, f.remote.paymentPosts)
        assertEquals(first.id, assertIs<FinancialResult.Success<PaymentInstrument>>(f.service.instrument(order.id, request, PaymentMethod.PIX, order.fingerprint, true, payerData)).value.id)
        assertEquals(1, f.count("receivable_instruments"))
    }

    @Test fun `webhook ciphertext precedes ACK token isolation duplicate inverted events cash once and reversal no reopened debt`() = fixture { f ->
        val i = f.instrument(f.approve())
        val body = """{"id":"evt_received","event":"PAYMENT_RECEIVED","account":{"id":"remote-account"},"payment":{"id":"pay_local","externalReference":"${i.id}"}}"""
        assertFalse(f.events.accept(f.account, "wrong", body))
        assertFalse(f.events.accept(UUID.randomUUID(), f.token, body))
        assertFalse(f.events.accept(f.account, f.token, body.replace("remote-account", "other-account")))
        assertEquals(0, f.count("receivable_provider_events"))
        assertTrue(f.events.accept(f.account, f.token, body))
        assertTrue(f.events.accept(f.account, f.token, body))
        assertEquals(1, f.count("receivable_provider_events"))
        assertEquals("PENDING", f.string("SELECT status::text FROM group_charges"))
        assertFalse(f.string("SELECT payload_encrypted FROM receivable_provider_events").contains("evt_received"))
        f.remote.paymentStatus = "RECEIVED"; f.events.processPending()
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges"))
        assertTrue(f.store.instrument(i.id)!!.settled)
        assertTrue(f.store.instrument(i.id)!!.available)
        assertFalse(f.store.instrument(i.id)!!.splitSettled)
        f.remote.paymentStatus = "CONFIRMED"
        f.events.accept(f.account, f.token, body.replace("evt_received", "evt_old")); f.events.processPending()
        assertEquals("AVAILABLE", f.store.instrument(i.id)!!.status)
        f.execution.reconcile(i.id)
        assertEquals(1, f.count("group_charge_payment_effects"))
        assertEquals(1, f.count("receivable_cash_effects"))
        f.remote.paymentStatus = "REFUNDED"; f.execution.reconcile(i.id); f.execution.reconcile(i.id)
        assertEquals("CANCELLED", f.string("SELECT status::text FROM group_charges"))
        assertEquals(2, f.count("group_charge_payment_effects"))
        assertEquals(2, f.count("receivable_cash_effects"))
        assertEquals("REFUNDED", f.store.order(i.orderId)!!.status)
    }

    @Test fun `amount mismatch creates occurrence and callback never marks a charge paid`() = fixture { f ->
        val i = f.instrument(f.approve())
        assertEquals("PENDING", f.string("SELECT status::text FROM group_charges"))
        f.remote.paymentStatus = "RECEIVED"; f.remote.wrongAmount = true
        f.execution.reconcile(i.id)
        assertEquals("PENDING", f.string("SELECT status::text FROM group_charges"))
        assertEquals(1, f.count("receivable_payment_occurrences"))
        assertEquals(0, f.count("receivable_cash_effects"))
    }

    @Test fun `payment versus manual settlement is serialized and cancellation releases only after remote proof`() = fixture { f ->
        val order = f.approve(); val i = f.instrument(order)
        Executors.newFixedThreadPool(2).use { pool ->
            val tasks = listOf(Callable { f.remote.paymentStatus = "RECEIVED"; f.execution.reconcile(i.id); true },
                Callable { f.manualStatus() == ChargeStatusResult.Conflict })
            assertTrue(pool.invokeAll(tasks).all { it.get() })
        }
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges"))
        assertEquals(1, f.count("group_charge_payment_effects"))
    }

    @Test fun `remote cancellation closes instrument before manual settlement can proceed`() = fixture { f ->
        val order = f.approve(); f.instrument(order)
        assertEquals(ChargeStatusResult.Conflict, f.manualStatus())
        val cancelled = assertIs<FinancialResult.Success<PaymentOrderDetail>>(f.service.cancel(order.id, f.request())).value
        assertEquals("CANCELLED", cancelled.order.status)
        assertEquals("CANCELLED", cancelled.instruments.single().status)
        assertIs<ChargeStatusResult.Success>(f.manualStatus())
    }

    @Test fun `QR outage cannot block cancellation of authenticated existing Pix`() = fixture { f ->
        val order = f.approve(); f.instrument(order)
        val qrRequests = f.remote.qrRequests
        f.remote.qrDown = true
        val result = assertIs<FinancialResult.Success<PaymentOrderDetail>>(f.service.cancel(order.id, f.request())).value
        assertEquals("CANCELLED", result.order.status)
        assertEquals(listOf("/v3/payments/pay_local"), f.remote.deletePaths)
        assertEquals(qrRequests, f.remote.qrRequests)
        assertEquals(1, f.remote.paymentPosts)
        assertIs<ChargeStatusResult.Success>(f.manualStatus())
    }

    @Test fun `QR outage preserves creation facts and uncertain cancellation recovers same payment`() = fixture { f ->
        f.remote.qrDown = true
        val order = f.approve(); val i = f.instrument(order)
        assertEquals("ACTIVE", i.status)
        assertEquals("pay_local", i.paymentId)
        assertNull(i.pixPayload)
        f.remote.disconnectDelete = true
        assertEquals("CANCEL_PENDING", assertIs<FinancialResult.Success<PaymentOrderDetail>>(f.service.cancel(order.id, f.request())).value.order.status)
        assertEquals(ChargeStatusResult.Conflict, f.manualStatus())
        f.remote.disconnectDelete = false
        f.sql("UPDATE receivable_operations SET next_attempt_at='-infinity' WHERE kind='CANCEL_INSTRUMENT'")
        assertTrue(f.execution.reconcile(i.id))
        assertEquals("CANCELLED", f.store.order(order.id)!!.status)
        assertTrue(f.remote.deletePaths.all { it == "/v3/payments/pay_local" })
        assertEquals(1, f.remote.paymentPosts)
        assertEquals(1, f.count("receivable_instruments"))
        assertIs<ChargeStatusResult.Success>(f.manualStatus())
    }

    @Test fun `cancellation validates payment identity method and amount before DELETE despite QR outage`() {
        for (mismatch in listOf("id", "externalReference", "billingType", "value")) fixture { f ->
            val order = f.approve(); f.instrument(order)
            f.remote.qrDown = true
            f.remote.paymentMismatch = mismatch
            val result = assertIs<FinancialResult.Success<PaymentOrderDetail>>(f.service.cancel(order.id, f.request())).value
            assertEquals("CANCEL_PENDING", result.order.status, mismatch)
            assertTrue(f.remote.deletePaths.isEmpty(), mismatch)
            assertEquals(1, f.remote.paymentPosts, mismatch)
            assertEquals(ChargeStatusResult.Conflict, f.manualStatus(), mismatch)
        }
    }

    @Test fun `first observation refunded never credits commission without effective debit`() = fixture { f ->
        val i = f.instrument(f.approve(), PaymentMethod.CARD)
        f.remote.paymentStatus = "REFUNDED"; f.remote.splitStatus = "REFUNDED"; f.remote.returnedCommissionCents = 300
        repeat(3) { assertTrue(f.execution.reconcile(i.id)) }
        assertEquals("REFUNDED", f.store.order(i.orderId)!!.status)
        assertEquals(0L, f.commissionBalance())
        assertEquals(0, f.jdbc.sql("SELECT count(*) FROM receivable_movements WHERE kind='COMMISSION'").query(Int::class.java).single())
        assertEquals(1, f.jdbc.sql("SELECT count(*) FROM receivable_payment_occurrences WHERE code='REVERSAL_SPLIT_PENDING'").query(Int::class.java).single())
        assertEquals(2, f.count("group_charge_payment_effects"))
        assertEquals(2, f.count("receivable_cash_effects"))
    }

    @Test fun `proven settled commission is returned once and delayed settlement evidence also conserves balance`() {
        for (refundFirst in listOf(false, true)) fixture { f ->
            val i = f.instrument(f.approve(), PaymentMethod.CARD)
            if (refundFirst) {
                f.remote.paymentStatus = "REFUNDED"; f.remote.splitStatus = "REFUNDED"; f.remote.returnedCommissionCents = 300
                assertTrue(f.execution.reconcile(i.id))
                assertEquals(0L, f.commissionBalance())
            }
            f.remote.paymentStatus = "RECEIVED"; f.remote.splitStatus = "DONE"; f.remote.returnedCommissionCents = null
            assertTrue(f.execution.reconcile(i.id))
            assertEquals(-300L, f.commissionBalance())
            f.remote.paymentStatus = "REFUNDED"; f.remote.splitStatus = "REFUNDED"; f.remote.returnedCommissionCents = 300
            repeat(3) { assertTrue(f.execution.reconcile(i.id)) }
            assertEquals(0L, f.commissionBalance())
            assertEquals(2, f.jdbc.sql("SELECT count(*) FROM receivable_movements WHERE kind='COMMISSION'").query(Int::class.java).single())
            assertEquals("REFUNDED", f.store.order(i.orderId)!!.status)
            assertEquals(2, f.count("group_charge_payment_effects"))
        }
    }

    @Test fun `commission return exceeding effective debit remains pending without fabricated credit`() = fixture { f ->
        val i = f.instrument(f.approve(), PaymentMethod.CARD)
        f.remote.paymentStatus = "RECEIVED"; f.remote.splitStatus = "DONE"
        assertTrue(f.execution.reconcile(i.id))
        f.remote.paymentStatus = "REFUNDED"; f.remote.splitStatus = "REFUNDED"; f.remote.returnedCommissionCents = 301
        repeat(2) { assertTrue(f.execution.reconcile(i.id)) }
        assertEquals(-300L, f.commissionBalance())
        assertEquals(1, f.jdbc.sql("SELECT count(*) FROM receivable_payment_occurrences WHERE code='REVERSAL_SPLIT_PENDING'").query(Int::class.java).single())
        f.remote.returnedCommissionCents = 300
        assertTrue(f.execution.reconcile(i.id))
        assertEquals(0L, f.commissionBalance())
    }

    @Test fun `HTTP requires stable request IDs payer acceptance and hides foreign order`() = fixture { f ->
        var actor = f.owner
        val resolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, request: NativeWebRequest,
                binder: WebDataBinderFactory?) = RequestIdentity("subject", "request")
        }
        val mvc = MockMvcBuilders.standaloneSetup(OneOffPaymentsController(FinancialActorResolver { actor }, f.service), PaymentWebhookController(f.events))
            .setCustomArgumentResolvers(resolver).build()
        val path = "/api/receivables/charges/${f.charge}/preview"
        assertEquals(400, mvc.perform(post(path).contentType("application/json").content("""{"accountId":"${f.account}"}""")).andReturn().response.status)
        val preview = mvc.perform(post(path).contentType("application/json").content("""{"requestId":"${UUID.randomUUID()}","accountId":"${f.account}"}""")).andReturn().response
        assertEquals(200, preview.status)
        assertEquals("no-store", preview.getHeader("Cache-Control"))
        val order = f.approve()
        actor = UUID.randomUUID()
        assertEquals(404, mvc.perform(get("/api/receivables/orders/${order.id}")).andReturn().response.status)
        actor = f.payer
        val response = mvc.perform(post("/api/receivables/orders/${order.id}/instruments").contentType("application/json")
            .content("""{"requestId":"${UUID.randomUUID()}","method":"PIX","fingerprint":"${order.fingerprint}","accepted":false,"payer":{"name":"Teste","cpfCnpj":"12345678909"}}""")).andReturn().response
        assertEquals(400, response.status)
        assertEquals(0, f.count("receivable_instruments"))
    }

    @Test fun `definitive customer rejection allows corrected payer data without creating a duplicate payment`() = fixture { f ->
        val order = f.approve()
        f.remote.rejectCustomer = true
        val rejected = f.instrument(order)
        assertEquals("CANCELLED", rejected.status)
        assertEquals(0, f.remote.paymentPosts)
        f.remote.rejectCustomer = false
        val corrected = assertIs<FinancialResult.Success<PaymentInstrument>>(f.service.instrument(order.id, f.payerRequest(), PaymentMethod.PIX,
            order.fingerprint, true, payerData.copy(name = "Nome corrigido"))).value
        assertEquals("ACTIVE", corrected.status)
        assertEquals(1, f.remote.paymentPosts)
    }

    @Test fun `webhook registration persists secret before timeout and recovers without another POST`() = fixture { f ->
        f.sql("DELETE FROM receivable_webhook_credentials")
        val registration = JdbcPaymentWebhookRegistration(f.ds, f.store, f.secrets, f.provider, f.clock)
        f.remote.disconnectWebhook = true
        val request = f.request()
        assertEquals("RESULT_PENDING", assertIs<FinancialResult.Success<String>>(registration.configure(f.account, request)).value)
        val cipher = f.string("SELECT token_encrypted FROM receivable_webhook_credentials")
        assertFalse(cipher.contains(f.remote.webhook!!["authToken"].asText()))
        f.remote.disconnectWebhook = false
        assertEquals("CONFIGURED", assertIs<FinancialResult.Success<String>>(registration.configure(f.account, request)).value)
        assertEquals(1, f.remote.webhookPosts)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(registration.configure(f.account, f.payerRequest())).error)
    }

    @Test fun `failed provider query keeps existing payment webhook pending and poisoned first batch cannot starve later events`() = fixture { f ->
        val i = f.instrument(f.approve())
        for (n in 1..51) f.events.accept(f.account, f.token,
            """{"id":"bad_$n","event":"PAYMENT_RECEIVED","payment":{"id":"missing"}}""")
        f.events.accept(f.account, f.token,
            """{"id":"good","event":"PAYMENT_RECEIVED","payment":{"id":"pay_local","externalReference":"${i.id}"}}""")
        f.events.processPending()
        f.remote.providerDown = true; f.events.processPending()
        assertEquals(0, f.jdbc.sql("SELECT count(*) FROM receivable_provider_events WHERE processed_at IS NOT NULL").query(Int::class.java).single())
        f.remote.providerDown = false; f.remote.paymentStatus = "RECEIVED"
        f.sql("UPDATE receivable_provider_events SET next_attempt_at='-infinity' WHERE provider_event_id='good'")
        f.events.processPending()
        assertEquals(1, f.jdbc.sql("SELECT count(*) FROM receivable_provider_events WHERE processed_at IS NOT NULL").query(Int::class.java).single())
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges"))
    }

    @Test fun `uncertain cancellation retries same DELETE only after authenticated query and retains original actor`() = fixture { f ->
        val order = f.approve(); f.instrument(order)
        f.sql("UPDATE group_memberships SET role='ADMIN' WHERE user_id='${f.payer}'")
        val acceptance = UUID.randomUUID()
        f.sql("INSERT INTO receivable_terms_acceptances VALUES ('$acceptance','${f.account}','${f.payer}','v1','DELEGATION','${UUID.randomUUID()}',now())")
        f.sql("INSERT INTO receivable_delegations VALUES ('${f.account}','${f.payer}','${f.owner}','$acceptance',now(),NULL)")
        f.remote.disconnectDelete = true
        assertEquals("CANCEL_PENDING", assertIs<FinancialResult.Success<PaymentOrderDetail>>(f.service.cancel(order.id, f.payerRequest())).value.order.status)
        assertEquals(ChargeStatusResult.Conflict, f.manualStatus())
        f.remote.disconnectDelete = false
        f.sql("UPDATE receivable_operations SET next_attempt_at='-infinity' WHERE kind='CANCEL_INSTRUMENT'")
        f.execution.reconcile(f.store.instruments(order.id).single().id)
        assertEquals("CANCELLED", f.store.order(order.id)!!.status)
        assertEquals(1, f.remote.paymentPosts)
        assertEquals(f.payer.toString(), f.string("SELECT actor_user_id::text FROM receivable_operations WHERE kind='CANCEL_INSTRUMENT'"))
    }

    @Test fun `risk analysis never permits cancellation or concurrent instrument`() = fixture { f ->
        val order = f.approve(); val i = f.instrument(order)
        f.remote.paymentStatus = "AWAITING_RISK_ANALYSIS"
        f.execution.reconcile(i.id)
        assertEquals("UNKNOWN", f.store.instrument(i.id)!!.status)
        f.service.cancel(order.id, f.request())
        assertFalse(f.remote.deleted)
        assertEquals("CANCEL_PENDING", f.store.order(order.id)!!.status)
        assertEquals(ChargeStatusResult.Conflict, f.manualStatus())
    }

    @Test fun `game cancellation fences live electronic debt and recovery closes it before manual write`() = fixture { f ->
        val game = UUID.randomUUID()
        f.sql("""INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at)
            VALUES ('$game','${f.group}','Treino','2026-09-20','19:30','UTC','2026-09-20 19:30Z',90,'2026-09-19 19:30Z','Arena','Rua Central 100',24,10000,'PUBLISHED',now(),now())""")
        f.sql("UPDATE group_charges SET kind='GAME',billing_month=NULL,game_id='$game'")
        val order = f.approve(); val i = f.instrument(order)
        val repository = JdbcChargeTransactionRepository(f.ds, JdbcGroupPaymentCancellation(f.ds, f.charges))
        JdbcTransactionRunner(f.ds).inTransaction {
            f.sql("UPDATE games SET status='CANCELLED' WHERE id='$game'")
            repository.reconcileGameCancellation(f.group, game, f.owner, now)
        }
        assertEquals("PENDING", f.string("SELECT status::text FROM group_charges"))
        assertEquals("CANCEL_PENDING", f.store.order(order.id)!!.status)
        assertTrue(f.jdbc.sql("SELECT review_required FROM group_charges").query(Boolean::class.java).single())
        f.execution.reconcile(i.id)
        assertEquals("CANCELLED", f.store.order(order.id)!!.status)
        assertEquals("CANCELLED", f.string("SELECT status::text FROM group_charges"))
        assertEquals(1, f.remote.paymentPosts)
    }

    @Test fun `batch recovery visits newer instruments after fifty old failures`() = fixture { f ->
        val order = f.approve(); val i = f.instrument(order)
        repeat(51) {
            val oid = UUID.randomUUID(); val iid = UUID.randomUUID()
            f.sql("""INSERT INTO receivable_orders(id,account_id,group_id,member_user_id,group_charge_id,due_date,status,base_cents,request_id,issued_at)
                SELECT '$oid',account_id,group_id,member_user_id,'${UUID.randomUUID()}',due_date,'ISSUED',base_cents,'${UUID.randomUUID()}',issued_at FROM receivable_orders WHERE id='${order.id}'""")
            f.sql("""INSERT INTO receivable_instruments(id,account_id,order_id,method,fee_schedule_id,base_cents,fees_cents,total_cents,commission_cents,expected_provider_fee_cents,expected_net_cents,acceptance_id,status,created_at)
                SELECT '$iid',account_id,'$oid',method,fee_schedule_id,base_cents,fees_cents,total_cents,commission_cents,expected_provider_fee_cents,expected_net_cents,acceptance_id,'ACTIVE',created_at FROM receivable_instruments WHERE id='${i.id}'""")
        }
        val seen = mutableSetOf<UUID>()
        val execution = object : PaymentExecution {
            override fun create(instrumentId: UUID) {}
            override fun cancel(instrumentId: UUID, request: FinancialRequest) {}
            override fun reconcile(instrumentId: UUID): Boolean { seen += instrumentId; return false }
        }
        val events = JdbcPaymentEvents(f.ds, f.secrets, execution, f.clock)
        events.recoverPending(); events.recoverPending()
        assertEquals(52, seen.size)
    }

    @Test fun `game cancellation before instrument closes order and releases reservation atomically`() = fixture { f ->
        val order = f.approve()
        JdbcTransactionRunner(f.ds).inTransaction { JdbcGroupPaymentCancellation(f.ds, f.charges).request(order.id, f.owner, now) }
        assertEquals("CANCELLED", f.store.order(order.id)!!.status)
        assertEquals(0, f.jdbc.sql("SELECT count(*) FROM group_charges WHERE electronic_order_id IS NOT NULL").query(Int::class.java).single())
        assertEquals(0, f.remote.paymentPosts)
    }

    @Test fun `won dispute returns to available without false refund or duplicate cash`() = fixture { f ->
        val i = f.instrument(f.approve(), PaymentMethod.CARD)
        f.remote.paymentStatus = "RECEIVED"; f.execution.reconcile(i.id)
        for ((remote, local) in listOf("CHARGEBACK_REQUESTED" to "DISPUTED", "CHARGEBACK_DISPUTE" to "DISPUTED",
            "AWAITING_CHARGEBACK_REVERSAL" to "RECOVERY_PENDING", "CONFIRMED" to "CONFIRMED", "RECEIVED" to "AVAILABLE")) {
            f.remote.paymentStatus = remote; f.execution.reconcile(i.id)
            assertEquals(local, f.store.instrument(i.id)!!.status)
            assertEquals("PAID", f.string("SELECT status::text FROM group_charges"))
        }
        assertEquals(1, f.count("group_charge_payment_effects"))
        assertEquals(0, f.jdbc.sql("SELECT count(*) FROM receivable_movements WHERE kind IN ('REFUND','CHARGEBACK')").query(Int::class.java).single())
    }

    @Test fun `authenticated malformed webhook returns400 and persists nothing`() = fixture { f ->
        val mvc = MockMvcBuilders.standaloneSetup(PaymentWebhookController(f.events)).build()
        assertEquals(400, mvc.perform(post("/api/receivables/webhooks/asaas/${f.account}")
            .header("asaas-access-token", f.token).contentType("application/json").content("{")).andReturn().response.status)
        assertEquals(0, f.count("receivable_provider_events"))
    }

    @Test fun `payment bootstrap composes provider controllers and recovery only when deployment enabled`() = fixture { f ->
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use { context ->
            context.environment.propertySources.addFirst(org.springframework.core.env.MapPropertySource("payment-test", mapOf(
                "saqz.receivables.payments-enabled" to "true", "saqz.receivables.asaas-base-url" to "http://127.0.0.1:${f.remote.server.address.port}/v3",
                "saqz.receivables.platform-wallet-id" to "wallet-platform", "saqz.receivables.webhook-base-url" to "https://saqz.test",
                "saqz.receivables.webhook-email" to "ops@saqz.test")))
            val beans = mapOf("dataSource" to f.ds, "financialSecrets" to f.secrets, "financialOperationStore" to JdbcFinancialOperationStore(f.ds),
                "financialAccounts" to f.accounts, "groupReceivables" to JdbcGroupReceivablesStore(f.ds),
                "groupAdministrators" to JdbcGroupAdministrationDirectory(f.ds), "conditions" to JdbcFinancialConditions(f.ds),
                "eligibility" to ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) },
                "rollout" to JdbcReceivablesRollout(f.ds, { true }, f.clock),
                "clock" to f.clock, "actors" to br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver { f.owner })
            beans.forEach { (name, bean) -> context.beanFactory.registerSingleton(name, bean) }
            context.register(br.com.saqz.bootstrap.configuration.OneOffPaymentsConfiguration::class.java)
            context.refresh()
            assertNotNull(context.getBean(OneOffPaymentsController::class.java))
            assertNotNull(context.getBean(PaymentWebhookController::class.java))
            assertNotNull(context.getBean(PaymentWebhookSetupController::class.java))
            assertNotNull(context.getBean(br.com.saqz.bootstrap.configuration.PaymentRecovery::class.java))
            assertEquals(0, f.remote.paymentPosts)
        }
    }

    @Test fun `concurrent instrument requests create exactly one remote payment`() = fixture { f ->
        val order = f.approve()
        val results = Executors.newFixedThreadPool(2).use { pool ->
            pool.invokeAll(listOf(PaymentMethod.PIX, PaymentMethod.CARD).map { method -> Callable {
                f.service.instrument(order.id, f.payerRequest(), method, order.fingerprint, true, payerData)
            } }).map { it.get() }
        }
        assertEquals(1, results.count { it is FinancialResult.Success })
        assertEquals(1, results.count { it is FinancialResult.Failure && it.error == FinancialError.CONFLICT })
        assertEquals(1, f.remote.customerPosts)
        assertEquals(1, f.remote.paymentPosts)
        assertEquals(1, f.count("receivable_instruments"))
    }

    @Test fun `definitive webhook rejection can be corrected without treating empty lookup as rejection`() = fixture { f ->
        f.sql("DELETE FROM receivable_webhook_credentials")
        val registration = JdbcPaymentWebhookRegistration(f.ds, f.store, f.secrets, f.provider, f.clock)
        f.remote.rejectWebhook = true
        assertEquals(FinancialError.CONFIGURATION_UNAVAILABLE, assertIs<FinancialResult.Failure>(registration.configure(f.account, f.request())).error)
        assertEquals("REJECTED", f.string("SELECT state FROM receivable_webhook_credentials"))
        f.remote.rejectWebhook = false
        assertEquals("CONFIGURED", assertIs<FinancialResult.Success<String>>(registration.configure(f.account, f.request())).value)
        assertEquals(2, f.remote.webhookPosts)
    }

    private fun fixture(methods: Set<PaymentMethod> = PaymentMethod.entries.toSet(), block: (Fixture) -> Unit) {
        Remote().use { remote -> block(Fixture(remote, methods)) }
    }
    private inner class Fixture(val remote: Remote, methods: Set<PaymentMethod>) {
        val ds = TestPostgres.migrated("classpath:db/migration", owner = this@OneOffPaymentsIntegrationTest).dataSource
        val jdbc = JdbcClient.create(ds)
        val owner = UUID.randomUUID(); val payer = UUID.randomUUID(); val group = UUID.randomUUID(); val account = UUID.randomUUID(); val charge = UUID.randomUUID()
        val token = "test-webhook-token-" + "a".repeat(32)
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val accounts = JdbcFinancialAccountRepository(ds)
        val store = JdbcPaymentStore(ds, secrets)
        val charges = JdbcGroupChargePayments(ds)
        val provider = HttpAsaasPayments(URI("http://127.0.0.1:${remote.server.address.port}/v3"), "wallet-platform", JdbcPaymentProviderCredentials(ds, secrets), "https://saqz.test", "ops@saqz.test")
        val execution = JdbcPaymentExecution(ds, store, charges, JdbcFinancialOperationStore(ds), provider, secrets, clock)
        val events = JdbcPaymentEvents(ds, secrets, execution, clock)
        var eligible = true
        val service = OneOffPayments(store, charges, accounts, JdbcGroupReceivablesStore(ds), JdbcGroupAdministrationDirectory(ds),
            JdbcFinancialConditions(ds), ReceivablesEligibility { _, _ -> ReceivablesEntitlement(eligible, null) },
            JdbcReceivablesRollout(ds, { true }, clock), execution, clock)
        init {
            for (id in listOf(owner, payer)) sql("INSERT INTO access_users(id,firebase_subject,email_verified,created_at,updated_at) VALUES ('$id','$id',true,now(),now())")
            sql("INSERT INTO access_groups(id,owner_user_id,creation_key,name,time_zone,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Test','UTC',now(),now())")
            sql("INSERT INTO group_memberships(group_id,user_id,role,created_at,updated_at) VALUES ('$group','$payer','ATHLETE',now(),now())")
            sql("""INSERT INTO group_charges(id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status,member_display_name,created_by_user_id,changed_by_user_id,created_at,updated_at)
                VALUES ('$charge','$group','$payer','MONTHLY','2026-08-01',10000,'2026-09-20','PENDING','Pagador','$owner','$owner',now(),now())""")
            sql("""INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,provider_account_id,credentials_encrypted,registration,new_operations_enabled,created_at,updated_at)
                VALUES ('$account','$owner','$owner','ciphertext','remote-account','${secrets.encrypt(account, "provider-key", "local-key")}','APPROVED',true,now(),now())""")
            sql("INSERT INTO receivable_webhook_credentials(account_id,token_encrypted) VALUES ('$account','${secrets.encrypt(account, "webhook-token", token)}')")
            sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'")
            sql("INSERT INTO receivable_group_links VALUES ('$account','$group',true,${PaymentMethod.PIX in methods},${PaymentMethod.CARD in methods},'$owner',now(),NULL)")
            sql("INSERT INTO receivable_terms VALUES ('v1','Test terms','${"a".repeat(64)}','2026-09-01','2026-09-01')")
            for (method in PaymentMethod.entries) sql("INSERT INTO receivable_fee_schedules VALUES ('${UUID.randomUUID()}','$method',0.0299,39,0.02,100,'v1','2026-09-01','2026-09-01','$owner')")
        }
        fun request() = FinancialRequest(UUID.randomUUID(), owner)
        fun payerRequest() = FinancialRequest(UUID.randomUUID(), payer)
        fun review() = assertIs<FinancialResult.Success<ChargePaymentReview>>(service.preview(charge, account, request())).value
        fun approve(review: ChargePaymentReview = review()) = assertIs<FinancialResult.Success<PaymentOrder>>(service.approve(charge, account, request(), review.fingerprint, true)).value
        fun instrument(order: PaymentOrder, method: PaymentMethod = PaymentMethod.PIX) = assertIs<FinancialResult.Success<PaymentInstrument>>(service.instrument(order.id, payerRequest(), method, order.fingerprint, true, payerData)).value
        fun manualStatus() = ChargeManagement(JdbcTransactionRunner(ds), JdbcChargeManagementRepository(ds), { now }, UUID::randomUUID)
            .status(owner, group, charge, 1, ChargeStatusCommand(ChargeStatus.PAID))
        fun sql(s: String) { jdbc.sql(s).update() }
        fun commissionBalance() = jdbc.sql("SELECT coalesce(sum(amount_cents),0) FROM receivable_movements WHERE kind='COMMISSION'").query(Long::class.java).single()
        fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Int::class.java).single()
        fun string(s: String) = jdbc.sql(s).query(String::class.java).single()
    }

    private inner class Remote : AutoCloseable {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var customerPosts = 0; var paymentPosts = 0
        var disconnectCustomer = false; var disconnectPayment = false; var wrongAmount = false
        var disconnectDelete = false; var rejectCustomer = false; var providerDown = false
        var rejectWebhook = false
        var qrDown = false; var qrRequests = 0
        var splitStatus = "PENDING"; var returnedCommissionCents: Long? = null
        var paymentMismatch: String? = null
        val deletePaths = mutableListOf<String>()
        var webhookPosts = 0; var webhook: com.fasterxml.jackson.databind.JsonNode? = null; var disconnectWebhook = false
        var customer: com.fasterxml.jackson.databind.JsonNode? = null
        var lastPayment: com.fasterxml.jackson.databind.JsonNode? = null
        var paymentStatus = "PENDING"; var deleted = false
        init {
            server.createContext("/v3") { exchange ->
                check(exchange.requestHeaders.getFirst("access_token") == "local-key")
                val path = exchange.requestURI.path; val method = exchange.requestMethod
                if (path.endsWith("/pixQrCode")) qrRequests++
                if (providerDown || (qrDown && path.endsWith("/pixQrCode"))) { exchange.sendResponseHeaders(503, -1); exchange.close(); return@createContext }
                val response: Any = when {
                    path == "/v3/webhooks" && method == "POST" -> {
                        webhookPosts++; webhook = mapper.readTree(exchange.requestBody.readBytes())
                        if (rejectWebhook) {
                            val bytes = """{"errors":[{"code":"invalid_email"}]}""".toByteArray()
                            exchange.sendResponseHeaders(400, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }; return@createContext
                        }
                        if (disconnectWebhook) { exchange.close(); return@createContext }
                        mapOf("id" to "webhook_local")
                    }
                    path == "/v3/webhooks" -> mapOf("hasMore" to false, "data" to listOfNotNull(webhook?.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()?.put("id", "webhook_local")?.put("hasAuthToken", true)))
                    path == "/v3/customers" && method == "POST" -> {
                        customerPosts++; customer = mapper.readTree(exchange.requestBody.readBytes())
                        if (rejectCustomer) {
                            val bytes = """{"errors":[{"code":"invalid_cpfCnpj"}]}""".toByteArray()
                            exchange.sendResponseHeaders(400, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }; return@createContext
                        }
                        if (disconnectCustomer) { exchange.close(); return@createContext }
                        customer!!.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("id", "cus_local")
                    }
                    path == "/v3/customers" -> mapOf("hasMore" to false, "data" to listOfNotNull(customer?.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()?.put("id", "cus_local")))
                    path == "/v3/payments" && method == "POST" -> {
                        paymentPosts++; lastPayment = mapper.readTree(exchange.requestBody.readBytes())
                        if (disconnectPayment) { exchange.close(); return@createContext }
                        payment()
                    }
                    path == "/v3/payments" -> mapOf("hasMore" to false, "data" to if (lastPayment == null) emptyList<Any>() else listOf(payment()))
                    path.endsWith("/pixQrCode") -> mapOf("payload" to "pix-copy", "encodedImage" to "cGl4", "expirationDate" to "2026-09-20 23:59:59")
                    method == "DELETE" -> {
                        deletePaths += path
                        if (disconnectDelete) { exchange.close(); return@createContext }
                        deleted = true; mapOf("deleted" to true, "id" to "pay_local") }
                    else -> payment()
                }
                val bytes = mapper.writeValueAsBytes(response)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
        fun payment(): Any {
            val p = lastPayment!!
            return mapOf("id" to if (paymentMismatch == "id") "pay_other" else "pay_local", "customer" to "cus_local",
                "externalReference" to if (paymentMismatch == "externalReference") "other" else p["externalReference"].asText(),
                "billingType" to if (paymentMismatch == "billingType") "CREDIT_CARD" else p["billingType"].asText(), "value" to if (wrongAmount || paymentMismatch == "value") java.math.BigDecimal("200") else p["value"].decimalValue(),
                "netValue" to java.math.BigDecimal("103.00"), "status" to paymentStatus, "deleted" to deleted,
                "invoiceUrl" to "https://asaas.com/i/pay_local", "split" to listOf(mapOf("id" to "split_local", "walletId" to "wallet-platform", "fixedValue" to java.math.BigDecimal("3.00"), "status" to splitStatus)),
                "refunds" to returnedCommissionCents?.let { returned -> listOf(mapOf("status" to "DONE", "value" to p["value"].decimalValue(),
                    "refundedSplits" to listOf(mapOf("id" to "split_local", "done" to true, "value" to java.math.BigDecimal.valueOf(returned, 2))))) }.orEmpty())
        }
        override fun close() { server.stop(0) }
    }
}
