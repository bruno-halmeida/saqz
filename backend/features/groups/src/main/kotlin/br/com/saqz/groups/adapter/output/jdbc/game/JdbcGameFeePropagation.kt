package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.settings.GameFeePropagation
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

class JdbcGameFeePropagation(dataSource: DataSource) : GameFeePropagation {
    private val jdbc = JdbcClient.create(dataSource)

    /**
     * O grupo deixou de estar "não definido" (DEB-146, JOGO-008): os horários da série e os jogos que não começaram
     * e seguem sem valor e sem marca passam a ter o valor ou a marca do grupo. Nenhuma diária nasce aqui: quem já
     * confirmou vai para a revisão da diária retroativa (FIN-016).
     */
    override fun propagate(groupId: UUID) {
        jdbc.sql(
            """
            UPDATE game_series_slots s
            SET game_fee_cents = g.default_game_fee_cents, no_game_fee = g.default_no_game_fee
            FROM access_groups g
            WHERE g.id = :groupId AND s.group_id = g.id
              AND s.game_fee_cents IS NULL AND NOT s.no_game_fee
            """.trimIndent(),
        ).param("groupId", groupId).update()
        jdbc.sql(
            """
            UPDATE games x
            SET game_fee_cents = g.default_game_fee_cents, no_game_fee = g.default_no_game_fee,
                version = x.version + 1, updated_at = now()
            FROM access_groups g
            WHERE g.id = :groupId AND x.group_id = g.id
              AND x.status IN ('DRAFT', 'PUBLISHED') AND x.starts_at > now()
              AND x.game_fee_cents IS NULL AND NOT x.no_game_fee
            """.trimIndent(),
        ).param("groupId", groupId).update()
    }
}
