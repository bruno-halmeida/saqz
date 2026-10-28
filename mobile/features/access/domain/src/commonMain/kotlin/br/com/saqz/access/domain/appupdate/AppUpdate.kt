package br.com.saqz.access.domain.appupdate

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

/**
 * As duas versões que o adm define por plataforma (FLG-007), como chegam em `appVersion` de
 * `GET /api/features`: `"N.N.N"` ou `null`. Texto cru de propósito: quem decide se o formato vale é
 * [decideAppUpdate], que trata versão fora do formato como ausente.
 */
data class AppUpdateVersions(
    val minimum: String?,
    val recommended: String?,
)

/** O que o pop-up mostra: nada, a folha que dá para fechar ou a que não fecha. */
sealed interface AppUpdatePrompt {
    data object None : AppUpdatePrompt

    /** Abaixo da recomendada; [version] é o que se guarda ao dispensar ("uma vez por versão recomendada"). */
    data class Recommended(val version: String) : AppUpdatePrompt

    /** Abaixo da mínima: não fecha. Vence a recomendada. */
    data object Required : AppUpdatePrompt
}

/**
 * A decisão da FLG-007, pura. Versão instalada fora de `N.N.N` nunca mostra nada (build de
 * desenvolvimento não pode se trancar); mínima ou recomendada fora do formato contam como ausentes.
 */
fun decideAppUpdate(
    installed: String,
    minimum: String?,
    recommended: String?,
    isRecommendedDismissed: (String) -> Boolean,
): AppUpdatePrompt {
    val current = AppVersionNumber.parse(installed) ?: return AppUpdatePrompt.None
    val min = minimum?.let(AppVersionNumber::parse)
    if (min != null && current < min) return AppUpdatePrompt.Required
    val rec = recommended?.let(AppVersionNumber::parse)
    return if (rec != null && current < rec && !isRecommendedDismissed(rec.text)) {
        AppUpdatePrompt.Recommended(rec.text)
    } else {
        AppUpdatePrompt.None
    }
}

/** `N.N.N` comparado parte a parte, como número: `1.0.10` > `1.0.9`. */
class AppVersionNumber private constructor(
    private val parts: List<Int>,
    val text: String,
) : Comparable<AppVersionNumber> {
    override fun compareTo(other: AppVersionNumber): Int =
        parts.zip(other.parts).firstOrNull { (a, b) -> a != b }?.let { (a, b) -> a.compareTo(b) } ?: 0

    companion object {
        private val Format = Regex("""^\d{1,6}\.\d{1,6}\.\d{1,6}$""")

        fun parse(value: String): AppVersionNumber? {
            val trimmed = value.trim()
            if (!Format.matches(trimmed)) return null
            return AppVersionNumber(trimmed.split('.').map(String::toInt), trimmed)
        }
    }
}

sealed interface AppUpdateError : SaqzError {
    data class Data(val error: DataError) : AppUpdateError
}

/** Lê só `appVersion` de `GET /api/features`, sem login (DEB-117). */
interface AppUpdateGateway {
    suspend fun versions(): SaqzResult<AppUpdateVersions, AppUpdateError>
}
