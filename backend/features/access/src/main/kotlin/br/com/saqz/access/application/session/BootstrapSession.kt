package br.com.saqz.access.application.session

import br.com.saqz.access.domain.AccessName
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.subscription.PlanOwnerLookup

class BootstrapSession(
    private val repository: SessionRepository,
    private val planOwners: PlanOwnerLookup = PlanOwnerLookup { false },
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
        return BootstrapSessionResult.Success(session.withPlanOwner(planOwners))
    }

    /**
     * Só o id de quem chama, para os resolvers de ator: conta que já existe custa 1 SELECT,
     * conta nova cai no bootstrap completo, que é quem cria a linha.
     */
    // ponytail: e-mail, nome e updated_at (o "visto por último" do painel admin) só se renovam
    // no PUT /api/session, que o app chama a cada abertura. Se o painel precisar de
    // granularidade por request, um UPDATE de updated_at com throttle aqui resolve.
    fun actorId(identity: RequestIdentity): SessionActorResult {
        val existing = repository.existingUser(identity.subject)
            ?: return when (val result = execute(identity)) {
                BootstrapSessionResult.InvalidDisplayName -> SessionActorResult.InvalidDisplayName
                BootstrapSessionResult.Suspended -> SessionActorResult.Suspended
                is BootstrapSessionResult.Success -> SessionActorResult.Found(result.session.user.id)
            }
        return if (existing.suspendedAt != null) SessionActorResult.Suspended else SessionActorResult.Found(existing.id)
    }
}
