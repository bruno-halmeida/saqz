package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.adapter.input.http.SubscriptionActorResolver
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

class RolloutSystemBody {
    var system: ReceivableSystem? = null
    var mode: RolloutMode? = null
    var decision: RolloutDecision? = null
}
class RolloutBody {
    var requestId: UUID? = null
    var expectedVersion: java.math.BigDecimal? = null
    var reason: String? = null
    var systems: List<RolloutSystemBody?>? = null
    var overrides: List<RolloutSystemBody?>? = null
    var accountOperationsEnabled: Boolean? = null
    private fun version() = expectedVersion?.longValueExact() ?: throw IllegalArgumentException()
    fun systemChange() = RolloutChange(requireNotNull(requestId), version(), requireNotNull(reason),
        requireNotNull(systems).map { SystemRollout(requireNotNull(it?.system), requireNotNull(it.mode)) })
    fun userChange() = UserRolloutChange(requireNotNull(requestId), version(), requireNotNull(reason),
        requireNotNull(overrides).map { UserRolloutOverride(requireNotNull(it?.system), requireNotNull(it.decision)) }, accountOperationsEnabled)
}

@RestController
class AdminReceivablesRolloutController(private val admins: PlatformAdminLookup, private val rollout: ReceivablesRollout,
    private val actors: SubscriptionActorResolver) {
    @GetMapping("/api/receivables/availability")
    fun availability(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<*> = respond {
        val actor = actors.resolve(identity)
        rollout.availability(actor)
    }
    @GetMapping("/admin/receivables/rollout")
    fun read(@AuthenticationPrincipal identity: RequestIdentity) = admin(identity) { rollout.read() }
    @PutMapping("/admin/receivables/rollout")
    fun change(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody body: RolloutBody) =
        admin(identity, body.requestId ?: UUID.randomUUID()) { rollout.change(it, body.systemChange()) }
    @GetMapping("/admin/receivables/rollout/users/{userId}")
    fun user(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable userId: UUID) = admin(identity) { rollout.user(userId) }
    @PutMapping("/admin/receivables/rollout/users/{userId}")
    fun changeUser(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable userId: UUID, @RequestBody body: RolloutBody) =
        admin(identity, body.requestId ?: UUID.randomUUID()) { rollout.changeUser(it, userId, body.userChange()) }
    @GetMapping("/admin/receivables/rollout/history")
    fun history(@AuthenticationPrincipal identity: RequestIdentity, @RequestParam(defaultValue="1") page: Int,
        @RequestParam(defaultValue="25") size: Int) = admin(identity) { rollout.history(page, size) }

    private fun admin(identity: RequestIdentity, id: UUID = UUID.randomUUID(), block: (UUID) -> Any) = respond(id) {
        val actor = admins.findBySubject(identity.subject)?.userId ?: throw RolloutFailure(FinancialError.UNAUTHORIZED)
        block(actor)
    }
    private fun respond(id: UUID = UUID.randomUUID(), block: () -> Any): ResponseEntity<*> = try {
        ResponseEntity.ok().header("Cache-Control", "no-store").body(block())
    } catch (_: IllegalArgumentException) {
        invalid()
    } catch (_: ArithmeticException) {
        invalid()
    } catch (failure: RolloutFailure) {
        ResponseEntity.status(when(failure.error) {
            FinancialError.NOT_FOUND -> 404
            FinancialError.UNAUTHORIZED -> 403
            FinancialError.CONFLICT -> 409
            else -> 400
        }).header("Cache-Control", "no-store").body(FinancialResult.Failure(failure.error, id))
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class)
    fun invalid(): ResponseEntity<*> = ResponseEntity.badRequest().header("Cache-Control", "no-store")
        .body(FinancialResult.Failure(FinancialError.INVALID_INPUT, UUID.randomUUID()))
}
