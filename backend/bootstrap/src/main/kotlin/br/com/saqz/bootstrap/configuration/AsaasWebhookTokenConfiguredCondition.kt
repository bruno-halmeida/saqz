package br.com.saqz.bootstrap.configuration

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * True when `saqz.asaas.webhook-token` is non-blank. Reads the Environment directly
 * so token characters never enter a SpEL expression string.
 */
class AsaasWebhookTokenConfiguredCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        isConfigured(context.environment.getProperty(PROPERTY))

    companion object {
        const val PROPERTY = "saqz.asaas.webhook-token"

        fun isConfigured(token: String?): Boolean = !token.isNullOrBlank()
    }
}
