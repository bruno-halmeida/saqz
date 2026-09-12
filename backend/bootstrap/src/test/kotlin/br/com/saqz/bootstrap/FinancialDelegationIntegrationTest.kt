package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.FinancialDelegationConfiguration
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcGroupAdministrationDirectory
import br.com.saqz.groups.adapter.output.jdbc.membership.JdbcMembershipRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.membership.ChangeMemberRole
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.PersistedMembershipRole
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.receivables.adapter.input.http.FinancialActorResolver
import br.com.saqz.receivables.adapter.input.http.FinancialAccountDirectoryController
import br.com.saqz.receivables.adapter.input.http.FinancialDelegationsController
import br.com.saqz.receivables.adapter.input.http.GrantFinancialDelegationRequest
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialAccountRepository
import br.com.saqz.receivables.adapter.output.jdbc.JdbcFinancialDelegationStore
import br.com.saqz.receivables.application.FinancialError
import br.com.saqz.receivables.application.FinancialRequest
import br.com.saqz.receivables.application.FinancialResult
import br.com.saqz.receivables.application.ManageFinancialDelegations
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.group.GroupAdministrationRevocation
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class FinancialDelegationIntegrationTest {
    private val now = Instant.parse("2026-09-12T12:00:00Z")

    @Test
    fun `only owner grants whole account authority and replay cannot restore a revoked delegation`() = fixture { f ->
        val request = FinancialRequest(UUID.randomUUID(), f.owner)
        assertEquals(FinancialError.INVALID_INPUT, (f.service.grant(f.account, request, f.admin, "v1", false, now) as FinancialResult.Failure).error)
        assertNull(f.accounts.findDelegation(f.account, f.admin))
        assertEquals(FinancialError.INVALID_INPUT, (f.service.grant(f.account, request, UUID.randomUUID(), "v1", true, now) as FinancialResult.Failure).error)
        val granted = f.service.grant(f.account, request, f.admin, "v1", true, now) as FinancialResult.Success
        assertEquals(f.admin, granted.value.userId)
        assertEquals(f.account, granted.value.accountId)
        assertNull(granted.value.revokedAt)
        val delegatedRequest = FinancialRequest(UUID.randomUUID(), f.admin)
        assertEquals(1, (f.service.list(f.account, delegatedRequest) as FinancialResult.Success).value.size)
        assertEquals(listOf(f.account), (f.service.accounts(delegatedRequest) as FinancialResult.Success).value.map { it.id })
        assertEquals(FinancialError.NOT_FOUND, (f.service.grant(f.account, delegatedRequest, f.owner, "v1", true, now) as FinancialResult.Failure).error)
        assertEquals(FinancialError.NOT_FOUND, (f.service.revoke(f.account, delegatedRequest, f.admin, now) as FinancialResult.Failure).error)
        assertIs<FinancialResult.Success<Unit>>(f.service.revoke(f.account, request.copy(requestId = UUID.randomUUID()), f.admin, now.plusSeconds(5)))
        val replay = f.service.grant(f.account, request, f.admin, "v1", true, now.plusSeconds(10)) as FinancialResult.Success
        assertEquals(now.plusSeconds(5), replay.value.revokedAt)
        assertEquals(FinancialError.NOT_FOUND, (f.service.list(f.account, delegatedRequest) as FinancialResult.Failure).error)
        assertEquals(emptyList(), (f.service.accounts(delegatedRequest) as FinancialResult.Success).value)
        assertEquals(1, (f.service.list(f.account, request) as FinancialResult.Success).value.size)
    }

    @Test
    fun `demotion revokes in database and promotion does not revive financial permission`() = fixture { f ->
        f.grant()
        val change = f.change(f.revocation)
        change.execute(f.owner, f.group, f.admin, PersistedMembershipRole.ATHLETE)
        assertEquals("ATHLETE", f.role())
        assertNotNull(f.accounts.findDelegation(f.account, f.admin)!!.revokedAt)
        change.execute(f.owner, f.group, f.admin, PersistedMembershipRole.ADMIN)
        assertEquals("ADMIN", f.role())
        assertEquals(FinancialError.NOT_FOUND, (f.service.list(f.account,
            FinancialRequest(UUID.randomUUID(), f.admin)) as FinancialResult.Failure).error)
    }

    @Test
    fun `self departure revokes open session and owner retains financial access after group deletion`() = fixture { f ->
        f.grant()
        val existingSession = FinancialRequest(UUID.randomUUID(), f.admin)
        assertIs<FinancialResult.Success<*>>(f.service.list(f.account, existingSession))
        val remove = RemoveAthlete(f.transaction, f.read, JdbcAthleteRepository(f.dataSource), GroupAccessPolicy(), f.revocation)
        remove.leave(f.admin, f.group)
        assertNotNull(f.accounts.findDelegation(f.account, f.admin)!!.revokedAt)
        assertEquals(FinancialError.NOT_FOUND, (f.service.list(f.account, existingSession) as FinancialResult.Failure).error)
        f.execute("UPDATE access_groups SET deleted_at=now() WHERE id='${f.group}'")
        assertIs<FinancialResult.Success<*>>(f.service.list(f.account, FinancialRequest(UUID.randomUUID(), f.owner)))
        assertEquals(f.owner, f.accounts.findById(f.account)!!.ownerUserId)
        assertEquals(listOf(f.account), (f.service.accounts(FinancialRequest(UUID.randomUUID(), f.owner)) as FinancialResult.Success).value.map { it.id })
    }

    @Test
    fun `failed group transaction rolls back role change and financial revocation together`() = fixture { f ->
        f.grant()
        val failure = GroupAdministrationRevocation { group, user ->
            f.revocation.revoked(group, user)
            error("force transaction rollback")
        }
        assertFailsWith<IllegalStateException> { f.change(failure).execute(f.owner, f.group, f.admin, PersistedMembershipRole.ATHLETE) }
        assertEquals("ADMIN", f.role())
        assertNull(f.accounts.findDelegation(f.account, f.admin)!!.revokedAt)
        assertIs<FinancialResult.Success<*>>(f.service.list(f.account, FinancialRequest(UUID.randomUUID(), f.admin)))
    }

    @Test
    fun `delegation controller exposes typed errors and request IDs without granting platform admin powers`() = fixture { f ->
        val controller = FinancialDelegationsController(FinancialActorResolver { UUID.fromString(it.subject) }, f.service,
            Clock.fixed(now, ZoneOffset.UTC))
        val owner = RequestIdentity(f.owner.toString())
        val stranger = RequestIdentity(UUID.randomUUID().toString())
        val requestId = UUID.randomUUID()
        val body = GrantFinancialDelegationRequest(requestId, f.admin, "v1", true)
        assertEquals(404, controller.grant(stranger, f.account, body).statusCode.value())
        val grant = controller.grant(owner, f.account, body)
        assertEquals(200, grant.statusCode.value())
        assertEquals(requestId, (grant.body as FinancialResult.Success<*>).requestId)
        assertEquals(200, controller.list(RequestIdentity(f.admin.toString()), f.account).statusCode.value())
        assertEquals(404, controller.revoke(stranger, f.account, f.admin, UUID.randomUUID()).statusCode.value())
        assertEquals(200, controller.revoke(owner, f.account, f.admin, UUID.randomUUID()).statusCode.value())
        assertEquals(404, controller.list(RequestIdentity(f.admin.toString()), f.account).statusCode.value())
    }

    private fun fixture(block: (Fixture) -> Unit) {
        val database = TestPostgres.migrated("classpath:db/migration", owner = this)
        block(Fixture(database.dataSource))
    }

    @Test
    fun `delegation HTTP routes bind consent preserve request IDs and enforce revocation in same session`() = fixture { f ->
        var actor = f.owner
        val actors = FinancialActorResolver { UUID.fromString(it.subject) }
        val controller = FinancialDelegationsController(actors, f.service, Clock.fixed(now, ZoneOffset.UTC))
        val resolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?,
                request: NativeWebRequest, binder: WebDataBinderFactory?) = RequestIdentity(actor.toString())
        }
        val mvc = MockMvcBuilders.standaloneSetup(controller, FinancialAccountDirectoryController(actors, f.service))
            .setCustomArgumentResolvers(resolver).build()
        val url = "/api/receivables/accounts/${f.account}/delegations"
        val requestId = UUID.randomUUID()
        fun grant(acknowledged: Boolean, version: String = "v1") = mvc.perform(post(url).contentType("application/json").content("""
            {"requestId":"$requestId","userId":"${f.admin}","termsVersion":"$version","acknowledgedWholeAccount":$acknowledged}
        """.trimIndent())).andReturn().response
        assertEquals(400, grant(false).status)
        assertEquals(400, grant(true, "unknown").status)
        val granted = grant(true)
        assertEquals(200, granted.status)
        assertTrue(granted.contentAsString.contains(requestId.toString()))
        assertEquals(409, grant(true, "changed-terms").status)
        actor = f.admin
        assertEquals(200, mvc.perform(get(url)).andReturn().response.status)
        assertEquals(404, grant(true).status)
        assertEquals(404, mvc.perform(delete("$url/${f.admin}").param("requestId", UUID.randomUUID().toString())).andReturn().response.status)
        val directory = mvc.perform(get("/api/receivables/accounts")).andReturn().response
        assertEquals(200, directory.status)
        assertTrue(directory.contentAsString.contains(f.account.toString()))
        actor = f.owner
        assertEquals(200, mvc.perform(delete("$url/${f.admin}").param("requestId", UUID.randomUUID().toString())).andReturn().response.status)
        actor = f.admin
        assertEquals(404, mvc.perform(get(url)).andReturn().response.status)
        assertFalse(mvc.perform(get("/api/receivables/accounts")).andReturn().response.contentAsString.contains(f.account.toString()))
    }

    private inner class Fixture(val dataSource: javax.sql.DataSource) {
        val owner = UUID.randomUUID()
        val admin = UUID.randomUUID()
        val group = UUID.randomUUID()
        val account = UUID.randomUUID()
        val accounts = JdbcFinancialAccountRepository(dataSource)
        private val directory = JdbcGroupAdministrationDirectory(dataSource)
        val service = ManageFinancialDelegations(accounts, directory, JdbcFinancialDelegationStore(dataSource))
        val transaction = JdbcTransactionRunner(dataSource)
        val revocation = FinancialDelegationConfiguration().financialAdministrationRevocation(directory, accounts,
            Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC))
        val read = object : GroupReadRepository {
            override fun find(key: GroupReadKey) = GroupReadSnapshot(group, AccessName.from("Test group"),
                IanaTimeZone.from("UTC"), if (key.actorUserId == owner) GroupRole.OWNER else GroupRole.ADMIN, 1)
        }
        init {
            listOf(owner, admin).forEach { user -> execute("""
                INSERT INTO access_users(id,firebase_subject,email_verified,display_name,created_at,updated_at)
                VALUES ('$user','$user',true,'Test User',now(),now())
            """) }
            execute("""
                INSERT INTO access_groups(id,owner_user_id,creation_key,name,time_zone,created_at,updated_at)
                VALUES ('$group','$owner','${UUID.randomUUID()}','Test group','UTC',now(),now())
            """)
            execute("INSERT INTO group_memberships(group_id,user_id,role,created_at,updated_at) VALUES ('$group','$admin','ADMIN',now(),now())")
            execute("""
                INSERT INTO receivable_accounts(id,owner_user_id,legal_identity_digest,legal_data_encrypted,registration,created_at,updated_at)
                VALUES ('$account','$owner','$owner','ciphertext','APPROVED',now(),now())
            """)
            execute("INSERT INTO receivable_terms VALUES ('v1','whole-account delegation terms','${"a".repeat(64)}','2026-01-01','2026-01-01')")
        }
        fun grant() { assertIs<FinancialResult.Success<*>>(service.grant(account, FinancialRequest(UUID.randomUUID(), owner), admin, "v1", true, now)) }
        fun change(observer: GroupAdministrationRevocation) = ChangeMemberRole(transaction, read,
            JdbcMembershipRepository(dataSource), GroupAccessPolicy(), observer)
        fun execute(sql: String) { dataSource.connection.use { it.createStatement().use { statement -> statement.execute(sql) } } }
        fun role(): String = dataSource.connection.use { it.createStatement().use { statement ->
            statement.executeQuery("SELECT role FROM group_memberships WHERE group_id='$group' AND user_id='$admin'")
                .use { rs -> rs.next(); rs.getString(1) }
        } }
    }
}
