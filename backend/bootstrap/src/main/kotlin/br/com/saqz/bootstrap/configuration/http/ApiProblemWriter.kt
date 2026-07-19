package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.sharedkernel.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper

class ApiProblemWriter(
    private val objectMapper: ObjectMapper,
) {
    fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: Int,
        code: ErrorCode? = null,
        fieldErrors: Map<String, List<String>>? = null,
        retryAfterSeconds: Int? = null,
    ) {
        val correlationId = requestCorrelationId(request).value
        response.status = status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        if (retryAfterSeconds != null) response.setHeader("Retry-After", retryAfterSeconds.toString())
        objectMapper.writeValue(
            response.outputStream,
            ApiProblem(status, code, correlationId, fieldErrors, retryAfterSeconds),
        )
    }
}
