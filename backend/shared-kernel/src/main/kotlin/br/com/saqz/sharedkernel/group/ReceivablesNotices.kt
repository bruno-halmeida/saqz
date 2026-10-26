package br.com.saqz.sharedkernel.group

import java.time.LocalDate
import java.util.UUID

/**
 * WhatsApp particular junto do push (FIN-012/FIN-013): texto, botão de URL e, numa segunda mensagem, só o copia e cola.
 * Sai pela chave de WhatsApp de cobranças e para o telefone confirmado, como a cobrança.
 */
data class PrivateWhatsApp(val text: String, val buttonLabel: String? = null, val buttonUrl: String? = null,
    val followUp: String? = null)

/** Para onde o toque no push leva: o pagamento daquele jogo ou daquela mensalidade (mesmo destino do link do acerto). */
data class SettleTarget(val gameId: UUID? = null, val billingMonth: LocalDate? = null)

/**
 * Avisos do recebimento pelo app (FIN-012, FIN-013, FIN-014) pela fila dos grupos. Implementada em grupos; participa da
 * transação de quem chama, então o aviso só sai se o que o motivou for gravado. [requestId] fixo deixa repetir sem duplicar.
 */
interface ReceivablesNotices {
    fun publish(groupId: UUID, recipients: Set<UUID>, title: String, body: String, requestId: UUID,
                whatsApp: PrivateWhatsApp? = null, target: SettleTarget? = null)

    /** Mensagem no grupo do WhatsApp vinculado (hora do acerto), com imagem, texto e um botão de URL. Nunca diz quem deve. */
    fun groupWhatsApp(groupId: UUID, text: String, buttonLabel: String, buttonUrl: String, requestId: UUID) {}

    companion object {
        val NONE = object : ReceivablesNotices {
            override fun publish(groupId: UUID, recipients: Set<UUID>, title: String, body: String, requestId: UUID,
                                 whatsApp: PrivateWhatsApp?, target: SettleTarget?) = Unit
        }
    }
}
