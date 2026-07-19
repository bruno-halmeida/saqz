package br.com.saqz.sharedkernel.actor

import br.com.saqz.sharedkernel.RequestIdentity
import java.util.UUID

data class AuthenticatedActor(
    val userId: UUID,
)

interface AuthenticatedActorResolver {
    fun resolve(identity: RequestIdentity): AuthenticatedActor
}
