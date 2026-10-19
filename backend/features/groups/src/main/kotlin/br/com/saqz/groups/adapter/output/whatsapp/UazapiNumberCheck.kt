package br.com.saqz.groups.adapter.output.whatsapp

import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import com.fasterxml.jackson.databind.JsonNode
import com.uazapi.sdk.UazapiClient
import com.uazapi.sdk.exception.UazapiApiException
import com.uazapi.sdk.exception.UazapiConnectionException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Consulta `chat/check` da Uazapi (DEB-095). É só um aviso no cadastro, então qualquer coisa fora do "sim" ou "não"
 * claro (falha, demora, resposta que não entendemos) vira `null`: a espera tem teto próprio, bem abaixo dos 30 s de
 * leitura do cliente, para não segurar a tela.
 */
class UazapiNumberCheck(
    private val client: UazapiClient,
    private val timeoutMillis: Long = 4_000,
) : WhatsAppNumberCheck {
    override fun hasWhatsApp(phone: String): Boolean? {
        val digits = phone.removePrefix("+")
        if (!Regex("[1-9][0-9]{7,14}").matches(digits)) return null
        return try {
            CompletableFuture.supplyAsync { client.chats().checkNumbers(listOf(digits)) }
                .get(timeoutMillis, TimeUnit.MILLISECONDS)
                .let(::answer)
        } catch (_: Exception) {
            null
        }
    }

    internal companion object {
        /** `[{"query":"5511…","isInWhatsapp":true,…}]`; só um booleano de verdade conta. */
        fun answer(response: JsonNode?): Boolean? {
            val first = response?.let { if (it.isArray) it.firstOrNull() else it } ?: return null
            val flag = first.get("isInWhatsapp") ?: first.get("IsInWhatsapp") ?: return null
            return if (flag.isBoolean) flag.booleanValue() else null
        }
    }
}
