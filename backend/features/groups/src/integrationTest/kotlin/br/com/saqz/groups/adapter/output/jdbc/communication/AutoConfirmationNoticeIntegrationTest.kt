package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import kotlin.test.assertEquals

/** Confirmação automática nasce desligada no grupo e avisa o mensalista quando passa a valer para ele. */
class AutoConfirmationNoticeIntegrationTest {
    private val source = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)

    private val owner = user("Dona")
    private val group = UUID.randomUUID()

    init {
        jdbc.sql(
            """INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at)
            VALUES (:id, :owner, :key, 'QA Vôlei', 'America/Sao_Paulo', now(), now())""",
        ).param("id", group).param("owner", owner).param("key", UUID.randomUUID()).update()
        membership(owner, "ADMIN", "AVULSO")
    }

    @Test fun `grupo novo nasce com a confirmacao automatica desligada e o membro com a preferencia ligada`() {
        val rafa = member("Rafa", "MENSALISTA")

        assertEquals(false, groupEnabled())
        assertEquals(true, memberEnabled(rafa))
    }

    @Test fun `gestor liga e so o mensalista com a preferencia ligada recebe o aviso`() {
        val rafa = member("Rafa", "MENSALISTA")
        val bia = member("Bia", "MENSALISTA")
        val avulso = member("Caio", "AVULSO")
        setMember(bia, false)

        jdbc.sql("UPDATE access_groups SET auto_confirm_enabled = true WHERE id = :g").param("g", group).update()

        assertEquals(
            "Você vai estar confirmado automaticamente nos jogos do QA Vôlei. Se não puder ir, toque Não vou.",
            body(),
        )
        assertEquals(setOf(rafa), recipients())
        assertEquals(true, pushEnabled(rafa))
        assertEquals(false, recipients().contains(bia))
    }

    @Test fun `desligar e ligar de novo no mesmo dia nao repete o aviso`() {
        val rafa = member("Rafa", "MENSALISTA")
        setMember(rafa, true)

        jdbc.sql("UPDATE access_groups SET auto_confirm_enabled = true WHERE id = :g").param("g", group).update()
        jdbc.sql("UPDATE access_groups SET auto_confirm_enabled = false WHERE id = :g").param("g", group).update()
        jdbc.sql("UPDATE access_groups SET auto_confirm_enabled = true WHERE id = :g").param("g", group).update()

        assertEquals(1, count())
    }

    @Test fun `salvar o grupo sem mudar a confirmacao automatica nao avisa`() {
        val rafa = member("Rafa", "MENSALISTA")
        setMember(rafa, true)
        jdbc.sql("UPDATE access_groups SET auto_confirm_enabled = true WHERE id = :g").param("g", group).update()

        jdbc.sql("UPDATE access_groups SET auto_confirm_enabled = true, name = 'Outro nome' WHERE id = :g")
            .param("g", group).update()

        assertEquals(1, count())
    }

    @Test fun `atleta que vira mensalista num grupo com a funcao ligada recebe o aviso`() {
        jdbc.sql("UPDATE access_groups SET auto_confirm_enabled = true WHERE id = :g").param("g", group).update()
        val caio = member("Caio", "AVULSO")
        setMember(caio, true)
        assertEquals(0, count())

        jdbc.sql("UPDATE group_memberships SET membership_type = 'MENSALISTA' WHERE user_id = :u").param("u", caio).update()

        assertEquals(setOf(caio), recipients())
    }

    @Test fun `grupo com a funcao desligada nao avisa quem vira mensalista`() {
        val caio = member("Caio", "AVULSO")
        setMember(caio, true)

        jdbc.sql("UPDATE group_memberships SET membership_type = 'MENSALISTA' WHERE user_id = :u").param("u", caio).update()

        assertEquals(0, count())
    }

    private fun groupEnabled() = jdbc.sql("SELECT auto_confirm_enabled FROM access_groups WHERE id = :g")
        .param("g", group).query(Boolean::class.javaObjectType).single()

    private fun memberEnabled(user: UUID) = jdbc.sql("SELECT auto_confirm_enabled FROM group_memberships WHERE user_id = :u")
        .param("u", user).query(Boolean::class.javaObjectType).single()

    private fun setMember(user: UUID, enabled: Boolean) =
        jdbc.sql("UPDATE group_memberships SET auto_confirm_enabled = :e WHERE user_id = :u")
            .param("e", enabled).param("u", user).update()

    private fun count() = jdbc.sql("SELECT count(*) FROM group_messages WHERE channel = 'AUTO_CONFIRMATION'")
        .query(Int::class.java).single()

    private fun body() = jdbc.sql("SELECT body FROM group_messages WHERE channel = 'AUTO_CONFIRMATION'")
        .query(String::class.java).single()

    private fun recipients() = jdbc.sql(
        """SELECT n.recipient_id FROM group_notifications n JOIN group_messages m ON m.id = n.message_id
           WHERE m.channel = 'AUTO_CONFIRMATION' AND n.in_app""",
    ).query(UUID::class.java).list().filterNotNull().toSet()

    private fun pushEnabled(recipient: UUID) = jdbc.sql(
        "SELECT push_enabled FROM notification_delivery_context WHERE recipient_id = :r AND channel = 'AUTO_CONFIRMATION'",
    ).param("r", recipient).query(Boolean::class.javaObjectType).single()

    private fun member(name: String, type: String) = user(name).also { membership(it, "ATHLETE", type) }

    private fun membership(user: UUID, role: String, type: String) = jdbc.sql(
        """INSERT INTO group_memberships(group_id, user_id, role, membership_type, active, created_at, updated_at)
        VALUES (:g, :u, CAST(:role AS group_role), CAST(:type AS athlete_membership_type), true, now(), now())""",
    ).param("g", group).param("u", user).param("role", role).param("type", type).update()

    private fun user(name: String): UUID = UUID.randomUUID().also { id ->
        jdbc.sql(
            """INSERT INTO access_users(id, firebase_subject, email_verified, display_name, phone, created_at, updated_at)
            VALUES (:id, :subject, true, :name, '+5511999999999', now(), now())""",
        ).param("id", id).param("subject", id.toString()).param("name", name).update()
    }
}
