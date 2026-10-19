package br.com.saqz.access.data.whatsapp

import br.com.saqz.access.domain.whatsapp.WhatsAppNumberCheckGateway
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

@Serializable
internal data class WhatsAppNumberCheckDto(val hasWhatsApp: Boolean? = null)

/** Qualquer falha vira `null`: a consulta é um aviso, e o cadastro segue sem ela. */
class KtorWhatsAppNumberCheckGateway(
    private val network: AuthenticatedNetworkClient,
) : WhatsAppNumberCheckGateway {
    override suspend fun hasWhatsApp(phone: String): Boolean? = when (
        val result = network.execute(
            HttpMethod.Get,
            "api/whatsapp/number-check",
            WhatsAppNumberCheckDto.serializer(),
            NetworkRequest(query = mapOf("phone" to phone)),
        )
    ) {
        is NetworkResult.Success -> result.value.hasWhatsApp
        is NetworkResult.Failure -> null
    }
}
