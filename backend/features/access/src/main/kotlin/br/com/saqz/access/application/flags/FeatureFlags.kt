package br.com.saqz.access.application.flags

import br.com.saqz.sharedkernel.client.ClientApp
import br.com.saqz.sharedkernel.client.ClientPlatform
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Persistência das flags; cada escrita grava a linha do histórico na mesma transação. */
interface FeatureFlagStore {
    /** Cria a linha das flags que ainda não têm regra; devolve as chaves criadas. */
    fun ensure(definitions: List<FeatureFlagDefinition>, now: Instant): List<String>
    fun flags(): Map<String, StoredFeatureFlag>
    fun userIdBySubject(subject: String): UUID?
    fun profile(userId: UUID): AudienceProfile?
    fun usersExist(ids: Collection<UUID>): Boolean
    fun groupMembers(names: Collection<String>): Map<String, Set<UUID>>
    fun changeFlag(change: FlagChange): StoredFeatureFlag
    fun groups(): List<FeatureGroup>
    fun changeGroup(change: GroupChange): FeatureGroup
    fun searchUsers(query: String): List<FeatureGroupMember>
    fun appVersions(): AppVersions
    fun changeAppVersions(change: AppVersionsChange): AppVersions
    fun history(type: FeatureHistoryType?, key: String?, page: Int, size: Int): FeatureHistoryPage
    fun historyEntry(id: UUID): FeatureHistoryEntry?
}

/** [validate] roda dentro da transação, depois da trava das escritas de flags e grupos. */
data class FlagChange(
    val id: UUID, val actor: UUID, val key: String, val expectedVersion: Long, val reason: String,
    val rule: FeatureRule, val now: Instant, val validate: () -> Unit,
)
/** [expectedVersion] 0 cria o grupo. [validate] recebe os membros novos, dentro da mesma trava. */
data class GroupChange(
    val id: UUID, val actor: UUID, val name: String, val expectedVersion: Long, val reason: String,
    val members: List<UUID>, val now: Instant, val validate: (Set<UUID>) -> Unit,
)
data class AppVersionsChange(
    val id: UUID, val actor: UUID, val expectedVersion: Long, val reason: String,
    val android: PlatformAppVersions, val ios: PlatformAppVersions, val now: Instant,
)

/** Aviso ao vivo (FLG-003): o app escuta este sinal e busca `GET /api/features` de novo. */
fun interface FeatureChangeNotifier { fun published(signal: String) }

enum class FeatureAlert { YELLOW, ORANGE, RED }
data class AdminFeatureFlag(
    val key: String,
    val description: String,
    val backendEnforced: Boolean,
    val parameters: List<FeatureParameter>,
    val version: Long,
    val rule: FeatureRule,
    val fullRolloutSince: Instant?,
    val alert: FeatureAlert?,
    val experimentRunning: Boolean,
    val updatedAt: Instant,
)

data class PersonFeatures(
    val flags: Map<String, FeatureValue>,
    val platform: FeaturePlatform?,
    val appVersions: PlatformAppVersions?,
)

/**
 * As flags (DEB-119): o que vale para cada pessoa, a edição pelo adm com motivo, versão esperada e
 * histórico, e o aviso ao vivo depois de cada publicação.
 */
