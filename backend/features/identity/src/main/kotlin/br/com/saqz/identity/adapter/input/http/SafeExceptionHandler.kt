package br.com.saqz.identity.adapter.input.http

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class SafeExceptionHandler {
    @ExceptionHandler(Exception::class)
    fun unexpected(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        writeProblem(request, response, 500)
    }
}
