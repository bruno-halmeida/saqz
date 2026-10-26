package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcReceivablesNotices
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcGroupChargeLedger
import br.com.saqz.groups.adapter.output.jdbc.group.delete.JdbcGroupDeletionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.charge.ChargeManagement
import br.com.saqz.groups.application.finance.charge.ChargeStatusResult
import br.com.saqz.groups.application.finance.charge.MonthlyGenerationCommand
import br.com.saqz.groups.domain.finance.charge.ChargeStatus
import br.com.saqz.groups.domain.finance.charge.ChargeStatusCommand
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasPaymentCodes
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

/** Recebimento pelo app v1 (DEB-140) com Postgres de verdade e o Asaas simulado por HTTP. */
class ReceivablesV1IntegrationTest {
    private val now = Instant.parse("2026-10-20T15:00:00Z")
    private val mapper = ObjectMapper()

    @Test fun `cobranca nova em grupo PASS grava o valor do atleta e a anterior a ativacao mantem o valor`() = fixture { f ->
        f.sql("UPDATE receivable_group_links SET enabled=false")
        val before = f.monthly("2026-08", f.athlete)
        f.sql("UPDATE receivable_group_links SET enabled=true,fee_policy='PASS'")
        val after = f.monthly("2026-09", f.athlete)
        assertNull(f.long("SELECT payable_cents FROM group_charges WHERE id='$before'"))
        val payable = f.long("SELECT payable_cents FROM group_charges WHERE id='$after'")!!
        assertTrue(payable > 10000)
        assertEquals("PASS", f.string("SELECT fee_policy FROM group_charges WHERE id='$after'"))
        f.sql("UPDATE receivable_group_links SET fee_policy='ABSORB'")
        val absorbed = f.monthly("2026-10", f.athlete)
        assertEquals(10000, f.long("SELECT payable_cents FROM group_charges WHERE id='$absorbed'"))
        val me = assertIs<FinancialResult.Success<AthletePaymentState>>(f.athletes.me(f.group, f.request(f.athlete))).value
        assertTrue(me.active)
        assertEquals(10000 + payable + 10000, me.totalCents)
        assertEquals(listOf(before, after, absorbed), me.openCharges.map { it.chargeId })
    }

    @Test fun `codigo Pix reaproveita o pendente e recusa cobranca de outro atleta e cartao sem CPF`() = fixture { f ->
        val mine = f.monthly("2026-09", f.athlete)
        val other = f.monthly("2026-09", f.other)
        val first = f.code(PaymentMethod.PIX, mine)
        assertEquals("PENDING", first.status)
        assertEquals("pix-payload", first.pix!!.payload)
        assertEquals(10000, first.amountCents)
        assertEquals(first.codeId, f.code(PaymentMethod.PIX, mine).codeId)
        assertEquals(1, f.remote.qrPosts)
        assertFalse(f.remote.lastQr!!["allowsMultiplePayments"].asBoolean())
        assertEquals(first.codeId.toString(), f.remote.lastQr!!["externalReference"].asText())
        assertEquals(FinancialError.CHARGE_NOT_PAYABLE, f.failure(PaymentMethod.PIX, other))
        assertEquals(FinancialError.DOCUMENT_REQUIRED, f.failure(PaymentMethod.CARD, mine))
        assertEquals(FinancialError.INVALID_INPUT, assertIs<FinancialResult.Failure>(f.athletes.saveDocument("123.456.789-00", f.request(f.athlete))).error)
        assertIs<FinancialResult.Success<DocumentState>>(f.athletes.saveDocument("529.982.247-25", f.request(f.athlete)))
        assertFalse(f.string("SELECT document_encrypted FROM receivable_payer_documents").contains("52998224725"))
        val card = f.code(PaymentMethod.CARD, mine)
        assertEquals("https://sandbox.asaas.com/i/pay_card", card.card!!.checkoutUrl)
        assertEquals("52998224725", f.remote.customer!!["cpfCnpj"].asText())
        assertEquals(0, BigDecimal("2.00").compareTo(f.remote.lastPayment!!["split"][0]["fixedValue"].decimalValue()))
        f.sql("UPDATE receivable_accounts SET pix_key_ready=false")
        assertEquals(FinancialError.PIX_NOT_READY, f.failure(PaymentMethod.PIX, mine, f.monthly("2026-10", f.athlete)))
    }

