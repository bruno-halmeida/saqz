package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.access.adapter.input.http.EmailNotVerifiedException
import br.com.saqz.access.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.access.adapter.input.http.InvalidGroupRequestException
import br.com.saqz.sharedkernel.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.HttpRequestMethodNotSupportedException

@RestControllerAdvice
class SafeExceptionHandler(
    private val problemWriter: ApiProblemWriter,
) {
    @ExceptionHandler(EmailNotVerifiedException::class)
    fun emailNotVerified(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 403, ErrorCode.EMAIL_NOT_VERIFIED)
    }

    @ExceptionHandler(InvalidDisplayNameException::class)
    fun invalidDisplayName(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = mapOf("displayName" to listOf("must be between 2 and 80 characters without controls")),
        )
    }

    @ExceptionHandler(InvalidGroupRequestException::class)
    fun invalidGroup(
        failure: InvalidGroupRequestException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = failure.fieldErrors,
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun methodNotAllowed(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 405)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(request, response, 500)
    }
}
