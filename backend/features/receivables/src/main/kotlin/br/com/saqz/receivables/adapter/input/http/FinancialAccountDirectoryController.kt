package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.FinancialRequest
import br.com.saqz.receivables.application.ManageFinancialDelegations
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class FinancialAccountDirectoryController(private val actors: FinancialActorResolver,
    private val service: ManageFinancialDelegations) {
    @GetMapping("/api/receivables/accounts")
    fun accounts(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<*> = ResponseEntity.ok()
        .header("Cache-Control", "no-store").body(service.accounts(
            FinancialRequest(UUID.randomUUID(), actors.resolve(identity)),
        ))
}