    @Test fun `webhook por pixQrCodeId da baixa uma vez com push transfere a comissao uma vez e lanca as taxas`() = fixture { f ->
        val charge = f.monthly("2026-09", f.athlete)
        val code = f.code(PaymentMethod.PIX, charge)
        f.remote.payment("pay_pix", "PIX", 10000, 9901, "RECEIVED", qr = "qr_1")
        f.event("evt_1", "PAYMENT_RECEIVED", "pay_pix", qr = "qr_1")
        f.event("evt_2", "PAYMENT_CONFIRMED", "pay_pix", qr = "qr_1")
        f.event("evt_1", "PAYMENT_RECEIVED", "pay_pix", qr = "qr_1")
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges WHERE id='$charge'"))
        assertEquals("APP", f.string("SELECT settled_by FROM group_charges WHERE id='$charge'"))
        assertEquals("PIX", f.string("SELECT paid_method::text FROM group_charges WHERE id='$charge'"))
        assertEquals("PIX", f.string("SELECT app_method FROM group_charges WHERE id='$charge'"))
        assertEquals("PAID", assertIs<FinancialResult.Success<PaymentCodeView>>(f.athletes.get(code.codeId, f.request(f.athlete))).value.status)
        assertEquals(1, f.count("payment_settlements"))
        assertEquals(1, f.remote.transferPosts)
        assertEquals(0, BigDecimal("2.00").compareTo(f.remote.lastTransfer!!["value"].decimalValue()))
        f.commissions.recoverPending()
        assertEquals(1, f.remote.transferPosts)
        // Caixa: entrada de R$ 100,00 pela cobrança; saída de 0,99 do Asaas + 2,00 de comissão.
        assertEquals(299, f.long("SELECT amount_cents FROM group_expenses WHERE origin='RECEIVABLES_FEES' AND direction='OUT'"))
        assertEquals(listOf("Pagador de teste pagou R$ 100,00", "Pagamento confirmado"), f.titles())
        assertEquals("Mensalidade de setembro · Vôlei do CERET", f.body("Pagador de teste pagou R$ 100,00"))
        assertEquals("R$ 100,00 · mensalidade de setembro, Vôlei do CERET.", f.body("Pagamento confirmado"))
        assertEquals(2, f.count("notification_push_queue"), "um push ao gestor e um ao atleta")
    }

    @Test fun `cartao por externalReference da baixa sem transferir comissao e Desfazer e recusado`() = fixture { f ->
        val charge = f.monthly("2026-09", f.athlete)
        f.athletes.saveDocument("52998224725", f.request(f.athlete))
        val code = f.code(PaymentMethod.CARD, charge)
        f.remote.payment("pay_card", "CREDIT_CARD", 10000, 9500, "CONFIRMED", reference = code.codeId.toString())
        f.event("evt_card", "PAYMENT_CONFIRMED", "pay_card", reference = code.codeId.toString(), billing = "CREDIT_CARD")
        assertEquals("OTHER", f.string("SELECT paid_method::text FROM group_charges WHERE id='$charge'"))
        assertEquals("CARD", f.string("SELECT app_method FROM group_charges WHERE id='$charge'"))
        assertEquals(0, f.remote.transferPosts)
        val version = f.long("SELECT version FROM group_charges WHERE id='$charge'")!!
        val undo = ChargeManagement(JdbcTransactionRunner(f.ds), JdbcChargeManagementRepository(f.ds), { now }, UUID::randomUUID)
            .status(f.owner, f.group, charge, version, ChargeStatusCommand(ChargeStatus.PENDING))
        assertEquals(ChargeStatusResult.InvalidTransition, undo)
    }

    @Test fun `Recebi no meio faz o Pix quitar as mais antigas e a sobra vai para a revisao`() = fixture { f ->
        val august = f.monthly("2026-08", f.athlete)
        val september = f.monthly("2026-09", f.athlete)
        val code = f.code(PaymentMethod.PIX, september)
        val manual = ChargeManagement(JdbcTransactionRunner(f.ds), JdbcChargeManagementRepository(f.ds), { now }, UUID::randomUUID)
            .status(f.owner, f.group, september, 1, ChargeStatusCommand(ChargeStatus.PAID))
        assertIs<ChargeStatusResult.Success>(manual)
        assertEquals("EXPIRED", assertIs<FinancialResult.Success<PaymentCodeView>>(f.athletes.get(code.codeId, f.request(f.athlete))).value.status)
        f.remote.payment("pay_late", "PIX", 15000, 14901, "RECEIVED", qr = "qr_1")
        f.event("evt_late", "PAYMENT_RECEIVED", "pay_late", qr = "qr_1")
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges WHERE id='$august'"))
        assertEquals("APP", f.string("SELECT settled_by FROM group_charges WHERE id='$august'"))
        assertNull(f.stringOrNull("SELECT settled_by FROM group_charges WHERE id='$september'"))
        assertEquals(5000, f.long("SELECT amount_cents FROM payment_reviews WHERE status='OPEN'"))
        assertEquals(0, f.remote.transferPosts, "valor diferente do código: sem comissão calculada")
    }

