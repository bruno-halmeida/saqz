package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.FinancialRequest
import br.com.saqz.receivables.application.SettleTime
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Link da hora do acerto (`/acerto/{codigo}`): o app logado resolve para o grupo e o jogo ou mês (seção J). */
@RestController
class SettleLinksController(private val actors: FinancialActorResolver, private val service: SettleTime) {
    @GetMapping("/api/receivables/settle-links/{code}")
    fun resolve(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable code: String): ResponseEntity<*> =
        receivablesResponse(service.resolve(code, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))
}
