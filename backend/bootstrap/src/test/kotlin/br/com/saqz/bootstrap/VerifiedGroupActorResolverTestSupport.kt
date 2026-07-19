package br.com.saqz.bootstrap

import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.groups.adapter.input.http.EmailNotVerifiedException
import br.com.saqz.groups.adapter.input.http.InvalidDisplayNameException
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver

fun verifiedGroupActorResolver(bootstrapSession: BootstrapSession) = VerifiedGroupActorResolver { identity ->
    when (val result = bootstrapSession.execute(identity)) {
        BootstrapSessionResult.EmailNotVerified -> throw EmailNotVerifiedException()
        BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
        is BootstrapSessionResult.Success -> result.session.user.id
    }
}
