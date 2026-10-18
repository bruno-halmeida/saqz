package br.com.saqz.bootstrap

import br.com.saqz.bootstrap.configuration.DailySubscriptionPastDueReminders
import br.com.saqz.bootstrap.configuration.DailyTrialEndingReminders
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AsaasGateway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.support.CronExpression
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * DEB-076: com os lembretes ligados e o token do webhook do Asaas vazio (pilha local, compose), o backend sobe, o aviso de atraso
 * existe sem o gateway do Asaas e roda a rodada inteira contra o banco migrado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubscriptionPastDueWiringTest {
    @Autowired private lateinit var context: ApplicationContext

    @Test fun `starts with reminders on and no Asaas token, and the job runs`() {
        assertEquals(0, context.getBeansOfType(AsaasGateway::class.java).size)
        val job = context.getBean(DailySubscriptionPastDueReminders::class.java)
        assertNotNull(context.getBean(DailyTrialEndingReminders::class.java))

        job.run()
    }

    @Test fun `runs once a day at 10h Sao Paulo time`() {
        val scheduled = DailySubscriptionPastDueReminders::class.java.getMethod("run").getAnnotation(Scheduled::class.java)
        val cron = CronExpression.parse(scheduled.cron.removePrefix("\${saqz.notifications.past-due.cron:").removeSuffix("}"))
        val zone = ZoneId.of(scheduled.zone)
        val first = cron.next(ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, zone))!!

        assertEquals("America/Sao_Paulo", scheduled.zone)
        assertEquals("2026-10-19T10:00", first.toLocalDateTime().toString())
        assertEquals("2026-10-20T10:00", cron.next(first)!!.toLocalDateTime().toString())
        assertFalse(scheduled.cron.isBlank())
    }

    companion object {
        private val database = TestPostgres.empty()

        @JvmStatic @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { database.jdbcUrl }
            registry.add("spring.datasource.username") { database.username }
            registry.add("spring.datasource.password") { database.password }
            registry.add("saqz.firebase.emulator.enabled") { "true" }
            registry.add("saqz.links.domain") { "https://join.test" }
            registry.add("saqz.password-reset.secret") { "segredo-de-teste-com-trinta-e-dois" }
            registry.add("saqz.notifications.reminder.enabled") { "true" }
            registry.add("saqz.asaas.webhook-token") { "" }
        }
    }
}
