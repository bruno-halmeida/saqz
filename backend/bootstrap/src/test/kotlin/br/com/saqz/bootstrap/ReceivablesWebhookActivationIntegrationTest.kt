package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeTransactionRepository
import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcGroupChargeLedger
import br.com.saqz.groups.adapter.output.jdbc.group.JdbcGroupFinancialSetupLookup
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcGroupAdministrationDirectory
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.charge.MonthlyGenerationCommand
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasPaymentCodes
import br.com.saqz.receivables.adapter.output.asaas.HttpAsaasPayments
import br.com.saqz.receivables.adapter.output.crypto.FinancialSecrets
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import org.springframework.jdbc.core.simple.JdbcClient
import java.net.InetSocketAddress
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

/** Webhook da subconta garantido na ativação do grupo e pelo job (DEB-140), com Postgres de verdade e o Asaas simulado. */
class ReceivablesWebhookActivationIntegrationTest {
    private val now = Instant.parse("2026-10-20T15:00:00Z")
    private val mapper = ObjectMapper()

    @Test fun `ativar o grupo registra o webhook uma vez e o codigo de pagamento sai`() = fixture { f ->
        f.activate(f.owner)
        assertEquals(1, f.remote.webhookPosts)
        assertEquals("SUCCEEDED", f.state())
        val post = f.remote.lastWebhook!!
        assertEquals("https://api.saqz.test/api/receivables/webhooks/asaas/${f.account}", post["url"].asText())
        assertEquals(setOf("PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED",
            "PAYMENT_CHARGEBACK_DISPUTE", "PAYMENT_AWAITING_CHARGEBACK_REVERSAL", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED"),
            post["events"].map { it.asText() }.toSet().intersect(PaymentSettlementEvents))
        assertIs<FinancialResult.Success<*>>(f.service.deactivate(f.account, f.group, f.request(f.owner)))
        f.activate(f.owner)
        f.webhooks.recoverPending()
        assertEquals(1, f.remote.webhookPosts)
        assertEquals(1, f.count("receivable_operations", "kind='CONFIGURE_WEBHOOK'"))
        assertEquals("pix-payload", f.code().pix?.payload)
    }

    @Test fun `ativacao por admin delegado registra o webhook em nome do dono`() = fixture { f ->
        val delegate = UUID.randomUUID()
        f.sql("INSERT INTO access_users(id,firebase_subject,email_verified,created_at,updated_at) VALUES ('$delegate','$delegate',true,now(),now())")
        f.sql("INSERT INTO group_memberships(group_id,user_id,role,created_at,updated_at) VALUES ('${f.group}','$delegate','ADMIN',now(),now())")
        val delegations = ManageFinancialDelegations(JdbcFinancialAccountRepository(f.ds), JdbcGroupAdministrationDirectory(f.ds),
            JdbcFinancialDelegationStore(f.ds))
        assertIs<FinancialResult.Success<*>>(delegations.grant(f.account, f.request(f.owner), delegate, "v1", true, now))
        f.activate(delegate)
        assertEquals(1, f.remote.webhookPosts)
        assertEquals("SUCCEEDED", f.state())
        assertEquals(f.owner.toString(), f.string("SELECT actor_user_id::text FROM receivable_webhook_credentials"))
    }

    @Test fun `Asaas fora na ativacao nao desfaz a ativacao e o job registra depois sem segundo webhook`() = fixture { f ->
        f.remote.webhooksDown = true
        f.activate(f.owner)
        assertTrue(f.string("SELECT enabled::text FROM receivable_group_links").toBoolean())
        assertEquals("UNKNOWN", f.state())
        assertEquals(FinancialError.CONFIGURATION_UNAVAILABLE, f.failure())
        f.remote.webhooksDown = false
        f.webhooks.recoverPending()
        assertEquals("SUCCEEDED", f.state())
        assertEquals(1, f.remote.registered.size)
        f.webhooks.recoverPending()
        assertEquals(1, f.remote.registered.size)
        assertEquals("pix-payload", f.code().pix?.payload)
    }

    @Test fun `POST que chegou ao Asaas sem resposta e recuperado pela consulta sem novo POST`() = fixture { f ->
        f.remote.loseResponse = true
        f.activate(f.owner)
        assertEquals("UNKNOWN", f.state())
        assertEquals(1, f.remote.webhookPosts)
        f.remote.loseResponse = false
        f.webhooks.recoverPending()
        assertEquals("SUCCEEDED", f.state())
        assertEquals(1, f.remote.webhookPosts)
    }

    private val PaymentSettlementEvents = setOf("PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED",
        "PAYMENT_CHARGEBACK_DISPUTE", "PAYMENT_AWAITING_CHARGEBACK_REVERSAL", "PAYMENT_CREDIT_CARD_CAPTURE_REFUSED")

    private fun fixture(block: (Fixture) -> Unit) { Remote().use { block(Fixture(it)) } }

    private inner class Fixture(val remote: Remote) {
        val ds = TestPostgres.migrated("classpath:db/migration", owner = this@ReceivablesWebhookActivationIntegrationTest).dataSource
        val jdbc = JdbcClient.create(ds)
        val owner = UUID.randomUUID(); val athlete = UUID.randomUUID()
        val group = UUID.randomUUID(); val account = UUID.randomUUID()
        val secrets = FinancialSecrets("test", mapOf("test" to ByteArray(32) { 1 }), ByteArray(32) { 2 })
        val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
        val conditions = JdbcFinancialConditions(ds)
        private val base = URI("http://127.0.0.1:${remote.server.address.port}/v3")
        private val credentials = JdbcPaymentProviderCredentials(ds, secrets)
        val webhooks = JdbcPaymentWebhookRegistration(ds, JdbcPaymentStore(ds, secrets), secrets,
            HttpAsaasPayments(base, "wallet-platform", credentials, "https://api.saqz.test", "financeiro@saqz.test"), clock)
        private val codes = HttpAsaasPaymentCodes(base, "wallet-platform", credentials)
        private val pixKeys = JdbcPixKeys(ds, codes)
        private val rollout = JdbcReceivablesRollout(ds, { true }, clock)
        private val eligibility = ReceivablesEligibility { _, _ -> ReceivablesEntitlement(true, null) }
        private val live = ProviderFinancialConditions(conditions, FinancialFeeProvider { methods, _, _ ->
            methods.associateWith { ProviderPaymentFee(java.math.BigDecimal.ZERO, 99) }
        })
        // A mesma ligação do GroupReceivablesConfiguration.
        val service = ManageGroupReceivables(JdbcFinancialAccountRepository(ds), JdbcGroupAdministrationDirectory(ds),
            JdbcGroupFinancialSetupLookup(ds), JdbcGroupReceivablesStore(ds), live, eligibility, clock, rollout,
            GroupActivationEffects { account, _ ->
                runCatching { pixKeys.ensure(account) }
                runCatching { webhooks.ensure(account) }
            })
        private val athletes = AthletePayments(JdbcPaymentCodeStore(ds, secrets), JdbcGroupChargeLedger(ds), conditions, eligibility,
            rollout, codes, clock)
        private val charges = JdbcChargeTransactionRepository(ds, receivables = JdbcGroupReceivablesPricing(ds, conditions, clock))
        init {
            for ((id, name) in listOf(owner to "Dono", athlete to "Pagador de teste"))
                sql("INSERT INTO access_users(id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$id',true,'$name',now(),now())")
            sql("INSERT INTO access_groups(id,owner_user_id,creation_key,name,time_zone,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Vôlei do CERET','UTC',now(),now())")
            sql("INSERT INTO group_memberships(group_id,user_id,role,created_at,updated_at) VALUES ('$group','$owner','ADMIN',now(),now()),('$group','$athlete','ATHLETE',now(),now())")
            sql("""INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,provider_account_id,credentials_encrypted,registration,new_operations_enabled,created_at,updated_at,pix_key_id,pix_key,pix_key_ready)
                VALUES ('$account','$owner','$owner','ciphertext','remote-account','${secrets.encrypt(account, "provider-key", "local-key")}','APPROVED',true,now(),now(),'key_1','evp-key',true)""")
            sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'")
            sql("INSERT INTO receivable_terms VALUES ('v1','Termos','${"a".repeat(64)}','2026-09-01','2026-09-01')")
            for (method in PaymentMethod.entries) sql("INSERT INTO receivable_fee_schedules VALUES ('${UUID.randomUUID()}','$method',0,0,0.02,0,'v1','2026-09-01','2026-09-01','$owner')")
        }
        fun request(actor: UUID) = FinancialRequest(UUID.randomUUID(), actor)
        fun activate(actor: UUID) {
            val review = assertIs<FinancialResult.Success<GroupReceivablesReview>>(
                service.preview(account, group, request(actor), setOf(PaymentMethod.PIX))).value
            assertIs<FinancialResult.Success<*>>(service.activate(account, group, request(actor), setOf(PaymentMethod.PIX), review.fingerprint, true))
        }
        private val charge by lazy {
            JdbcTransactionRunner(ds).inTransaction {
                val ym = YearMonth.parse("2026-11")
                charges.createMonthlyCharge(MonthlyGenerationCommand(UUID.randomUUID(), group, owner, ym, 10000, ym.atDay(10), setOf(athlete)), athlete, now).id
            }
        }
        fun code() = assertIs<FinancialResult.Success<PaymentCodeView>>(athletes.create(group, PaymentMethod.PIX, listOf(charge), request(athlete))).value
        fun failure() = assertIs<FinancialResult.Failure>(athletes.create(group, PaymentMethod.PIX, listOf(charge), request(athlete))).error
        fun state() = string("SELECT state FROM receivable_webhook_credentials")
        fun sql(s: String) { jdbc.sql(s).update() }
        fun count(table: String, where: String) = jdbc.sql("SELECT count(*) FROM $table WHERE $where").query(Int::class.java).single()
        fun string(s: String) = jdbc.sql(s).query(String::class.java).single()
    }

    private inner class Remote : AutoCloseable {
        val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var webhookPosts = 0; var webhooksDown = false; var loseResponse = false
        var lastWebhook: JsonNode? = null
        val registered = mutableListOf<Map<String, Any?>>()
        init {
            server.createContext("/v3") { exchange ->
                check(exchange.requestHeaders.getFirst("access_token") == "local-key")
                val path = exchange.requestURI.path; val method = exchange.requestMethod
                var status = 200
                val response: Any = when {
                    path == "/v3/webhooks" && webhooksDown -> { status = 503; mapOf("message" to "fora") }
                    path == "/v3/webhooks" && method == "POST" -> {
                        webhookPosts++; val body = mapper.readTree(exchange.requestBody.readBytes()); lastWebhook = body
                        val hook = mapOf("id" to "wh_${registered.size + 1}", "name" to body["name"].asText(), "url" to body["url"].asText(),
                            "hasAuthToken" to true, "enabled" to true)
                        registered += hook
                        if (loseResponse) { status = 504; mapOf("message" to "timeout") } else hook
                    }
                    path == "/v3/webhooks" -> mapOf("hasMore" to false, "data" to registered)
                    path == "/v3/pix/qrCodes/static" && method == "POST" -> mapOf("id" to "qr_1", "payload" to "pix-payload", "encodedImage" to "aW1n")
                    else -> mapOf("hasMore" to false, "data" to emptyList<Any>())
                }
                val bytes = mapper.writeValueAsBytes(response)
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
        }
        override fun close() { server.stop(0) }
    }
}
