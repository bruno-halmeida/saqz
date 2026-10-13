package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.groups.application.communication.FirstConfirmationNotice
import br.com.saqz.groups.application.communication.FirstMemberNotice
import br.com.saqz.groups.application.communication.MessageChannel
import br.com.saqz.groups.application.communication.firstConfirmationBody
import br.com.saqz.groups.application.communication.firstMemberBody
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.sql.DataSource

/**
 * Avisos de "pico" do onboarding. Rodam dentro da transação do evento (entrada / resposta), que já trava a
 * linha do grupo, então o teste de "primeiro" e a gravação não correm. O índice único é a segunda trava:
 * `publishOnce` usa ON CONFLICT DO NOTHING e nunca falha por causa dele.
 */
class JdbcOnboardingNotices(
    dataSource: DataSource,
    private val communication: JdbcGroupCommunicationRepository,
) : FirstMemberNotice, FirstConfirmationNotice {
    private val jdbc = JdbcClient.create(dataSource)

    /**
     * Primeiro atleta: o grupo nunca teve outra pessoa além do dono, nem ativa nem removida
     * (group_memberships e group_membership_removals). Quem volta depois de sair não é "primeiro".
     */
    override fun joined(groupId: UUID, userId: UUID) = guarded {
        val row = jdbc.sql(
            """
            SELECT g.name AS group_name, coalesce(gm.nickname, u.nickname, u.display_name) AS who
            FROM access_groups g
            JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = :user
            JOIN access_users u ON u.id = :user
            WHERE g.id = :group AND g.deleted_at IS NULL AND g.owner_user_id <> :user
              AND NOT EXISTS (SELECT 1 FROM group_memberships o
                              WHERE o.group_id = g.id AND o.user_id <> g.owner_user_id AND o.user_id <> :user)
              AND NOT EXISTS (SELECT 1 FROM group_membership_removals r
                              WHERE r.group_id = g.id AND r.user_id <> g.owner_user_id)
            """.trimIndent(),
        ).param("group", groupId).param("user", userId)
            .query { rs, _ -> rs.getString("who") to rs.getString("group_name") }.optional().orElse(null) ?: return@guarded
        communication.publishOnce(groupId, userId, MessageChannel.FIRST_MEMBER, firstMemberBody(row.first, row.second), null)
        Unit
    }

    /**
     * Primeira confirmação: nenhum outro evento SELF CONFIRMED (sem convidado) de não-dono no grupo.
     * SYSTEM (mensalista, promoção) e ORGANIZER não têm source SELF e ficam de fora.
     */
    override fun confirmed(groupId: UUID, gameId: UUID, memberId: UUID, eventId: UUID) = guarded {
        val row = jdbc.sql(
            """
            SELECT coalesce(gm.nickname, u.nickname, u.display_name) AS who, game.local_date, game.local_time
            FROM games game
            JOIN access_groups g ON g.id = game.group_id AND g.deleted_at IS NULL
            JOIN group_memberships gm ON gm.group_id = g.id AND gm.user_id = :member
            JOIN access_users u ON u.id = :member
            WHERE game.group_id = :group AND game.id = :game AND g.owner_user_id <> :member
              AND NOT EXISTS (SELECT 1 FROM attendance_events e
                              WHERE e.group_id = g.id AND e.source = 'SELF' AND e.new_status = 'CONFIRMED'
                                AND e.guest_seq = 0 AND e.member_user_id <> g.owner_user_id AND e.id <> :event)
            """.trimIndent(),
        ).param("group", groupId).param("game", gameId).param("member", memberId).param("event", eventId)
            .query { rs, _ ->
                Triple(rs.getString("who"), rs.getObject("local_date", LocalDate::class.java), rs.getObject("local_time", LocalTime::class.java))
            }.optional().orElse(null) ?: return@guarded
        communication.publishOnce(
            groupId, memberId, MessageChannel.FIRST_CONFIRMATION, firstConfirmationBody(row.first, row.second, row.third), gameId,
        )
        Unit
    }

    /**
     * O aviso nunca desfaz a entrada nem a confirmação: um erro de SQL aqui abortaria a transação inteira do
     * Postgres, então roda num SAVEPOINT e, se falhar, volta só até ele e segue.
     */
    private fun guarded(block: () -> Unit) {
        jdbc.sql("SAVEPOINT onboarding_notice").update()
        try {
            block()
            jdbc.sql("RELEASE SAVEPOINT onboarding_notice").update()
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            jdbc.sql("ROLLBACK TO SAVEPOINT onboarding_notice").update()
            log.warn("onboarding notice failed", failure)
        }
    }

    private companion object {
        val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(JdbcOnboardingNotices::class.java)
    }
}
