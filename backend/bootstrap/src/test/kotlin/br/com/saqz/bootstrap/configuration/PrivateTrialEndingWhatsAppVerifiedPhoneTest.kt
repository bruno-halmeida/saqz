package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.communication.WhatsAppNotification
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** DEB-081: fim do teste e atraso de pagamento só vão por WhatsApp particular para o número confirmado, lido do banco. */
class PrivateTrialEndingWhatsAppVerifiedPhoneTest {
    private val source = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val sent = mutableListOf<WhatsAppNotification>()
    private val sender = NotificationWhatsAppSender { message -> sent += message; WhatsAppDelivery.Accepted }
    private val whatsApp = PrivateTrialEndingWhatsApp(jdbc, { sender })

    @Test
    fun `numero confirmado recebe o WhatsApp particular`() {
        whatsApp.send(user(phone = "+5511987654321", verified = "+5511987654321"), "Seu teste termina hoje.")

        assertEquals(listOf("+5511987654321"), sent.map { it.phone })
    }

    @Test
    fun `numero digitado e nao confirmado nao recebe nada`() {
        whatsApp.send(user(phone = "+5511987654321", verified = null), "Seu teste termina hoje.")

        assertTrue(sent.isEmpty())
    }

    @Test
    fun `numero trocado depois da confirmacao deixa de receber ate confirmar o novo`() {
        whatsApp.send(user(phone = "+5511911112222", verified = "+5511987654321"), "Seu teste termina hoje.")

        assertTrue(sent.isEmpty())
    }

    private fun user(phone: String, verified: String?): UUID = UUID.randomUUID().also { id ->
        jdbc.sql("""INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, verified_phone, created_at, updated_at)
            VALUES (:id, :subject, true, 'Dona', :phone, :verified, now(), now())""")
            .param("id", id).param("subject", id.toString()).param("phone", phone).param("verified", verified).update()
    }
}
