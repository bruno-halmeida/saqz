package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationWhatsApp
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcReceivablesNotices
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcGroupChargeLedger
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.PublicAttendanceLinkFactory
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import br.com.saqz.groups.application.finance.charge.MonthlyGenerationCommand
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

/** F3 (mensalidade no cartão) e F4 (hora do acerto, link, "Cobrar") com Postgres e o Asaas simulado. */
class ReceivablesV1RecurrenceAndSettleIntegrationTest {
    private val mapper = ObjectMapper()

    @Test fun `adesao so para mensalista com CPF e mensalidade em aberto cobrada na hora`() = fixture { f ->
        f.sql("UPDATE group_memberships SET membership_type='AVULSO' WHERE user_id='${f.athlete}'")
        assertEquals(FinancialError.CONFLICT, f.startFailure())
        f.sql("UPDATE group_memberships SET membership_type='MENSALISTA' WHERE user_id='${f.athlete}'")
        assertEquals(FinancialError.DOCUMENT_REQUIRED, f.startFailure())
        f.athletes.saveDocument("52998224725", f.request(f.athlete))
        f.monthly("2026-10")
        val request = f.request(f.athlete)
        val checkout = assertIs<FinancialResult.Success<RecurrenceCheckout>>(f.cards.start(f.group, request)).value
        assertEquals("https://sandbox.asaas.com/c/chk_1", checkout.checkoutUrl)
        assertEquals(checkout, assertIs<FinancialResult.Success<RecurrenceCheckout>>(f.cards.start(f.group, request)).value)
        val body = f.remote.lastCheckout!!
        assertEquals("2026-10-20", body["subscription"]["nextDueDate"].asText(), "mês em aberto: cobrado na hora")
        assertEquals(0, BigDecimal("100.00").compareTo(body["items"][0]["value"].decimalValue()))
        assertEquals(0, BigDecimal("2.00").compareTo(body["splits"][0]["fixedValue"].decimalValue()))
        assertEquals("cus_local", body["customer"].asText())
        assertEquals(1, f.remote.checkoutPosts)
        assertEquals("AUTHORIZING", f.cards.view(f.group, f.athlete)!!.status)
    }

    @Test fun `pagamento da assinatura da baixa na mensalidade inclusive quando o aviso chega antes da cobranca`() = fixture { f ->
        f.active()
        f.remote.payment("pay_sub_1", "CREDIT_CARD", 10000, 9500, "CONFIRMED", due = "2026-11-10", subscription = "sub_1")
        f.event("evt_1", "PAYMENT_CONFIRMED", "pay_sub_1", subscription = "sub_1")
        assertEquals(0, f.count("payment_settlements"), "mensalidade de novembro ainda não existe: tenta de novo")
        val november = f.monthly("2026-11")
        f.clock.advance(120)
        f.events.processPending()
        assertEquals("PAID", f.string("SELECT status::text FROM group_charges WHERE id='$november'"))
        assertEquals("CARD", f.string("SELECT app_method FROM group_charges WHERE id='$november'"))
        assertEquals(0, f.remote.transferPosts, "cartão: comissão na divisão")
        assertEquals(1, f.cards.view(f.group, f.athlete)!!.history.size)
        f.remote.payment("pay_sub_late", "CREDIT_CARD", 10000, 9500, "CONFIRMED", due = "2026-12-10", subscription = "sub_1")
        f.event("evt_late", "PAYMENT_CONFIRMED", "pay_sub_late", subscription = "sub_1")
        f.clock.advance(4 * 86400L)
        f.events.processPending()
        assertEquals(10000, f.long("SELECT amount_cents FROM payment_reviews WHERE provider_payment_id='pay_sub_late'"), "sem mensalidade depois de dias: revisão")
    }

