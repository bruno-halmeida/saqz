package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.adminweb.http.AdminWhatsAppController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.link.PublicAttendanceLinkFactory
import br.com.saqz.groups.application.whatsapp.AdminWhatsAppMessages
import br.com.saqz.sharedkernel.group.GroupReceivables
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor
import java.lang.reflect.Proxy
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Sobe a [NotificationWhatsAppConfiguration] de verdade, como em produção. Sem `@EnableScheduling` nenhum job
 * roda; o base-url `.invalid` não resolve e o DataSource dublê conta qualquer uso, que tem de ficar em zero.
 */
class NotificationWhatsAppWiringTest {
    private val runner = ApplicationContextRunner()
        .withUserConfiguration(Doubles::class.java, NotificationWhatsAppConfiguration::class.java)
        .withPropertyValues(
            "saqz.notifications.whatsapp.base-url=https://uazapi.invalid",
            "saqz.notifications.whatsapp.token=teste",
            "saqz.links.domain=https://links.invalid",
        )

    @Test
    fun `aba ligada como em producao sobe sem job, rede nem banco`() {
        dataSourceUses.set(0)
        runner.withPropertyValues("saqz.notifications.whatsapp.enabled=true", "saqz.admin.inbox.enabled=true").run { context ->
            assertNull(context.startupFailure)
            assertEquals(1, context.getBeansOfType(AdminWhatsAppController::class.java).size)
            assertEquals(1, context.getBeansOfType(AdminWhatsAppMessages::class.java).size)
            assertEquals(0, context.getBeansOfType(ScheduledAnnotationBeanPostProcessor::class.java).size)
        }
        assertEquals(0, dataSourceUses.get())
    }

    /** Dev: WhatsApp ligado com a instância real e admin de seed. A aba não pode existir aí. */
    @Test
    fun `whatsapp ligado sem a chave da aba nao registra a aba Mensagens`() {
        runner.withPropertyValues("saqz.notifications.whatsapp.enabled=true").run { context ->
            assertNull(context.startupFailure)
            assertEquals(0, context.getBeansOfType(AdminWhatsAppController::class.java).size)
            assertEquals(0, context.getBeansOfType(AdminWhatsAppMessages::class.java).size)
        }
    }

    @Test
    fun `whatsapp desligado nao registra a aba Mensagens mesmo com a chave`() {
        runner.withPropertyValues("saqz.notifications.whatsapp.enabled=false", "saqz.admin.inbox.enabled=true").run { context ->
            assertNull(context.startupFailure)
            assertEquals(0, context.getBeansOfType(AdminWhatsAppController::class.java).size)
            assertEquals(0, context.getBeansOfType(AdminWhatsAppMessages::class.java).size)
        }
    }

    @Configuration(proxyBeanMethods = false)
    class Doubles {
        @Bean fun dataSource(): DataSource = Proxy.newProxyInstance(DataSource::class.java.classLoader, arrayOf(DataSource::class.java)) { _, method, _ ->
            dataSourceUses.incrementAndGet()
            error("banco não pode ser usado neste teste: ${method.name}")
        } as DataSource
        @Bean fun transaction(dataSource: DataSource) = JdbcTransactionRunner(dataSource)
        @Bean fun groups(dataSource: DataSource) = JdbcGroupReadRepository(dataSource)
        @Bean fun links() = PublicAttendanceLinkFactory(URI("https://links.invalid"))
        @Bean fun receivables(): GroupReceivables = GroupReceivables.NONE
        @Bean fun actors() = VerifiedGroupActorResolver { UUID.randomUUID() }
        @Bean fun admins() = PlatformAdminLookup { null }
    }

    private companion object {
        val dataSourceUses = AtomicInteger()
    }
}
