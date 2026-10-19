package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.CourtesyUserNotFoundException
import br.com.saqz.subscriptions.application.InvalidCourtesyException
import br.com.saqz.subscriptions.application.PlanCourtesies
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.PlanCourtesy
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class AdminCourtesyEntry(
    val id: UUID,
    val plan: Plan,
    val endsAt: Instant?,
    val reason: String,
    val grantedBy: UUID,
    val createdAt: Instant,
    val revokedAt: Instant?,
    val revokedBy: UUID?,
    val revokedReason: String?,
)

data class AdminUserCourtesyResponse(val active: AdminCourtesyEntry?, val history: List<AdminCourtesyEntry>)

data class AdminActiveCourtesy(
    val ownerUserId: UUID,
    val displayName: String?,
    val email: String?,
    val plan: Plan,
    val endsAt: Instant?,
    val reason: String,
    val grantedBy: UUID,
    val createdAt: Instant,
)

data class AdminGrantCourtesyRequest(val plan: String?, val endsAt: Instant?, val reason: String?)

data class AdminRevokeCourtesyRequest(val reason: String?)

/** `dryRun` ausente vale `true`: gravar em lote exige pedir. Com `dryRun: false`, `ownerIds` é a lista conferida. */
data class AdminCourtesyBatchRequest(
    val plan: String?,
    val endsAt: Instant?,
    val reason: String?,
    val dryRun: Boolean? = true,
    val ownerIds: List<UUID>? = null,
)

data class AdminCourtesyCandidate(
    val ownerUserId: UUID,
    val displayName: String?,
    val email: String?,
    val groups: List<String>,
    val effectivePlan: Plan?,
    val hasCourtesy: Boolean,
)

data class AdminCourtesyBatchItem(val ownerUserId: UUID, val granted: Boolean, val error: String?)

/** Só um dos dois vem preenchido: `candidates` no dry-run, `results` ao gravar. */
data class AdminCourtesyBatchResponse(
    val dryRun: Boolean,
    val candidates: List<AdminCourtesyCandidate>?,
    val results: List<AdminCourtesyBatchItem>?,
)

/** Cortesia de plano (DEB-097) no adm-web. Fora do component scan; fiação em PlatformAdminConfiguration. */
@RestController
class AdminCourtesiesController(
    private val courtesies: PlanCourtesies,
    private val adminLookup: PlatformAdminLookup,
) {
    private val log = LoggerFactory.getLogger("br.com.saqz.admin.courtesy")

    @GetMapping("/admin/users/{id}/courtesy")
    fun view(@PathVariable id: UUID): ResponseEntity<AdminUserCourtesyResponse> {
        val (active, history) = courtesies.view(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(AdminUserCourtesyResponse(active?.toEntry(), history.map { it.toEntry() }))
    }

    @PostMapping("/admin/users/{id}/courtesy")
    fun grant(
        @PathVariable id: UUID,
        @RequestBody body: AdminGrantCourtesyRequest,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<AdminCourtesyEntry> {
        val admin = adminId(identity) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val plan = body.plan.toPlan() ?: return ResponseEntity.badRequest().build()
        return try {
            val granted = courtesies.grant(admin, id, plan, body.endsAt, body.reason.orEmpty())
            log.info("admin_courtesy action=grant admin={} owner={} plan={} endsAt={}", admin, id, plan, granted.endsAt)
            ResponseEntity.ok(granted.toEntry())
        } catch (_: InvalidCourtesyException) {
            ResponseEntity.badRequest().build()
        } catch (_: CourtesyUserNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/admin/users/{id}/courtesy/revoke")
    fun revoke(
        @PathVariable id: UUID,
        @RequestBody(required = false) body: AdminRevokeCourtesyRequest?,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<Void> {
        val admin = adminId(identity) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return try {
            if (courtesies.revoke(admin, id, body?.reason)) {
                log.info("admin_courtesy action=revoke admin={} owner={}", admin, id)
                ResponseEntity.noContent().build()
            } else {
                ResponseEntity.status(HttpStatus.CONFLICT).build()
            }
        } catch (_: CourtesyUserNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/admin/courtesies")
    fun active(): List<AdminActiveCourtesy> = courtesies.listActive().map {
        val c = it.courtesy
        AdminActiveCourtesy(c.ownerUserId, it.displayName, it.email, c.plan, c.endsAt, c.reason, c.grantedBy, c.createdAt)
    }

    @PostMapping("/admin/courtesies/batch")
    fun batch(
        @RequestBody body: AdminCourtesyBatchRequest,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<AdminCourtesyBatchResponse> {
        val admin = adminId(identity) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val plan = body.plan.toPlan() ?: return ResponseEntity.badRequest().build()
        val reason = body.reason.orEmpty()
        return try {
            if (body.dryRun != false) {
                val candidates = courtesies.preview(plan, body.endsAt, reason).map {
                    AdminCourtesyCandidate(it.ownerUserId, it.displayName, it.email, it.groups, it.effectivePlan, it.hasCourtesy)
                }
                ResponseEntity.ok(AdminCourtesyBatchResponse(dryRun = true, candidates = candidates, results = null))
            } else {
                val owners = body.ownerIds?.takeIf { it.isNotEmpty() } ?: return ResponseEntity.badRequest().build()
                val results = courtesies.grantBatch(admin, owners, plan, body.endsAt, reason)
                log.info(
                    "admin_courtesy action=batch admin={} plan={} requested={} granted={}",
                    admin, plan, results.size, results.count { it.granted },
                )
                ResponseEntity.ok(
                    AdminCourtesyBatchResponse(false, null, results.map { AdminCourtesyBatchItem(it.ownerUserId, it.granted, it.error) }),
                )
            }
        } catch (_: InvalidCourtesyException) {
            ResponseEntity.badRequest().build()
        }
    }

    private fun adminId(identity: RequestIdentity): UUID? = adminLookup.findBySubject(identity.subject)?.userId

    private fun String?.toPlan(): Plan? = this?.let { runCatching { Plan.valueOf(it) }.getOrNull() }

    private fun PlanCourtesy.toEntry() =
        AdminCourtesyEntry(id, plan, endsAt, reason, grantedBy, createdAt, revokedAt, revokedBy, revokedReason)
}
