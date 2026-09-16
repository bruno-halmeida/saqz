package br.com.saqz.groups.application.whatsapp

/**
 * Grupo do WhatsApp como a porta o enxerga.
 *
 * [admins] são telefones **só dígitos** (o adapter normaliza o `PhoneNumber`, que chega como
 * JID completo `551153040175@s.whatsapp.net`) — é o formato comparável ao telefone de um
 * membro Saqz. A lista nunca depende de `Owner*`.
 */
data class WhatsAppGroupInfo(val jid: String, val name: String, val admins: List<String>)

/**
 * Falha da porta. `RuntimeException` para poder ser lançada como sinal de domínio; os quatro
 * casos são os mesmos do design (o campo `cause` do design virou [Unavailable.reason] porque
 * `Throwable.cause` já existe e tem outro tipo).
 */
sealed class DirectoryError(message: String) : RuntimeException(message) {
    /** Código de convite inexistente, expirado ou inutilizável. */
    data object InvalidInvite : DirectoryError("invalid invite")

    /** Grupo inexistente ou fora dele — o HTTP 500 `that group does not exist` do Uazapi. */
    data object NotInGroup : DirectoryError("that group does not exist")

    /** Instância do WhatsApp desconectada. */
    data object Disconnected : DirectoryError("instance disconnected")

    /** Falha transitória do provedor: rede, timeout ou 5xx sem semântica conhecida. */
    data class Unavailable(val reason: String) : DirectoryError(reason)
}

/**
 * Único ponto de parse do `JsonNode` do SDK Uazapi. Todo o resto do Saqz conversa com este
 * contrato tipado; nenhuma outra classe lê `Participants`, `PhoneNumber` ou `Owner*`.
 */
interface WhatsAppGroupDirectory {
    /** Telefone da instância em dígitos; lança [DirectoryError.Disconnected] se desconectada. */
    fun instanceStatus(): String

    fun inviteInfo(inviteCode: String): WhatsAppGroupInfo

    fun join(inviteCode: String)

    /** Lança [DirectoryError.NotInGroup] quando o grupo não existe ou fomos removidos. */
    fun groupInfo(jid: String): WhatsAppGroupInfo

    fun isMember(jid: String): Boolean
}