    @Test fun `reajuste avisa 7 dias antes e o Asaas so cobra o valor novo no ciclo certo`() = fixture { f ->
        f.clock.set(java.time.Instant.parse("2026-10-05T15:00:00Z"))
        f.active()
        f.sql("UPDATE access_groups SET monthly_fee_cents=12000")
        f.cards.adjustValues()
        val row = f.jdbc.sql("SELECT next_amount_cents,next_amount_from::text FROM card_subscriptions").query { rs, _ -> rs.getLong(1) to rs.getString(2) }.single()
        assertEquals(12000L to "2026-11-10", row, "05/10 + 7 dias = 12/10: o primeiro vencimento depois é 10/11")
        assertEquals("A partir de 10/11, R$ 120,00 no automático do seu cartão.", f.body("Mensalidade com valor novo"))
        assertTrue(f.string("SELECT whatsapp_body FROM group_messages WHERE push_title='Mensalidade com valor novo'").startsWith("A mensalidade muda de R$ 100,00 para R$ 120,00 a partir de 10/11."))
        assertEquals(0, f.remote.subscriptionPuts, "a cobrança de 10/10 ainda é do valor antigo")
        f.clock.set(java.time.Instant.parse("2026-10-11T15:00:00Z"))
        f.cards.adjustValues()
        assertEquals(1, f.count("group_messages", "push_title='Mensalidade com valor novo'"))
        assertEquals(1, f.remote.subscriptionPuts)
        assertFalse(f.remote.lastPut!!["updatePendingPayments"].asBoolean())
        assertEquals(12000, f.long("SELECT amount_cents FROM card_subscriptions"))
    }

    @Test fun `isencao remove a cobranca do mes no Asaas e corte encerra a assinatura`() = fixture { f ->
        f.active()
        f.sql("UPDATE group_memberships SET monthly_fee_exempt=true WHERE user_id='${f.athlete}'")
        f.remote.subscriptionPayments = listOf(mapOf("id" to "pay_next", "dueDate" to "2026-11-10", "status" to "PENDING", "value" to 100),
            mapOf("id" to "pay_old", "dueDate" to "2026-09-10", "status" to "OVERDUE", "value" to 100))
        f.cards.removeExemptMonths()
        assertEquals(listOf("/v3/payments/pay_next"), f.remote.deletes)
        f.sql("UPDATE group_memberships SET monthly_fee_exempt=false,membership_type='AVULSO' WHERE user_id='${f.athlete}'")
        f.cards.enforceCutoffs()
        assertEquals("ENDED", f.string("SELECT status FROM card_subscriptions"))
        assertEquals("NOT_MENSALISTA", f.string("SELECT end_reason FROM card_subscriptions"))
        assertTrue(f.remote.deletes.contains("/v3/subscriptions/sub_1"))
        assertFalse(f.jdbc.sql("SELECT remote_cancel_pending FROM card_subscriptions").query(Boolean::class.java).single())
    }

    @Test fun `desligar o grupo corta a assinatura`() = fixture { f ->
        f.active()
        JdbcGroupReceivablesStore(f.ds).groupDeleted(f.group, f.owner, f.clock.instant())
        assertEquals("GROUP_DELETED", f.string("SELECT end_reason FROM card_subscriptions"))
        f.cards.enforceCutoffs()
        assertTrue(f.remote.deletes.contains("/v3/subscriptions/sub_1"))
    }

    @Test fun `cancelar exige motivo continua mensalista e avisa o gestor`() = fixture { f ->
        f.active()
        assertIs<FinancialResult.Success<*>>(f.cards.cancel(f.group, CancelReason.PREFER_PIX, f.request(f.athlete)))
        assertEquals("PREFER_PIX", f.string("SELECT cancel_reason FROM card_subscriptions"))
        assertEquals("MENSALISTA", f.string("SELECT membership_type::text FROM group_memberships WHERE user_id='${f.athlete}'"))
        assertEquals("As próximas mensalidades chegam para pagar por Pix.", f.body("Pagador de teste cancelou o automático"))
        val controller = br.com.saqz.receivables.adapter.input.http.CardSubscriptionsController({ f.athlete }, f.cards)
        val missing = controller.cancel(br.com.saqz.sharedkernel.RequestIdentity("x", "x@x", true), f.group,
            br.com.saqz.receivables.adapter.input.http.RecurrenceCommand().apply { requestId = UUID.randomUUID() })
        assertEquals(400, missing.statusCode.value())
    }

