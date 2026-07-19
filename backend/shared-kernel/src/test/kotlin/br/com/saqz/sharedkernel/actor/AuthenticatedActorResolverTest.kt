package br.com.saqz.sharedkernel.actor

import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class AuthenticatedActorResolverTest {
    @Test
    fun `resolves request identity to its stable internal actor`() {
        val actor = AuthenticatedActor(UUID.randomUUID())
        val resolver = RecordingResolver(actor)
        val identity = RequestIdentity(subject = "verified-subject")

        assertEquals(actor, resolver.resolve(identity))
        assertEquals(identity, resolver.identities.single())
    }

    @Test
    fun `authenticated actor exposes only stable user id`() {
        val id = UUID.randomUUID()

        assertEquals(id, AuthenticatedActor(id).userId)
    }

    private class RecordingResolver(
        private val actor: AuthenticatedActor,
    ) : AuthenticatedActorResolver {
        val identities = mutableListOf<RequestIdentity>()

        override fun resolve(identity: RequestIdentity): AuthenticatedActor {
            identities += identity
            return actor
        }
    }
}
