package br.com.saqz.groups.adapter.output.jdbc.whatsapp

import br.com.saqz.groups.application.whatsapp.GroupWhatsAppBinding
import br.com.saqz.groups.application.whatsapp.GroupWhatsAppBindingRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupWhatsAppBindingRepository(dataSource: DataSource) : GroupWhatsAppBindingRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(groupId: UUID): GroupWhatsAppBinding? = jdbc.sql(SELECT)
        .param("groupId", groupId)
        .query { result, _ ->
            GroupWhatsAppBinding(
                groupId = result.getObject("group_id", UUID::class.java),
                whatsappJid = result.getString("whatsapp_jid"),
                inviteCode = result.getString("invite_code"),
                groupName = result.getString("group_name"),
                instanceJid = result.getString("instance_jid"),
                enabled = result.getBoolean("enabled"),
                brokenAt = result.getObject("broken_at", OffsetDateTime::class.java)?.toInstant(),
                createdBy = result.getObject("created_by", UUID::class.java),
            )
        }
        .optional()
        .orElse(null)

    override fun upsert(binding: GroupWhatsAppBinding) {
        jdbc.sql(UPSERT)
            .param("groupId", binding.groupId)
            .param("whatsappJid", binding.whatsappJid)
            .param("inviteCode", binding.inviteCode)
            .param("groupName", binding.groupName)
            .param("instanceJid", binding.instanceJid)
            .param("enabled", binding.enabled)
            .param("brokenAt", binding.brokenAt?.let(Timestamp::from), Types.TIMESTAMP)
            .param("createdBy", binding.createdBy)
            .update()
    }

    override fun setEnabled(groupId: UUID, enabled: Boolean) {
        jdbc.sql(SET_ENABLED)
            .param("groupId", groupId)
            .param("enabled", enabled)
            .update()
    }

    override fun markBroken(groupId: UUID) {
        jdbc.sql(MARK_BROKEN)
            .param("groupId", groupId)
            .update()
    }

    private companion object {
        const val SELECT = """
            SELECT group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, broken_at, created_by
            FROM group_whatsapp_bindings
            WHERE group_id = :groupId
        """

        const val UPSERT = """
            INSERT INTO group_whatsapp_bindings (
                group_id, whatsapp_jid, invite_code, group_name, instance_jid, enabled, broken_at,
                created_by, created_at, updated_at
            ) VALUES (
                :groupId, :whatsappJid, :inviteCode, :groupName, :instanceJid, :enabled, :brokenAt,
                :createdBy, now(), now()
            )
            ON CONFLICT (group_id) DO UPDATE SET
                whatsapp_jid = EXCLUDED.whatsapp_jid,
                invite_code = EXCLUDED.invite_code,
                group_name = EXCLUDED.group_name,
                instance_jid = EXCLUDED.instance_jid,
                enabled = EXCLUDED.enabled,
                broken_at = EXCLUDED.broken_at,
                created_by = EXCLUDED.created_by,
                updated_at = now()
        """

        const val SET_ENABLED = """
            UPDATE group_whatsapp_bindings SET enabled = :enabled, updated_at = now()
            WHERE group_id = :groupId
        """

        const val MARK_BROKEN = """
            UPDATE group_whatsapp_bindings SET broken_at = now(), updated_at = now()
            WHERE group_id = :groupId
        """
    }
}