    @Test fun `cartao recusado avisa atleta e gestor uma vez com o copia e cola numa segunda mensagem`() = fixture { f ->
        f.active()
        val october = f.monthly("2026-10")
        f.remote.payment("pay_refused", "CREDIT_CARD", 10000, 9500, "PENDING", due = "2026-10-10", subscription = "sub_1")
        f.event("evt_r", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED", "pay_refused", subscription = "sub_1")
        f.event("evt_r2", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED", "pay_refused", subscription = "sub_1")
        assertEquals("A mensalidade de outubro continua em aberto. Atualize o cartão ou pague por Pix.", f.body("Seu cartão não passou"))
        assertEquals("A mensalidade de outubro de Pagador de teste não passou no cartão. A pessoa já foi avisada.", f.body("Cartão recusado"))
        val wa = f.jdbc.sql("SELECT whatsapp_body,whatsapp_button_label,whatsapp_follow_up FROM group_messages WHERE push_title='Seu cartão não passou'")
            .query { rs, _ -> Triple(rs.getString(1), rs.getString(2), rs.getString(3)) }.single()
        assertTrue(wa.first.startsWith("Seu cartão não passou. A mensalidade de outubro (R$ 100,00) continua em aberto."))
        assertEquals("Atualizar cartão", wa.second)
        assertEquals("pix-payload", wa.third)
        assertEquals(1, f.count("group_messages", "push_title='Seu cartão não passou'"))
        assertEquals("PENDING", f.string("SELECT status::text FROM group_charges WHERE id='$october'"))
        assertEquals("REFUSED", f.cards.view(f.group, f.athlete)!!.status)
        assertEquals("ACTIVE", f.string("SELECT status FROM card_subscriptions"))
    }

    @Test fun `hora do acerto do jogo sai 30 minutos depois do fim uma vez so com devedor e sem nomes`() = fixture { f ->
        f.sql("INSERT INTO group_whatsapp_bindings(group_id,whatsapp_jid,invite_code,group_name,instance_jid,created_by) VALUES ('${f.group}','1203@g.us','inv','Vôlei','5511@s.whatsapp.net','${f.owner}')")
        val game = f.game(endedMinutesAgo = 29)
        f.gameCharge(game, f.athlete, 0)
        f.gameCharge(game, f.athlete, 1)
        f.settle.games()
        assertEquals(0, f.count("group_messages", "event_kind='SETTLE_TIME'"))
        f.clock.advance(120)
        f.settle.games(); f.settle.games()
        val text = f.string("SELECT body FROM group_messages WHERE event_kind='SETTLE_TIME'")
        assertTrue(text.contains("Jogo finalizado, hora do acerto") && text.contains("Quem jogou avulso paga pelo Saqz"))
        assertFalse(text.contains("Pagador"))
        assertTrue(f.string("SELECT whatsapp_button_url FROM group_messages WHERE event_kind='SETTLE_TIME'").startsWith("https://links.saqz.app/acerto/"))
        assertEquals(1, f.count("notification_whatsapp_group_queue"))
        assertEquals(listOf("R$ 200,00 do jogo de terça no Vôlei do CERET. Toque para pagar."),
            f.jdbc.sql("SELECT push_body FROM group_messages WHERE push_title='Hora do acerto'").query(String::class.java).list())
        assertEquals(game, f.jdbc.sql("SELECT settle_game_id FROM group_messages WHERE push_title='Hora do acerto'").query(UUID::class.java).single())
        val quiet = f.game(endedMinutesAgo = 200)
        f.settle.games()
        assertEquals(1, f.count("group_messages", "event_kind='SETTLE_TIME'"), "jogo $quiet sem devedor: nada")
    }

    @Test fun `hora do acerto dos mensalistas as 9h sem quem esta no cartao`() = fixture { f ->
        f.active()
        f.monthly("2026-10", day = 20)
        f.monthly("2026-10", member = f.other, day = 20)
        f.clock.set(Instant.parse("2026-10-20T11:59:00Z"))
        f.settle.monthly()
        assertEquals(0, f.count("group_messages", "event_kind='SETTLE_TIME'"), "antes das 9h em São Paulo")
        f.clock.set(Instant.parse("2026-10-20T12:01:00Z"))
        f.settle.monthly(); f.settle.monthly()
        assertTrue(f.string("SELECT body FROM group_messages WHERE event_kind='SETTLE_TIME'").contains("Mensalidade de outubro · vence hoje"))
        val pushes = f.jdbc.sql("""SELECT n.recipient_id FROM group_notifications n JOIN group_messages m ON m.id=n.message_id
            WHERE m.push_title='Hora do acerto'""").query(UUID::class.java).list()
        assertEquals(listOf(f.other), pushes)
    }

    @Test fun `me traz o nome do grupo e a oferta NONE para o mensalista e nada para o avulso`() = fixture { f ->
        val me = assertIs<FinancialResult.Success<AthletePaymentState>>(f.athletes.me(f.group, f.request(f.athlete))).value
        assertEquals("Vôlei do CERET", me.groupName)
        val offer = assertIs<RecurrenceView>(me.recurrence)
        assertEquals("NONE", offer.status)
        assertEquals(10000, offer.amountCents)
        assertEquals(LocalDate.of(2026, 11, 10), offer.nextChargeDate)
        assertTrue(offer.history.isEmpty())
        f.sql("UPDATE group_memberships SET membership_type='AVULSO' WHERE user_id='${f.athlete}'")
        assertNull(assertIs<FinancialResult.Success<AthletePaymentState>>(f.athletes.me(f.group, f.request(f.athlete))).value.recurrence)
        f.sql("UPDATE group_memberships SET membership_type='MENSALISTA' WHERE user_id='${f.athlete}'")
        f.sql("UPDATE receivable_group_links SET enabled=false")
        assertNull(f.cards.view(f.group, f.athlete), "sem recebimento, sem oferta")
    }

    @Test fun `historico marca a cobranca feita na hora da adesao`() = fixture { f ->
        f.monthly("2026-10")
        f.active()
        f.remote.payment("pay_sub_0", "CREDIT_CARD", 10000, 9500, "CONFIRMED", due = "2026-10-20", subscription = "sub_1")
        f.event("evt_0", "PAYMENT_CONFIRMED", "pay_sub_0", subscription = "sub_1")
        val history = f.cards.view(f.group, f.athlete)!!.history
        assertEquals(1, history.size)
        assertTrue(history.single().atAdhesion, "outubro em aberto: cobrado na adesão, no dia 20")
        assertEquals(LocalDate.of(2026, 10, 1), history.single().month)
    }

    @Test fun `link do acerto resolve so para membro`() = fixture { f ->
        val game = f.game(endedMinutesAgo = 40)
        val url = JdbcSettleLinks(f.ds, URI("https://links.saqz.app"), f.clock).gameUrl(f.group, game)
        val code = url.substringAfterLast('/')
        val ok = assertIs<FinancialResult.Success<SettleDestination>>(f.settle.resolve(code, f.request(f.athlete))).value
        assertEquals(SettleDestination(f.group, game, null, "Vôlei do CERET"), ok)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.settle.resolve(code, f.request(UUID.randomUUID()))).error)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.settle.resolve("nada", f.request(f.athlete))).error)
    }

    @Test fun `Cobrar com recebimentos leva Pagar no app e copia e cola e sem recebimentos sai como hoje`() = fixture { f ->
        val charge = f.monthly("2026-10")
        f.sql("UPDATE access_users SET phone='+5511999990000',verified_phone='+5511999990000' WHERE id='${f.athlete}'")
        f.sql("UPDATE access_groups SET pix_key='marcos@exemplo.com'")
        val sent = mutableListOf<WhatsAppNotification>()
        val queue = JdbcNotificationWhatsApp(f.ds, JdbcTransactionRunner(f.ds), PublicAttendanceLinkFactory(URI("https://links.saqz.app")),
            JdbcGroupReceivablesPricing(f.ds, f.conditions, f.clock) { g, p, c -> f.settle.collection(g, p, c) })
        f.charge(charge)
        queue.drain({ sent += it; WhatsAppDelivery.Accepted })
        val active = sent.single()
        assertTrue(active.body.contains("Você tem R$ 100,00 em aberto (venceu em 10/10).\nPague pelo app ou copie o código da próxima mensagem"))
        assertFalse(active.body.contains("marcos@exemplo.com"))
        assertEquals("Pagar no app", active.button!!.label)
        assertTrue(active.button!!.url.startsWith("https://links.saqz.app/acerto/"))
        assertEquals("pix-payload", active.followUp)
        f.sql("UPDATE receivable_group_links SET enabled=false")
        f.charge(charge)
        queue.drain({ sent += it; WhatsAppDelivery.Accepted })
        val plain = sent.last()
        assertTrue(plain.body.contains("Pix: marcos@exemplo.com"))
        assertNull(plain.button); assertNull(plain.followUp)
    }

    @Test fun `Cobrar com a chave Pix ainda nao pronta sai uma vez so sem o copia e cola e depois sai com ele`() = fixture { f ->
        val charge = f.monthly("2026-10")
        f.sql("UPDATE access_users SET phone='+5511999990000',verified_phone='+5511999990000' WHERE id='${f.athlete}'")
        f.sql("UPDATE receivable_accounts SET pix_key_ready=false")
        val sent = mutableListOf<WhatsAppNotification>()
        val queue = JdbcNotificationWhatsApp(f.ds, JdbcTransactionRunner(f.ds), PublicAttendanceLinkFactory(URI("https://links.saqz.app")),
            JdbcGroupReceivablesPricing(f.ds, f.conditions, f.clock) { g, p, c -> f.settle.collection(g, p, c) })
        f.charge(charge)
        queue.drain({ sent += it; WhatsAppDelivery.Accepted })
        queue.drain({ sent += it; WhatsAppDelivery.Accepted })
        assertEquals(1, sent.size, "PIX_NOT_READY dentro do código interno não derruba o commit da fila")
        assertEquals(listOf("ACCEPTED"), f.jdbc.sql("SELECT status FROM notification_whatsapp_queue").query(String::class.java).list())
        assertEquals("Pagar no app", sent.single().button!!.label)
        assertNull(sent.single().followUp)
        f.sql("UPDATE receivable_accounts SET pix_key_ready=true")
        f.charge(charge)
        queue.drain({ sent += it; WhatsAppDelivery.Accepted })
        assertEquals(2, sent.size)
        assertEquals("pix-payload", sent.last().followUp)
        assertEquals(listOf("ACCEPTED", "ACCEPTED"), f.jdbc.sql("SELECT status FROM notification_whatsapp_queue ORDER BY notification_id").query(String::class.java).list())
    }

    @Test fun `adesao cobrada na hora no dia 22 realinha a assinatura para o dia 10 e a troca do dia realinha de novo`() = fixture { f ->
        f.clock.set(java.time.Instant.parse("2026-10-22T15:00:00Z"))
        f.monthly("2026-10")
        f.active()
        assertEquals("2026-10-22", f.remote.lastCheckout!!["subscription"]["nextDueDate"].asText(), "mês em aberto: primeira cobrança hoje")
        f.remote.subscriptionPayments = listOf(mapOf("id" to "pay_wrong", "dueDate" to "2026-11-22", "status" to "PENDING", "value" to 100))
        f.cards.realignDueDates()
        assertEquals(listOf("2026-11-10"), f.remote.realigned, "outubro já foi cobrado na adesão: a seguinte é 10/11")
        assertEquals(listOf("/v3/payments/pay_wrong"), f.remote.deletes, "cobrança gerada no dia 22 sai para o Asaas gerar no dia 10")
        f.remote.subscriptionPayments = emptyList()
        f.cards.realignDueDates()
        assertEquals(1, f.remote.realigned.size, "já alinhada: não repete")
        f.sql("UPDATE group_memberships SET monthly_due_day=15 WHERE user_id='${f.athlete}'")
        f.cards.realignDueDates()
        assertEquals("2026-11-15", f.remote.realigned.last())
    }

    @Test fun `trava global de deploy desligada nao corta assinatura e o adm desligando a pessoa corta`() = fixture { f ->
        f.active()
        val locked = LaunchReceivablesRollout(f.rollout, false)
        val athletes = AthletePayments(f.codes, f.ledger, f.conditions, ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) },
            locked, f.provider, f.clock)
        val cards = CardSubscriptions(f.subscriptions, f.codes, athletes, f.ledger, f.pricing, f.conditions, f.provider, f.notices, f.links, f.clock)
        cards.enforceCutoffs()
        assertEquals("ACTIVE", f.string("SELECT status FROM card_subscriptions"), "launch-enabled desligado: só barra negócio novo")
        assertTrue(f.remote.deletes.isEmpty())
        f.sql("INSERT INTO receivable_rollout_users VALUES ('${f.owner}',1)")
        f.sql("INSERT INTO receivable_rollout_overrides VALUES ('${f.owner}','BACKEND','DENY')")
        cards.enforceCutoffs()
        assertEquals("INELIGIBLE", f.string("SELECT end_reason FROM card_subscriptions"))
        assertTrue(f.remote.deletes.contains("/v3/subscriptions/sub_1"))
    }

    private fun fixture(block: (Fixture) -> Unit) { Remote().use { block(Fixture(it)) } }

    class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant = now
        fun advance(seconds: Long) { now = now.plusSeconds(seconds) }
        fun set(instant: Instant) { now = instant }
    }

    private inner class Fixture(val remote: Remote) {
        val ds = TestPostgres.migrated("classpath:db/migration", owner = this@ReceivablesV1RecurrenceAndSettleIntegrationTest).dataSource
        val jdbc = JdbcClient.create(ds)
        val owner = UUID.randomUUID(); val athlete = UUID.randomUUID(); val other = UUID.randomUUID()
        val group = UUID.randomUUID(); val account = UUID.randomUUID()
        val token = "test-webhook-token-" + "a".repeat(32)
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val clock = MutableClock(Instant.parse("2026-10-20T15:00:00Z"))
        val conditions = JdbcFinancialConditions(ds)
        val pricing = JdbcGroupReceivablesPricing(ds, conditions, clock)
        val ledger = JdbcGroupChargeLedger(ds)
        val provider = HttpAsaasPaymentCodes(URI("http://127.0.0.1:${remote.server.address.port}/v3"), "wallet-platform",
            JdbcPaymentProviderCredentials(ds, secrets), URI("https://saqz.test"))
        val codes = JdbcPaymentCodeStore(ds, secrets)
        val rollout = JdbcReceivablesRollout(ds, { true }, clock)
        val subscriptions = JdbcSubscriptionStore(ds)
        val links = JdbcSettleLinks(ds, URI("https://links.saqz.app"), clock)
        val notices = JdbcReceivablesNotices(ds)
        lateinit var cards: CardSubscriptions
        val athletes = AthletePayments(codes, ledger, conditions, ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) },
            rollout, provider, clock, recurrence = { g, m -> cards.view(g, m) })
        init { cards = CardSubscriptions(subscriptions, codes, athletes, ledger, pricing, conditions, provider, notices, links, clock) }
        val settle = SettleTime(ledger, codes, athletes, subscriptions, links, notices, clock)
        val commissions = JdbcCommissionTransfers(ds, JdbcFinancialOperationStore(ds), provider, clock)
        val settlement = PaymentSettlement(JdbcSettlementStore(ds), codes, ledger, notices, provider, commissions, clock,
            subscriptions = subscriptions, cards = cards)
        val events = JdbcPaymentEvents(ds, secrets, object : PaymentExecution {
            override fun create(instrumentId: UUID) = Unit
            override fun cancel(instrumentId: UUID, request: FinancialRequest) = Unit
            override fun reconcile(instrumentId: UUID) = false
        }, clock, settlement)
        private val charges = JdbcChargeTransactionRepository(ds, receivables = pricing)
        init {
            for ((id, name) in listOf(owner to "Dono", athlete to "Pagador de teste", other to "Outro atleta"))
                sql("INSERT INTO access_users(id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$id',true,'$name',now(),now())")
            sql("INSERT INTO access_groups(id,owner_user_id,creation_key,name,time_zone,monthly_fee_cents,monthly_due_day,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Vôlei do CERET','UTC',10000,10,now(),now())")
            sql("INSERT INTO group_memberships(group_id,user_id,role,membership_type,created_at,updated_at) VALUES ('$group','$owner','ADMIN','AVULSO',now(),now()),('$group','$athlete','ATHLETE','MENSALISTA',now(),now()),('$group','$other','ATHLETE','MENSALISTA',now(),now())")
            sql("""INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,provider_account_id,credentials_encrypted,registration,new_operations_enabled,created_at,updated_at,pix_key_id,pix_key,pix_key_ready)
                VALUES ('$account','$owner','$owner','ciphertext','remote-account','${secrets.encrypt(account, "provider-key", "local-key")}','APPROVED',true,now(),now(),'key_1','evp-key',true)""")
            sql("INSERT INTO receivable_webhook_credentials(account_id,token_encrypted) VALUES ('$account','${secrets.encrypt(account, "webhook-token", token)}')")
            sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'")
            sql("""INSERT INTO receivable_group_links(account_id,group_id,enabled,pix_enabled,card_enabled,activated_by,activated_at,fee_policy)
                VALUES ('$account','$group',true,true,true,'$owner',now(),'ABSORB')""")
            sql("INSERT INTO receivable_terms VALUES ('v1','Termos','${"a".repeat(64)}','2026-09-01','2026-09-01')")
            for (method in PaymentMethod.entries) sql("INSERT INTO receivable_fee_schedules VALUES ('${UUID.randomUUID()}','$method',0,0,0.02,0,'v1','2026-09-01','2026-09-01','$owner')")
        }
        fun request(actor: UUID) = FinancialRequest(UUID.randomUUID(), actor)
        fun startFailure() = assertIs<FinancialResult.Failure>(cards.start(group, request(athlete))).error
        /** Assinatura ativa no Asaas (sub_1), criada pela adesão e ativada pelo checkout. */
        fun active() {
            athletes.saveDocument("52998224725", request(athlete))
            assertIs<FinancialResult.Success<RecurrenceCheckout>>(cards.start(group, request(athlete)))
            remote.checkoutSubscription = "sub_1"
            cards.activatePending()
            assertEquals("ACTIVE", string("SELECT status FROM card_subscriptions"))
        }
        fun monthly(month: String, member: UUID = athlete, day: Int = 10): UUID = JdbcTransactionRunner(ds).inTransaction {
            val ym = YearMonth.parse(month)
            charges.createMonthlyCharge(MonthlyGenerationCommand(UUID.randomUUID(), group, owner, ym, 10000, ym.atDay(day), setOf(member)), member, clock.instant()).id
        }
        private var games = 0
        fun game(endedMinutesAgo: Long): UUID {
            val id = UUID.randomUUID()
            val time = "%02d:00".format(10 + games++)
            val starts = clock.instant().minusSeconds((endedMinutesAgo + 90) * 60)
            jdbc.sql("""INSERT INTO games(id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at)
                VALUES (:id, :g, 'Jogo', '2026-10-20', CAST(:time AS time), 'America/Sao_Paulo', :starts, 90, :starts, 'CERET', 'Rua 1', 12, 'PUBLISHED', now(), now())""")
                .param("id", id).param("g", group).param("starts", java.sql.Timestamp.from(starts)).param("time", time).update()
            return id
        }
        fun gameCharge(game: UUID, member: UUID, guest: Int) {
            val guestName = if (guest > 0) "'Rafa'" else "NULL"
            sql("""INSERT INTO group_charges(id,group_id,member_user_id,kind,game_id,amount_cents,due_date,status,member_display_name,guest_seq,guest_display_name,created_by_user_id,changed_by_user_id,created_at,updated_at)
                VALUES ('${UUID.randomUUID()}','$group','$member','GAME','$game',10000,'2026-10-20','PENDING','Pagador',$guest,$guestName,'$owner','$owner',now(),now())""")
        }
        /** "Cobrar": aviso de cobrança da mensalidade para o atleta (canal CHARGE), como o lembrete do gestor grava. */
        fun charge(chargeId: UUID) {
            val message = UUID.randomUUID()
            sql("""INSERT INTO group_messages(id,group_id,author_id,author_name,channel,request_id,body,charge_id)
                VALUES ('$message','$group','$owner','Dono','CHARGE','${UUID.randomUUID()}','Cobrança','$chargeId')""")
            sql("INSERT INTO group_notifications(recipient_id,message_id) VALUES ('$athlete','$message')")
        }
        fun event(id: String, type: String, payment: String, subscription: String? = null) {
            val body = mapper.writeValueAsString(mapOf("id" to id, "event" to type, "payment" to mapOf("id" to payment,
                "billingType" to "CREDIT_CARD", "subscription" to subscription)))
            assertTrue(events.accept(account, token, body))
            events.processPending()
        }
        fun body(title: String) = jdbc.sql("SELECT push_body FROM group_messages WHERE push_title=:t").param("t", title).query(String::class.java).single()
        fun sql(s: String) { jdbc.sql(s).update() }
        fun count(table: String, where: String = "true") = jdbc.sql("SELECT count(*) FROM $table WHERE $where").query(Int::class.java).single()
        fun string(s: String) = jdbc.sql(s).query(String::class.java).single()
        fun long(s: String): Long? = jdbc.sql(s).query { rs, _ -> rs.getObject(1)?.let { (it as Number).toLong() } }.list().single()
    }

    private inner class Remote : AutoCloseable {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var checkoutPosts = 0; var transferPosts = 0; var subscriptionPuts = 0
        var lastCheckout: JsonNode? = null; var lastPut: JsonNode? = null
        var checkoutSubscription: String? = null
        var subscriptionPayments: List<Map<String, Any>> = emptyList()
        val realigned = mutableListOf<String>()
        val deletes = mutableListOf<String>()
        private val payments = mutableMapOf<String, Map<String, Any?>>()
        fun payment(id: String, billing: String, cents: Long, net: Long, status: String, due: String, subscription: String? = null) {
            payments[id] = mapOf("id" to id, "billingType" to billing, "value" to BigDecimal.valueOf(cents, 2),
                "netValue" to BigDecimal.valueOf(net, 2), "status" to status, "dueDate" to due, "subscription" to subscription, "deleted" to false)
        }
        init {
            server.createContext("/v3") { exchange ->
                val path = exchange.requestURI.path; val method = exchange.requestMethod; val query = exchange.requestURI.query.orEmpty()
                val response: Any = when {
                    path == "/v3/pix/qrCodes/static" && method == "POST" -> mapOf("id" to "qr_1", "payload" to "pix-payload", "encodedImage" to "aW1n")
                    path == "/v3/customers" && method == "POST" -> mapper.readTree(exchange.requestBody.readBytes())
                        .deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().put("id", "cus_local")
                    path == "/v3/checkouts" && method == "POST" -> {
                        checkoutPosts++; lastCheckout = mapper.readTree(exchange.requestBody.readBytes())
                        mapOf("id" to "chk_1", "link" to "https://sandbox.asaas.com/c/chk_1")
                    }
                    path == "/v3/payments" && query.contains("checkoutSession") ->
                        mapOf("hasMore" to false, "data" to listOfNotNull(checkoutSubscription?.let { mapOf("id" to "pay_first", "subscription" to it) }))
                    path.startsWith("/v3/subscriptions/") && path.endsWith("/payments") -> mapOf("hasMore" to false, "data" to subscriptionPayments)
                    path.startsWith("/v3/subscriptions/") && method == "PUT" -> {
                        subscriptionPuts++; lastPut = mapper.readTree(exchange.requestBody.readBytes())
                        lastPut!!["nextDueDate"]?.let { realigned += it.asText() }
                        mapOf("id" to path.substringAfterLast('/'), "value" to (lastPut!!["value"]?.decimalValue() ?: BigDecimal.ONE))
                    }
                    method == "DELETE" -> { deletes += path; mapOf("deleted" to true, "id" to path.substringAfterLast('/')) }
                    path.startsWith("/v3/payments/") -> payments.getValue(path.substringAfterLast('/'))
                    path == "/v3/transfers" && method == "POST" -> { transferPosts++; mapOf("id" to "tra_1", "value" to 2) }
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
