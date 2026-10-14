package br.com.saqz.access.adapter.output.jdbc.clientapp

import br.com.saqz.access.application.clientapp.ClientAppRecordResult
import br.com.saqz.access.application.clientapp.ClientAppVersionStore
import br.com.saqz.sharedkernel.client.ClientApp
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcClientAppVersionStore(dataSource: DataSource) : ClientAppVersionStore {
    private val jdbc = JdbcClient.create(dataSource)

    override fun record(
        subject: String,
        app: ClientApp.Identified,
        now: Instant,
        refreshAfter: Duration,
    ): ClientAppRecordResult {
        val userId = jdbc.sql(
            "SELECT id FROM access_users WHERE firebase_subject = :subject AND deleted_at IS NULL",
        )
            .param("subject", subject)
            .query(UUID::class.java)
            .optional()
            .orElse(null) ?: return ClientAppRecordResult.UNKNOWN_USER

        // O WHERE do DO UPDATE é o que impede escrever a cada requisição (e vale entre instâncias do backend):
        // sem mudança de versão/build e com a última vez ainda recente, a linha nem é tocada. first_seen_at
        // não está no SET de propósito: é a primeira vez do usuário naquela plataforma e não muda.
        val written = jdbc.sql(
            """
            INSERT INTO access_user_app_versions (user_id, platform, app_version, app_build, first_seen_at, last_seen_at)
            VALUES (:userId, :platform, :version, :build, :now, :now)
            ON CONFLICT (user_id, platform) DO UPDATE
               SET app_version = EXCLUDED.app_version,
                   app_build = EXCLUDED.app_build,
                   last_seen_at = EXCLUDED.last_seen_at
             WHERE access_user_app_versions.app_version IS DISTINCT FROM EXCLUDED.app_version
                OR access_user_app_versions.app_build IS DISTINCT FROM EXCLUDED.app_build
                OR access_user_app_versions.last_seen_at < :staleBefore
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("platform", app.platform.name)
            .param("version", app.version?.toString(), Types.VARCHAR)
            .param("build", app.build, Types.INTEGER)
            .param("now", Timestamp.from(now))
            .param("staleBefore", Timestamp.from(now.minus(refreshAfter)))
            .update()
        return if (written > 0) ClientAppRecordResult.WRITTEN else ClientAppRecordResult.UNCHANGED
    }
}
