package br.com.saqz.access.application.flags

/** Chaves das flags registradas. O app recebe exatamente estas chaves em `GET /api/features`. */
object FeatureKeys {
    const val CHAT = "chat"
    const val PURCHASES_ASAAS = "purchases_asaas"
    const val PURCHASES_APP_STORE = "purchases_app_store"
    const val PURCHASES_GOOGLE_PLAY = "purchases_google_play"
    const val WHATSAPP = "whatsapp"
}

/**
 * O registro das flags (FLG-001 e FLG-008). Toda flag nasce aqui: o adm só edita a regra das que
 * existem, e uma chave que sai daqui some do adm e do app no deploy seguinte (a linha no banco fica,
 * sem uso). Os valores iniciais são os de antes das flags, para nada mudar no dia da troca.
 *
 * [whatsAppConfigured] é `saqz.notifications.whatsapp.enabled`: o WhatsApp nasce como a variável de
 * cada ambiente (em produção, ligado).
 */
class FeatureCatalog(whatsAppConfigured: Boolean) {
    val definitions: List<FeatureFlagDefinition> = listOf(
        FeatureFlagDefinition(
            key = FeatureKeys.CHAT,
            description = "Chat do grupo",
            // Ainda não há rota de chat no backend: o registro já barra quando ela existir.
            backendEnforced = true,
            initialRule = FeatureRule(state = FeatureState.OFF),
        ),
        FeatureFlagDefinition(
            key = FeatureKeys.PURCHASES_ASAAS,
            description = "Assinatura pelo Asaas dentro do app (o site continua vendendo)",
            backendEnforced = true,
            initialRule = FeatureRule(state = FeatureState.OFF),
        ),
        FeatureFlagDefinition(
            key = FeatureKeys.PURCHASES_APP_STORE,
            description = "Assinatura pela App Store (desligar só esconde o botão; a validação da compra nunca é barrada)",
            backendEnforced = false,
            initialRule = FeatureRule(state = FeatureState.ALL, android = false, ios = true),
        ),
        FeatureFlagDefinition(
            key = FeatureKeys.PURCHASES_GOOGLE_PLAY,
            description = "Assinatura pelo Google Play (desligar só esconde o botão; a validação da compra nunca é barrada)",
            backendEnforced = false,
            initialRule = FeatureRule(state = FeatureState.ALL, android = true, ios = false),
        ),
        FeatureFlagDefinition(
            key = FeatureKeys.WHATSAPP,
            description = "WhatsApp do Saqz: vínculo com o grupo, aba das preferências e envios",
            backendEnforced = true,
            initialRule = FeatureRule(state = if (whatsAppConfigured) FeatureState.ALL else FeatureState.OFF),
            initialReason = "Valor inicial da variável de ambiente (saqz.notifications.whatsapp.enabled=$whatsAppConfigured)",
        ),
    )

    private val byKey = definitions.associateBy { it.key }

    init {
        require(byKey.size == definitions.size) { "Chave de flag repetida" }
        definitions.forEach { FeatureRules.validate(it, it.initialRule) }
    }

    fun find(key: String): FeatureFlagDefinition? = byKey[key]
}
