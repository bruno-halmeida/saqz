package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.groups.adapter.input.http.AccessForbiddenException
import br.com.saqz.groups.adapter.input.http.GroupNotFoundException
import br.com.saqz.access.adapter.input.http.InvalidDisplayNameException as AccessInvalidDisplayNameException
import br.com.saqz.access.adapter.input.http.InvalidPhoneException
import br.com.saqz.access.adapter.input.http.InvalidSessionProfileFieldException
import br.com.saqz.access.adapter.input.http.AccountNotFoundException
import br.com.saqz.access.adapter.input.http.AccountSuspendedException
import br.com.saqz.access.adapter.input.http.PasswordResetAttemptLimitException
import br.com.saqz.access.adapter.input.http.PasswordResetCodeExpiredException
import br.com.saqz.access.adapter.input.http.PasswordResetCodeInvalidException
import br.com.saqz.access.adapter.input.http.PasswordResetRateLimitException
import br.com.saqz.access.adapter.input.http.PasswordResetTokenInvalidException
import br.com.saqz.access.adapter.input.http.WeakPasswordException
import br.com.saqz.access.application.passwordreset.PasswordAccountsUnavailable
import br.com.saqz.groups.adapter.input.http.AthleteLimitExceededException
import br.com.saqz.groups.adapter.input.http.EntryRequestAthleteLimitExceededException
import br.com.saqz.groups.adapter.input.http.EntryRequestNotFoundException
import br.com.saqz.groups.adapter.input.http.GroupLimitExceededException
import br.com.saqz.groups.adapter.input.http.InvalidGroupRequestException
import br.com.saqz.groups.adapter.input.http.InviteAttemptLimitException
import br.com.saqz.groups.adapter.input.http.InviteGroupDeletedException
import br.com.saqz.groups.adapter.input.http.InviteInvalidOrExpiredException
import br.com.saqz.groups.adapter.input.http.InvitePreviewAttemptLimitException
import br.com.saqz.groups.adapter.input.http.InvitePreviewExpiredException
import br.com.saqz.groups.adapter.input.http.InvitePreviewInvalidException
import br.com.saqz.groups.adapter.input.http.AttendanceLinkAttemptLimitException
import br.com.saqz.groups.adapter.input.http.AttendanceLinkInvalidOrExpiredException
import br.com.saqz.groups.adapter.input.http.AttendanceLinkUnavailableException
import br.com.saqz.groups.adapter.input.http.VersionConflictException
import br.com.saqz.groups.adapter.input.http.PreconditionRequiredException
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.groups.adapter.input.http.InvalidGroupPhotoException
import br.com.saqz.groups.adapter.input.http.GroupPhotoTooLargeException
import br.com.saqz.access.adapter.input.http.InvalidUserPhotoException
import br.com.saqz.access.adapter.input.http.UserPhotoNotFoundException
import br.com.saqz.access.adapter.input.http.UserPhotoTooLargeException
import br.com.saqz.groups.adapter.input.http.GameNotFoundException
import br.com.saqz.groups.adapter.input.http.GameScheduleConflictException
import br.com.saqz.groups.adapter.input.http.InvalidGameTransitionException
import br.com.saqz.groups.adapter.input.http.AttendanceDeadlinePassedException
import br.com.saqz.groups.adapter.input.http.AttendanceFrozenException
import br.com.saqz.groups.application.game.GameScheduleConflictWriteException
import br.com.saqz.subscriptions.adapter.input.http.AsaasWebhookSubscriptionNotReadyException
import br.com.saqz.subscriptions.adapter.input.http.AsaasWebhookUnauthorizedException
import br.com.saqz.subscriptions.adapter.input.http.CouponAlreadyRedeemedException
import br.com.saqz.subscriptions.adapter.input.http.CouponExpiredException
import br.com.saqz.subscriptions.adapter.input.http.CouponNotFoundException
import br.com.saqz.subscriptions.adapter.input.http.CreditCardDeclinedException
import br.com.saqz.subscriptions.adapter.input.http.DowngradeBlockedException
import br.com.saqz.subscriptions.adapter.input.http.InvalidCouponRequestException
import br.com.saqz.subscriptions.adapter.input.http.InvalidSubscriptionRequestException
import br.com.saqz.subscriptions.adapter.input.http.PendingCheckoutMismatchException
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionConflictException
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionNotFoundException
import br.com.saqz.subscriptions.application.InvalidReceiptPaginationException
import br.com.saqz.sharedkernel.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class SafeExceptionHandler(
    private val problemWriter: ApiProblemWriter,
) {
    @ExceptionHandler(InvalidDisplayNameException::class, AccessInvalidDisplayNameException::class)
    fun invalidDisplayName(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = mapOf("displayName" to listOf("must be between 2 and 80 characters without controls")),
        )
    }

    @ExceptionHandler(InvalidPhoneException::class)
    fun invalidPhone(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = mapOf("phone" to listOf("must be a valid Brazilian mobile number (+55DDD9XXXXXXXX)")),
        )
    }

    @ExceptionHandler(InvalidSessionProfileFieldException::class)
    fun invalidSessionProfileField(
        failure: InvalidSessionProfileFieldException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = mapOf(failure.field to listOf("valor inválido")),
        )
    }

    @ExceptionHandler(AccountNotFoundException::class)
    fun accountNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.ACCOUNT_NOT_FOUND)
    }

    @ExceptionHandler(PasswordResetRateLimitException::class)
    fun passwordResetRateLimit(
        failure: PasswordResetRateLimitException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            429,
            ErrorCode.PASSWORD_RESET_RATE_LIMIT,
            retryAfterSeconds = failure.retryAfterSeconds,
        )
    }

    /**
     * "Código incorreto. Restam 2 tentativas." e "Esse código expirou." são linhas
     * distintas na tela 1k, então são respostas distintas aqui.
     */
    @ExceptionHandler(PasswordResetCodeInvalidException::class)
    fun passwordResetCodeInvalid(
        failure: PasswordResetCodeInvalidException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.PASSWORD_RESET_CODE_INVALID,
            remainingAttempts = failure.remainingAttempts,
        )
    }

    @ExceptionHandler(PasswordResetCodeExpiredException::class)
    fun passwordResetCodeExpired(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 410, ErrorCode.PASSWORD_RESET_CODE_EXPIRED)
    }

    @ExceptionHandler(PasswordResetAttemptLimitException::class)
    fun passwordResetAttemptLimit(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 429, ErrorCode.PASSWORD_RESET_ATTEMPT_LIMIT)
    }

    @ExceptionHandler(PasswordResetTokenInvalidException::class)
    fun passwordResetTokenInvalid(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 410, ErrorCode.PASSWORD_RESET_TOKEN_INVALID)
    }

    /** Provedor de identidade fora do ar não é conta inexistente nem token inválido. */
    @ExceptionHandler(PasswordAccountsUnavailable::class)
    fun passwordAccountsUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 503, ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
    }

    @ExceptionHandler(WeakPasswordException::class)
    fun weakPassword(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = mapOf("novaSenha" to listOf("deve ter entre 8 e 128 caracteres")),
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
            failure.status,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = failure.fieldErrors,
        )
    }

    @ExceptionHandler(GroupNotFoundException::class)
    fun groupNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.GROUP_NOT_FOUND)
    }

    @ExceptionHandler(AccessForbiddenException::class)
    fun accessForbidden(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 403, ErrorCode.ACCESS_FORBIDDEN)
    }

    @ExceptionHandler(AccountSuspendedException::class)
    fun accountSuspended(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 403, ErrorCode.ACCOUNT_SUSPENDED)
    }

    @ExceptionHandler(EntryRequestNotFoundException::class)
    fun entryRequestNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.ENTRY_REQUEST_NOT_FOUND)
    }

    @ExceptionHandler(PreconditionRequiredException::class)
    fun preconditionRequired(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 428, ErrorCode.PRECONDITION_REQUIRED)
    }

    @ExceptionHandler(VersionConflictException::class)
    fun versionConflict(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.VERSION_CONFLICT)
    }

    @ExceptionHandler(GameScheduleConflictException::class)
    fun gameScheduleConflict(
        failure: GameScheduleConflictException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            409,
            ErrorCode.GAME_SCHEDULE_CONFLICT,
            conflictGameId = failure.gameId.toString(),
        )
    }

    @ExceptionHandler(GameScheduleConflictWriteException::class)
    fun gameScheduleConflictWrite(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.GAME_SCHEDULE_CONFLICT)
    }

    @ExceptionHandler(GameNotFoundException::class)
    fun gameNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.GAME_NOT_FOUND)
    }

    @ExceptionHandler(InvalidGameTransitionException::class)
    fun invalidGameTransition(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.INVALID_GAME_TRANSITION)
    }

    @ExceptionHandler(AttendanceDeadlinePassedException::class)
    fun attendanceDeadlinePassed(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.ATTENDANCE_DEADLINE_PASSED)
    }

    @ExceptionHandler(AttendanceFrozenException::class)
    fun attendanceFrozen(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.ATTENDANCE_FROZEN)
    }

    @ExceptionHandler(InviteInvalidOrExpiredException::class)
    fun inviteInvalidOrExpired(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.INVITE_INVALID_OR_EXPIRED)
    }

    @ExceptionHandler(InvitePreviewInvalidException::class)
    fun invitePreviewInvalid(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.INVITE_INVALID)
    }

    @ExceptionHandler(InvitePreviewExpiredException::class)
    fun invitePreviewExpired(
        failure: InvitePreviewExpiredException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(request, response, 410, ErrorCode.INVITE_EXPIRED, expiredAt = failure.expiredAt)
    }

    @ExceptionHandler(InvitePreviewAttemptLimitException::class)
    fun invitePreviewAttemptLimit(
        failure: InvitePreviewAttemptLimitException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            429,
            ErrorCode.INVITE_ATTEMPT_LIMIT,
            retryAfterSeconds = failure.retryAfterSeconds,
        )
    }

    @ExceptionHandler(InviteGroupDeletedException::class)
    fun inviteGroupDeleted(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 410, ErrorCode.INVITE_GROUP_DELETED)
    }

    @ExceptionHandler(InviteAttemptLimitException::class)
    fun inviteAttemptLimit(
        failure: InviteAttemptLimitException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            429,
            ErrorCode.INVITE_ATTEMPT_LIMIT,
            retryAfterSeconds = failure.retryAfterSeconds,
        )
    }

    @ExceptionHandler(GroupLimitExceededException::class)
    fun groupLimitExceeded(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 403, ErrorCode.GROUP_LIMIT_EXCEEDED)
    }

    @ExceptionHandler(AthleteLimitExceededException::class)
    fun athleteLimitExceeded(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 403, ErrorCode.ATHLETE_LIMIT_EXCEEDED)
    }

    @ExceptionHandler(EntryRequestAthleteLimitExceededException::class)
    fun entryRequestAthleteLimitExceeded(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 422, ErrorCode.ATHLETE_LIMIT_EXCEEDED)
    }

    @ExceptionHandler(AttendanceLinkInvalidOrExpiredException::class)
    fun attendanceLinkInvalidOrExpired(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.ATTENDANCE_LINK_INVALID_OR_EXPIRED)
    }

    @ExceptionHandler(AttendanceLinkAttemptLimitException::class)
    fun attendanceLinkAttemptLimit(
        failure: AttendanceLinkAttemptLimitException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            429,
            ErrorCode.ATTENDANCE_LINK_ATTEMPT_LIMIT,
            retryAfterSeconds = failure.retryAfterSeconds,
        )
    }

    @ExceptionHandler(AttendanceLinkUnavailableException::class)
    fun attendanceLinkUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 503, ErrorCode.ATTENDANCE_LINK_UNAVAILABLE)
    }

    @ExceptionHandler(
        GroupPhotoTooLargeException::class,
        UserPhotoTooLargeException::class,
        MaxUploadSizeExceededException::class,
    )
    fun photoTooLarge(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 413, ErrorCode.PHOTO_TOO_LARGE)
    }

    @ExceptionHandler(
        InvalidGroupPhotoException::class,
        InvalidUserPhotoException::class,
        MissingServletRequestPartException::class,
        MultipartException::class,
    )
    fun invalidPhoto(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 400, ErrorCode.PHOTO_INVALID)
    }

    @ExceptionHandler(UserPhotoNotFoundException::class)
    fun photoNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.PHOTO_NOT_FOUND)
    }

    @ExceptionHandler(InvalidCouponRequestException::class)
    fun invalidCouponRequest(
        failure: InvalidCouponRequestException,
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

    @ExceptionHandler(AsaasWebhookUnauthorizedException::class)
    fun asaasWebhookUnauthorized(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
    }

    /** 503 so Asaas retries until the local subscription row is committed. */
    @ExceptionHandler(AsaasWebhookSubscriptionNotReadyException::class)
    fun asaasWebhookSubscriptionNotReady(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 503)
    }

    @ExceptionHandler(InvalidSubscriptionRequestException::class)
    fun invalidSubscriptionRequest(
        failure: InvalidSubscriptionRequestException,
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

    @ExceptionHandler(InvalidReceiptPaginationException::class)
    fun invalidReceiptPagination(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 400, ErrorCode.VALIDATION_FAILED)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun methodArgumentTypeMismatch(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 400, ErrorCode.VALIDATION_FAILED)
    }

    @ExceptionHandler(SubscriptionNotFoundException::class)
    fun subscriptionNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.SUBSCRIPTION_NOT_FOUND)
    }

    @ExceptionHandler(SubscriptionConflictException::class)
    fun subscriptionConflict(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.SUBSCRIPTION_CONFLICT)
    }

    @ExceptionHandler(PendingCheckoutMismatchException::class)
    fun subscriptionPendingCheckoutMismatch(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.SUBSCRIPTION_PENDING_CHECKOUT_MISMATCH)
    }

    @ExceptionHandler(CouponNotFoundException::class)
    fun couponNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.COUPON_NOT_FOUND)
    }

    @ExceptionHandler(CouponExpiredException::class)
    fun couponExpired(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 410, ErrorCode.COUPON_EXPIRED)
    }

    @ExceptionHandler(CouponAlreadyRedeemedException::class)
    fun couponAlreadyRedeemed(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.COUPON_ALREADY_REDEEMED)
    }

    @ExceptionHandler(DowngradeBlockedException::class)
    fun downgradeBlocked(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.DOWNGRADE_BLOCKED)
    }

    /** Shape pinado com o mobile (VUL-196) — não o `ApiProblem` genérico. Ver [ApiProblemWriter.writeCardDeclined]. */
    @ExceptionHandler(CreditCardDeclinedException::class)
    fun creditCardDeclined(
        failure: CreditCardDeclinedException,
        response: HttpServletResponse,
    ) {
        problemWriter.writeCardDeclined(response, failure.reason, failure.description)
    }

    /**
     * Chunked / understated Content-Length bodies trip the stream cap inside MVC
     * `@RequestBody` resolution — that path never returns to the filter catch.
     */
    @ExceptionHandler(BodyTooLargeException::class)
    fun bodyTooLarge(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 413)
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
