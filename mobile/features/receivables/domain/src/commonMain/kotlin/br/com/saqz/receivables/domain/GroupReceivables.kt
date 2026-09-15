package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

enum class ReceiptMethod { PIX, CARD }
enum class AccountRegistration { INCOMPLETE, UNDER_REVIEW, CORRECTION_REQUIRED, APPROVED, REJECTED }
data class ReceiptAccount(val id: String, val registration: AccountRegistration, val operationsEnabled: Boolean)
data class ReceiptConfiguration(val accountId: String, val groupId: String, val enabled: Boolean,
    val pixEnabled: Boolean, val cardEnabled: Boolean)
data class ReceiptPermission(val allowed: Boolean, val reason: String? = null)
data class ReceiptStatus(val state: ReceiptConfiguration, val permissions: Map<String, ReceiptPermission>)
data class ReceiptSchedule(val method: ReceiptMethod, val termsVersion: String,
    val providerRate: String, val providerFixedCents: Long, val commissionRate: String, val commissionFixedCents: Long,
    val providerMinimumCents: Long = 0, val providerMaximumCents: Long? = null)
data class ReceiptPrice(val kind: String, val method: ReceiptMethod, val baseCents: Long,
    val feesCents: Long, val totalCents: Long, val expectedNetCents: Long)
data class ReceiptReview(val state: ReceiptConfiguration, val schedules: List<ReceiptSchedule>,
    val prices: List<ReceiptPrice>, val permissions: Map<String, ReceiptPermission>,
    val effectiveCutoffAt: String?, val fingerprint: String)
data class ReceiptTerms(val version: String, val content: String)
data class ReceiptCommand(val requestId: String, val accountId: String, val methods: Set<ReceiptMethod> = emptySet(),
    val fingerprint: String? = null, val accepted: Boolean = false)
enum class ReceiptError : SaqzError { DENIED, STALE, INVALID, UNAVAILABLE, NETWORK, UNCERTAIN, SIGNED_OUT }
fun interface ReceiptAccountDirectory {
    suspend fun accounts(): SaqzResult<List<ReceiptAccount>, ReceiptError>
}
interface GroupReceivablesGateway : ReceiptAccountDirectory {
    suspend fun status(groupId: String, accountId: String): SaqzResult<ReceiptStatus, ReceiptError>
    suspend fun preview(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptReview, ReceiptError>
    suspend fun terms(version: String): SaqzResult<ReceiptTerms, ReceiptError>
    suspend fun activate(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptConfiguration, ReceiptError>
    suspend fun deactivate(groupId: String, command: ReceiptCommand): SaqzResult<ReceiptConfiguration, ReceiptError>
}

/** Stable authenticated actor identity for restoring a command, never sent as a request parameter. */
fun interface ReceivablesRecoveryIdentity {
    fun currentUserId(): String?
}
