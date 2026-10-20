package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.becameMensalistaBody
import br.com.saqz.groups.application.communication.monthlyJoinedAsAvulsoBody
import br.com.saqz.groups.application.communication.monthlyJoinedBody
import br.com.saqz.groups.application.invite.redeem.MonthlyJoinNotice
import br.com.saqz.groups.application.invite.redeem.MonthlyJoinOutcome
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * Aviso a dono e admins (menos quem entrou) quando alguém entra ou vira mensalista pelo link de mensalistas (NOT-004):
 * canal ENTRY_REQUEST com a marca `MEMBER_JOINED_BY_LINK`, que libera o WhatsApp para o telefone cadastrado (V179).
 * Roda na transação do resgate, num SAVEPOINT: falhar aqui nunca desfaz a entrada. O `request_id` sai do grupo, da
 * pessoa, do que mudou e do início do vínculo, então repetir o resgate não duplica o aviso.
 */
class JdbcMonthlyJoinNotices(
    dataSource: DataSource,
    private val communication: JdbcGroupCommunicationRepository,
) : MonthlyJoinNotice {
    private val jdbc = JdbcClient.create(dataSource)

    override fun joined(groupId: UUID, userId: UUID, outcome: MonthlyJoinOutcome) {
        jdbc.sql("SAVEPOINT monthly_join_notice").update()
        try {
            publish(groupId, userId, outcome)
            jdbc.sql("RELEASE SAVEPOINT monthly_join_notice").update()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            jdbc.sql("ROLLBACK TO SAVEPOINT monthly_join_notice").update()
            log.warn("monthly join notice failed", failure)
        }
    }

    private fun publish(groupId: UUID, userId: UUID, outcome: MonthlyJoinOutcome) {
        val row = jdbc.sql(
            """
            SELECT g.name AS group_name, coalesce(gm.nickname, u.nickname, u.display_name) AS who, gm.created_at
            FROM access_groups g
            JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = :user
            JOIN access_users u ON u.id = :user
            WHERE g.id = :group AND g.deleted_at IS NULL
            """.trimIndent(),
        ).param("group", groupId).param("user", userId)
            .query { rs, _ -> Triple(rs.getString("who"), rs.getString("group_name"), rs.getTimestamp("created_at").toInstant()) }
            .optional().orElse(null) ?: return
        val (who, groupName, since) = row
        val body = when (outcome) {
            MonthlyJoinOutcome.JOINED_MENSALISTA -> monthlyJoinedBody(who, groupName)
            MonthlyJoinOutcome.JOINED_AVULSO_NO_DEFAULT_FEE -> monthlyJoinedAsAvulsoBody(who, groupName)
            MonthlyJoinOutcome.BECAME_MENSALISTA -> becameMensalistaBody(who, groupName)
        }
        val request = UUID.nameUUIDFromBytes("monthly-join:$groupId:$userId:$outcome:$since".toByteArray())
        communication.publishOnce(groupId, userId, MessageChannel.ENTRY_REQUEST, body, null, request, EVENT_KIND)
    }

    companion object {
        const val EVENT_KIND = "MEMBER_JOINED_BY_LINK"
        private val log = LoggerFactory.getLogger(JdbcMonthlyJoinNotices::class.java)
    }
}
