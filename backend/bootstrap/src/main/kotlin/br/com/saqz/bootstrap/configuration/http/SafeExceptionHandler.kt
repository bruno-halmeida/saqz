package br.com.saqz.bootstrap.configuration.http

import br.com.saqz.groups.adapter.input.http.AccessForbiddenException
import br.com.saqz.groups.adapter.input.http.GroupNotFoundException
import br.com.saqz.groups.adapter.input.http.MemberPhotoNotFoundException
import br.com.saqz.access.adapter.input.http.InvalidDisplayNameException as AccessInvalidDisplayNameException
import br.com.saqz.access.adapter.input.http.InvalidPhoneException
import br.com.saqz.access.adapter.input.http.InvalidSessionProfileFieldException
import br.com.saqz.access.adapter.input.http.AccountNotFoundException
import br.com.saqz.access.adapter.input.http.AccountSuspendedException
import br.com.saqz.access.adapter.input.http.AppOnboardingCodeInvalidException
import br.com.saqz.access.adapter.input.http.AppOnboardingIdentityUnavailableException
import br.com.saqz.access.adapter.input.http.EmailVerificationRateLimitException
import br.com.saqz.access.adapter.input.http.PasswordResetAttemptLimitException
import br.com.saqz.access.adapter.input.http.PasswordResetCodeExpiredException
import br.com.saqz.access.adapter.input.http.PasswordResetCodeInvalidException
import br.com.saqz.access.adapter.input.http.PasswordResetRateLimitException
import br.com.saqz.access.adapter.input.http.PasswordResetTokenInvalidException
import br.com.saqz.access.adapter.input.http.WeakPasswordException
import br.com.saqz.access.application.emailverification.VerificationLinksUnavailable
import br.com.saqz.access.application.passwordreset.PasswordAccountsUnavailable
import br.com.saqz.groups.adapter.input.http.AthleteLimitExceededException
import br.com.saqz.groups.application.places.PlacesRateLimitedException
import br.com.saqz.groups.application.places.PlacesUnavailableException
import br.com.saqz.groups.application.places.PlacesUpstreamException
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
import br.com.saqz.groups.adapter.input.http.WhatsAppGroupBindingConflictException
import br.com.saqz.groups.adapter.input.http.WhatsAppGroupBindingUnavailableException
import br.com.saqz.groups.adapter.input.http.PreconditionRequiredException
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.groups.adapter.input.http.InvalidGroupPhotoException
import br.com.saqz.groups.adapter.input.http.GroupPhotoTooLargeException
import br.com.saqz.access.adapter.input.http.InvalidUserPhotoException
import br.com.saqz.access.adapter.input.http.UserPhotoNotFoundException
import br.com.saqz.access.adapter.input.http.UserPhotoTooLargeException
import br.com.saqz.groups.adapter.input.http.GameNotFoundException
import br.com.saqz.groups.adapter.input.http.GameAfterPlanEndException
import br.com.saqz.groups.adapter.input.http.GameScheduleConflictException
import br.com.saqz.groups.adapter.input.http.InvalidGameTransitionException
import br.com.saqz.groups.adapter.input.http.AttendanceDeadlinePassedException
import br.com.saqz.groups.adapter.input.http.AttendanceFrozenException
import br.com.saqz.groups.adapter.input.http.AttendanceHostNotGoingException
import br.com.saqz.groups.application.game.GameScheduleConflictWriteException
import br.com.saqz.subscriptions.adapter.input.http.AppStoreNotificationInvalidException
import br.com.saqz.subscriptions.adapter.input.http.AppStoreTransactionInvalidException
import br.com.saqz.subscriptions.adapter.input.http.AppStoreTransactionOwnedByAnotherAccountException
import br.com.saqz.subscriptions.adapter.input.http.AsaasWebhookSubscriptionNotReadyException
import br.com.saqz.subscriptions.adapter.input.http.GooglePlayNotificationUnauthorizedException
import br.com.saqz.subscriptions.adapter.input.http.GooglePlayPurchaseInvalidException
import br.com.saqz.subscriptions.adapter.input.http.GooglePlayPurchaseOwnedByAnotherAccountException
import br.com.saqz.subscriptions.adapter.input.http.AsaasWebhookUnauthorizedException
import br.com.saqz.subscriptions.adapter.input.http.CheckoutLoginTokenInvalidException
import br.com.saqz.subscriptions.adapter.input.http.CouponAlreadyRedeemedException
import br.com.saqz.subscriptions.adapter.input.http.CouponExhaustedException
import br.com.saqz.subscriptions.adapter.input.http.CouponExpiredException
import br.com.saqz.subscriptions.adapter.input.http.CouponNotEligibleException
import br.com.saqz.subscriptions.adapter.input.http.CouponNotValidForPlanException
import br.com.saqz.subscriptions.adapter.input.http.CouponNotFoundException
import br.com.saqz.subscriptions.adapter.input.http.CreditCardDeclinedException
import br.com.saqz.subscriptions.adapter.input.http.PaymentMethodUnavailableHttpException
import br.com.saqz.subscriptions.adapter.input.http.DowngradeBlockedException
import br.com.saqz.subscriptions.adapter.input.http.InvalidCouponRequestException
import br.com.saqz.subscriptions.adapter.input.http.InvalidSubscriptionRequestException
import br.com.saqz.subscriptions.adapter.input.http.PendingCheckoutMismatchException
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionConflictException
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionNotFoundException
import br.com.saqz.subscriptions.application.AppStoreVerificationUnavailableException
import br.com.saqz.subscriptions.application.CheckoutIdentityUnavailable
import br.com.saqz.subscriptions.application.GooglePlayUnavailableException
import br.com.saqz.subscriptions.application.InvalidReceiptPaginationException
import br.com.saqz.sharedkernel.ErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.sql.SQLException
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.HttpRequestMethodNotSupportedException
import tools.jackson.core.JacksonException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