    @Test fun `Pix sem codigo cai na revisao com push e o gestor atribui ou diz que nao e cobranca`() = fixture { f ->
        val august = f.monthly("2026-08", f.athlete)
        val september = f.monthly("2026-09", f.athlete)
        f.remote.payment("pay_loose", "PIX", 15000, 14901, "RECEIVED", customer = "cus_joao")
        f.event("evt_loose", "PAYMENT_RECEIVED", "pay_loose")
        assertTrue(f.titles().contains("Pix para revisar"))
        assertEquals("R$ 150,00 de João P. Silva caiu fora de uma cobrança. Quem é?", f.body("Pix para revisar"))
        val reviews = assertIs<FinancialResult.Success<ReviewList>>(f.reviews.list(f.group, f.account, f.request(f.owner))).value.reviews
        assertEquals(1, assertIs<FinancialResult.Success<GroupReceivablesMaintenance>>(
            f.groupReceivables.read(f.account, f.group, f.request(f.owner))).value.state.openReviews)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.reviews.list(f.group, f.account, f.request(f.athlete))).error)
        val review = reviews.single()
        val suggestion = assertIs<FinancialResult.Success<ReviewSuggestion>>(f.reviews.suggestion(review.id, f.athlete, f.request(f.owner))).value
        assertEquals(listOf(august), suggestion.charges.map { it.chargeId })
        assertEquals("Mensalidade · agosto", suggestion.charges.single().label)
        val request = f.request(f.owner)
        val assigned = assertIs<FinancialResult.Success<ReviewAssignment>>(f.reviews.assign(review.id, f.athlete, listOf(august), request)).value
        assertEquals(5000, assigned.leftoverCents)
        assertEquals(assigned, assertIs<FinancialResult.Success<ReviewAssignment>>(f.reviews.assign(review.id, f.athlete, listOf(august), request)).value)
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges WHERE id='$august'"))
        assertEquals("PENDING", f.string("SELECT status::text FROM group_charges WHERE id='$september'"))
        assertEquals(5000, f.long("SELECT amount_cents FROM group_expenses WHERE origin='RECEIVABLES_REVIEW'"))
        assertEquals(99, f.long("SELECT amount_cents FROM group_expenses WHERE origin='RECEIVABLES_FEES'"))
        f.remote.payment("pay_other", "PIX", 3000, 2901, "RECEIVED", customer = "cus_joao")
        f.event("evt_other", "PAYMENT_RECEIVED", "pay_other")
        val second = assertIs<FinancialResult.Success<ReviewList>>(f.reviews.list(f.group, f.account, f.request(f.owner))).value.reviews.single()
        val expense = assertIs<FinancialResult.Success<ReviewNotCharge>>(f.reviews.notCharge(second.id, f.request(f.owner))).value.expenseId
        assertEquals("Pix de João P. Silva", f.string("SELECT description FROM group_expenses WHERE id='$expense'"))
        assertEquals(3000, f.long("SELECT amount_cents FROM group_expenses WHERE id='$expense'"))
    }

    @Test fun `excluir o grupo cancela os codigos e agenda o cancelamento remoto`() = fixture { f ->
        val charge = f.monthly("2026-09", f.athlete)
        f.athletes.saveDocument("52998224725", f.request(f.athlete))
        f.code(PaymentMethod.PIX, charge)
        f.code(PaymentMethod.CARD, charge)
        JdbcTransactionRunner(f.ds).inTransaction { JdbcGroupDeletionRepository(f.ds, f.pricing).softDelete(f.owner, f.group) }
        assertEquals(2, f.count("payment_codes", "status='CANCELLED'"))
        assertFalse(f.pricing.active(f.group))
        JdbcPaymentCodeCancellations(f.ds, JdbcFinancialOperationStore(f.ds), f.provider, f.clock).recoverPending()
        assertEquals(setOf("/v3/pix/qrCodes/static/qr_1", "/v3/payments/pay_card"), f.remote.deletes.toSet())
        assertEquals(2, f.count("receivable_operations", "kind='CANCEL_PAYMENT_CODE' AND status='SUCCEEDED'"))
    }

    @Test fun `DTO antigo de cobranca continua com os mesmos campos e status`() = fixture { f ->
        val charge = f.monthly("2026-09", f.athlete)
        val listed = ChargeManagement(JdbcTransactionRunner(f.ds), JdbcChargeManagementRepository(f.ds), { now }, UUID::randomUUID)
            .listOwn(f.athlete, f.group)
        val row = assertIs<br.com.saqz.groups.application.finance.charge.ChargeListResult.Success>(listed).charges.single().charge
        assertEquals(charge, row.id)
        assertEquals(ChargeStatus.PENDING, row.status)
        assertEquals(10000, row.amountCents)
        assertEquals(10000, row.payableCents)
        val fields = br.com.saqz.groups.adapter.input.http.ChargeResponse::class.java.declaredFields.map { it.name }
        assertTrue(fields.containsAll(listOf("id", "groupId", "memberId", "kind", "gameId", "month", "amountCents", "dueDate", "status",
            "paidMethod", "reviewRequired", "version", "events", "guestDisplayName", "withdrew", "electronicOrder", "noShow",
            "payableCents", "settledBy", "appMethod")))
    }

    @Test fun `repasse no Pix e no cartao entra pelo valor pago e o saldo do caixa e o que caiu na carteira`() = fixture { f ->
        f.sql("UPDATE receivable_group_links SET fee_policy='PASS'")
        val september = f.monthly("2026-09", f.athlete)
        val october = f.monthly("2026-10", f.athlete)
        val paid = f.long("SELECT payable_cents FROM group_charges WHERE id='$september'")!!
        assertTrue(paid > 10000)
        val pix = f.code(PaymentMethod.PIX, september)
        f.athletes.saveDocument("52998224725", f.request(f.athlete))
        val card = f.code(PaymentMethod.CARD, october)
        val commission = Math.round(paid * 0.02)
        val pixNet = paid - 99
        val cardNet = paid - (Math.round(paid * 0.0299) + 49)
        f.remote.payment("pay_pix", "PIX", paid, pixNet, "RECEIVED", qr = "qr_1")
        f.event("evt_pix", "PAYMENT_RECEIVED", "pay_pix", qr = "qr_1")
        f.remote.payment("pay_card", "CREDIT_CARD", paid, cardNet, "CONFIRMED", reference = card.codeId.toString())
        f.event("evt_card", "PAYMENT_CONFIRMED", "pay_card", reference = card.codeId.toString(), billing = "CREDIT_CARD")
        assertEquals(paid, pix.amountCents)
        val wallet = (pixNet - commission) + (cardNet - commission)
        val fees = f.jdbc.sql("SELECT amount_cents FROM group_expenses WHERE origin='RECEIVABLES_FEES' ORDER BY amount_cents").query(Long::class.java).list()
        assertEquals(listOf(paid - (pixNet - commission), paid - (cardNet - commission)).sorted(), fees)
        assertEquals(0, f.count("group_expenses", "direction='IN'"), "sem entrada automática de repasse")
        val summary = br.com.saqz.groups.adapter.output.jdbc.finance.JdbcFinanceStatementRepository(f.ds).page(
            br.com.saqz.groups.application.finance.statement.FinanceStatementQuery(f.group, YearMonth.of(2026, 10), null, 50, 0)).summary
        assertEquals(2 * paid, summary.totalInCents)
        assertEquals(fees.sumOf { it!! }, summary.totalOutCents)
        assertEquals(wallet, summary.accumulatedBalanceCents)
    }

    @Test fun `total do atleta na Inicio soma o valor que ele paga`() = fixture { f ->
        f.sql("UPDATE receivable_group_links SET fee_policy='PASS'")
        val charge = f.monthly("2026-09", f.athlete)
        val payable = f.long("SELECT payable_cents FROM group_charges WHERE id='$charge'")!!
        val home = br.com.saqz.groups.adapter.output.jdbc.home.JdbcHomeRepository(f.ds).find(f.athlete, now, LocalDate.of(2026, 10, 20))
        assertEquals(payable, home.ownCharges!!.totalCents)
        assertEquals(payable, home.ownCharges!!.groups.single().totalCents)
    }

    @Test fun `PUT fee-policy vale so para cobranca nova e admin sem delegacao nao ve`() = fixture { f ->
        val old = f.monthly("2026-08", f.athlete)
        val admin = UUID.randomUUID()
        f.sql("INSERT INTO access_users(id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$admin','$admin',true,'Admin',now(),now())")
        f.sql("INSERT INTO group_memberships(group_id,user_id,role,created_at,updated_at) VALUES ('${f.group}','$admin','ADMIN',now(),now())")
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(
            f.groupReceivables.feePolicy(f.account, f.group, f.request(admin), FeePolicy.PASS)).error)
        val request = f.request(f.owner)
        val state = assertIs<FinancialResult.Success<GroupReceivablesState>>(f.groupReceivables.feePolicy(f.account, f.group, request, FeePolicy.PASS)).value
        assertEquals("PASS", state.feePolicy)
        assertIs<FinancialResult.Success<GroupReceivablesState>>(f.groupReceivables.feePolicy(f.account, f.group, request, FeePolicy.PASS))
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(f.groupReceivables.feePolicy(f.account, f.group, request, FeePolicy.ABSORB)).error)
        val fresh = f.monthly("2026-09", f.athlete)
        // A anterior nasceu em ABSORB e não é recalculada.
        assertEquals(10000, f.long("SELECT payable_cents FROM group_charges WHERE id='$old'"))
        assertEquals("ABSORB", f.string("SELECT fee_policy FROM group_charges WHERE id='$old'"))
        val expected = br.com.saqz.receivables.domain.FeeCalculator.passThrough(10000, f.live.current(setOf(PaymentMethod.CARD), now, f.account).single())
        assertEquals(expected, f.long("SELECT payable_cents FROM group_charges WHERE id='$fresh'"))
    }

    @Test fun `quote bate com o FeeCalculator nas duas politicas`() = fixture { f ->
        val quote = assertIs<FinancialResult.Success<GroupFeeQuote>>(f.groupReceivables.quote(f.account, f.group, 2000, f.request(f.owner))).value
        val schedules = f.live.current(PaymentMethod.entries.toSet(), now, f.account).associateBy { it.method }
        val pix = schedules.getValue(PaymentMethod.PIX); val card = schedules.getValue(PaymentMethod.CARD)
        val calc = br.com.saqz.receivables.domain.FeeCalculator
        assertEquals(PolicyQuote(2000, calc.net(2000, pix), calc.net(2000, card)), quote.absorb)
        val pass = maxOf(calc.passThrough(2000, pix), calc.passThrough(2000, card))
        assertEquals(PolicyQuote(pass, calc.net(pass, pix), calc.net(pass, card)), quote.pass)
        assertTrue(quote.pass.receivesCardCents >= 2000 && quote.pass.receivesPixCents > quote.pass.receivesCardCents)
        assertEquals("2% + R$ 0,99", quote.pixFeeLabel)
        assertEquals("4,99% + R$ 0,49", quote.cardFeeLabel)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.groupReceivables.quote(f.account, f.group, 2000, f.request(f.athlete))).error)
    }

    private fun fixture(block: (Fixture) -> Unit) { Remote().use { block(Fixture(it)) } }

    private inner class Fixture(val remote: Remote) {
        val ds = TestPostgres.migrated("classpath:db/migration", owner = this@ReceivablesV1IntegrationTest).dataSource
        val jdbc = JdbcClient.create(ds)
        val owner = UUID.randomUUID(); val athlete = UUID.randomUUID(); val other = UUID.randomUUID()
        val group = UUID.randomUUID(); val account = UUID.randomUUID()
        val token = "test-webhook-token-" + "a".repeat(32)
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val conditions = JdbcFinancialConditions(ds)
        val pricing = JdbcGroupReceivablesPricing(ds, conditions, clock)
        /** Tarifas do Asaas da conta simuladas como na produção: Pix R$ 0,99; cartão 2,99% + R$ 0,49 (iguais ao retrato do vínculo). */
        val live = ProviderFinancialConditions(conditions, FinancialFeeProvider { methods, _, _ ->
            methods.associateWith { if (it == PaymentMethod.PIX) ProviderPaymentFee(BigDecimal.ZERO, 99) else ProviderPaymentFee(BigDecimal("0.0299"), 49) }
        })
        val ledger = JdbcGroupChargeLedger(ds)
        val provider = HttpAsaasPaymentCodes(URI("http://127.0.0.1:${remote.server.address.port}/v3"), "wallet-platform",
            JdbcPaymentProviderCredentials(ds, secrets))
        val codes = JdbcPaymentCodeStore(ds, secrets)
        val rollout = JdbcReceivablesRollout(ds, { true }, clock)
        val athletes = AthletePayments(codes, ledger, conditions, ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) },
            rollout, provider, clock)
        val commissions = JdbcCommissionTransfers(ds, JdbcFinancialOperationStore(ds), provider, clock)
        val settlement = PaymentSettlement(JdbcSettlementStore(ds), codes, ledger, JdbcReceivablesNotices(ds), provider, commissions, clock)
        val reviews = PaymentReviews(JdbcReviewStore(ds), ledger, clock)
        val groupReceivables = ManageGroupReceivables(JdbcFinancialAccountRepository(ds),
            br.com.saqz.groups.adapter.output.jdbc.membership.JdbcGroupAdministrationDirectory(ds),
            br.com.saqz.groups.adapter.output.jdbc.group.JdbcGroupFinancialSetupLookup(ds), JdbcGroupReceivablesStore(ds),
            live, ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) }, clock, rollout)
        val events = JdbcPaymentEvents(ds, secrets, object : PaymentExecution {
            override fun create(instrumentId: UUID) = Unit
            override fun cancel(instrumentId: UUID, request: FinancialRequest) = Unit
            override fun reconcile(instrumentId: UUID) = false
        }, clock, settlement)
        private val charges = JdbcChargeTransactionRepository(ds, receivables = pricing)
        init {
            for ((id, name) in listOf(owner to "Dono", athlete to "Pagador de teste", other to "Outro atleta"))
                sql("INSERT INTO access_users(id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$id',true,'$name',now(),now())")
            sql("INSERT INTO access_groups(id,owner_user_id,creation_key,name,time_zone,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Vôlei do CERET','UTC',now(),now())")
            sql("INSERT INTO group_memberships(group_id,user_id,role,created_at,updated_at) VALUES ('$group','$owner','ADMIN',now(),now()),('$group','$athlete','ATHLETE',now(),now()),('$group','$other','ATHLETE',now(),now())")
            sql("INSERT INTO notification_devices(installation_id,user_id,token,platform) VALUES ('${UUID.randomUUID()}','$owner','token-owner','IOS')")
            sql("""INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,provider_account_id,credentials_encrypted,registration,new_operations_enabled,created_at,updated_at,pix_key_id,pix_key,pix_key_ready)
                VALUES ('$account','$owner','$owner','ciphertext','remote-account','${secrets.encrypt(account, "provider-key", "local-key")}','APPROVED',true,now(),now(),'key_1','evp-key',true)""")
            sql("INSERT INTO receivable_webhook_credentials(account_id,token_encrypted) VALUES ('$account','${secrets.encrypt(account, "webhook-token", token)}')")
            sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'")
            sql("""INSERT INTO receivable_group_links(account_id,group_id,enabled,pix_enabled,card_enabled,activated_by,activated_at,fee_policy,fee_snapshot)
                VALUES ('$account','$group',true,true,true,'$owner',now(),'ABSORB','{"PIX":{"rate":0,"fixedCents":99,"minimumCents":0,"maximumCents":null},"CARD":{"rate":0.0299,"fixedCents":49,"minimumCents":0,"maximumCents":null}}')""")
            sql("INSERT INTO receivable_terms VALUES ('v1','Termos','${"a".repeat(64)}','2026-09-01','2026-09-01')")
            for (method in PaymentMethod.entries) sql("INSERT INTO receivable_fee_schedules VALUES ('${UUID.randomUUID()}','$method',0,0,0.02,0,'v1','2026-09-01','2026-09-01','$owner')")
        }
        fun request(actor: UUID) = FinancialRequest(UUID.randomUUID(), actor)
        fun monthly(month: String, member: UUID): UUID = JdbcTransactionRunner(ds).inTransaction {
            val ym = YearMonth.parse(month)
            charges.createMonthlyCharge(MonthlyGenerationCommand(UUID.randomUUID(), group, owner, ym, 10000, ym.atDay(10), setOf(member)), member, now).id
        }
        fun code(method: PaymentMethod, vararg ids: UUID) =
            assertIs<FinancialResult.Success<PaymentCodeView>>(athletes.create(group, method, ids.toList(), request(athlete))).value
        fun failure(method: PaymentMethod, vararg ids: UUID) =
            assertIs<FinancialResult.Failure>(athletes.create(group, method, ids.toList(), request(athlete))).error
        fun event(id: String, type: String, payment: String, qr: String? = null, reference: String? = null, billing: String = "PIX") {
            val body = mapper.writeValueAsString(mapOf("id" to id, "event" to type, "payment" to mapOf("id" to payment,
                "billingType" to billing, "pixQrCodeId" to qr, "externalReference" to reference)))
            assertTrue(events.accept(account, token, body))
            events.processPending()
        }
        fun titles() = jdbc.sql("SELECT push_title FROM group_messages WHERE channel='PAYMENT' ORDER BY sequence").query(String::class.java).list()
        fun body(title: String) = jdbc.sql("SELECT push_body FROM group_messages WHERE push_title=:t").param("t", title).query(String::class.java).single()
        fun sql(s: String) { jdbc.sql(s).update() }
        fun count(table: String, where: String = "true") = jdbc.sql("SELECT count(*) FROM $table WHERE $where").query(Int::class.java).single()
        fun string(s: String) = jdbc.sql(s).query(String::class.java).single()
        fun stringOrNull(s: String): String? = jdbc.sql(s).query { rs, _ -> rs.getString(1) }.list().single()
        fun long(s: String): Long? = jdbc.sql(s).query { rs, _ -> rs.getObject(1)?.let { (it as Number).toLong() } }.list().single()
    }

    private inner class Remote : AutoCloseable {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var qrPosts = 0; var transferPosts = 0
        var lastQr: JsonNode? = null; var lastPayment: JsonNode? = null; var lastTransfer: JsonNode? = null; var customer: JsonNode? = null
        val deletes = mutableListOf<String>()
        private val payments = mutableMapOf<String, Map<String, Any?>>()
        fun payment(id: String, billing: String, cents: Long, net: Long, status: String, qr: String? = null, reference: String? = null,
                    customer: String? = null) {
            payments[id] = mapOf("id" to id, "billingType" to billing, "value" to BigDecimal.valueOf(cents, 2),
                "netValue" to BigDecimal.valueOf(net, 2), "status" to status, "pixQrCodeId" to qr, "externalReference" to reference,
                "customer" to customer, "deleted" to false)
        }
        init {
            server.createContext("/v3") { exchange ->
                check(exchange.requestHeaders.getFirst("access_token") == "local-key")
                val path = exchange.requestURI.path; val method = exchange.requestMethod
                val response: Any = when {
                    path == "/v3/pix/qrCodes/static" && method == "POST" -> {
                        qrPosts++; lastQr = mapper.readTree(exchange.requestBody.readBytes())
                        mapOf("id" to "qr_1", "payload" to "pix-payload", "encodedImage" to "aW1n")
                    }
                    path == "/v3/customers" && method == "POST" -> {
                        customer = mapper.readTree(exchange.requestBody.readBytes())
                        (customer!!.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()).put("id", "cus_local")
                    }
                    path == "/v3/customers" -> mapOf("hasMore" to false, "data" to emptyList<Any>())
                    path == "/v3/customers/cus_joao" -> mapOf("id" to "cus_joao", "name" to "João P. Silva")
                    path == "/v3/payments" && method == "POST" -> {
                        lastPayment = mapper.readTree(exchange.requestBody.readBytes())
                        mapOf("id" to "pay_card", "externalReference" to lastPayment!!["externalReference"].asText(),
                            "value" to lastPayment!!["value"].decimalValue(), "invoiceUrl" to "https://sandbox.asaas.com/i/pay_card")
                    }
                    path == "/v3/transfers" && method == "POST" -> {
                        transferPosts++; lastTransfer = mapper.readTree(exchange.requestBody.readBytes())
                        mapOf("id" to "tra_1", "value" to lastTransfer!!["value"].decimalValue())
                    }
                    method == "DELETE" -> { deletes += path; mapOf("deleted" to true, "id" to path.substringAfterLast('/')) }
                    path.startsWith("/v3/payments/") -> payments.getValue(path.substringAfterLast('/'))
                    else -> mapOf("hasMore" to false, "data" to emptyList<Any>())
                }
                val bytes = mapper.writeValueAsBytes(response)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
        override fun close() { server.stop(0) }
    }
}
