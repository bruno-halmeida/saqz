package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.domain.game.recurrence.ResolvedWeeklyOccurrence
import java.sql.Connection
import java.sql.Timestamp

/**
 * Jogo avulso PUBLICADO no horário da ocorrência vira o jogo da série: ganha a linhagem, a revisão, o
 * horário (slot) e o título do horário, e mais nada (id, status, vagas, quadra, valor e presenças ficam).
 * Rascunho avulso é do gestor e jogo de outra série não é nosso: ambos ficam de fora pela cláusula.
 * Precisa rodar na transação que já gravou a revisão e o slot (fk_games_series_slot).
 */
internal fun Connection.adoptStandaloneGame(occurrence: ResolvedWeeklyOccurrence): Boolean =
    prepareStatement(ADOPT_STANDALONE_GAME).use { statement ->
        statement.setObject(1, occurrence.seriesId)
        statement.setObject(2, occurrence.revisionId)
        statement.setObject(3, occurrence.slot.slotKey)
        statement.setString(4, occurrence.slot.title.trim())
        statement.setObject(5, occurrence.groupId)
        statement.setTimestamp(6, Timestamp.from(occurrence.startsAt))
        statement.executeUpdate() == 1
    }

private const val ADOPT_STANDALONE_GAME = """
    UPDATE games SET series_id = ?, series_revision_id = ?, slot_key = ?, title = ?,
                     version = version + 1, updated_at = now()
    WHERE group_id = ? AND starts_at = ? AND series_id IS NULL
      AND status = 'PUBLISHED' AND NOT detached_from_series
"""
