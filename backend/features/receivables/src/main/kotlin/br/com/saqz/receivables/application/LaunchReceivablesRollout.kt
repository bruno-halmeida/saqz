package br.com.saqz.receivables.application

import java.util.UUID

/** Deployment lock is independent of persisted rollout settings; existing records remain maintainable. */
class LaunchReceivablesRollout(
    private val delegate: ReceivablesRollout,
    private val enabled: Boolean,
) : ReceivablesRollout by delegate {
    override fun availability(userId: UUID): ReceivablesAvailability =
        if (enabled) delegate.availability(userId) else ReceivablesAvailability(false, false)

    override fun user(userId: UUID): UserRolloutState = delegate.user(userId).let {
        if (enabled) it else it.copy(backendEnabled = false, mobileEnabled = false)
    }

    override fun changeUser(actor: UUID, userId: UUID, change: UserRolloutChange): UserRolloutState =
        delegate.changeUser(actor, userId, change).let {
            if (enabled) it else it.copy(backendEnabled = false, mobileEnabled = false)
        }
}
