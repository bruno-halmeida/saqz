package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.sharedkernel.RequestIdentity

class BootstrapSession(
    private val repository: SessionRepository,
) {
    fun execute(identity: RequestIdentity): BootstrapSessionResult {
        if (identity.emailVerified != true) return BootstrapSessionResult.EmailNotVerified
        val displayName = identity.displayName
            ?.let { runCatching { AccessName.from(it) }.getOrNull() }
            ?: return BootstrapSessionResult.InvalidDisplayName
        val session = repository.upsertAndLoad(
            SessionUpsert(
                subject = identity.subject,
                email = identity.email,
                displayName = displayName,
            ),
        )
        return BootstrapSessionResult.Success(session)
    }
}
