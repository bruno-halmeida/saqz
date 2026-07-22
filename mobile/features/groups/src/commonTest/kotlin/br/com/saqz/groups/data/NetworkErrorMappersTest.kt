package br.com.saqz.groups.data

import br.com.saqz.network.ApiProblem
import br.com.saqz.network.NetworkError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetworkErrorMappersTest {
    @Test
    fun setupValidationMaps400ToValidation() {
        assertEquals(SetupFailure.Validation, problem(400).toSetupFailure())
    }

    @Test
    fun setupForbiddenMaps403() {
        assertEquals(SetupFailure.Forbidden, problem(403).toSetupFailure())
    }

    @Test
    fun setupNotFoundMaps404() {
        assertEquals(SetupFailure.NotFound, problem(404).toSetupFailure())
    }

    @Test
    fun setupVersionConflictMaps409WithCode() {
        assertEquals(SetupFailure.Conflict, problem(409, "VERSION_CONFLICT").toSetupFailure())
    }

    @Test
    fun setupUnknownApiProblemMapsUnavailable() {
        assertEquals(SetupFailure.Unavailable, problem(409, "OTHER").toSetupFailure())
    }

    @Test
    fun setupTransportErrorsMapUnavailable() {
        assertEquals(SetupFailure.Unavailable, NetworkError.Timeout.toSetupFailure())
        assertEquals(SetupFailure.Unavailable, NetworkError.HttpStatus(500).toSetupFailure())
    }

    @Test
    fun administrationMapsSameStatusCodesAsSetup() {
        assertEquals(AdministrationFailure.Validation, problem(400).toAdministrationFailure())
        assertEquals(AdministrationFailure.Forbidden, problem(403).toAdministrationFailure())
        assertEquals(AdministrationFailure.NotFound, problem(404).toAdministrationFailure())
        assertEquals(AdministrationFailure.Conflict, problem(409, "VERSION_CONFLICT").toAdministrationFailure())
        assertEquals(AdministrationFailure.Unavailable, NetworkError.Timeout.toAdministrationFailure())
    }

    @Test
    fun photoNotFoundMapsFromHttpStatusAndApiProblem() {
        assertEquals(PhotoFailure.NotFound, NetworkError.HttpStatus(404).toPhotoFailure())
        assertEquals(PhotoFailure.NotFound, problem(404).toPhotoFailure())
    }

    @Test
    fun photoStaleVersionMaps409ConflictCode() {
        assertEquals(PhotoFailure.StaleVersion, problem(409, "VERSION_CONFLICT").toPhotoFailure())
    }

    @Test
    fun photoOtherErrorsMapFailed() {
        assertEquals(PhotoFailure.Failed, NetworkError.HttpStatus(500).toPhotoFailure())
        assertEquals(PhotoFailure.Failed, problem(422).toPhotoFailure())
        assertEquals(PhotoFailure.Failed, NetworkError.Timeout.toPhotoFailure())
    }

    @Test
    fun deferredInviteInvalidOrExpired() {
        val result = problem(404, "INVITE_INVALID_OR_EXPIRED").toDeferredLinkFailure(
            invalidCode = "INVITE_INVALID_OR_EXPIRED",
            attemptLimitCode = "INVITE_ATTEMPT_LIMIT",
        )
        assertEquals(DeferredLinkFailure.InvalidOrExpired, result)
    }

    @Test
    fun deferredInviteAttemptLimitCarriesRetryAfter() {
        val result = problem(429, "INVITE_ATTEMPT_LIMIT", retryAfterSeconds = 42).toDeferredLinkFailure(
            invalidCode = "INVITE_INVALID_OR_EXPIRED",
            attemptLimitCode = "INVITE_ATTEMPT_LIMIT",
        )
        val attemptLimit = assertIs<DeferredLinkFailure.AttemptLimit>(result)
        assertEquals(42, attemptLimit.retryAfterSeconds)
    }

    @Test
    fun deferredInviteUnknownMapsUnavailable() {
        val result = problem(500).toDeferredLinkFailure(
            invalidCode = "INVITE_INVALID_OR_EXPIRED",
            attemptLimitCode = "INVITE_ATTEMPT_LIMIT",
        )
        assertEquals(DeferredLinkFailure.Unavailable, result)
    }

    @Test
    fun deferredAttendanceUsesDifferentCodes() {
        val result = problem(404, "ATTENDANCE_LINK_INVALID_OR_EXPIRED").toDeferredLinkFailure(
            invalidCode = "ATTENDANCE_LINK_INVALID_OR_EXPIRED",
            attemptLimitCode = "ATTENDANCE_LINK_ATTEMPT_LIMIT",
        )
        assertEquals(DeferredLinkFailure.InvalidOrExpired, result)
    }

    private fun problem(
        status: Int,
        code: String? = null,
        retryAfterSeconds: Int? = null,
    ): NetworkError = NetworkError.ApiProblemError(
        ApiProblem(
            status = status,
            code = code,
            correlationId = "corr",
            fieldErrors = null,
            retryAfterSeconds = retryAfterSeconds,
        ),
    )
}
