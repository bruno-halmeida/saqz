package br.com.saqz.bootstrap.configuration

import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID

/** Runs in DeleteAccount's transaction, after owned groups become inaccessible. */
internal fun removeDeletedAccountPersonalData(jdbc: JdbcClient, userId: UUID) {
    val messages = "SELECT id FROM group_messages WHERE author_id = :id OR group_id IN (SELECT id FROM access_groups WHERE owner_user_id = :id)"
    val notifications = "SELECT sequence FROM group_notifications WHERE recipient_id = :id OR message_id IN ($messages)"
    fun execute(sql: String) { jdbc.sql(sql).param("id", userId).update() }

    // Delete the notification payloads/queues before their parent messages.
    listOf("notification_push_deliveries", "notification_push_queue", "notification_whatsapp_queue").forEach {
        execute("DELETE FROM $it WHERE notification_id IN ($notifications)")
    }
    execute("DELETE FROM group_notifications WHERE sequence IN ($notifications)")
    listOf("notification_attendance_links", "notification_whatsapp_group_queue").forEach {
        execute("DELETE FROM $it WHERE message_id IN ($messages)")
    }
    execute("DELETE FROM group_messages WHERE id IN ($messages)")
    listOf("notification_devices", "group_notification_preferences", "group_memberships",
        "group_membership_removals", "group_entry_requests", "subscription_checkout_login_tokens",
        "subscription_purchase_information_emails", "subscription_purchase_information_email_successes",
        "invite_redemption_limits", "attendance_link_resolution_limits").forEach {
        execute("DELETE FROM $it WHERE user_id = :id")
    }
    execute("DELETE FROM trial_coupon_selections WHERE owner_user_id = :id")
    execute("DELETE FROM group_photos WHERE updated_by = :id OR group_id IN (SELECT id FROM access_groups WHERE owner_user_id = :id)")
    execute("DELETE FROM group_invites WHERE created_by_user_id = :id OR group_id IN (SELECT id FROM access_groups WHERE owner_user_id = :id)")
    execute("UPDATE game_attendance SET member_display_name = 'Conta excluída' WHERE member_user_id = :id")
    execute("UPDATE group_charges SET member_display_name = 'Conta excluída' WHERE member_user_id = :id")
    execute("UPDATE attendance_events SET reason = NULL WHERE actor_user_id = :id OR member_user_id = :id")
    execute("UPDATE access_groups SET name = 'Grupo excluído', description = NULL, pix_key = NULL, pix_label = NULL WHERE owner_user_id = :id")
    execute("UPDATE group_venues SET name = 'Local removido', address = 'Endereço removido' WHERE group_id IN (SELECT id FROM access_groups WHERE owner_user_id = :id)")
    execute("DELETE FROM group_whatsapp_bindings WHERE created_by = :id OR group_id IN (SELECT id FROM access_groups WHERE owner_user_id = :id)")
}
