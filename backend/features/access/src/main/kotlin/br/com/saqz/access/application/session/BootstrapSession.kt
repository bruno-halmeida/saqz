package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.sharedkernel.RequestIdentity

class BootstrapSession(
    private val repository: SessionRepository,
) {
    fun execute(identity: RequestIdentity): BootstrapSessionResult {
        val displayName = identity.displayName
            ?.let { runCatching { AccessName.from(it) }.getOrNull() }
            ?: return BootstrapSessionResult.InvalidDisplayName
        // Antes do upsert: suspenso não vira "ativo" nem tem updated_at bumpado.
        if (repository.suspendedAt(identity.subject) != null) {
            return BootstrapSessionResult.Suspended
        }
        val session = repository.upsertAndLoad(
            SessionUpsert(
                subject = identity.subject,
                email = identity.email,
                emailVerified = identity.hasVerifiedEmail(),
                displayName = displayName,
            ),
        )
        return BootstrapSessionResult.Success(session)
    }
}
