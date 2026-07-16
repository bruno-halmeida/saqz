package br.com.saqz.bootstrap.configuration.http

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class SafeExceptionHandler(
    private val problemWriter: ApiProblemWriter,
) {
    @ExceptionHandler(Exception::class)
    fun unexpected(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(request, response, 500)
    }
}
