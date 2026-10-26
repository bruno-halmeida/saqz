package br.com.saqz.adminweb.http

import br.com.saqz.access.adapter.output.jdbc.flags.FeatureJson
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.flags.FeatureError
import br.com.saqz.access.application.flags.FeatureFailure
import br.com.saqz.access.application.flags.FeatureFlags
import br.com.saqz.access.application.flags.FeatureHistoryType
import br.com.saqz.access.application.flags.FeatureRule
import br.com.saqz.access.application.flags.PlatformAppVersions
import br.com.saqz.sharedkernel.RequestIdentity
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Corpos lidos pelo [FeatureJson] (o mesmo formato da regra no banco e no histórico). */
data class AdminFeatureChangeBody(
    val requestId: UUID? = null,
    val expectedVersion: Long? = null,
    val reason: String? = null,
    val rule: FeatureRule? = null,
    val historyId: UUID? = null,
    val members: List<UUID>? = null,
    val android: PlatformAppVersions? = null,
    val ios: PlatformAppVersions? = null,
)

data class AdminFeatureErrorResponse(val error: FeatureError, val message: String)

/**
 * Flags no adm (FLG-004 e FLG-007): lista com alertas, edição com motivo e versão esperada, grupos com
 * nome, versões do app e o histórico imutável com "voltar para esta versão". Quem não é admin já para na
 * guarda do /admin; aqui o admin vira o autor da linha do histórico.
 */
@RestController
class AdminFeaturesController(private val features: FeatureFlags, private val admins: PlatformAdminLookup) {
    private val log = LoggerFactory.getLogger("br.com.saqz.admin.features")

    @GetMapping("/admin/features")
    fun list(@AuthenticationPrincipal identity: RequestIdentity) = admin(identity) { mapOf("flags" to features.adminList()) }

    @GetMapping("/admin/features/history")
    fun history(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) key: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ) = admin(identity) {
        val parsed = type?.let { runCatching { FeatureHistoryType.valueOf(it) }.getOrNull() ?: invalid() }
        features.history(parsed, key?.takeIf { it.isNotBlank() }, page, size)
    }

    @GetMapping("/admin/features/{key}")
    fun flag(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable key: String) = admin(identity) { features.adminFlag(key) }

    @PutMapping("/admin/features/{key}")
    fun change(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable key: String, @RequestBody raw: String) =
        admin(identity) { actor ->
            val body = parse(raw)
            features.changeFlag(body.requestId ?: UUID.randomUUID(), actor, key, body.version(), body.reason.orEmpty(), body.rule ?: invalid())
                .also { log.info("admin_feature action=change admin={} key={} version={}", actor, key, it.version) }
        }

    @PostMapping("/admin/features/{key}/revert")
    fun revert(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable key: String, @RequestBody raw: String) =
        admin(identity) { actor ->
            val body = parse(raw)
            features.revertFlag(body.requestId ?: UUID.randomUUID(), actor, key, body.historyId ?: invalid(), body.version(),
                body.reason.orEmpty(), FeatureJson::rule)
                .also { log.info("admin_feature action=revert admin={} key={} version={}", actor, key, it.version) }
        }

    @GetMapping("/admin/feature-groups")
    fun groups(@AuthenticationPrincipal identity: RequestIdentity) = admin(identity) { mapOf("groups" to features.groups()) }

    /** Busca a pessoa por e-mail ou telefone para pôr num grupo. */
    @GetMapping("/admin/feature-groups/users")
    fun users(@AuthenticationPrincipal identity: RequestIdentity, @RequestParam q: String) =
        admin(identity) { mapOf("users" to features.searchUsers(q)) }

    /** `expectedVersion` 0 cria o grupo. */
    @PutMapping("/admin/feature-groups/{name}")
    fun changeGroup(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable name: String, @RequestBody raw: String) =
        admin(identity) { actor ->
            val body = parse(raw)
            features.changeGroup(body.requestId ?: UUID.randomUUID(), actor, name, body.version(), body.reason.orEmpty(), body.members ?: invalid())
                .also { log.info("admin_feature action=group admin={} group={} version={}", actor, name, it.version) }
        }

    @GetMapping("/admin/app-versions")
    fun appVersions(@AuthenticationPrincipal identity: RequestIdentity) = admin(identity) { features.appVersions() }

    @PutMapping("/admin/app-versions")
    fun changeAppVersions(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody raw: String) =
        admin(identity) { actor ->
            val body = parse(raw)
            features.changeAppVersions(body.requestId ?: UUID.randomUUID(), actor, body.version(), body.reason.orEmpty(),
                body.android ?: invalid(), body.ios ?: invalid())
                .also { log.info("admin_feature action=app_versions admin={} version={}", actor, it.version) }
        }

    private fun parse(raw: String): AdminFeatureChangeBody = try {
        FeatureJson.read(raw, AdminFeatureChangeBody::class.java).let { it.copy(rule = it.rule?.let(FeatureJson::normalize)) }
    } catch (_: Exception) {
        invalid()
    }

    private fun AdminFeatureChangeBody.version(): Long = expectedVersion?.takeIf { it >= 0 } ?: invalid()

    private fun invalid(): Nothing = throw FeatureFailure(FeatureError.INVALID_INPUT, "Pedido inválido")

    private fun admin(identity: RequestIdentity, block: (UUID) -> Any): ResponseEntity<*> = try {
        val actor = admins.findBySubject(identity.subject)?.userId ?: throw FeatureFailure(FeatureError.UNAUTHORIZED)
        ResponseEntity.ok().header("Cache-Control", "no-store").body(block(actor))
    } catch (failure: FeatureFailure) {
        val status = when (failure.error) {
            FeatureError.NOT_FOUND -> 404
            FeatureError.UNAUTHORIZED -> 403
            FeatureError.CONFLICT -> 409
            FeatureError.INVALID_INPUT -> 400
        }
        ResponseEntity.status(status).header("Cache-Control", "no-store").body(AdminFeatureErrorResponse(failure.error, failure.detail))
    }
}
