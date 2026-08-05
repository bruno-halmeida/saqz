package br.com.saqz.subscriptions.application

/**
 * A Asaas, por padrão, só devolve o código genérico abaixo por segurança antifraude — não existe
 * uma lista pública de motivos de recusa (teria que habilitar "detailed errors" com o gerente de
 * conta). Cobre só o que a doc documenta hoje; amplie o `when` se a Asaas passar a devolver outros
 * códigos.
 */
enum class CardDeclineReason {
    INVALID_CARD_DATA,
    OTHER,
    ;

    companion object {
        fun fromAsaasCode(code: String): CardDeclineReason = when (code) {
            "invalid_creditCard" -> INVALID_CARD_DATA
            else -> OTHER
        }
    }
}
