package br.com.saqz.groups.presentation.home

/**
 * Lê o código de convite de um texto colado na folha "Tenho um convite" da Início: o link
 * completo (`https://links.saqz.app/?saqz_invite=<código>`, `saqz://…?saqz_invite=<código>`)
 * ou o código solto. É o mesmo formato que os adapters de link nativos aceitam; aqui ele é
 * validado antes de ir ao coordinator, para a folha apontar o erro sem precisar de rede.
 *
 * Só convite de grupo. Link de presença (`saqz_attendance`) e de primeiro acesso
 * (`saqz_onboarding`) não são convite e voltam `null`.
 */
object InviteLinkParser {
    private const val INVITE_PARAMETER = "saqz_invite"
    private val inviteCode = Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")

    fun parse(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (inviteCode.matches(text)) return text
        val query = text.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        val candidates = query.split('&')
            .filter { it.substringBefore('=') == INVITE_PARAMETER }
            .map { it.substringAfter('=', missingDelimiterValue = "") }
        // Dois códigos no mesmo link é link adulterado, não convite: o adapter nativo recusa igual.
        val code = candidates.singleOrNull() ?: return null
        return code.takeIf(inviteCode::matches)
    }
}
