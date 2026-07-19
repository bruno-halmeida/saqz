package br.com.saqz.groups.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import java.util.UUID

fun interface VerifiedGroupActorResolver {
    fun resolve(identity: RequestIdentity): UUID
}
