package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.FinishedGamesRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class JdbcFinishedGamesRepository(dataSource: DataSource) : FinishedGamesRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun completeFinished(now: Instant): Int =
        jdbc.sql(COMPLETE_FINISHED).param("now", Timestamp.from(now)).update()

    private companion object {
        // Mesma versão e carimbo do UPDATE_GAME: quem tinha o jogo aberto recebe conflito de
        // versão em vez de gravar por cima do encerramento.
        const val COMPLETE_FINISHED = """
            UPDATE games SET status = 'COMPLETED', version = version + 1, updated_at = now()
            WHERE status = 'PUBLISHED'
              AND starts_at + make_interval(mins => duration_minutes) <= :now
              AND EXISTS (
                  SELECT 1 FROM access_groups
                  WHERE access_groups.id = games.group_id AND access_groups.deleted_at IS NULL
              )
        """
    }
}
