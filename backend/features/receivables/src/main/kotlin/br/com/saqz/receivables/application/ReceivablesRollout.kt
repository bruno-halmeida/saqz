package br.com.saqz.receivables.application

import java.util.UUID

enum class ReceivableSystem { BACKEND, MOBILE }
enum class RolloutMode { OFF, SELECTED_USERS, ALL_USERS }
enum class RolloutDecision { INHERIT, ALLOW, DENY }
data class SystemRollout(val system: ReceivableSystem, val mode: RolloutMode)
data class UserRolloutOverride(val system: ReceivableSystem, val decision: RolloutDecision)
data class RolloutState(val version: Long, val systems: List<SystemRollout>)
data class ReceivablesAvailability(val backendEnabled: Boolean, val mobileEnabled: Boolean, val maintenanceAvailable: Boolean = true)
data class UserRolloutState(val userId: UUID, val version: Long, val overrides: List<UserRolloutOverride>,
    val backendEnabled: Boolean, val mobileEnabled: Boolean, val accountId: UUID?, val accountOperationsEnabled: Boolean?)
data class RolloutChange(val requestId: UUID, val expectedVersion: Long, val reason: String, val systems: List<SystemRollout>)
data class UserRolloutChange(val requestId: UUID, val expectedVersion: Long, val reason: String,
    val overrides: List<UserRolloutOverride>, val accountOperationsEnabled: Boolean?)
data class RolloutHistoryItem(val requestId: UUID, val actorUserId: UUID, val userId: UUID?, val reason: String,
    val createdAt: String, val before: Any, val after: Any)
data class RolloutHistory(val page: Int, val size: Int, val total: Long, val items: List<RolloutHistoryItem>)

object RolloutPolicy {
    fun enabled(mode: RolloutMode, decision: RolloutDecision): Boolean = when (mode) {
        RolloutMode.OFF -> false
        RolloutMode.SELECTED_USERS -> decision == RolloutDecision.ALLOW
        RolloutMode.ALL_USERS -> decision != RolloutDecision.DENY
    }
    fun availability(systems: List<SystemRollout>, overrides: List<UserRolloutOverride>): ReceivablesAvailability {
        fun enabled(system: ReceivableSystem) = enabled(systems.single { it.system == system }.mode,
            overrides.singleOrNull { it.system == system }?.decision ?: RolloutDecision.INHERIT)
        val backend = enabled(ReceivableSystem.BACKEND)
        return ReceivablesAvailability(backend, backend && enabled(ReceivableSystem.MOBILE))
    }
}

fun interface ReceivablesRolloutAccess { fun availability(userId: UUID): ReceivablesAvailability }
interface ReceivablesRollout : ReceivablesRolloutAccess {
    fun read(): RolloutState
    fun user(userId: UUID): UserRolloutState
    fun change(actor: UUID, change: RolloutChange): RolloutState
    fun changeUser(actor: UUID, userId: UUID, change: UserRolloutChange): UserRolloutState
    fun history(page: Int, size: Int): RolloutHistory
}
class RolloutFailure(val error: FinancialError) : RuntimeException(error.name)
