package br.com.saqz.receivables.application

import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Operational input only: the caller must authenticate the operator outside the public web application. */
data class CredentialRecoveryCommand(
    val requestId: UUID, val accountId: UUID, val ownerUserId: UUID,
    val creationOperationId: UUID, val operatorId: UUID,
    val providerAccountId: String, val walletId: String,
)

/** Never serialize this type or include it in diagnostics. */
class RecoveryCredential(val value: String) {
    override fun toString() = "RecoveryCredential[REDACTED]"
}

class CredentialRecoveryTarget(val cpfCnpj: String, val version: Long)
enum class CredentialRecoveryResult { RECOVERED, ALREADY_RECOVERED, CONFLICT, NOT_ELIGIBLE, IDENTITY_MISMATCH, UNAVAILABLE }
class CredentialRecoveryConflict : RuntimeException("Credential recovery conflict")
class CredentialRecoveryIneligible : RuntimeException("Credential recovery is not eligible")

interface CredentialRecoveryStore {
    /** Reserve request identity durably; null is an exact successful replay. Candidate secret is encrypted. */
    fun begin(command: CredentialRecoveryCommand, credential: RecoveryCredential, now: Instant): CredentialRecoveryTarget?
    /** Recheck owner, account version and UNKNOWN creation; attach encrypted key, audit and resolve creation atomically. */
    fun complete(command: CredentialRecoveryCommand, target: CredentialRecoveryTarget, now: Instant)
}

interface CredentialRecoveryProvider {
    /** Read-only: exact unique parent-owned account, legal identity and credential-owned wallet must all agree. */
    fun matches(command: CredentialRecoveryCommand, target: CredentialRecoveryTarget, credential: RecoveryCredential): Boolean
}

class RecoverFinancialCredential(
    private val store: CredentialRecoveryStore,
    private val provider: CredentialRecoveryProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun recover(command: CredentialRecoveryCommand, credential: RecoveryCredential): CredentialRecoveryResult {
        if (!command.providerAccountId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            !command.walletId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            credential.value.isBlank() || credential.value.length > 8192 ||
            credential.value.any { it.isWhitespace() || it.isISOControl() }) return CredentialRecoveryResult.NOT_ELIGIBLE
        return try {
            val target = store.begin(command, credential, clock.instant())
                ?: return CredentialRecoveryResult.ALREADY_RECOVERED
            if (!provider.matches(command, target, credential)) return CredentialRecoveryResult.IDENTITY_MISMATCH
            store.complete(command, target, clock.instant())
            CredentialRecoveryResult.RECOVERED
        } catch (_: CredentialRecoveryConflict) {
            CredentialRecoveryResult.CONFLICT
        } catch (_: CredentialRecoveryIneligible) {
            CredentialRecoveryResult.NOT_ELIGIBLE
        } catch (_: Exception) {
            // Do not propagate HTTP/JDBC exceptions, bodies, headers, PII or credential values to the operator.
            CredentialRecoveryResult.UNAVAILABLE
        }
    }
}
