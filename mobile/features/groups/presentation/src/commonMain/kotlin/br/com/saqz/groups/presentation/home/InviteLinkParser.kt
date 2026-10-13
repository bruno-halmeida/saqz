package br.com.saqz.groups.presentation.home

/**
 * Lê o convite de um texto colado na folha "Tenho um convite" da Início: o link completo
 * (`https://links.saqz.app/?saqz_invite=<código>`, `saqz://…?saqz_invite=<código>`) ou o código
 * solto. É o mesmo formato que os adapters de link nativos aceitam; aqui ele é validado antes de
 * ir ao coordinator, para a folha apontar o erro sem precisar de rede.
 *
 * O código é o curto do grupo (`44DF-8SA8`: 8 caracteres sem 0/O/1/I/L, em maiúscula ou
 * minúscula, com ou sem hífen/espaço) ou o token longo dos links já enviados antes dele. O retorno
 * é sempre a forma canônica: curto em maiúscula e sem hífen; longo como veio.
 *
 * Só convite de grupo. Link de presença (`saqz_attendance`) e de primeiro acesso
 * (`saqz_onboarding`) não são convite e voltam `null`.
 */
object InviteLinkParser {
    private const val INVITE_PARAMETER = "saqz_invite"
    private val longCode = Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")
    private val shortCode = Regex("[2-9A-HJKMNP-Z]{8}")

    fun parse(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        normalize(text)?.let { return it }
        val query = text.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        val candidates = query.split('&')
            .filter { it.substringBefore('=') == INVITE_PARAMETER }
            .map { it.substringAfter('=', missingDelimiterValue = "") }
        // Dois códigos no mesmo link é link adulterado, não convite: o adapter nativo recusa igual.
        val code = candidates.singleOrNull() ?: return null
        return normalize(code)
    }

    /** `44df-8sa8` → `44DF8SA8`; o token longo passa como está; o resto não é convite. */
    fun normalize(value: String): String? {
        if (longCode.matches(value)) return value
        val short = value.filterNot { it == '-' || it.isWhitespace() }.uppercase()
        return short.takeIf(shortCode::matches)
    }

    /** `44DF8SA8` → `44DF-8SA8` para mostrar; o que não é código curto volta como veio. */
    fun display(code: String): String =
        if (shortCode.matches(code)) "${code.substring(0, 4)}-${code.substring(4)}" else code
}
