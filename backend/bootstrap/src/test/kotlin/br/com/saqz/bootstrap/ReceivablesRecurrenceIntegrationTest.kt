package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcGroupChargePayments
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcGroupAdministrationDirectory
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.input.http.RecurrencePaymentsController
import br.com.saqz.receivables.adapter.input.http.RecurrencePixRenewalController
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.*

class ReceivablesRecurrenceIntegrationTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val mapper = ObjectMapper().findAndRegisterModules()

    @Test fun `mobile HTTP preview requires stable request and returns exact no-store JSON`() = fixture { f ->
        val resolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, request: NativeWebRequest,
                binder: WebDataBinderFactory?) = RequestIdentity("subject", "request")
        }
        val mvc = MockMvcBuilders.standaloneSetup(RecurrencePaymentsController(FinancialActorResolver { f.member }, f.service))
            .setCustomArgumentResolvers(resolver).build()
        val requestId = UUID.randomUUID()
        val body = """{"requestId":"$requestId","accountId":"${f.account}","groupId":"${f.group}","method":"PIX","firstDueDate":"2026-10-10"}"""
        val response = mvc.perform(post("/api/receivables/recurrences/preview").contentType("application/json").content(body)).andReturn().response
        assertEquals(200, response.status)
        assertEquals("no-store", response.getHeader("Cache-Control"))
        val json = mapper.readTree(response.contentAsString)
        assertEquals(requestId.toString(), json.path("requestId").asText())
        assertEquals(10_000, json.path("value").path("baseCents").asLong())
        assertEquals(100, json.path("value").path("feesCents").asLong())
        assertEquals(10_100, json.path("value").path("totalCents").asLong())
        assertEquals(400, mvc.perform(post("/api/receivables/recurrences/preview").contentType("application/json")
            .content(body.replace("\"requestId\":\"$requestId\",", ""))).andReturn().response.status)

        val current = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/receivables/recurrences/current")
            .queryParam("accountId", f.account.toString()).queryParam("groupId", f.group.toString())).andReturn().response
        assertEquals(200, current.status)
        assertTrue(mapper.readTree(current.contentAsString).path("value").isNull)
    }

    @Test fun `authorization is durable idempotent actor bound and concurrent live recurrence is unique`() = fixture { f ->
        val review = assertIs<FinancialResult.Success<RecurrenceReview>>(f.service.preview(f.account, f.group,
            PaymentMethod.PIX, LocalDate.of(2026, 10, 10), f.request())).value
        val request = f.request(); val payer = PaymentPayer("Maria Silva", "12345678901")
        val first = assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true, payer, request)).value
        val replay = assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true, payer, request)).value
        assertEquals(first.id, replay.id)
        assertEquals(1, f.count("receivable_recurrences"))
        assertEquals(1, f.count("receivable_terms_acceptances", "purpose='RECURRENCE'"))
        assertEquals(f.member, f.uuid("SELECT approved_by FROM receivable_recurrences WHERE id='${first.id}'"))
        assertEquals(first.id, assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.byRequest(request)).value.id)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.service.byRequest(
            FinancialRequest(request.requestId, UUID.randomUUID()))).error)
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true,
            PaymentPayer("Outra Pessoa", "98765432100"), request)).error)

        Executors.newFixedThreadPool(2).use { pool ->
            val outcomes = (1..2).map { pool.submit<FinancialResult<PaymentRecurrence>> {
                f.service.authorize(f.account, f.group, PaymentMethod.PIX, review.firstDueDate,
                    review.fingerprint, true, payer, f.request()) } }.map { it.get() }
            assertEquals(2, outcomes.count { it is FinancialResult.Failure && it.error == FinancialError.CONFLICT })
        }
        assertEquals(1, f.count("receivable_recurrences"))
    }

    @Test fun `provider competencies materialize once with immutable recurrence quote snapshot`() = fixture { f ->
        val review = assertIs<FinancialResult.Success<RecurrenceReview>>(f.service.preview(f.account, f.group,
            PaymentMethod.PIX, LocalDate.of(2026, 10, 10), f.request())).value
        val recurrence = assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true,
            PaymentPayer("Maria Silva", "12345678901"), f.request())).value
        val active = f.store.find(recurrence.id)!!
        val payment = ProviderRecurringPayment("pay_1", "sub_1", LocalDate.of(2026, 10, 10), "PENDING", PaymentMethod.PIX, review.totalCents)
        repeat(2) { f.store.transaction { f.store.materialize(active, listOf(payment), now) } }
        assertEquals(1, f.count("receivable_orders", "recurrence_id='${recurrence.id}'"))
        assertEquals(1, f.count("receivable_instruments", "provider_payment_id='pay_1'"))
        assertEquals(1, f.count("group_charges", "group_id='${f.group}' AND member_user_id='${f.member}' AND billing_month='2026-10-01'"))
        assertEquals(review.totalCents, f.long("SELECT total_cents FROM receivable_instruments WHERE provider_payment_id='pay_1'"))
        assertEquals(review.feesCents, f.long("SELECT fees_cents FROM receivable_instruments WHERE provider_payment_id='pay_1'"))
        assertEquals(review.termsVersion, f.string("SELECT quote->>'termsVersion' FROM receivable_order_quotes"))
    }

    @Test fun `completed cutoff cancels only future recurrence competencies and preserves overdue debt`() = fixture { f ->
        val review = assertIs<FinancialResult.Success<RecurrenceReview>>(f.service.preview(f.account, f.group,
            PaymentMethod.PIX, LocalDate.of(2026, 10, 10), f.request())).value
        val recurrence = assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true,
            PaymentPayer("Maria Silva", "12345678901"), f.request())).value
        val active = f.store.find(recurrence.id)!!
        f.store.transaction { f.store.materialize(active, listOf(
            ProviderRecurringPayment("pay_overdue", "sub_1", LocalDate.of(2026, 9, 12), "OVERDUE", PaymentMethod.PIX, review.totalCents),
            ProviderRecurringPayment("pay_future", "sub_1", LocalDate.of(2026, 10, 10), "PENDING", PaymentMethod.PIX, review.totalCents)
        ), now) }

        val stopRequest = f.request()
        val pending = f.store.transaction { f.store.requestStop(recurrence.id, stopRequest, "MEMBER_INACTIVE", "stop-digest", now) }
        val claim = f.store.claim(pending.recurrence.id, now, now.plusSeconds(90))!!
        assertTrue(f.store.finish(claim, ProviderRecurrenceResult(subscriptionId = "sub_1", status = "STOPPED"), true, now))

        assertEquals("ACTIVE", f.string("SELECT status FROM receivable_instruments WHERE provider_payment_id='pay_overdue'"))
        assertEquals("ISSUED", f.string("SELECT o.status FROM receivable_orders o JOIN receivable_instruments i ON i.order_id=o.id WHERE i.provider_payment_id='pay_overdue'"))
        assertEquals("PENDING", f.string("SELECT c.status FROM group_charges c JOIN receivable_orders o ON o.group_charge_id=c.id JOIN receivable_instruments i ON i.order_id=o.id WHERE i.provider_payment_id='pay_overdue'"))
        assertEquals("CANCELLED", f.string("SELECT status FROM receivable_instruments WHERE provider_payment_id='pay_future'"))
        assertEquals("CANCELLED", f.string("SELECT o.status FROM receivable_orders o JOIN receivable_instruments i ON i.order_id=o.id WHERE i.provider_payment_id='pay_future'"))
        assertEquals("CANCELLED", f.string("SELECT c.status FROM group_charges c JOIN receivable_orders o ON o.group_charge_id=c.id JOIN receivable_instruments i ON i.order_id=o.id WHERE i.provider_payment_id='pay_future'"))
        assertEquals(1, f.count("group_charge_events", "new_status='CANCELLED'"))
    }

    @Test fun `expired pix renewal after cutoff preserves debt snapshot and recovers timeout without second update`() = fixture { f ->
        val review = assertIs<FinancialResult.Success<RecurrenceReview>>(f.service.preview(f.account, f.group,
            PaymentMethod.PIX, LocalDate.of(2026, 10, 10), f.request())).value
        val recurrence = assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true, PaymentPayer("Maria Silva", "12345678901"), f.request())).value
        val active = f.store.find(recurrence.id)!!
        val instrument = f.store.transaction { f.store.materialize(active, listOf(ProviderRecurringPayment("pay_1", "sub_1",
            LocalDate.of(2026, 10, 10), "OVERDUE", PaymentMethod.PIX, review.totalCents)), now).single() }
        f.sql("UPDATE receivable_recurrences SET status='STOPPED',cutoff_at=now() WHERE id='${recurrence.id}'")
        f.sql("UPDATE receivable_instruments SET status='EXPIRED',expires_at='2026-09-12' WHERE id='$instrument'")
        val remote = TimeoutRenewalProvider(LocalDate.of(2026, 10, 10))
        val paymentStore = JdbcPaymentStore(f.ds, f.secrets)
        val paymentExecution = JdbcPaymentExecution(f.ds, paymentStore, JdbcGroupChargePayments(f.ds),
            JdbcFinancialOperationStore(f.ds), remote, f.secrets, Clock.fixed(now, ZoneOffset.UTC),
            ReconcileExternalResidualCost(JdbcExternalResidualCostLedger(f.ds)))
        val oneOff = OneOffPayments(paymentStore, JdbcGroupChargePayments(f.ds), JdbcFinancialAccountRepository(f.ds),
            JdbcGroupReceivablesStore(f.ds), JdbcGroupAdministrationDirectory(f.ds), JdbcFinancialConditions(f.ds),
            ReceivablesEligibility { _, _ -> ReceivablesEntitlement(false, now) },
            ReceivablesRolloutAccess { ReceivablesAvailability(false, false) }, paymentExecution, Clock.fixed(now, ZoneOffset.UTC))
        val order = f.uuid("SELECT order_id FROM receivable_instruments WHERE id='$instrument'")
        val request = f.request(); val target = LocalDate.of(2026, 10, 20)
        val pending = assertIs<FinancialResult.Failure>(oneOff.renewPix(order, target, request))
        assertEquals(FinancialError.RESULT_PENDING, pending.error)
        assertEquals(FinancialError.RESULT_PENDING, assertIs<FinancialResult.Failure>(oneOff.pixRenewal(order, request)).error)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(oneOff.pixRenewal(order,
            FinancialRequest(request.requestId, UUID.randomUUID()))).error)
        val resolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, webRequest: NativeWebRequest,
                binder: WebDataBinderFactory?) = RequestIdentity("subject", "request")
        }
        val recoveryMvc = MockMvcBuilders.standaloneSetup(RecurrencePixRenewalController(FinancialActorResolver { f.member }, oneOff))
            .setCustomArgumentResolvers(resolver).build()
        assertEquals(202, recoveryMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/api/receivables/orders/$order/pix-renewal/${request.requestId}")).andReturn().response.status)
        assertEquals(1, remote.renewCalls)
        assertEquals(1, f.count("receivable_pix_renewals"))
        f.sql("UPDATE receivable_pix_renewals SET next_attempt_at='2026-09-13 11:00:00Z'")
        paymentExecution.recoverPixRenewals()
        val replay = assertIs<FinancialResult.Success<PixRenewal>>(oneOff.renewPix(order, target, request))
        val recoveredByRequest = assertIs<FinancialResult.Success<PixRenewal>>(oneOff.pixRenewal(order, request))
        assertEquals(instrument, replay.value.instrumentId)
        assertEquals(replay.value, recoveredByRequest.value)
        assertEquals(200, recoveryMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
            "/api/receivables/orders/$order/pix-renewal/${request.requestId}")).andReturn().response.status)
        assertEquals("pay_1", replay.value.providerPaymentId)
        assertEquals(1, remote.renewCalls)
        assertEquals(1, remote.recoverCalls)
        assertEquals("SUCCEEDED", f.string("SELECT status FROM receivable_pix_renewals"))
        assertEquals(target.toString(), f.string("SELECT due_date::text FROM receivable_orders WHERE id='$order'"))
        assertEquals(review.totalCents, f.long("SELECT total_cents FROM receivable_instruments WHERE id='$instrument'"))
        assertEquals(1, f.count("receivable_orders", "id='$order'"))
        assertEquals(1, f.count("receivable_instruments", "id='$instrument'"))
    }

    @Test fun `terminal reversal appends exact observed residual cost once`() = fixture { f ->
        val review = assertIs<FinancialResult.Success<RecurrenceReview>>(f.service.preview(f.account, f.group,
            PaymentMethod.PIX, LocalDate.of(2026, 10, 10), f.request())).value
        val recurrence = assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true,
            PaymentPayer("Maria Silva", "12345678901"), f.request())).value
        val active = f.store.find(recurrence.id)!!
        val instrumentId = f.store.transaction { f.store.materialize(active, listOf(ProviderRecurringPayment("pay_refund", "sub_1",
            LocalDate.of(2026, 10, 10), "RECEIVED", PaymentMethod.PIX, review.totalCents)), now).single() }
        val paymentStore = JdbcPaymentStore(f.ds, f.secrets)
        val execution = JdbcPaymentExecution(f.ds, paymentStore, JdbcGroupChargePayments(f.ds), JdbcFinancialOperationStore(f.ds),
            ReversalProvider(), f.secrets, Clock.fixed(now, ZoneOffset.UTC),
            ReconcileExternalResidualCost(JdbcExternalResidualCostLedger(f.ds)))

        assertTrue(execution.reconcile(instrumentId))
        execution.reconcile(instrumentId)
        assertEquals("REFUNDED", f.string("SELECT status FROM receivable_instruments WHERE id='$instrumentId'"))
        assertEquals(1, f.count("receivable_movements", "instrument_id='$instrumentId' AND kind='RESIDUAL_COST'"))
        assertEquals(-175, f.long("SELECT amount_cents FROM receivable_movements WHERE instrument_id='$instrumentId' AND kind='RESIDUAL_COST'"))
        assertEquals("txn_fee_1", f.string("SELECT provider_reference FROM receivable_movements WHERE instrument_id='$instrumentId' AND kind='RESIDUAL_COST'"))
    }

    @Test fun `administrative observation probe never creates cancels or renews uncertain payment`() = fixture { f ->
        val review = assertIs<FinancialResult.Success<RecurrenceReview>>(f.service.preview(f.account, f.group,
            PaymentMethod.PIX, LocalDate.of(2026, 10, 10), f.request())).value
        val recurrence = assertIs<FinancialResult.Success<PaymentRecurrence>>(f.service.authorize(f.account, f.group,
            PaymentMethod.PIX, review.firstDueDate, review.fingerprint, true,
            PaymentPayer("Maria Silva", "12345678901"), f.request())).value
        val instrumentId = f.store.transaction { f.store.materialize(f.store.find(recurrence.id)!!,
            listOf(ProviderRecurringPayment("pay_probe", "sub_1", LocalDate.of(2026, 10, 10), "PENDING",
                PaymentMethod.PIX, review.totalCents)), now).single() }
        val orderId = f.uuid("SELECT order_id FROM receivable_instruments WHERE id='$instrumentId'")
        f.sql("UPDATE receivable_instruments SET status='UNKNOWN' WHERE id='$instrumentId'")
        f.sql("UPDATE receivable_orders SET status='CANCEL_PENDING' WHERE id='$orderId'")
        val provider = ReadOnlyProbeProvider()
        val execution = JdbcPaymentExecution(f.ds, JdbcPaymentStore(f.ds, f.secrets), JdbcGroupChargePayments(f.ds),
            JdbcFinancialOperationStore(f.ds), provider, f.secrets, Clock.fixed(now, ZoneOffset.UTC),
            ReconcileExternalResidualCost(JdbcExternalResidualCostLedger(f.ds)))

        val operationStore = JdbcFinancialOperationStore(f.ds)
        operationStore.register(FinancialOperation(instrumentId, f.account, UUID.randomUUID(), f.member,
            OperationKind.CREATE_INSTRUMENT, instrumentId, "a".repeat(64)), now)
        f.sql("UPDATE receivable_operations SET status='UNKNOWN' WHERE id='$instrumentId'")
        val factory = org.springframework.beans.factory.support.StaticListableBeanFactory()
        factory.addBean("payments", execution)
        val adminStore = JdbcOperationalReceivables(f.ds)
        val probe = br.com.saqz.bootstrap.configuration.ReceivablesOperationsConfiguration()
            .operationalRecoveryProbe(factory.getBeanProvider(JdbcPaymentExecution::class.java), adminStore)
        fun observe() = probe.observe(adminStore.detail(instrumentId)!!.operation)
        assertEquals(OperationStatus.UNKNOWN, observe().status)
        f.sql("UPDATE receivable_instruments SET status='CREATING' WHERE id='$instrumentId'")
        assertEquals(OperationStatus.UNKNOWN, observe().status)
        assertEquals(2, provider.recoverCalls)
        assertEquals(0, provider.createCalls + provider.cancelCalls + provider.renewCalls)
        assertEquals("CANCEL_PENDING", f.string("SELECT status FROM receivable_orders WHERE id='$orderId'"))
        provider.observation = { context -> ProviderPaymentObservation(
            paymentId = "pay_probe", reference = context.instrument.id.toString(), method = PaymentMethod.PIX,
            totalCents = 10100, status = "ACTIVE", providerFeeCents = 90, splitCents = 10) }
        assertEquals(OperationStatus.SUCCEEDED, observe().status)
        assertEquals("SUCCEEDED", f.string("SELECT status FROM receivable_operations WHERE id='$instrumentId'"))
        assertEquals(3, provider.recoverCalls)
        assertEquals(0, provider.createCalls + provider.cancelCalls + provider.renewCalls)
        assertEquals("CANCEL_PENDING", f.string("SELECT status FROM receivable_orders WHERE id='$orderId'"))
        assertEquals(0, f.count("receivable_movements", "instrument_id='$instrumentId'"))
    }

    private fun fixture(test: (Fixture) -> Unit) = test(Fixture())

    private class TimeoutRenewalProvider(var remoteDueDate: LocalDate) : OneOffPaymentProvider {
        var renewCalls = 0; var recoverCalls = 0
        override fun prepareCustomer(context: ProviderPaymentContext) = true
        override fun create(context: ProviderPaymentContext) = null
        override fun cancel(context: ProviderPaymentContext) = null
        override fun recover(context: ProviderPaymentContext): ProviderPaymentObservation {
            recoverCalls++
            return observation(context, remoteDueDate)
        }
        override fun renewPix(context: ProviderPaymentContext, dueDate: LocalDate): ProviderPaymentObservation {
            renewCalls++; remoteDueDate = dueDate
            throw IllegalStateException("response lost after provider update")
        }
        private fun observation(context: ProviderPaymentContext, dueDate: LocalDate) = ProviderPaymentObservation(
            paymentId = context.instrument.paymentId, reference = context.instrument.id.toString(), method = PaymentMethod.PIX,
            totalCents = context.instrument.quote.totalCents, status = "ACTIVE",
            providerFeeCents = context.instrument.quote.providerFeeCents, splitCents = context.instrument.quote.commissionCents,
            pixPayload = "000201renewed", pixImage = "qr-renewed", expiresAt = Instant.parse("2026-10-20T23:59:59Z"), dueDate = dueDate)
    }
    private class ReversalProvider : OneOffPaymentProvider {
        override fun prepareCustomer(context: ProviderPaymentContext) = true
        override fun create(context: ProviderPaymentContext) = null
        override fun cancel(context: ProviderPaymentContext) = null
        override fun recover(context: ProviderPaymentContext) = ProviderPaymentObservation(
            paymentId = context.instrument.paymentId, reference = context.instrument.id.toString(),
            method = context.instrument.quote.method, totalCents = context.instrument.quote.totalCents,
            status = "REFUNDED", providerFeeCents = context.instrument.quote.providerFeeCents,
            splitCents = context.instrument.quote.commissionCents, splitSettled = true,
            returnedCommissionCents = context.instrument.quote.commissionCents,
            residualCosts = listOf(ProviderResidualCost("txn_fee_1", 175)))
    }
    private class ReadOnlyProbeProvider : OneOffPaymentProvider {
        var observation: ((ProviderPaymentContext) -> ProviderPaymentObservation)? = null
        var createCalls = 0; var cancelCalls = 0; var renewCalls = 0; var recoverCalls = 0
        override fun prepareCustomer(context: ProviderPaymentContext) = true
        override fun create(context: ProviderPaymentContext): ProviderPaymentObservation? { createCalls++; return null }
        override fun cancel(context: ProviderPaymentContext): ProviderPaymentObservation? { cancelCalls++; return null }
        override fun renewPix(context: ProviderPaymentContext, dueDate: LocalDate): ProviderPaymentObservation? { renewCalls++; return null }
        override fun recover(context: ProviderPaymentContext): ProviderPaymentObservation? { recoverCalls++; return observation?.invoke(context) }
    }
    private inner class Fixture {
        val ds = TestPostgres.migrated("classpath:db/migration", owner = this@ReceivablesRecurrenceIntegrationTest).dataSource
        val jdbc = JdbcClient.create(ds)
        val owner = UUID.randomUUID(); val member = UUID.randomUUID(); val group = UUID.randomUUID(); val account = UUID.randomUUID()
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val store = JdbcRecurrenceStore(ds, secrets)
        private val execution = object : RecurrenceExecution {
            override fun start(id: UUID) { val claim = store.claim(id, now, now.plusSeconds(90)) ?: return; store.finish(claim,
                ProviderRecurrenceResult(subscriptionId = "sub_1", status = "ACTIVE"), false, now) }
            override fun stop(id: UUID) = Unit
            override fun recoverDue() = Unit; override fun synchronize() = Unit; override fun enforceCutoffs() = Unit
        }
        val service: RecurrencePayments
        init {
            sql("INSERT INTO access_users(id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$owner','$owner',true,'Titular',now(),now()),('$member','$member',true,'Pagador',now(),now())")
            sql("INSERT INTO access_groups(id,owner_user_id,creation_key,name,time_zone,monthly_fee_cents,monthly_due_day,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Test','UTC',10000,10,now(),now())")
            sql("""INSERT INTO group_memberships(group_id,user_id,role,membership_type,active,created_at,updated_at)
                VALUES ('$group','$member','ATHLETE','MENSALISTA',true,now(),now())""")
            sql("""INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,provider_account_id,credentials_encrypted,registration,new_operations_enabled,created_at,updated_at)
                VALUES ('$account','$owner','$owner','ciphertext','remote-account','${secrets.encrypt(account, "provider-key", "local-key")}','APPROVED',true,now(),now())""")
            sql("INSERT INTO receivable_webhook_credentials(account_id,token_encrypted) VALUES ('$account','ciphertext')")
            sql("INSERT INTO receivable_group_links VALUES ('$account','$group',true,true,true,'$owner',now(),NULL)")
            sql("INSERT INTO receivable_terms VALUES ('v1','Terms','${"a".repeat(64)}','2026-09-01','2026-09-01')")
            sql("INSERT INTO receivable_fee_schedules VALUES ('${UUID.randomUUID()}','PIX',0,90,0,10,'v1','2026-09-01','2026-09-01','$owner')")
            val accounts = JdbcFinancialAccountRepository(ds)
            service = RecurrencePayments(store, accounts, JdbcGroupReceivablesStore(ds), JdbcFinancialConditions(ds),
                ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) },
                ReceivablesRolloutAccess { ReceivablesAvailability(true, true) }, execution, Clock.fixed(now, ZoneOffset.UTC))
        }
        fun request() = FinancialRequest(UUID.randomUUID(), member)
        fun sql(value: String) { jdbc.sql(value).update() }
        fun count(table: String, where: String = "true") = jdbc.sql("SELECT count(*) FROM $table WHERE $where").query(Int::class.java).single()
        fun uuid(value: String) = jdbc.sql(value).query(UUID::class.java).single()
        fun long(value: String) = jdbc.sql(value).query(Long::class.java).single()
        fun string(value: String) = jdbc.sql(value).query(String::class.java).single()
    }
}
