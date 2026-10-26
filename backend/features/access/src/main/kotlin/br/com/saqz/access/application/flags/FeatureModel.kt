package br.com.saqz.access.application.flags

import br.com.saqz.sharedkernel.client.AppVersion
import java.time.Instant
import java.util.UUID

/** As duas plataformas em que as flags valem (FLG-001): o web app saiu em 21/10. */
enum class FeaturePlatform { ANDROID, IOS }

/** Estado da regra (FLG-002): desligada (só a lista "sempre ligada"), ligada para o público ou para todos. */
enum class FeatureState { OFF, AUDIENCE, ALL }

/** Plano da pessoa, de `owner_effective_plans`: TRIAL é o teste do Organizador, NONE é sem plano. */
enum class AudiencePlan { TITULAR, ORGANIZADOR, ILIMITADO, TRIAL, NONE }

enum class AudienceSubscriptionStatus { ACTIVE, PAST_DUE, CANCELED }

/** O maior papel da pessoa em qualquer grupo, na ordem da declaração (dono vence admin, que vence atleta). */
enum class AudienceRole { OWNER, ADMIN, ATHLETE, NONE }

enum class FeatureParameterType { NUMBER, TEXT, CHOICE }

/**
 * Parâmetro de uma flag (FLG-001): número (ex.: dias de teste), texto ou escolha entre opções. O valor
 * é [Long] para NUMBER e [String] para os outros.
 */
data class FeatureParameter(
    val key: String,
    val type: FeatureParameterType,
    val default: Any,
    val description: String,
    val choices: List<String> = emptyList(),
    val min: Long? = null,
    val max: Long? = null,
)

/**
 * Uma flag registrada no código. [backendEnforced] diz se o backend também recusa as ações novas da
 * feature com `FEATURE_DISABLED` quando ela está desligada para a pessoa (FLG-003).
 */
data class FeatureFlagDefinition(
    val key: String,
    val description: String,
    val backendEnforced: Boolean,
    val parameters: List<FeatureParameter> = emptyList(),
    /** Regra gravada na primeira subida sem regra no banco, com [initialReason] no histórico. */
    val initialRule: FeatureRule,
    val initialReason: String = "Valor inicial, igual ao de antes das flags",
) {
    /** A flag sempre nasce desligada no app; o valor padrão dos parâmetros vale para quem não a recebe. */
    val defaultEnabled: Boolean get() = false
    val defaultValues: Map<String, Any> get() = parameters.associate { it.key to it.default }
}

data class FeatureMembers(val users: List<UUID> = emptyList(), val groups: List<String> = emptyList()) {
    val isEmpty: Boolean get() = users.isEmpty() && groups.isEmpty()
}

/** Critérios do público: todos valem juntos; lista vazia ou versão nula não filtram. */
data class FeatureAudience(
    val platforms: List<FeaturePlatform> = emptyList(),
    val minVersion: String? = null,
    val maxVersion: String? = null,
    val plans: List<AudiencePlan> = emptyList(),
    val subscriptionStatuses: List<AudienceSubscriptionStatus> = emptyList(),
    val roles: List<AudienceRole> = emptyList(),
)

/** Variante de experimento (FLG-005). O formato já existe; quem usa é a DEB-121. */
data class FeatureVariant(val key: String, val weight: Int, val values: Map<String, Any> = emptyMap())

/** A regra única de uma flag (FLG-002), na ordem em que é avaliada. */
data class FeatureRule(
    val android: Boolean = true,
    val ios: Boolean = true,
    val alwaysOff: FeatureMembers = FeatureMembers(),
    val alwaysOn: FeatureMembers = FeatureMembers(),
    val state: FeatureState = FeatureState.OFF,
    val audience: FeatureAudience = FeatureAudience(),
    val percentage: Int = 100,
    /** Valores dos parâmetros para quem recebe a flag; parâmetro ausente vale o padrão. */
    val values: Map<String, Any> = emptyMap(),
    val variants: List<FeatureVariant> = emptyList(),
    /** Desliga o alerta de flag antiga (FLG-004), para interruptores permanentes. */
    val alertDisabled: Boolean = false,
) {
    /** 100% nas duas plataformas: é o que conta para os alertas de 30/60/90 dias. */
    val fullRollout: Boolean get() = state == FeatureState.ALL && android && ios
}

data class StoredFeatureFlag(
    val key: String,
    val version: Long,
    val rule: FeatureRule,
    val fullRolloutSince: Instant?,
    val updatedAt: Instant,
)

/** O que se sabe da pessoa para avaliar o público e as listas. */
data class AudienceProfile(
    val userId: UUID,
    val plan: AudiencePlan,
    val subscriptionStatus: AudienceSubscriptionStatus?,
    val role: AudienceRole,
    val groups: Set<String>,
)

/**
 * Quem pergunta. [platform] e [version] vêm do `ClientApp`: sem cabeçalho (ou web), ficam nulos e os
 * critérios de plataforma e versão não casam. [profile] nulo = antes do login.
 */
data class FeatureSubject(val platform: FeaturePlatform?, val version: AppVersion?, val profile: AudienceProfile?)

data class FeatureValue(val enabled: Boolean, val values: Map<String, Any>)

data class PlatformAppVersions(val minimum: String?, val recommended: String?)
data class AppVersions(val version: Long, val android: PlatformAppVersions, val ios: PlatformAppVersions) {
    fun of(platform: FeaturePlatform) = if (platform == FeaturePlatform.ANDROID) android else ios
}

data class FeatureGroup(val name: String, val version: Long, val members: List<FeatureGroupMember>, val updatedAt: Instant)
data class FeatureGroupMember(val userId: UUID, val displayName: String?, val email: String?, val phone: String?)

enum class FeatureHistoryType { FLAG, GROUP, APP_VERSIONS }
data class FeatureHistoryEntry(
    val id: UUID,
    val type: FeatureHistoryType,
    val key: String,
    val version: Long,
    val actorUserId: UUID?,
    val actorName: String?,
    val reason: String,
    val createdAt: Instant,
    val before: Any?,
    val after: Any,
)
data class FeatureHistoryPage(val page: Int, val size: Int, val total: Long, val items: List<FeatureHistoryEntry>)

enum class FeatureError { INVALID_INPUT, NOT_FOUND, CONFLICT, UNAUTHORIZED }
class FeatureFailure(val error: FeatureError, val detail: String = error.name) : RuntimeException(detail)
