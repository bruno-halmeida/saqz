package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.http.ApiProblemWriter
import br.com.saqz.bootstrap.configuration.http.RequestCorrelationFilter
import br.com.saqz.bootstrap.configuration.http.SafeExceptionHandler
import br.com.saqz.groups.adapter.input.http.NotificationDeviceController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationPush
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Registro do aparelho com o token de push-to-start da Live Activity (VUL-260). */
class NotificationDeviceHttpTest {
    private val dataSource = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
    private val jdbc = JdbcClient.create(dataSource)
    private val member = UUID.randomUUID().also { id ->
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'Member', now(), now())")
            .param("id", id).param("subject", id.toString()).update()
    }
    private val installation = UUID.randomUUID()
    private val mvc = run {
        val resolver = object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, container: ModelAndViewContainer?, request: NativeWebRequest,
                binder: WebDataBinderFactory?) = RequestIdentity(member.toString())
        }
        val devices = JdbcNotificationPush(dataSource, JdbcTransactionRunner(dataSource))
        MockMvcBuilders.standaloneSetup(NotificationDeviceController(VerifiedGroupActorResolver { UUID.fromString(it.subject) }, devices))
            .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(RequestCorrelationFilter())
            .setCustomArgumentResolvers(resolver)
            .setControllerAdvice(SafeExceptionHandler(ApiProblemWriter(JsonMapper.builder().build()))).build()
    }

    @Test fun `iOS registration stores the start token and a later registration without it clears it`() {
        assertEquals(204, register("""{"token":"fcm-token","platform":"IOS","liveActivityStartToken":"start-token"}"""))
        assertEquals("start-token", startToken())

        assertEquals(204, register("""{"token":"fcm-token","platform":"IOS"}"""))
        assertNull(startToken())
    }

    @Test fun `an explicit null clears the start token`() {
        assertEquals(204, register("""{"token":"fcm-token-2","platform":"IOS","liveActivityStartToken":"start-token"}"""))

        assertEquals(204, register("""{"token":"fcm-token-2","platform":"IOS","liveActivityStartToken":null}"""))

        assertNull(startToken())
    }

    @Test fun `the start token is refused on Android and when malformed`() {
        assertEquals(422, register("""{"token":"fcm-token-3","platform":"ANDROID","liveActivityStartToken":"start-token"}"""))
        assertEquals(422, register("""{"token":"fcm-token-3","platform":"IOS","liveActivityStartToken":"bad token"}"""))
        assertEquals(422, register("""{"token":"fcm-token-3","platform":"IOS","liveActivityStartToken":""}"""))
        assertEquals(422, register("""{"token":"fcm-token-3","platform":"IOS","liveActivityStartToken":"${"a".repeat(4097)}"}"""))

        assertEquals(0L, jdbc.sql("SELECT count(*) FROM notification_devices WHERE installation_id = :id")
            .param("id", installation).query(Long::class.java).single())
    }

    private fun register(body: String) = mvc.perform(put("/api/me/notification-devices/$installation")
        .contentType("application/json").content(body)).andReturn().response.status

    private fun startToken(): String? = jdbc.sql("SELECT live_activity_start_token FROM notification_devices WHERE installation_id = :id")
        .param("id", installation).query { rs, _ -> rs.getString(1) }.list().single()
}
