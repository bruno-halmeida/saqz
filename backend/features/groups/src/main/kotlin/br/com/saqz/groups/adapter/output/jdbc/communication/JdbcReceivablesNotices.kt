package br.com.saqz.groups.adapter.output.jdbc.communication

import br.com.saqz.sharedkernel.group.PrivateWhatsApp
import br.com.saqz.sharedkernel.group.ReceivablesNotices
import br.com.saqz.sharedkernel.group.SettleTarget
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * Push do recebimento pelo app (FIN-013/FIN-014) no canal PAYMENT, só para [recipients] que ainda são membros ativos.
 * O título vai em `push_title` (V195); o autor é o dono, como nos avisos de sistema. O `request_id` fixo faz o UNIQUE de
 * `group_messages` derrubar a repetição do mesmo pagamento. Os apps das lojas descartam o canal na central.
 */
class JdbcReceivablesNotices(dataSource: DataSource) : ReceivablesNotices {
    private val jdbc = JdbcClient.create(dataSource)

    override fun publish(groupId: UUID, recipients: Set<UUID>, title: String, body: String, requestId: UUID,
                         whatsApp: PrivateWhatsApp?, target: SettleTarget?) {
        if (recipients.isEmpty()) return
        val id = UUID.randomUUID()
        val inserted = jdbc.sql("""
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, push_body, push_title,
                whatsapp_body, whatsapp_button_label, whatsapp_button_url, whatsapp_follow_up, settle_game_id, settle_month)
            SELECT :id, g.id, g.owner_user_id, u.display_name, CAST('PAYMENT' AS group_message_channel), :request, :body, :body, :title,
                :waBody, :waLabel, :waUrl, :waFollow, :settleGame, :settleMonth
            FROM access_groups g JOIN access_users u ON u.id = g.owner_user_id WHERE g.id = :group AND g.deleted_at IS NULL
            ON CONFLICT DO NOTHING
        """.trimIndent()).param("id", id).param("group", groupId).param("request", requestId)
            .param("body", body.take(2000)).param("title", title.take(120))
            .param("waBody", whatsApp?.text?.take(2000), java.sql.Types.VARCHAR).param("waLabel", whatsApp?.buttonLabel?.take(40), java.sql.Types.VARCHAR)
            .param("waUrl", whatsApp?.buttonUrl, java.sql.Types.VARCHAR).param("waFollow", whatsApp?.followUp, java.sql.Types.VARCHAR)
            .param("settleGame", target?.gameId, java.sql.Types.OTHER).param("settleMonth", target?.billingMonth, java.sql.Types.DATE).update()
        if (inserted == 0) return
        jdbc.sql("""
            INSERT INTO group_notifications (recipient_id, message_id)
            SELECT m.user_id, :id FROM group_memberships m WHERE m.group_id = :group AND m.active AND m.user_id IN (:recipients)
        """.trimIndent()).param("id", id).param("group", groupId).param("recipients", recipients.toList()).update()
        jdbc.sql("UPDATE group_messages SET recipient_count = (SELECT count(*) FROM group_notifications WHERE message_id = :id AND in_app) WHERE id = :id")
            .param("id", id).update()
    }

    /** Hora do acerto no grupo do WhatsApp (FIN-013): sem destinatários na central; o gatilho da V197 põe na fila do grupo. */
    override fun groupWhatsApp(groupId: UUID, text: String, buttonLabel: String, buttonUrl: String, requestId: UUID) {
        jdbc.sql("""
            INSERT INTO group_messages (id, group_id, author_id, author_name, channel, request_id, body, event_kind,
                whatsapp_button_label, whatsapp_button_url)
            SELECT :id, g.id, g.owner_user_id, u.display_name, CAST('PAYMENT' AS group_message_channel), :request, :body, 'SETTLE_TIME',
                :label, :url
            FROM access_groups g JOIN access_users u ON u.id = g.owner_user_id WHERE g.id = :group AND g.deleted_at IS NULL
            ON CONFLICT DO NOTHING
        """.trimIndent()).param("id", UUID.randomUUID()).param("group", groupId).param("request", requestId)
            .param("body", text.take(2000)).param("label", buttonLabel.take(40)).param("url", buttonUrl).update()
    }
}