class FeatureFlags(
    private val catalog: FeatureCatalog,
    private val store: FeatureFlagStore,
    private val notifier: FeatureChangeNotifier,
    private val clock: Clock,
) {
    /** Grava a regra inicial das flags novas do registro. Roda na subida. */
    fun ensureCatalog(): List<String> = store.ensure(catalog.definitions, clock.instant())

    fun subject(userId: UUID?, client: ClientApp): FeatureSubject {
        val identified = client as? ClientApp.Identified
        val platform = when (identified?.platform) {
            ClientPlatform.ANDROID -> FeaturePlatform.ANDROID
            ClientPlatform.IOS -> FeaturePlatform.IOS
            else -> null
        }
        return FeatureSubject(platform, identified?.version?.takeIf { platform != null }, userId?.let(store::profile))
    }

    fun userIdBySubject(subject: String): UUID? = store.userIdBySubject(subject)

    /** `GET /api/features`: só os valores da pessoa, nada de regra, lista ou porcentagem. */
    fun forPerson(userId: UUID?, client: ClientApp): PersonFeatures {
        val subject = subject(userId, client)
        val rules = store.flags()
        val flags = catalog.definitions.associate { definition ->
            definition.key to (rules[definition.key]?.let { FeatureRules.evaluate(definition, it.rule, subject) }
                ?: FeatureValue(definition.defaultEnabled, definition.defaultValues))
        }
        return PersonFeatures(flags, subject.platform, subject.platform?.let { store.appVersions().of(it) })
    }

    /** Se a flag está ligada para a pessoa nesta chamada. Flag sem regra gravada vale o padrão (desligada). */
    fun enabledFor(key: String, userId: UUID?, client: ClientApp): Boolean {
        val rule = store.flags()[key]?.rule ?: return false
        return FeatureRules.enabled(key, rule, subject(userId, client))
    }

    /**
     * Para o que o servidor faz sem uma pessoa do outro lado (ex.: a fila de envios do WhatsApp): ligada
     * quando o estado não é "desligada" e ao menos um interruptor de plataforma está ligado.
     */
    fun enabledOnServer(key: String): Boolean {
        val rule = store.flags()[key]?.rule ?: return false
        return rule.state != FeatureState.OFF && (rule.android || rule.ios)
    }

    fun adminList(): List<AdminFeatureFlag> {
        val rules = store.flags()
        val now = clock.instant()
        return catalog.definitions.mapNotNull { definition -> rules[definition.key]?.let { admin(definition, it, now) } }
            .sortedWith(compareByDescending<AdminFeatureFlag> { it.alert?.ordinal ?: -1 }.thenBy { it.key })
    }

    fun adminFlag(key: String): AdminFeatureFlag {
        val definition = definition(key)
        val stored = store.flags()[key] ?: throw FeatureFailure(FeatureError.NOT_FOUND)
        return admin(definition, stored, clock.instant())
    }

    fun changeFlag(id: UUID, actor: UUID, key: String, expectedVersion: Long, reason: String, rule: FeatureRule): AdminFeatureFlag {
        val definition = definition(key)
        val trimmed = reason(reason)
        FeatureRules.validate(definition, rule)
        val stored = store.changeFlag(FlagChange(id, actor, key, expectedVersion, trimmed, rule, clock.instant()) { validateMembers(rule) })
        publish("flag:$key:${stored.version}")
        return admin(definition, stored, clock.instant())
    }

    /** "Voltar para esta versão": a regra daquela linha vira uma mudança nova, com motivo. */
    fun revertFlag(id: UUID, actor: UUID, key: String, historyId: UUID, expectedVersion: Long, reason: String,
        decode: (Any) -> FeatureRule): AdminFeatureFlag {
        val entry = store.historyEntry(historyId)
        if (entry == null || entry.type != FeatureHistoryType.FLAG || entry.key != key) throw FeatureFailure(FeatureError.NOT_FOUND)
        return changeFlag(id, actor, key, expectedVersion, reason, decode(entry.after))
    }

    fun groups(): List<FeatureGroup> = store.groups()

    fun searchUsers(query: String): List<FeatureGroupMember> {
        val trimmed = query.trim()
        if (trimmed.length < 3) throw FeatureFailure(FeatureError.INVALID_INPUT, "Digite ao menos 3 caracteres")
        return store.searchUsers(trimmed)
    }

    fun changeGroup(id: UUID, actor: UUID, name: String, expectedVersion: Long, reason: String, members: List<UUID>): FeatureGroup {
        if (!Regex("^[a-z0-9][a-z0-9_-]{1,39}$").matches(name)) {
            throw FeatureFailure(FeatureError.INVALID_INPUT, "Nome do grupo: 2 a 40 letras minúsculas, números, - ou _")
        }
        val trimmed = reason(reason)
        if (members.toSet().size != members.size || members.size > 5000) throw FeatureFailure(FeatureError.INVALID_INPUT, "Pessoa repetida")
        if (!store.usersExist(members)) throw FeatureFailure(FeatureError.INVALID_INPUT, "Pessoa não encontrada")
        val group = store.changeGroup(GroupChange(id, actor, name, expectedVersion, trimmed, members, clock.instant()) { newMembers ->
            // A mesma pessoa não pode ficar nas duas listas de uma flag por causa do grupo novo.
            store.flags().values.forEach { flag ->
                if (name in flag.rule.alwaysOn.groups || name in flag.rule.alwaysOff.groups) {
                    validateMembers(flag.rule, mapOf(name to newMembers))
                }
            }
        })
        publish("group:$name:${group.version}")
        return group
    }

    fun appVersions(): AppVersions = store.appVersions()

    fun changeAppVersions(id: UUID, actor: UUID, expectedVersion: Long, reason: String,
        android: PlatformAppVersions, ios: PlatformAppVersions): AppVersions {
        val trimmed = reason(reason)
        val normalized = listOf(android, ios).map { PlatformAppVersions(FeatureRules.normalizeVersion(it.minimum), FeatureRules.normalizeVersion(it.recommended)) }
        val versions = store.changeAppVersions(AppVersionsChange(id, actor, expectedVersion, trimmed, normalized[0], normalized[1], clock.instant()))
        publish("app-versions:${versions.version}")
        return versions
    }

    fun history(type: FeatureHistoryType?, key: String?, page: Int, size: Int): FeatureHistoryPage {
        if (page < 1 || size !in 1..100) throw FeatureFailure(FeatureError.INVALID_INPUT)
        return store.history(type, key, page, size)
    }

    private fun definition(key: String) = catalog.find(key) ?: throw FeatureFailure(FeatureError.NOT_FOUND)

    private fun reason(reason: String): String = reason.trim().takeIf { it.length in 3..500 }
        ?: throw FeatureFailure(FeatureError.INVALID_INPUT, "Motivo obrigatório, de 3 a 500 caracteres")

    /** Pessoas e grupos das listas existem, e ninguém fica nas duas (também através dos grupos). */
    private fun validateMembers(rule: FeatureRule, override: Map<String, Set<UUID>> = emptyMap()) {
        val users = rule.alwaysOn.users + rule.alwaysOff.users
        if (!store.usersExist(users)) throw FeatureFailure(FeatureError.INVALID_INPUT, "Pessoa não encontrada")
        val names = rule.alwaysOn.groups + rule.alwaysOff.groups
        val members = store.groupMembers(names) + override
        if (names.any { it !in members }) throw FeatureFailure(FeatureError.INVALID_INPUT, "Grupo não encontrado")
        fun people(list: FeatureMembers) = list.users.toSet() + list.groups.flatMap { members[it].orEmpty() }
        if (people(rule.alwaysOn).any { it in people(rule.alwaysOff) }) {
            throw FeatureFailure(FeatureError.INVALID_INPUT, "A mesma pessoa não pode estar em \"sempre ligada\" e \"sempre desligada\"")
        }
    }

    private fun publish(signal: String) {
        // Falha no aviso não desfaz a mudança: o app também busca ao abrir e ao voltar.
        runCatching { notifier.published(signal) }
    }

    private fun admin(definition: FeatureFlagDefinition, stored: StoredFeatureFlag, now: Instant): AdminFeatureFlag {
        val since = stored.fullRolloutSince
        val days = since?.let { Duration.between(it, now).toDays() } ?: -1
        val alert = when {
            stored.rule.alertDisabled || since == null || !stored.rule.fullRollout() -> null
            days >= 90 -> FeatureAlert.RED
            days >= 60 -> FeatureAlert.ORANGE
            days >= 30 -> FeatureAlert.YELLOW
            else -> null
        }
        return AdminFeatureFlag(definition.key, definition.description, definition.backendEnforced, definition.parameters,
            stored.version, stored.rule, since, alert, stored.rule.variants.isNotEmpty(), stored.updatedAt)
    }
}
