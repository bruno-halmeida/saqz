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
    /** Motivo aditivo de um erro de código conhecido (link de presença: jogo começou, cancelado, despublicado). */
    val reason: String? = null,
    /** Convite de mensalistas: as pessoas recusadas do lote (`INVALID_PEOPLE`), pela posição no pedido. */
    val people: List<ApiProblemPerson>? = null,
    /** Convite de mensalistas (`PLAN_LIMIT`): limite do plano, atletas de agora, pedidos no lote e vagas livres. */
    val limit: Int? = null,
    val current: Int? = null,
    val requested: Int? = null,
    val free: Int? = null,
)

data class ApiProblemPerson(val index: Int, val reason: String)
