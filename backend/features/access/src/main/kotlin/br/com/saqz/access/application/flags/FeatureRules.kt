package br.com.saqz.access.application.flags

import br.com.saqz.sharedkernel.client.AppVersion
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/** Avaliação (FLG-002) e validação das regras. Sem banco: quem chama traz a regra e a pessoa. */
object FeatureRules {
    private const val MAX_TEXT = 500
    private const val MAX_LIST = 500
    private const val MAX_VARIANTS = 4

    /**
     * Faixa estável da pessoa na flag: `hash(userId + chave) mod 100`. Não depende do aparelho (mesma
     * faixa no Android e no iPhone) e cada flag tem a sua (os 10% de uma não são os 10% de outra).
     */
    fun bucket(userId: UUID, key: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest("$userId:$key".toByteArray(Charsets.UTF_8))
        return Math.floorMod(ByteBuffer.wrap(digest, 0, 8).long, 100L).toInt()
    }

    /**
     * A ordem do FLG-002:
     *  1. interruptor da plataforma (sem plataforma conhecida, vale só com os dois ligados);
     *  2. listas: "sempre desligada" vence "sempre ligada" (as duas só depois do login);
     *  3. estado: desligada, todos, ou público;
     *  4. público: todos os critérios juntos (antes do login, público nunca casa);
     *  5. porcentagem pela faixa da pessoa.
     */
    fun enabled(key: String, rule: FeatureRule, subject: FeatureSubject): Boolean {
        val switchOn = when (subject.platform) {
            FeaturePlatform.ANDROID -> rule.android
            FeaturePlatform.IOS -> rule.ios
            null -> rule.android && rule.ios
        }
        if (!switchOn) return false
        val profile = subject.profile
        if (profile != null) {
            if (rule.alwaysOff.contains(profile)) return false
            if (rule.alwaysOn.contains(profile)) return true
        }
        return when (rule.state) {
            FeatureState.OFF -> false
            FeatureState.ALL -> true
            FeatureState.AUDIENCE -> profile != null && matches(rule.audience, subject, profile) &&
                bucket(profile.userId, key) < rule.percentage
        }
    }

    fun evaluate(definition: FeatureFlagDefinition, rule: FeatureRule, subject: FeatureSubject): FeatureValue {
        val enabled = enabled(definition.key, rule, subject)
        val values = if (enabled) definition.defaultValues + rule.values else definition.defaultValues
        return FeatureValue(enabled, values)
    }

    private fun FeatureMembers.contains(profile: AudienceProfile) =
        profile.userId in users || groups.any { it in profile.groups }

    private fun matches(audience: FeatureAudience, subject: FeatureSubject, profile: AudienceProfile): Boolean {
        if (audience.platforms.isNotEmpty() && subject.platform !in audience.platforms) return false
        val min = audience.minVersion?.let(AppVersion::parse)
        val max = audience.maxVersion?.let(AppVersion::parse)
        if (min != null || max != null) {
            val version = subject.version ?: return false
            if (min != null && version < min) return false
            if (max != null && version > max) return false
        }
        if (audience.plans.isNotEmpty() && profile.plan !in audience.plans) return false
        if (audience.subscriptionStatuses.isNotEmpty() && profile.subscriptionStatus !in audience.subscriptionStatuses) return false
        if (audience.roles.isNotEmpty() && profile.role !in audience.roles) return false
        return true
    }

    /** Valida a forma da regra; grupos e pessoas que existem e listas cruzadas ficam com o banco. */
    fun validate(definition: FeatureFlagDefinition, rule: FeatureRule) {
        fun invalid(detail: String): Nothing = throw FeatureFailure(FeatureError.INVALID_INPUT, detail)
        if (rule.percentage !in 0..100) invalid("A porcentagem vai de 0 a 100")
        val min = rule.audience.minVersion?.let { AppVersion.parse(it) ?: invalid("Versão mínima fora do formato N.N.N") }
        val max = rule.audience.maxVersion?.let { AppVersion.parse(it) ?: invalid("Versão máxima fora do formato N.N.N") }
        if (min != null && max != null && min > max) invalid("A versão mínima passa da máxima")
        for (members in listOf(rule.alwaysOn, rule.alwaysOff)) {
            if (members.users.size > MAX_LIST || members.groups.size > MAX_LIST) invalid("Lista grande demais")
            if (members.users.toSet().size != members.users.size || members.groups.toSet().size != members.groups.size) {
                invalid("Pessoa ou grupo repetido na lista")
            }
        }
        if (rule.alwaysOn.users.any { it in rule.alwaysOff.users } || rule.alwaysOn.groups.any { it in rule.alwaysOff.groups }) {
            invalid("A mesma pessoa não pode estar em \"sempre ligada\" e \"sempre desligada\"")
        }
        validateValues(definition, rule.values)
        if (rule.variants.size > MAX_VARIANTS) invalid("No máximo 4 variantes")
        // Experimentos são da DEB-121: o formato guarda variantes, mas a avaliação ainda não as usa.
        if (rule.variants.isNotEmpty()) invalid("Experimentos ainda não estão disponíveis")
    }

    fun validateValues(definition: FeatureFlagDefinition, values: Map<String, Any>) {
        fun invalid(detail: String): Nothing = throw FeatureFailure(FeatureError.INVALID_INPUT, detail)
        values.forEach { (key, value) ->
            val parameter = definition.parameters.firstOrNull { it.key == key } ?: invalid("Parâmetro desconhecido: $key")
            when (parameter.type) {
                FeatureParameterType.NUMBER -> {
                    val number = value as? Long ?: invalid("O parâmetro $key é um número inteiro")
                    if (parameter.min != null && number < parameter.min) invalid("O parâmetro $key começa em ${parameter.min}")
                    if (parameter.max != null && number > parameter.max) invalid("O parâmetro $key vai até ${parameter.max}")
                }
                FeatureParameterType.TEXT -> {
                    val text = value as? String ?: invalid("O parâmetro $key é um texto")
                    if (text.length > MAX_TEXT) invalid("O parâmetro $key tem até $MAX_TEXT caracteres")
                }
                FeatureParameterType.CHOICE -> if (value !in parameter.choices) invalid("Opção inválida para $key")
            }
        }
    }

    /** "Versão" no formato do `X-Saqz-App-Version`; nulo ou vazio = sem versão. */
    fun normalizeVersion(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return AppVersion.parse(value)?.toString() ?: throw FeatureFailure(FeatureError.INVALID_INPUT, "Versão fora do formato N.N.N")
    }
}
