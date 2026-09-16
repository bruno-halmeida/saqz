package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.whatsapp.DirectoryError
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupInfo
import com.fasterxml.jackson.databind.JsonNode
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiConnectionException
import com.uazapi.sdk.group.GroupInfoOptions

/**
 * Adapter da porta sobre o SDK Uazapi, seguindo os contratos de parser homologados no T0:
 *
 * 1. `inviteInfo`/`join` embrulham o Group em `{"group": ...}` — desembrulhar.
 * 2. Fora do grupo/inexistente = HTTP 500 `{"error":"that group does not exist"}` (não 404):
 *    vira [DirectoryError.NotInGroup] e **nunca retry**.
 * 3. `Participants[].PhoneNumber` é JID completo (`...@s.whatsapp.net`): normalizar para dígitos.
 * 4. `join` devolve Group vazio mesmo em sucesso — a entrada só é confirmada por `group/info`.
 * 5. Membership usa `group/info` (`force`), nunca `group/list` (cache defasada).
 * 6. Anti-sequestro usa `Participants[].IsAdmin` + `PhoneNumber`, nunca `Owner*`.
 */
class UazapiGroupDirectory(private val client: UazapiClient) : WhatsAppGroupDirectory {
    override fun instanceStatus(): String {
        val status = try {
            client.instance().status()?.status()
        } catch (error: UazapiApiException) {
            throw DirectoryError.Unavailable(detail(error))
        } catch (error: UazapiConnectionException) {
            throw DirectoryError.Unavailable(error.message ?: "connection")
        }
        if (status == null || !status.connected()) throw DirectoryError.Disconnected
        val jid = status.jid()?.let(::plainJid) ?: throw DirectoryError.Unavailable("instance jid unavailable")
        return digits(jid).ifBlank { throw DirectoryError.Unavailable("instance jid unavailable") }
    }

    override fun inviteInfo(inviteCode: String): WhatsAppGroupInfo = try {
        parseGroup(client.groups().inviteInfo(inviteCode))
    } catch (error: UazapiApiException) {
        throw inviteFailure(error)
    } catch (error: UazapiConnectionException) {
        throw DirectoryError.Unavailable(error.message ?: "connection")
    }

    override fun join(inviteCode: String) {
        try {
            client.groups().join(inviteCode)
        } catch (error: UazapiApiException) {
            throw inviteFailure(error)
        } catch (error: UazapiConnectionException) {
            throw DirectoryError.Unavailable(error.message ?: "connection")
        }
    }

    override fun groupInfo(jid: String): WhatsAppGroupInfo = parseGroup(loadGroup(jid))

    override fun isMember(jid: String): Boolean {
        val self = instanceStatus()
        return participants(unwrap(loadGroup(jid))).any { isSelf(it) || digits(phone(it)) == self }
    }

    private fun loadGroup(jid: String): JsonNode = try {
        client.groups().info(GroupInfoOptions.builder(jid).force(true).build())
    } catch (error: UazapiApiException) {
        throw groupFailure(error)
    } catch (error: UazapiConnectionException) {
        throw DirectoryError.Unavailable(error.message ?: "connection")
    }

    private fun parseGroup(node: JsonNode): WhatsAppGroupInfo {
        val group = unwrap(node)
        return WhatsAppGroupInfo(
            jid = text(group, "JID", "Jid", "jid", "ID", "Id", "id", "groupJid"),
            name = text(group, "Name", "name", "GroupName", "groupName"),
            admins = participants(group).filter(::isAdmin).map { digits(phone(it)) }.filter { it.isNotBlank() },
        )
    }

    private fun unwrap(node: JsonNode): JsonNode = node.get("group")?.takeIf { it.isObject } ?: node

    private fun participants(group: JsonNode): List<JsonNode> {
        val node = group.get("Participants") ?: group.get("participants") ?: return emptyList()
        return if (node.isArray) node.toList() else emptyList()
    }

    private fun plainJid(node: JsonNode): String = when {
        node.isTextual -> node.asText()
        node.get("user")?.isTextual == true -> node.get("user").asText()
        node.get("User")?.isTextual == true -> node.get("User").asText()
        else -> node.asText()
    }

    private fun text(node: JsonNode, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key -> node.get(key)?.takeUnless { it.isNull }?.asText() }?.trim().orEmpty()

    private fun flag(node: JsonNode, vararg keys: String): Boolean =
        keys.firstNotNullOfOrNull { key -> node.get(key)?.takeIf { it.isBoolean }?.asBoolean() } ?: false

    private fun isAdmin(participant: JsonNode) = flag(participant, "IsAdmin", "isAdmin", "is_admin")

    private fun isSelf(participant: JsonNode) = flag(participant, "IsMe", "isMe", "is_me")

    private fun phone(participant: JsonNode) = text(
        participant, "PhoneNumber", "phoneNumber", "phone", "Phone", "JID", "Jid", "jid", "id", "ID",
    )

    /**
     * WhatsApp multi-device JIDs look like `551153040175:2@s.whatsapp.net`. Taking every
     * digit would keep the device index (`…01752`) and miss the participant `PhoneNumber`.
     */
    private fun digits(value: String) =
        value.substringBefore("@").substringBefore(":").filter(Char::isDigit)

    private fun missingGroup(error: UazapiApiException) =
        error.statusCode() == 500 && error.responseBody()?.contains("that group does not exist") == true

    private fun inviteFailure(error: UazapiApiException): DirectoryError = when {
        missingGroup(error) -> DirectoryError.NotInGroup
        error.statusCode() in 400..499 -> DirectoryError.InvalidInvite
        else -> DirectoryError.Unavailable(detail(error))
    }

    private fun groupFailure(error: UazapiApiException): DirectoryError =
        if (missingGroup(error) || error.statusCode() in 400..499) {
            DirectoryError.NotInGroup
        } else {
            DirectoryError.Unavailable(detail(error))
        }

    private fun detail(error: UazapiApiException) =
        "HTTP ${error.statusCode()}: ${error.responseBody() ?: error.message ?: "unknown"}"
}
