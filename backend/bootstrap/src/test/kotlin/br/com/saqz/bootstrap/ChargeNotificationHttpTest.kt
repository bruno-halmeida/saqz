package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.input.http.*
import br.com.saqz.groups.adapter.output.jdbc.communication.*
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import br.com.saqz.bootstrap.configuration.http.ApiProblemWriter
import br.com.saqz.bootstrap.configuration.http.SafeExceptionHandler
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.*

class ChargeNotificationHttpTest {
    private val dataSource = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
    private val jdbc = JdbcClient.create(dataSource)
    private val owner = user("Owner")
    private val member = user("Member")
    private val group = UUID.randomUUID()

    @Test fun `HTTP binds charge IDs and device tokens and maps authorization validation and replay`() {
        jdbc.sql("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES (:id, :owner, :key, 'Training', 'UTC', now(), now())")
            .param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN"); membership(member)
        val transaction = JdbcTransactionRunner(dataSource)
        val service = ChargeReminderService(transaction, JdbcGroupReadRepository(dataSource),
            JdbcGroupCommunicationRepository(dataSource), JdbcChargeReminderStore(dataSource))
        val push = JdbcNotificationPush(dataSource, transaction)
        var actor = owner
        val resolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, request: NativeWebRequest,
                binder: WebDataBinderFactory?) = RequestIdentity(actor.toString())
        }
        val actors = VerifiedGroupActorResolver { UUID.fromString(it.subject) }
        val mapper = JsonMapper.builder().build()
        val mvc = MockMvcBuilders.standaloneSetup(ChargeReminderController(actors, service), NotificationDeviceController(actors, push))
            .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(br.com.saqz.bootstrap.configuration.http.RequestCorrelationFilter())
            .setCustomArgumentResolvers(resolver).setControllerAdvice(SafeExceptionHandler(ApiProblemWriter(mapper))).build()
        val charge = charge(member)
        val request = UUID.randomUUID()
        fun send(body: String) = mvc.perform(post("/api/groups/$group/charges/notify")
            .contentType("application/json").content(body)).andReturn().response
        val body = """{"requestId":"$request","chargeIds":["$charge"]}"""
        actor = member
        assertEquals(403, send(body).status)
        actor = owner
        assertEquals(400, send("{}").status)
        assertEquals(422, send("""{"requestId":"$request","chargeIds":[]}""").status)
        val response = send(body)
        assertEquals(200, response.status)
        assertEquals(1, mapper.readTree(response.contentAsString)["notificationCount"].intValue())
        assertEquals(200, send(body).status)
        assertEquals(1L, count("group_notifications"))
        assertEquals(409, send("""{"requestId":"$request","chargeIds":["${UUID.randomUUID()}"]}""").status)
        actor = UUID.randomUUID()
        assertEquals(404, send(body).status)
        actor = member
        val installation = UUID.randomUUID()
        fun register(body: String) = mvc.perform(put("/api/me/notification-devices/$installation")
            .contentType("application/json").content(body)).andReturn().response.status
        assertEquals(422, register("""{"token":"bad token","platform":"ANDROID"}"""))
        assertEquals(400, register("{}"))
        assertEquals(204, register("""{"token":"fcm-token","platform":"IOS"}"""))
        assertEquals(member, jdbc.sql("SELECT user_id FROM notification_devices WHERE installation_id = :id")
            .param("id", installation).query(UUID::class.java).single())
        actor = owner
        assertEquals(204, mvc.perform(delete("/api/me/notification-devices/$installation")).andReturn().response.status)
        assertEquals(1L, count("notification_devices"))
        actor = member
        assertEquals(204, mvc.perform(delete("/api/me/notification-devices/$installation")).andReturn().response.status)
        assertEquals(0L, count("notification_devices"))
    }
    private fun count(table: String) = jdbc.sql("SELECT count(*) FROM $table").query(Long::class.java).single()
    private fun charge(recipient: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("""
            INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date,
                created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name)
            VALUES (:id, :g, :member, 'MONTHLY', '2026-09-01', 7000, '2026-09-10', :owner, :owner, now(), now(), 'Member Person')
        """).param("id", id).param("g", group).param("member", recipient).param("owner", owner).update()
        return id
    }
    private fun user(name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, :name, now(), now())")
            .param("id", id).param("subject", id.toString()).param("name", name).update()
        return id
    }
    private fun membership(user: UUID, role: String = "ATHLETE", active: Boolean = true) {
        jdbc.sql("INSERT INTO group_memberships (group_id, user_id, role, membership_type, active, created_at, updated_at) VALUES (:group, :user, :role, 'AVULSO', :active, now(), now())")
            .param("group", group).param("user", user).param("role", role, java.sql.Types.OTHER).param("active", active).update()
    }
    private fun <T> CommunicationResult<T>.success(): T = assertIs<CommunicationResult.Success<T>>(this).value
}