private const val MAX_CAUSE_DEPTH = 8

@RestControllerAdvice
class SafeExceptionHandler(
    private val problemWriter: ApiProblemWriter,
) {
    private val logger = LoggerFactory.getLogger(SafeExceptionHandler::class.java)

    @ExceptionHandler(br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException::class)
    fun subscriptionRequired(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 403, ErrorCode.SUBSCRIPTION_REQUIRED)
    }

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

    @ExceptionHandler(EmailVerificationRateLimitException::class)
    fun emailVerificationRateLimit(
        failure: EmailVerificationRateLimitException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            429,
            ErrorCode.EMAIL_VERIFICATION_RATE_LIMIT,
            retryAfterSeconds = failure.retryAfterSeconds,
        )
    }

    /** Provedor de identidade fora do ar não é conta inexistente nem token inválido. */
    @ExceptionHandler(
        PasswordAccountsUnavailable::class,
        VerificationLinksUnavailable::class,
        CheckoutIdentityUnavailable::class,
    )
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

    @ExceptionHandler(br.com.saqz.groups.adapter.input.http.MonthlyFeeRequiredException::class)
    fun monthlyFeeRequired(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 422, ErrorCode.MONTHLY_FEE_REQUIRED)
    }

    @ExceptionHandler(br.com.saqz.groups.adapter.input.http.WhatsAppUnavailableException::class)
    fun whatsAppUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.WHATSAPP_UNAVAILABLE)
    }

    @ExceptionHandler(br.com.saqz.groups.adapter.input.http.MonthlyInviteeNotFoundException::class)
    fun monthlyInviteeNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.MONTHLY_INVITEE_NOT_FOUND)
    }

    @ExceptionHandler(br.com.saqz.groups.adapter.input.http.InvalidMonthlyInvitePeopleException::class)
    fun invalidMonthlyInvitePeople(
        failure: br.com.saqz.groups.adapter.input.http.InvalidMonthlyInvitePeopleException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(
            request,
            response,
            422,
            ErrorCode.INVALID_PEOPLE,
            people = failure.errors.map { ApiProblemPerson(it.index, it.reason.name) },
        )
    }

    @ExceptionHandler(br.com.saqz.groups.adapter.input.http.MonthlyInvitePlanLimitException::class)
    fun monthlyInvitePlanLimit(
        failure: br.com.saqz.groups.adapter.input.http.MonthlyInvitePlanLimitException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val limit = failure.limit
        problemWriter.write(
            request,
            response,
            409,
            ErrorCode.PLAN_LIMIT,
            planLimit = listOf(limit.limit, limit.current, limit.requested, limit.free),
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

    @ExceptionHandler(br.com.saqz.access.adapter.input.http.RecentAuthenticationRequiredException::class)
    fun deletionNeedsRecentAuthentication(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 403, ErrorCode.RECENT_AUTHENTICATION_REQUIRED)
    }

    @ExceptionHandler(
        br.com.saqz.access.application.session.AccountDeleted::class,
        br.com.saqz.subscriptions.application.SubscriptionOwnerUnavailable::class,
    )
    fun deletedAccount(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
    }

    @ExceptionHandler(br.com.saqz.access.application.session.AccountDeletionIdentityMismatch::class)
    fun deletionIdentityMismatch(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.VERSION_CONFLICT)
    }

    @ExceptionHandler(AppOnboardingCodeInvalidException::class)
    fun appOnboardingCodeInvalid(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 400, ErrorCode.APP_ONBOARDING_CODE_INVALID)
    }

    @ExceptionHandler(AppOnboardingIdentityUnavailableException::class)
    fun appOnboardingIdentityUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 503, ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
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

    @ExceptionHandler(WhatsAppGroupBindingConflictException::class)
    fun whatsAppGroupBindingConflict(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409)
    }

    @ExceptionHandler(PlacesUnavailableException::class)
    fun placesUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 503)
    }

    /** Decodificação de imagem no limite de memória do processo: o app tenta de novo em instantes. */
    @ExceptionHandler(br.com.saqz.sharedkernel.media.ImageDecodeBusyException::class)
    fun imageDecodeBusy(
        failure: br.com.saqz.sharedkernel.media.ImageDecodeBusyException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(request, response, 503, retryAfterSeconds = failure.retryAfterSeconds)
    }

    @ExceptionHandler(PlacesUpstreamException::class)
    fun placesUpstream(failure: PlacesUpstreamException, request: HttpServletRequest, response: HttpServletResponse) {
        // Só a mensagem (sem URL nem chave) vai ao log.
        logger.warn("Google Places falhou: {}", failure.message)
        problemWriter.write(request, response, 502)
    }

    @ExceptionHandler(PlacesRateLimitedException::class)
    fun placesRateLimited(
        failure: PlacesRateLimitedException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(request, response, 429, retryAfterSeconds = failure.retryAfterSeconds)
    }

    @ExceptionHandler(WhatsAppGroupBindingUnavailableException::class)
    fun whatsAppGroupBindingUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 502)
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

    @ExceptionHandler(GameAfterPlanEndException::class)
    fun gameAfterPlanEnd(
        failure: GameAfterPlanEndException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        // `expiredAt` carrega o primeiro instante que já não vale; o app tira dele o último dia possível.
        problemWriter.write(
            request,
            response,
            422,
            ErrorCode.GAME_AFTER_PLAN_END,
            fieldErrors = mapOf("startsAt" to listOf("must be before the plan end")),
            expiredAt = failure.startsBefore,
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

    @ExceptionHandler(AttendanceHostNotGoingException::class)
    fun attendanceHostNotGoing(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.ATTENDANCE_HOST_NOT_GOING)
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
    fun attendanceLinkInvalidOrExpired(
        failure: AttendanceLinkInvalidOrExpiredException,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        problemWriter.write(request, response, 404, ErrorCode.ATTENDANCE_LINK_INVALID_OR_EXPIRED, reason = failure.reason)
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

    @ExceptionHandler(UserPhotoNotFoundException::class, MemberPhotoNotFoundException::class)
    fun photoNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404, ErrorCode.PHOTO_NOT_FOUND)
    }

    @ExceptionHandler(CheckoutLoginTokenInvalidException::class)
    fun checkoutLoginTokenInvalid(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 410, ErrorCode.CHECKOUT_LOGIN_TOKEN_INVALID)
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

    @ExceptionHandler(AppStoreNotificationInvalidException::class)
    fun appStoreNotificationInvalid(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
    }

    /** 503: o app não finaliza a transação e a Apple reenvia a notificação. */
    @ExceptionHandler(AppStoreVerificationUnavailableException::class)
    fun appStoreVerificationUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 503)
    }

    @ExceptionHandler(GooglePlayNotificationUnauthorizedException::class)
    fun googlePlayNotificationUnauthorized(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 401, ErrorCode.AUTHENTICATION_REQUIRED)
    }

    /** 503: o app reenvia a compra na próxima abertura e o Pub/Sub reenvia a notificação. */
    @ExceptionHandler(GooglePlayUnavailableException::class)
    fun googlePlayUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 503)
    }

    @ExceptionHandler(GooglePlayPurchaseInvalidException::class)
    fun googlePlayPurchaseInvalid(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 422, ErrorCode.GOOGLE_PLAY_PURCHASE_INVALID)
    }

    @ExceptionHandler(GooglePlayPurchaseOwnedByAnotherAccountException::class)
    fun googlePlayPurchaseOwnedByAnotherAccount(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.GOOGLE_PLAY_PURCHASE_OWNED_BY_ANOTHER_ACCOUNT)
    }

    @ExceptionHandler(AppStoreTransactionInvalidException::class)
    fun appStoreTransactionInvalid(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 422, ErrorCode.APP_STORE_TRANSACTION_INVALID)
    }

    @ExceptionHandler(AppStoreTransactionOwnedByAnotherAccountException::class)
    fun appStoreTransactionOwnedByAnotherAccount(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.APP_STORE_TRANSACTION_OWNED_BY_ANOTHER_ACCOUNT)
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

    /**
     * Corpo ilegível (JSON malformado, tipo errado, enum desconhecido) é erro do cliente: 400 com o código de
     * validação e, quando o Jackson sabe, o campo. Só o caminho do campo é registrado/devolvido, nunca o valor
     * enviado (pode carregar dado pessoal).
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun messageNotReadable(failure: HttpMessageNotReadableException, request: HttpServletRequest, response: HttpServletResponse) {
        if (request.requestURI == "/api/session/app-link/redeem") {
            problemWriter.write(request, response, 400, ErrorCode.APP_ONBOARDING_CODE_INVALID)
            return
        }
        val field = (failure.cause as? JacksonException)?.path
            ?.mapNotNull { it.propertyName }
            ?.joinToString(".")
            ?.takeIf { it.isNotBlank() }
        logger.warn(
            "invalid_request_body method={} path={} field={} cause={}",
            request.method,
            request.requestURI,
            field ?: "-",
            failure.cause?.javaClass?.simpleName ?: failure.javaClass.simpleName,
        )
        problemWriter.write(
            request,
            response,
            400,
            ErrorCode.VALIDATION_FAILED,
            fieldErrors = field?.let { mapOf(it to listOf("valor inválido")) },
        )
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

    @ExceptionHandler(CouponNotEligibleException::class)
    fun couponNotEligible(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.COUPON_NOT_ELIGIBLE)
    }

    @ExceptionHandler(CouponExhaustedException::class)
    fun couponExhausted(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.COUPON_EXHAUSTED)
    }

    @ExceptionHandler(CouponNotValidForPlanException::class)
    fun couponNotValidForPlan(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 422, ErrorCode.COUPON_NOT_VALID_FOR_PLAN)
    }

    @ExceptionHandler(DowngradeBlockedException::class)
    fun downgradeBlocked(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 409, ErrorCode.DOWNGRADE_BLOCKED)
    }

    @ExceptionHandler(PaymentMethodUnavailableHttpException::class)
    fun paymentMethodUnavailable(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 422, ErrorCode.PAYMENT_METHOD_UNAVAILABLE)
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

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException::class,
        org.springframework.web.servlet.NoHandlerFoundException::class)
    fun routeNotFound(request: HttpServletRequest, response: HttpServletResponse) {
        problemWriter.write(request, response, 404)
    }

    /**
     * Registra classe, SQLSTATE e pilha da falha, nunca a mensagem: ela pode carregar segredo (chave do
     * Firebase, corpo da requisição), e o SafeDiagnosticsIntegrationTest garante que nada disso vai ao log.
     * O correlationId entra pelo MDC (`corr=` no padrão de log).
     */
    @ExceptionHandler(Exception::class)
    fun unexpected(
        failure: Exception,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        logger.error("unexpected_failure method={} path={}\n{}", request.method, request.requestURI, redactedTrace(failure))
        problemWriter.write(request, response, 500)
    }

    /** Mesma forma do stack trace padrão (classe, `at` por quadro, `Caused by:`), sem as mensagens. */
    private fun redactedTrace(failure: Throwable): String = buildString {
        var current: Throwable? = failure
        var depth = 0
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            if (depth > 0) append("Caused by: ")
            append(current.javaClass.name)
            if (current is SQLException) append(" [sqlState=").append(current.sqlState).append(']')
            for (frame in current.stackTrace) append("\n\tat ").append(frame)
            append('\n')
            current = current.cause
            depth++
        }
    }
}
