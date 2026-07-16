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
)
