package br.com.saqz.access.adapter.output.jdbc.clientapp

import br.com.saqz.access.application.clientapp.ClientAppRecordResult
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.client.AppVersion
import br.com.saqz.sharedkernel.client.ClientApp
import br.com.saqz.sharedkernel.client.ClientPlatform
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcClientAppVersionStoreIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var store: JdbcClientAppVersionStore

    private val refresh = Duration.ofHours(6)
    private val start = Instant.parse("2026-10-14T12:00:00Z")
    private val ios = ClientApp.Identified(ClientPlatform.IOS, AppVersion(1, 0, 3), 14)

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated("classpath:db/migration").dataSource
        jdbc = JdbcClient.create(dataSource)
        store = JdbcClientAppVersionStore(dataSource)
    }

    @BeforeEach
    fun clearData() {
        jdbc.sql("TRUNCATE access_users CASCADE").update()
    }

    @Test
    fun `the first sighting inserts the row with the platform version and build`() {
        val user = insertUser("maria")

        assertEquals(ClientAppRecordResult.WRITTEN, store.record("maria", ios, start, refresh))

        assertEquals(
            Row(user, "IOS", "1.0.3", 14, start, start),
            rows().single(),
        )
    }

    @Test
    fun `the same app seen again inside the window leaves the row untouched`() {
        insertUser("maria")
        store.record("maria", ios, start, refresh)

        val result = store.record("maria", ios, start.plus(Duration.ofHours(5)), refresh)

        assertEquals(ClientAppRecordResult.UNCHANGED, result)
        assertEquals(start, rows().single().lastSeenAt)
    }

    @Test
    fun `the last seen is renewed only after more than six hours`() {
        insertUser("maria")
        store.record("maria", ios, start, refresh)

        val justBefore = store.record("maria", ios, start.plus(refresh).minusSeconds(1), refresh)
        assertEquals(ClientAppRecordResult.UNCHANGED, justBefore)
        // Exatamente 6 h ainda não é "mais de 6 h".
        val atSixHours = store.record("maria", ios, start.plus(refresh), refresh)
        assertEquals(ClientAppRecordResult.UNCHANGED, atSixHours)
        assertEquals(start, rows().single().lastSeenAt)

        val justAfter = store.record("maria", ios, start.plus(refresh).plusSeconds(1), refresh)

        assertEquals(ClientAppRecordResult.WRITTEN, justAfter)
        assertEquals(start.plus(refresh).plusSeconds(1), rows().single().lastSeenAt)
        assertEquals(start, rows().single().firstSeenAt)
    }

    @Test
    fun `a new version overwrites the row at once and keeps the first sighting`() {
        insertUser("maria")
        store.record("maria", ios, start, refresh)
        val later = start.plusSeconds(30)

        val result = store.record("maria", ios.copy(version = AppVersion(1, 0, 4), build = 15), later, refresh)

        assertEquals(ClientAppRecordResult.WRITTEN, result)
        val row = rows().single()
        assertEquals("1.0.4", row.appVersion)
        assertEquals(15, row.appBuild)
        assertEquals(start, row.firstSeenAt)
        assertEquals(later, row.lastSeenAt)
    }

    @Test
    fun `a new build with the same version is also written`() {
        insertUser("maria")
        store.record("maria", ios, start, refresh)

        assertEquals(ClientAppRecordResult.WRITTEN, store.record("maria", ios.copy(build = 15), start.plusSeconds(1), refresh))

        assertEquals(15, rows().single().appBuild)
    }

    @Test
    fun `an app without version or build is stored with nulls and a later version replaces them`() {
        insertUser("maria")
        val web = ClientApp.Identified(ClientPlatform.WEB)

        assertEquals(ClientAppRecordResult.WRITTEN, store.record("maria", web, start, refresh))
        assertEquals(Row(rows().single().userId, "WEB", null, null, start, start), rows().single())
        assertEquals(ClientAppRecordResult.UNCHANGED, store.record("maria", web, start.plusSeconds(60), refresh))

        assertEquals(ClientAppRecordResult.WRITTEN, store.record("maria", web.copy(version = AppVersion(1, 0, 3)), start.plusSeconds(120), refresh))
        assertEquals("1.0.3", rows().single().appVersion)

        assertEquals(ClientAppRecordResult.WRITTEN, store.record("maria", web, start.plusSeconds(180), refresh))
        assertNull(rows().single().appVersion)
    }

    @Test
    fun `each platform of the same user has its own row`() {
        insertUser("maria")
        store.record("maria", ios, start, refresh)
        store.record("maria", ClientApp.Identified(ClientPlatform.ANDROID, AppVersion(1, 0, 2), 13), start, refresh)

        assertEquals(listOf("ANDROID" to "1.0.2", "IOS" to "1.0.3"), rows().map { it.platform to it.appVersion })
    }

    @Test
    fun `each user has their own rows`() {
        insertUser("maria")
        insertUser("joao")
        store.record("maria", ios, start, refresh)
        store.record("joao", ios.copy(version = AppVersion(1, 0, 2), build = 13), start, refresh)

        assertEquals(2, rows().size)
        assertEquals(1, count("SELECT count(*) FROM access_user_app_versions WHERE app_version = '1.0.3'"))
        assertEquals(1, count("SELECT count(*) FROM access_user_app_versions WHERE app_version = '1.0.2'"))
    }

    @Test
    fun `an unknown subject writes nothing`() {
        assertEquals(ClientAppRecordResult.UNKNOWN_USER, store.record("ninguem", ios, start, refresh))

        assertEquals(0, rows().size)
    }

    @Test
    fun `a deleted account writes nothing`() {
        val user = insertUser("excluida")
        jdbc.sql("UPDATE access_users SET deleted_at = now() WHERE id = :id").param("id", user).update()

        assertEquals(ClientAppRecordResult.UNKNOWN_USER, store.record("excluida", ios, start, refresh))

        assertEquals(0, rows().size)
    }

    @Test
    fun `a new account created after the old one was deleted is recorded under its own id`() {
        val old = insertUser("mesma-pessoa")
        jdbc.sql("UPDATE access_users SET deleted_at = now() WHERE id = :id").param("id", old).update()
        val replacement = insertUser("mesma-pessoa")

        store.record("mesma-pessoa", ios, start, refresh)

        assertEquals(listOf(replacement), rows().map { it.userId })
    }

    @Test
    fun `removing the user removes the rows`() {
        val user = insertUser("maria")
        store.record("maria", ios, start, refresh)

        jdbc.sql("DELETE FROM access_users WHERE id = :id").param("id", user).update()

        assertEquals(0, rows().size)
    }

    @Test
    fun `parallel first sightings of the same user and platform leave one row`() {
        insertUser("maria")
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        try {
            val results = (1..threads).map {
                pool.submit(Callable {
                    ready.countDown()
                    go.await()
                    store.record("maria", ios, start, refresh)
                })
            }
            ready.await()
            go.countDown()
            val outcomes = results.map { it.get(30, TimeUnit.SECONDS) }

            assertEquals(1, outcomes.count { it == ClientAppRecordResult.WRITTEN })
            assertEquals(threads - 1, outcomes.count { it == ClientAppRecordResult.UNCHANGED })
            assertEquals(1, rows().size)
        } finally {
            pool.shutdownNow()
        }
    }

    private data class Row(
        val userId: UUID,
        val platform: String,
        val appVersion: String?,
        val appBuild: Int?,
        val firstSeenAt: Instant,
        val lastSeenAt: Instant,
    )

    private fun rows(): List<Row> = jdbc.sql(
        "SELECT user_id, platform, app_version, app_build, first_seen_at, last_seen_at " +
            "FROM access_user_app_versions ORDER BY platform, user_id",
    ).query { result, _ ->
        Row(
            userId = result.getObject("user_id", UUID::class.java),
            platform = result.getString("platform"),
            appVersion = result.getString("app_version"),
            appBuild = result.getObject("app_build") as Int?,
            firstSeenAt = result.getTimestamp("first_seen_at").toInstant(),
            lastSeenAt = result.getTimestamp("last_seen_at").toInstant(),
        )
    }.list()

    private fun count(sql: String): Int = jdbc.sql(sql).query(Int::class.java).single()

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES (:id, :subject, true, 'Pessoa Teste', :now, :now)",
        ).param("id", id).param("subject", subject).param("now", Timestamp.from(start)).update()
        return id
    }
}
