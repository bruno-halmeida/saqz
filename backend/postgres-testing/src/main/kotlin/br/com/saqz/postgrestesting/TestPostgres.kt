package br.com.saqz.postgrestesting

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Coordenadas JDBC de um banco recem-criado, exclusivo de quem pediu. */
class TestDatabase(val jdbcUrl: String, val username: String, val password: String) {
    val dataSource: DriverManagerDataSource by lazy { DriverManagerDataSource(jdbcUrl, username, password) }
}

/**
 * Um unico PostgreSQL real (binario do zonky, sem Docker) por JVM de teste.
 *
 * Rodar o Flyway a cada classe era o custo dominante da suite; aqui ele roda uma
 * vez por combinacao de migracoes e vira um TEMPLATE do proprio Postgres, entao
 * cada chamada de [migrated] e um CREATE DATABASE de milissegundos. Testes que
 * exercitam o Flyway em si (target, migrationsExecuted) pedem [empty] e migram
 * de verdade.
 *
 * Durabilidade fica desligada (fsync etc.) porque o banco inteiro e descartavel;
 * locale C + UTF-8 espelham o postgres:16-alpine de producao.
 */
object TestPostgres {
    private const val SUPERUSER = "postgres"

    private val postgres: EmbeddedPostgres by lazy {
        // Dados ficam em build/ (some no clean); um dado-<pid> novo por JVM, e os
        // de execucoes anteriores sao purgados aqui porque so uma task de
        // integrationTest roda por modulo de cada vez.
        val root = Path.of(System.getProperty("user.dir"), "build", "embedded-postgres")
        Files.createDirectories(root)
        root.toFile().listFiles { file -> file.name.startsWith("data-") }?.forEach { it.deleteRecursively() }
        val dataDirectory = root.resolve("data-${ProcessHandle.current().pid()}")

        val instance = EmbeddedPostgres.builder()
            .setDataDirectory(dataDirectory)
            .setCleanDataDirectory(true)
            .setLocaleConfig("locale", "C")
            .setLocaleConfig("encoding", "UTF-8")
            .setServerConfig("fsync", "off")
            .setServerConfig("synchronous_commit", "off")
            .setServerConfig("full_page_writes", "off")
            .start()
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { instance.close() } })

        instance.postgresDatabase.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SHOW server_encoding").use { result ->
                    result.next()
                    check(result.getString(1) == "UTF8") {
                        "PostgreSQL embutido subiu com encoding ${result.getString(1)}; " +
                            "os testes assumem UTF8 como em producao"
                    }
                }
            }
        }
        instance
    }

    private val sequence = AtomicInteger()
    private val templates = ConcurrentHashMap<List<String>, String>()
    private val lastDatabaseByOwner = ConcurrentHashMap<String, String>()

    /**
     * Banco novo ja migrado com as [flywayLocations] dadas. Passe [owner] (a
     * instancia do teste) ao chamar por teste: o banco anterior do mesmo dono e
     * derrubado para o disco nao crescer com a suite.
     */
    fun migrated(vararg flywayLocations: String, owner: Any? = null): TestDatabase {
        val template = templates.computeIfAbsent(flywayLocations.toList()) { locations ->
            val name = "tpl_${sequence.incrementAndGet()}"
            admin { statement -> statement.execute("CREATE DATABASE $name") }
            Flyway.configure()
                .dataSource(jdbcUrl(name), SUPERUSER, SUPERUSER)
                .locations(*locations.toTypedArray())
                .load()
                .migrate()
            name
        }
        return create(template, owner)
    }

    /** Banco novo vazio, para quem roda o Flyway por conta propria. */
    fun empty(owner: Any? = null): TestDatabase = create(template = null, owner)

    private fun create(template: String?, owner: Any?): TestDatabase {
        val name = "test_${sequence.incrementAndGet()}"
        val previous = owner?.let { lastDatabaseByOwner.put(it.javaClass.name, name) }
        admin { statement ->
            if (previous != null) statement.execute("DROP DATABASE IF EXISTS $previous WITH (FORCE)")
            statement.execute("CREATE DATABASE $name" + if (template != null) " TEMPLATE $template" else "")
        }
        return TestDatabase(jdbcUrl(name), SUPERUSER, SUPERUSER)
    }

    private fun jdbcUrl(database: String) = "jdbc:postgresql://localhost:${postgres.port}/$database"

    private fun admin(block: (java.sql.Statement) -> Unit) {
        postgres.postgresDatabase.connection.use { connection: Connection ->
            connection.createStatement().use(block)
        }
    }
}
