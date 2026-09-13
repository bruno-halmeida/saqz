package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.group.JdbcGroupFinancialSetupLookup
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcGroupAdministrationDirectory
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.output.jdbc.*
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.input.http.GroupReceivablesController
import br.com.saqz.sharedkernel.RequestIdentity
import tools.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class GroupReceivablesIntegrationTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")

    @Test fun `direct activation reevaluates owner rollout while cancellation survives missing configuration`() = fixture { f ->
        val review = f.preview()
        f.sql("UPDATE receivable_rollout SET backend_mode='OFF'")
        assertEquals(FinancialError.OPERATIONS_DISABLED,assertIs<FinancialResult.Failure>(f.activate(review)).error)
        assertEquals(0,f.count("receivable_group_links"))
        f.sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'")
        assertIs<FinancialResult.Success<GroupReceivablesState>>(f.activate(review))
        f.sql("DROP TABLE receivable_rollout")
        val cancelled = assertIs<FinancialResult.Success<GroupReceivablesState>>(f.service.deactivate(f.account,f.group,f.request()))
        assertFalse(cancelled.value.enabled)
    }

    @Test fun `activation requires explicit acceptance and preserves reviewed prices without creating debt`() = fixture { f ->
        val review = f.preview()
        assertFalse(review.state.enabled)
        assertEquals(ActionPermission(true), review.permissions[FinancialAction.ACTIVATE_GROUP])
        assertEquals(listOf(2500L, 10000L), review.prices.map { it.baseCents })
        assertEquals(10658, review.prices.last().quotes.single().totalCents)
        assertEquals(FinancialError.INVALID_INPUT, assertIs<FinancialResult.Failure>(f.activate(review, accepted = false)).error)
        assertEquals(0, f.count("receivable_group_links"))
        val activated = assertIs<FinancialResult.Success<GroupReceivablesState>>(f.activate(review)).value
        assertTrue(activated.enabled)
        assertTrue(activated.pixEnabled)
        assertFalse(activated.cardEnabled)
        assertEquals(f.account, activated.accountId)
        assertEquals(f.group, activated.groupId)
        assertEquals(1, f.count("receivable_group_configurations"))
        assertEquals(0, f.count("receivable_orders"))
        assertEquals(0, f.count("receivable_instruments"))
        assertTrue(f.jdbc.sql("SELECT accepted_conditions::text FROM receivable_group_configurations").query(String::class.java).single().contains("10658"))
    }

    @Test fun `commercial approval and pilot restrictions deny activation but preserve financial access`() = fixture { f ->
        f.eligible = false
        assertEquals(UnavailabilityReason.INELIGIBLE_PLAN, f.preview().permissions[FinancialAction.ACTIVATE_GROUP]!!.reason)
        assertEquals(FinancialError.INELIGIBLE_PLAN, assertIs<FinancialResult.Failure>(f.activate(f.preview())).error)
        f.eligible = true
        f.sql("UPDATE receivable_accounts SET registration='UNDER_REVIEW'")
        assertEquals(UnavailabilityReason.REGISTRATION_NOT_APPROVED, f.preview().permissions[FinancialAction.ACTIVATE_GROUP]!!.reason)
        assertEquals(FinancialError.REGISTRATION_RESTRICTED, assertIs<FinancialResult.Failure>(f.activate(f.preview())).error)
        f.sql("UPDATE receivable_accounts SET registration='APPROVED',new_operations_enabled=false")
        assertEquals(FinancialError.OPERATIONS_DISABLED, assertIs<FinancialResult.Failure>(f.activate(f.preview())).error)
        assertEquals(ActionPermission(true), f.preview().permissions[FinancialAction.READ])
        assertEquals(0, f.count("receivable_group_links"))
    }

    @Test fun `changing prices or published rates invalidates the accepted review`() = fixture { f ->
        val review = f.preview()
        f.sql("UPDATE access_groups SET monthly_fee_cents=12000 WHERE id='${f.group}'")
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(f.activate(review)).error)
        val revised = f.preview()
        f.sql("""INSERT INTO receivable_fee_schedules VALUES ('${UUID.randomUUID()}','PIX',0.04,50,0.02,100,'v1','2026-09-13','2026-09-13','${f.owner}')""")
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(f.activate(revised)).error)
        assertEquals(0, f.count("receivable_group_configurations"))
        assertIs<FinancialResult.Success<*>>(f.activate(f.preview()))
    }

    @Test fun `concurrent activation is unique and replay after deactivation never reactivates`() = fixture { f ->
        val review = f.preview()
        val request = f.request()
        Executors.newFixedThreadPool(2).use { pool ->
            val futures = (1..2).map { pool.submit<FinancialResult<GroupReceivablesState>> { f.activate(review, request = request) } }
            futures.forEach { assertTrue(assertIs<FinancialResult.Success<GroupReceivablesState>>(it.get(10, TimeUnit.SECONDS)).value.enabled) }
        }
        assertEquals(1, f.count("receivable_group_configurations"))
        f.eligible = false
        assertFalse(assertIs<FinancialResult.Success<GroupReceivablesState>>(f.service.deactivate(f.account, f.group, f.request())).value.enabled)
        assertFalse(assertIs<FinancialResult.Success<GroupReceivablesState>>(f.activate(review, request = request)).value.enabled)
        assertEquals(2, f.count("receivable_group_configurations"))
        assertEquals(1, f.count("receivable_accounts"))
        assertEquals(FinancialError.CONFLICT, assertIs<FinancialResult.Failure>(f.service.activate(f.account, f.group,
            request, setOf(PaymentMethod.CARD), review.fingerprint, true)).error)
    }

    @Test fun `financial delegation is checked afresh and new group owner cannot use old account`() = fixture { f ->
        val delegate = UUID.randomUUID()
        f.sql("INSERT INTO access_users(id,firebase_subject,email_verified,created_at,updated_at) VALUES ('$delegate','$delegate',true,now(),now())")
        f.sql("INSERT INTO group_memberships(group_id,user_id,role,created_at,updated_at) VALUES ('${f.group}','$delegate','ADMIN',now(),now())")
        val manage = ManageFinancialDelegations(f.accounts, JdbcGroupAdministrationDirectory(f.ds), JdbcFinancialDelegationStore(f.ds))
        assertIs<FinancialResult.Success<*>>(manage.grant(f.account, f.request(), delegate, "v1", true, now))
        val session = FinancialRequest(UUID.randomUUID(), delegate)
        val review = assertIs<FinancialResult.Success<GroupReceivablesReview>>(f.service.preview(f.account, f.group, session, setOf(PaymentMethod.PIX))).value
        f.sql("INSERT INTO receivable_rollout_users VALUES ('${f.owner}',1),('$delegate',1)")
        f.sql("INSERT INTO receivable_rollout_overrides VALUES ('${f.owner}','BACKEND','DENY'),('$delegate','BACKEND','ALLOW')")
        assertEquals(FinancialError.OPERATIONS_DISABLED,assertIs<FinancialResult.Failure>(f.activate(review,request=session)).error)
        f.sql("DELETE FROM receivable_rollout_overrides WHERE user_id='${f.owner}'")
        assertIs<FinancialResult.Success<*>>(f.activate(review, request = session))
        assertIs<FinancialResult.Success<*>>(manage.revoke(f.account, f.request(), delegate, now))
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.service.deactivate(f.account, f.group, session)).error)
        f.sql("UPDATE access_groups SET owner_user_id='$delegate' WHERE id='${f.group}'")
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.service.preview(f.account, f.group, session, setOf(PaymentMethod.PIX))).error)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.service.preview(f.account, f.group, f.request(), setOf(PaymentMethod.PIX))).error)
        assertEquals(f.owner, f.accounts.findById(f.account)!!.ownerUserId)
        assertIs<FinancialResult.Success<*>>(f.service.deactivate(f.account, f.group, f.request()))
    }

    @Test fun `deactivation preserves deleted group history and queues interruption of live recurrence`() = fixture { f ->
        assertIs<FinancialResult.Success<*>>(f.activate(f.preview()))
        val acceptance = UUID.randomUUID()
        val recurrence = UUID.randomUUID()
        f.sql("INSERT INTO receivable_terms_acceptances VALUES ('$acceptance','${f.account}','${f.owner}','v1','RECURRENCE','${UUID.randomUUID()}','2026-09-01')")
        f.sql("""INSERT INTO receivable_recurrences(id,account_id,group_id,member_user_id,method,fee_schedule_id,acceptance_id,base_cents,total_cents,first_due_date,status,created_at)
            VALUES ('$recurrence','${f.account}','${f.group}','${f.owner}','PIX','${f.fee}','$acceptance',10000,10658,'2026-10-01','ACTIVE','2026-09-01')""")
        f.sql("UPDATE access_groups SET deleted_at=now() WHERE id='${f.group}'")
        f.eligible = false
        val request = f.request()
        repeat(2) { assertIs<FinancialResult.Success<*>>(f.service.deactivate(f.account, f.group, request)) }
        assertEquals("STOP_PENDING", f.jdbc.sql("SELECT status FROM receivable_recurrences").query(String::class.java).single())
        assertEquals(1L, f.jdbc.sql("SELECT count(*) FROM receivable_operations WHERE kind='STOP_RECURRENCE' AND status='READY'").query(Long::class.java).single())
        assertEquals(2, f.count("receivable_group_configurations"))
        assertEquals(f.owner, f.accounts.findById(f.account)!!.ownerUserId)
    }

    @Test fun `group HTTP routes bind review consent request IDs and availability reasons`() = fixture { f ->
        var actor = f.owner
        val resolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, request: NativeWebRequest,
                binder: WebDataBinderFactory?) = RequestIdentity(actor.toString())
        }
        val mvc = MockMvcBuilders.standaloneSetup(GroupReceivablesController(FinancialActorResolver { UUID.fromString(it.subject) }, f.service))
            .setCustomArgumentResolvers(resolver).build()
        val mapper = JsonMapper.builder().build()
        val id = UUID.randomUUID()
        val body = mutableMapOf<String, Any>("requestId" to id.toString(), "accountId" to f.account.toString(), "methods" to listOf("PIX"))
        fun call(action: String) = mvc.perform(post("/api/receivables/groups/${f.group}/$action")
            .contentType("application/json").content(mapper.writeValueAsString(body))).andReturn().response
        val review = call("preview")
        assertEquals(200, review.status)
        val json = mapper.readTree(review.contentAsString)
        assertEquals(id.toString(), json["requestId"].stringValue())
        assertTrue(json["value"]["permissions"]["ACTIVATE_GROUP"]["allowed"].booleanValue())
        body["fingerprint"] = json["value"]["fingerprint"].stringValue()
        assertEquals(400, call("activate").status)
        body["accepted"] = true
        f.eligible = false
        val denied = call("activate")
        assertEquals(403, denied.status)
        assertEquals("INELIGIBLE_PLAN", mapper.readTree(denied.contentAsString)["error"].stringValue())
        f.eligible = true
        assertEquals(200, call("activate").status)
        body["requestId"] = UUID.randomUUID().toString()
        assertEquals(200, call("deactivate").status)
        body["methods"] = listOf("CARD")
        assertEquals(503, call("preview").status)
        actor = UUID.randomUUID()
        assertEquals(404, call("preview").status)
        assertEquals(404, call("activate").status)
        assertEquals(404, call("deactivate").status)
        body.remove("accountId")
        assertEquals(400, call("preview").status)
    }

    @Test fun `empty selection is invalid while maintenance read works without conditions rollout or active group`() = fixture { f ->
        assertEquals(FinancialError.INVALID_INPUT, assertIs<FinancialResult.Failure>(f.service.preview(f.account, f.group, f.request(), emptySet())).error)
        assertEquals(FinancialError.INVALID_INPUT, assertIs<FinancialResult.Failure>(f.service.activate(f.account, f.group, f.request(), emptySet(), "a".repeat(64), true)).error)
        f.activate(f.preview())
        f.sql("UPDATE access_groups SET deleted_at=now()")
        f.sql("DROP TABLE receivable_rollout")
        f.eligible = false
        val state = assertIs<FinancialResult.Success<GroupReceivablesMaintenance>>(f.service.read(f.account, f.group, f.request())).value
        assertTrue(state.state.pixEnabled)
        assertTrue(state.permissions.getValue(FinancialAction.CANCEL).allowed)
        assertEquals(FinancialError.NOT_FOUND, assertIs<FinancialResult.Failure>(f.service.read(f.account, f.group, FinancialRequest(UUID.randomUUID(), UUID.randomUUID()))).error)
    }

    @Test fun `activation persists only explicitly selected PIX CARD or both`() {
        for (methods in listOf(setOf(PaymentMethod.PIX), setOf(PaymentMethod.CARD), PaymentMethod.entries.toSet())) fixture { f ->
            f.sql("INSERT INTO receivable_fee_schedules VALUES ('${UUID.randomUUID()}','CARD',0.03,50,0.02,100,'v1','2026-09-01','2026-09-01','${f.owner}')")
            val review = assertIs<FinancialResult.Success<GroupReceivablesReview>>(f.service.preview(f.account, f.group, f.request(), methods)).value
            val state = assertIs<FinancialResult.Success<GroupReceivablesState>>(f.service.activate(f.account, f.group, f.request(), methods, review.fingerprint, true)).value
            assertEquals(PaymentMethod.PIX in methods, state.pixEnabled)
            assertEquals(PaymentMethod.CARD in methods, state.cardEnabled)
            assertTrue(state.enabled)
        }
    }

    private fun fixture(block: (Fixture) -> Unit) { block(Fixture(TestPostgres.migrated("classpath:db/migration", owner = this).dataSource)) }
    private inner class Fixture(val ds: javax.sql.DataSource) {
        val owner = UUID.randomUUID(); val group = UUID.randomUUID(); val account = UUID.randomUUID(); val fee = UUID.randomUUID()
        val jdbc = JdbcClient.create(ds)
        val accounts = JdbcFinancialAccountRepository(ds)
        var eligible = true
        val service = ManageGroupReceivables(accounts, JdbcGroupAdministrationDirectory(ds), JdbcGroupFinancialSetupLookup(ds),
            JdbcGroupReceivablesStore(ds), JdbcFinancialConditions(ds), ReceivablesEligibility { _, _ -> ReceivablesEntitlement(eligible, null) },
            Clock.fixed(now, ZoneOffset.UTC), br.com.saqz.receivables.adapter.output.jdbc.JdbcReceivablesRollout(ds, { true }, Clock.fixed(now, ZoneOffset.UTC)))
        init {
            sql("UPDATE receivable_rollout SET backend_mode='ALL_USERS'")
            sql("INSERT INTO access_users(id,firebase_subject,email_verified,created_at,updated_at) VALUES ('$owner','$owner',true,now(),now())")
            sql("""INSERT INTO access_groups(id,owner_user_id,creation_key,name,time_zone,default_game_fee_cents,monthly_fee_cents,monthly_due_day,created_at,updated_at)
                VALUES ('$group','$owner','${UUID.randomUUID()}','Test group','UTC',2500,10000,10,now(),now())""")
            sql("""INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,registration,new_operations_enabled,created_at,updated_at)
                VALUES ('$account','$owner','$owner','ciphertext','APPROVED',true,now(),now())""")
            sql("INSERT INTO receivable_terms VALUES ('v1','Test terms','${"a".repeat(64)}','2026-09-01','2026-09-01')")
            sql("INSERT INTO receivable_fee_schedules VALUES ('$fee','PIX',0.0299,39,0.02,100,'v1','2026-09-01','2026-09-01','$owner')")
        }
        fun request() = FinancialRequest(UUID.randomUUID(), owner)
        fun preview() = assertIs<FinancialResult.Success<GroupReceivablesReview>>(service.preview(account, group, request(), setOf(PaymentMethod.PIX))).value
        fun activate(review: GroupReceivablesReview, accepted: Boolean = true, request: FinancialRequest = request()) =
            service.activate(account, group, request, setOf(PaymentMethod.PIX), review.fingerprint, accepted)
        fun sql(value: String) { jdbc.sql(value).update() }
        fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Int::class.java).single()
    }
}
