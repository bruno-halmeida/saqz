package br.com.saqz.groups.adapter.output.jdbc.admin

import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminGroupDirectoryRepositoryIntegrationTest {
    // O diretório junta o plano do organizador: precisa das migrações de subscriptions também.
    private val database = TestPostgres.migrated(
        "filesystem:" + migrations("access"),
        "filesystem:" + migrations("groups"),
        "filesystem:" + migrations("subscriptions"),
    )
    private lateinit var repository: JdbcAdminGroupDirectoryRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        repository = JdbcAdminGroupDirectoryRepository(database.dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE game_attendance, games, subscriptions, plan_courtesies, app_store_subscriptions, google_play_subscriptions, organizer_trials, group_memberships, access_groups, access_users CASCADE")
    }

    @Test
    fun `lista busca por nome do grupo ou do dono com plano e contagens`() {
        val dona = insertUser("dona", name = "Camila Rocha")
        val outra = insertUser("outra", name = "Pessoa Outra")
        val grupo = insertGroup(dona, name = "Volei do CERET", createdAt = now.minusSeconds(200))
        insertGroup(outra, name = "Racha da Rede", createdAt = now.minusSeconds(100))
        insertMembership(grupo, dona)
        insertSubscription(dona, plan = "ORGANIZADOR")
        insertGame(grupo, startsAt = now.minusSeconds(86_400), status = "PUBLISHED")

        val porGrupo = repository.list(query = "ceret", status = null, page = 1, size = 10)
        val porDona = repository.list(query = "camila", status = null, page = 1, size = 10)

        assertEquals(1, porGrupo.total)
        val item = porGrupo.items.single()
        assertEquals("Volei do CERET", item.name)
        assertEquals("Camila Rocha", item.ownerName)
        assertEquals("ORGANIZADOR", item.ownerPlan)
        assertEquals(1, item.members)
        assertEquals(1, item.gamesPlayed)
        assertEquals(listOf("Volei do CERET"), porDona.items.map { it.name })
    }

    @Test
    fun `ordena por nome e por membros nos dois sentidos antes da paginacao`() {
        val dona = insertUser("dona", name = "Camila")
        val outra = insertUser("outra", name = "Bruna")
        val alfa = insertGroup(dona, name = "alfa", createdAt = now.minusSeconds(300))
        insertGroup(dona, name = "Beta", createdAt = now.minusSeconds(100))
        val gama = insertGroup(dona, name = "Gama", createdAt = now.minusSeconds(200))
        insertMembership(gama, dona)
        insertMembership(gama, outra)
        insertMembership(alfa, dona)

        fun names(sort: String, desc: Boolean, page: Int = 1, size: Int = 10) =
            repository.list(null, null, page, size, sort, desc).items.map { it.name }

        assertEquals(listOf("alfa", "Beta", "Gama"), names("name", desc = false))
        assertEquals(listOf("Gama", "Beta", "alfa"), names("name", desc = true))
        assertEquals(listOf("Beta", "alfa", "Gama"), names("members", desc = false))
        assertEquals(listOf("Gama", "alfa", "Beta"), names("members", desc = true))
        // Página 2 continua a ordem pedida, não a padrão (mais novos primeiro).
        assertEquals(listOf("Gama", "Beta"), names("name", desc = true, page = 1, size = 2))
        assertEquals(listOf("alfa"), names("name", desc = true, page = 2, size = 2))
        assertEquals(listOf("Beta", "Gama", "alfa"), repository.list(null, null, 1, 10).items.map { it.name })
    }

    @Test
    fun `dono sem assinatura aparece sem plano`() {
        val dono = insertUser("gratis", name = "Sem Plano")
        insertGroup(dono, name = "Grupo Gratis", createdAt = now)

        assertNull(repository.list(null, null, 1, 10).items.single().ownerPlan)
    }

    @Test
    fun `dono com checkout nunca pago fica sem plano`() {
        val dono = insertUser("nunca-pagou", name = "Nunca Pagou")
        insertGroup(dono, name = "Grupo Sem Pagamento", createdAt = now)
        insertSubscription(dono, plan = "ORGANIZADOR")
        execute("UPDATE subscriptions SET status = 'PAST_DUE', first_confirmed_at = NULL WHERE owner_user_id = '$dono'")

        assertNull(repository.list(null, null, 1, 10).items.single().ownerPlan)
    }

    @Test
    fun `assinatura do site cancelada ainda no periodo pago aparece`() {
        val dono = insertUser("cancelada-no-periodo", name = "Cancelou no Periodo")
        val grupo = insertGroup(dono, name = "Grupo Periodo", createdAt = now)
        insertSubscription(dono, plan = "ORGANIZADOR")
        execute("UPDATE subscriptions SET status = 'CANCELED', current_period_end = now() + interval '5 days' WHERE owner_user_id = '$dono'")

        assertEquals("ORGANIZADOR", repository.list(null, null, 1, 10).items.single().ownerPlan)
        assertEquals("SITE", repository.find(grupo)?.ownerPlanSource)
    }

    @Test
    fun `assinatura do site em atraso ja congelado fica sem plano`() {
        val dono = insertUser("congelada", name = "Atraso Congelado")
        insertGroup(dono, name = "Grupo Congelado", createdAt = now)
        insertSubscription(dono, plan = "ORGANIZADOR")
        execute("UPDATE subscriptions SET status = 'PAST_DUE', past_due_since = now() - interval '30 days' WHERE owner_user_id = '$dono'")

        assertNull(repository.list(null, null, 1, 10).items.single().ownerPlan)
    }

    @Test
    fun `plano efetivo traz a origem e o fim da cortesia e do teste`() {
        val comCortesia = insertUser("cortesia", name = "Com Cortesia")
        val semPrazo = insertUser("sem-prazo", name = "Cortesia Sem Prazo")
        val emTeste = insertUser("teste", name = "Em Teste")
        val grupoA = insertGroup(comCortesia, name = "Grupo A", createdAt = now)
        val grupoB = insertGroup(semPrazo, name = "Grupo B", createdAt = now)
        val grupoC = insertGroup(emTeste, name = "Grupo C", createdAt = now)
        val fim = Instant.parse("2099-11-30T15:00:00Z")
        insertCourtesy(comCortesia, plan = "ORGANIZADOR", endsAt = "'$fim'")
        insertCourtesy(semPrazo, plan = "ILIMITADO", endsAt = "NULL")
        execute(
            "INSERT INTO organizer_trials (owner_user_id, started_at, ends_at) VALUES " +
                "('$emTeste', '2099-11-16T15:00:00Z', '2099-11-30T15:00:00Z')",
        )

        val a = repository.find(grupoA)!!
        val b = repository.find(grupoB)!!
        val c = repository.list(null, null, 1, 10).items.single { it.groupId == grupoC }

        assertEquals(Triple("ORGANIZADOR", "COURTESY", fim), Triple(a.ownerPlan, a.ownerPlanSource, a.ownerPlanEndsAt))
        assertEquals(Triple("ILIMITADO", "COURTESY", null), Triple(b.ownerPlan, b.ownerPlanSource, b.ownerPlanEndsAt))
        assertEquals(Triple("ORGANIZADOR", "TRIAL", fim), Triple(c.ownerPlan, c.ownerPlanSource, c.ownerPlanEndsAt))
    }

    @Test
    fun `site TITULAR com cortesia ILIMITADO vale a cortesia e empate fica com o site`() {
        val maior = insertUser("maior", name = "Cortesia Maior")
        val empate = insertUser("empate", name = "Empate")
        val grupoMaior = insertGroup(maior, name = "Grupo Maior", createdAt = now)
        val grupoEmpate = insertGroup(empate, name = "Grupo Empate", createdAt = now)
        insertSubscription(maior, plan = "TITULAR")
        insertCourtesy(maior, plan = "ILIMITADO", endsAt = "NULL")
        insertSubscription(empate, plan = "ORGANIZADOR")
        insertCourtesy(empate, plan = "ORGANIZADOR", endsAt = "NULL")

        assertEquals("ILIMITADO" to "COURTESY", repository.find(grupoMaior)!!.let { it.ownerPlan to it.ownerPlanSource })
        assertEquals("ORGANIZADOR" to "SITE", repository.find(grupoEmpate)!!.let { it.ownerPlan to it.ownerPlanSource })
    }

    @Test
    fun `cortesia revogada ou vencida e dono sem nada ficam sem plano`() {
        val revogada = insertUser("revogada", name = "Revogada")
        val vencida = insertUser("vencida", name = "Vencida")
        insertGroup(revogada, name = "Grupo Revogada", createdAt = now)
        insertGroup(vencida, name = "Grupo Vencida", createdAt = now)
        insertCourtesy(revogada, plan = "ILIMITADO", endsAt = "NULL", revoked = true)
        insertCourtesy(vencida, plan = "ILIMITADO", endsAt = "now() - interval '1 day'")

        repository.list(null, null, 1, 10).items.forEach {
            assertNull(it.ownerPlan)
            assertNull(it.ownerPlanSource)
        }
    }

    @Test
    fun `lista default esconde apagados e o filtro deleted os expoe`() {
        val dono = insertUser("dono", name = "Dono Comum")
        insertGroup(dono, name = "Grupo Vivo", createdAt = now)
        val apagado = insertGroup(dono, name = "Grupo Apagado", createdAt = now)
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$apagado'")

        assertEquals(listOf("Grupo Vivo"), repository.list(null, null, 1, 10).items.map { it.name })
        assertEquals(
            listOf("Grupo Apagado"),
            repository.list(null, status = "deleted", page = 1, size = 10).items.map { it.name },
        )
    }

    @Test
    fun `pagina alem do fim preserva o total`() {
        val dono = insertUser("dono-p", name = "Dono Paginas")
        insertGroup(dono, name = "Grupo Unico", createdAt = now)

        val page = repository.list(null, null, page = 4, size = 10)

        assertEquals(1, page.total)
        assertEquals(0, page.items.size)
    }

    @Test
    fun `detalhe traz ultimos jogos com confirmados, sem rascunho`() {
        val dono = insertUser("dono", name = "Dona Detalhe")
        val membro = insertUser("membro", name = "Membro Um")
        val grupo = insertGroup(dono, name = "Grupo Detalhe", createdAt = now)
        insertMembership(grupo, dono)
        insertMembership(grupo, membro)
        val jogo = insertGame(grupo, startsAt = now.minusSeconds(86_400), status = "PUBLISHED")
        insertGame(grupo, startsAt = now.minusSeconds(172_800), status = "DRAFT")
        // Futuro em relação ao relógio do banco (a query usa now() real, não o fixture).
        insertGame(grupo, startsAt = Instant.now().plusSeconds(30 * 86_400), status = "PUBLISHED")
        insertAttendance(grupo, jogo, dono, status = "CONFIRMED")
        insertAttendance(grupo, jogo, membro, status = "DECLINED")

        val detail = repository.find(grupo)

        assertNotNull(detail)
        assertEquals(2, detail.members)
        assertEquals(1, detail.lastGames.size)
        assertEquals(1, detail.lastGames.single().confirmed)
        assertEquals("PUBLISHED", detail.lastGames.single().status)
        assertNull(repository.find(UUID.randomUUID()))
    }

    @Test
    fun `detalhe traz o cadastro inteiro, o local, os horarios, o whatsapp e o contato do dono`() {
        val dona = insertUser("dona", name = "Camila Rocha")
        execute("UPDATE access_users SET email = 'camila@saqz.test', phone = '+5511900001234', verified_phone = '+5511900001234' WHERE id = '$dona'")
        val grupo = insertGroup(dona, name = "Volei do Parque", createdAt = now)
        val venue = UUID.randomUUID()
        execute(
            "INSERT INTO group_venues (id, group_id, name, address, court, created_at, updated_at) " +
                "VALUES ('$venue', '$grupo', 'Quadra Parque', 'Rua das Arvores, 10', 'Quadra 2', now(), now())",
        )
        execute(
            """
            UPDATE access_groups SET city = 'Sao Paulo', description = 'Volei misto de quinta', modality = 'COURT_VOLLEYBALL',
                composition = 'MIXED', default_capacity = 18, default_duration_minutes = 120,
                default_confirmation_lead_minutes = 1440, default_game_fee_cents = 2500, monthly_fee_cents = 9000,
                monthly_due_day = 10, pix_key = 'camila@saqz.test', pix_label = 'Camila', opening_balance_cents = 32000,
                entry_requires_approval = true, default_venue_id = '$venue'
            WHERE id = '$grupo'
            """.trimIndent(),
        )
        execute(
            "INSERT INTO group_regular_slots (id, group_id, venue_id, weekday, start_time, duration_minutes, created_at, updated_at) " +
                "VALUES ('${UUID.randomUUID()}', '$grupo', '$venue', 4, '19:00', 120, now(), now())",
        )
        execute(
            "INSERT INTO group_whatsapp_bindings (group_id, whatsapp_jid, invite_code, group_name, instance_jid, created_by) " +
                "VALUES ('$grupo', '120363000@g.us', 'codigo-secreto', 'Volei do Parque WhatsApp', '5511@s.whatsapp.net', '$dona')",
        )
        val jogoAntes = now.minusSeconds(86_400)
        insertGame(grupo, startsAt = jogoAntes, status = "COMPLETED")

        val detail = assertNotNull(repository.find(grupo))

        val profile = assertNotNull(detail.profile)
        assertEquals("Sao Paulo", profile.city)
        assertEquals("Volei misto de quinta", profile.description)
        assertEquals("COURT_VOLLEYBALL", profile.modality)
        assertEquals("MIXED", profile.composition)
        assertEquals(18, profile.defaultCapacity)
        assertEquals(1440, profile.defaultConfirmationLeadMinutes)
        assertEquals(2500L, profile.defaultGameFeeCents)
        assertEquals(9000L, profile.monthlyFeeCents)
        assertEquals(10, profile.monthlyDueDay)
        assertEquals("camila@saqz.test", profile.pixKey)
        assertEquals(32000L, profile.openingBalanceCents)
        assertEquals(true, profile.entryRequiresApproval)
        assertEquals("Quadra Parque", detail.venue?.name)
        assertEquals("Quadra 2", detail.venue?.court)
        val slot = detail.slots.single()
        assertEquals(4, slot.weekday)
        assertEquals("19:00", slot.startTime)
        assertEquals("Quadra Parque", slot.venueName)
        assertEquals("Volei do Parque WhatsApp", detail.whatsApp?.name)
        assertEquals("120363000@g.us", detail.whatsApp?.chatId)
        assertEquals(false, detail.whatsApp?.broken)
        assertEquals(jogoAntes, detail.lastGameAt)
        assertEquals("+5511900001234", detail.ownerContact?.phone)
        assertEquals(true, detail.ownerContact?.phoneVerified)
        assertEquals("camila@saqz.test", detail.ownerContact?.email)
        assertEquals(false, detail.frozen)
    }

    @Test
    fun `grupo congela so quando o dono teve teste e ficou sem plano`() {
        val semTeste = insertUser("sem", name = "Nunca Testou")
        val acabou = insertUser("acabou", name = "Teste Acabou")
        val pagante = insertUser("paga", name = "Assinante")
        val apagou = insertUser("apagou", name = "Apagou O Grupo")
        val fim = Instant.now().minusSeconds(86_400)
        for (dono in listOf(acabou, pagante, apagou)) {
            execute("INSERT INTO organizer_trials (owner_user_id, started_at, ends_at) VALUES ('$dono', '${fim.minusSeconds(1_209_600)}', '$fim')")
        }
        insertSubscription(pagante, plan = "ORGANIZADOR")
        val apagado = insertGroup(apagou, "Apagado", now)
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '$apagado'")

        assertEquals(false, repository.find(insertGroup(semTeste, "Grupo A", now))?.frozen)
        assertEquals(true, repository.find(insertGroup(acabou, "Grupo B", now))?.frozen)
        assertEquals(false, repository.find(insertGroup(pagante, "Grupo C", now))?.frozen)
        assertEquals(false, repository.find(apagado)?.frozen)
    }

    @Test
    fun `dono que pediu para nao ser procurado aparece bloqueado na ficha e na lista de membros`() {
        val dona = insertUser("dona", name = "Camila Rocha")
        val equipe = insertUser("equipe", name = "Equipe Saqz")
        val grupo = insertGroup(dona, "Volei", now)
        insertMembership(grupo, dona)
        fun contato(result: String) = execute(
            "INSERT INTO admin_user_contacts (id, user_id, result, created_by) VALUES ('${UUID.randomUUID()}', '$dona', '$result', '$equipe')",
        )

        contato("DO_NOT_CONTACT")
        assertEquals(true, repository.find(grupo)?.ownerContact?.doNotContact)
        assertEquals(true, repository.members(grupo, null, 1, 10)?.items?.single()?.doNotContact)

        contato("OTHER")
        assertEquals(false, repository.find(grupo)?.ownerContact?.doNotContact)
        assertEquals(false, repository.members(grupo, null, 1, 10)?.items?.single()?.doNotContact)
    }

    @Test
    fun `membros vem com contato e perfil, dono e admin primeiro, com busca e paginacao`() {
        val dona = insertUser("dona", name = "Zelia Dona")
        val admin = insertUser("admin", name = "Xavier Admin")
        val atleta = insertUser("atleta", name = "Bruna Atleta")
        val avulso = insertUser("avulso", name = "Caio Avulso")
        val grupo = insertGroup(dona, "Volei", now)
        for (id in listOf(dona, admin, atleta, avulso)) insertMembership(grupo, id)
        execute("UPDATE group_memberships SET role = 'ADMIN' WHERE user_id = '$admin'")
        execute(
            "UPDATE group_memberships SET membership_type = 'MENSALISTA', position = 'LEVANTADOR', level = 'AVANCADO', " +
                "preferred_side = 'DIREITA', height_cm = 172, monthly_fee_exempt = true, mensalista_since = '2026-10-01' " +
                "WHERE user_id = '$atleta'",
        )
        execute("UPDATE access_users SET phone = '+5511900001234', verified_phone = '+5511900001234' WHERE id = '$atleta'")
        execute("UPDATE access_users SET phone = '+5521900005678' WHERE id = '$avulso'")
        val noApp = Instant.now().plusSeconds(3_600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        execute(
            "INSERT INTO access_user_app_versions (user_id, platform, app_version, app_build, first_seen_at, last_seen_at) " +
                "VALUES ('$atleta', 'ANDROID', '1.0.16', 33, '$noApp', '$noApp')",
        )

        val pagina = assertNotNull(repository.members(grupo, null, 1, 10))

        assertEquals(4, pagina.total)
        assertEquals(listOf("Zelia Dona", "Xavier Admin", "Bruna Atleta", "Caio Avulso"), pagina.items.map { it.displayName })
        assertEquals(listOf("OWNER", "ADMIN", "ATHLETE", "ATHLETE"), pagina.items.map { it.role })
        val bruna = pagina.items[2]
        assertEquals("+5511900001234", bruna.phone)
        assertEquals(true, bruna.phoneVerified)
        assertEquals("MENSALISTA", bruna.membershipType)
        assertEquals("LEVANTADOR", bruna.position)
        assertEquals("AVANCADO", bruna.level)
        assertEquals("DIREITA", bruna.preferredSide)
        assertEquals(172, bruna.heightCm)
        assertEquals(true, bruna.monthlyFeeExempt)
        assertEquals(noApp, bruna.lastSeenAt)
        assertEquals(false, pagina.items[3].phoneVerified)

        assertEquals(listOf("Caio Avulso"), repository.members(grupo, "(21) 90000", 1, 10)?.items?.map { it.displayName })
        assertEquals(listOf("Bruna Atleta"), repository.members(grupo, "bruna", 1, 10)?.items?.map { it.displayName })
        assertEquals(listOf("Xavier Admin"), repository.members(grupo, null, 2, 1)?.items?.map { it.displayName })
        // Fora da última página, a contagem ainda bate.
        assertEquals(4, repository.members(grupo, null, 9, 10)?.total)
        assertEquals(1, repository.members(grupo, "bruna", 9, 10)?.total)
        assertNull(repository.members(UUID.randomUUID(), null, 1, 10))
    }

    private fun insertUser(subject: String, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject', true, '$name', now(), now())",
        )
        return id
    }

    private fun insertGroup(ownerId: UUID, name: String, createdAt: Instant): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$ownerId', '${UUID.randomUUID()}', '$name', 'America/Sao_Paulo', '$createdAt', '$createdAt')",
        )
        return id
    }

    private fun insertMembership(groupId: UUID, userId: UUID) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$groupId', '$userId', 'ATHLETE', now(), now())",
        )
    }

    private fun insertSubscription(ownerId: UUID, plan: String) {
        execute(
            "INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, " +
                "asaas_subscription_id, current_period_end, first_confirmed_at, created_at, updated_at) " +
                "VALUES ('$ownerId', '$plan', 'MONTHLY', 'ACTIVE', 'cus-$ownerId', 'sub-$ownerId', " +
                "'${now.plusSeconds(2_592_000)}', now(), now(), now())",
        )
    }

    private fun insertCourtesy(ownerId: UUID, plan: String, endsAt: String, revoked: Boolean = false) {
        val admin = insertUser("admin-${UUID.randomUUID()}", name = "Admin")
        execute(
            "INSERT INTO plan_courtesies (id, owner_user_id, plan, ends_at, reason, granted_by, revoked_at, revoked_by) " +
                "VALUES ('${UUID.randomUUID()}', '$ownerId', '$plan', $endsAt, 'teste', '$admin', " +
                "${if (revoked) "now()" else "NULL"}, ${if (revoked) "'$admin'" else "NULL"})",
        )
    }

    private fun insertGame(groupId: UUID, startsAt: Instant, status: String): UUID {
        val id = UUID.randomUUID()
        execute(
            """
            INSERT INTO games (
                id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes,
                confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at
            ) VALUES (
                '$id', '$groupId', 'Jogo da Semana', '2026-07-01', '19:30', 'America/Sao_Paulo',
                '$startsAt', 90, '$startsAt', 'Quadra Central', 'Rua dos Esportes, 100', 12, '$status', now(), now()
            )
            """.trimIndent(),
        )
        return id
    }

    private fun insertAttendance(groupId: UUID, gameId: UUID, userId: UUID, status: String) {
        execute(
            "INSERT INTO game_attendance (game_id, group_id, member_user_id, member_display_name, status, " +
                "responded_at, updated_at) " +
                "VALUES ('$gameId', '$groupId', '$userId', 'Nome Presenca', '$status', now(), now())",
        )
    }

    private fun migrations(feature: String): Path {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        val candidates = listOf(
            workingDirectory.resolve("backend/features/$feature/src/main/resources/db/migration"),
            workingDirectory.resolve("features/$feature/src/main/resources/db/migration"),
            workingDirectory.resolve("../$feature/src/main/resources/db/migration").normalize(),
        )
        return candidates.firstOrNull(Files::isDirectory)
            ?: error("Cannot find $feature migrations from $workingDirectory")
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
