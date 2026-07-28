package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.sharedkernel.ErrorCode
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiProblem(
    val status: Int,
    val code: ErrorCode?,
    val correlationId: String,
    val fieldErrors: Map<String, List<String>>? = null,
    val retryAfterSeconds: Int? = null,
    /** Quantas tentativas restam no código de recuperação — a tela 1k desenha o número. */
    val remainingAttempts: Int? = null,
)
