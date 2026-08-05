package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.sharedkernel.ErrorCode
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiProblem(
    val status: Int,
    val code: ErrorCode?,
    val correlationId: String,
    val fieldErrors: Map<String, List<String>>? = null,
    val retryAfterSeconds: Int? = null,
    /** Quantas tentativas restam no código de recuperação — a tela 1k desenha o número. */
    val remainingAttempts: Int? = null,
    val expiredAt: Instant? = null,
    val conflictGameId: String? = null,
    /** Motivo mapeado do código de recusa da Asaas — nunca dado de cartão. */
    val cardDeclineReason: String? = null,
)
