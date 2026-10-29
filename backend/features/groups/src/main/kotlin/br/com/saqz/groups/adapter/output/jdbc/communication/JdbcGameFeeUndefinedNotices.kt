package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.GAME_FEE_UNDEFINED_BODY
import br.com.saqz.groups.application.communication.GAME_FEE_UNDEFINED_EVENT
import br.com.saqz.groups.application.communication.GameFeeUndefinedNoticeRepository
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.gameFeeUndefinedTitle
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * O aviso vai pelo FIRST_MEMBER (só papel ADMIN, e o dono tem linha ADMIN), assinado pelo dono e entregue também a ele.
 * Central e push seguem a preferência de avisos, como todo FIRST_MEMBER.
 */
class JdbcGameFeeUndefinedNotices(
    dataSource: DataSource,
    private val communication: JdbcGroupCommunicationRepository,
) : GameFeeUndefinedNoticeRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun pending(): List<UUID> = jdbc.sql(
        """
        SELECT g.id FROM access_groups g
        WHERE g.deleted_at IS NULL AND g.default_game_fee_cents IS NULL AND NOT g.default_no_game_fee
          AND NOT EXISTS (SELECT 1 FROM group_messages m WHERE m.group_id = g.id AND m.event_kind = '$GAME_FEE_UNDEFINED_EVENT')
        ORDER BY g.id
        """.trimIndent(),
    ).query(UUID::class.java).list().filterNotNull()

    override fun publish(groupId: UUID): Boolean {
        // Trava o grupo e confere de novo: quem definiu o valor entre a lista e aqui não recebe.
        val row = jdbc.sql(
            """
            SELECT owner_user_id, name FROM access_groups
            WHERE id = :group AND deleted_at IS NULL AND default_game_fee_cents IS NULL AND NOT default_no_game_fee
            FOR UPDATE
            """.trimIndent(),
        ).param("group", groupId).query { rs, _ -> rs.getObject("owner_user_id", UUID::class.java) to rs.getString("name") }
            .optional().orElse(null) ?: return false
        return communication.publishOnce(
            groupId, row.first, MessageChannel.FIRST_MEMBER, GAME_FEE_UNDEFINED_BODY, null,
            requestId = UUID.nameUUIDFromBytes("game-fee-undefined:$groupId".toByteArray()),
            eventKind = GAME_FEE_UNDEFINED_EVENT,
            pushTitle = gameFeeUndefinedTitle(row.second),
            systemAuthored = true,
        )
    }
}
