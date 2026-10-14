package br.com.saqz.sharedkernel.client

/**
 * Atributo da requisição onde o filtro do bootstrap deixa o [ClientApp] de quem chamou. Quem precisar
 * decidir pela versão do app (por exemplo, responder no formato antigo) lê daqui; sem o atributo, a
 * chamada é [ClientApp.Legacy].
 */
const val CLIENT_APP_REQUEST_ATTRIBUTE = "br.com.saqz.clientApp"

/** As plataformas que o app informa em `X-Saqz-Platform`. */
enum class ClientPlatform(val wire: String) {
    ANDROID("android"),
    IOS("ios"),
    WEB("web"),
    ;

    companion object {
        fun fromWire(raw: String?): ClientPlatform? {
            val value = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.wire == value }
        }
    }
}

/**
 * A versão publicada do app, `X.Y.Z` (versionName no Android, CFBundleShortVersionString no iOS).
 * Comparável, para decisões como "app mais velho que 1.0.3".
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        const val MAX_LENGTH = 32
        private val FORMAT = Regex("""(\d+)\.(\d+)\.(\d+)""")

        /** Só o formato exato `X.Y.Z` com até [MAX_LENGTH] caracteres; qualquer outra coisa vira `null`. */
        fun parse(raw: String?): AppVersion? {
            if (raw == null || raw.length > MAX_LENGTH) return null
            val (major, minor, patch) = FORMAT.matchEntire(raw)?.destructured ?: return null
            return AppVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}

/**
 * Quem está chamando a API, segundo os cabeçalhos `X-Saqz-Platform`, `X-Saqz-App-Version` e
 * `X-Saqz-App-Build` que o app manda em toda requisição.
 *
 * [Legacy] é o cliente que não se identifica: o app anterior a estes cabeçalhos (o build 9 das lojas)
 * não manda nada, e valor fora do padrão é descartado em vez de recusado. Nunca lança: cabeçalho ruim
 * não derruba chamada.
 */
sealed interface ClientApp {
    /** Texto curto para o log (`ios/1.0.3(14)`); `-` quando o cliente não se identificou. */
    val label: String

    val isLegacy: Boolean get() = this is Legacy

    data object Legacy : ClientApp {
        override val label: String = "-"
    }

    /** [version] e [build] ficam `null` quando o app não os mandou ou mandou fora do padrão. */
    data class Identified(
        val platform: ClientPlatform,
        val version: AppVersion? = null,
        val build: Int? = null,
    ) : ClientApp {
        override val label: String = buildString {
            append(platform.wire)
            version?.let { append('/').append(it) }
            build?.let { append('(').append(it).append(')') }
        }
    }

    companion object {
        /** Só é [Identified] com plataforma válida; sem ela não há como saber de que app veio. */
        fun fromHeaders(platform: String?, version: String?, build: String?): ClientApp {
            val knownPlatform = ClientPlatform.fromWire(platform) ?: return Legacy
            return Identified(knownPlatform, AppVersion.parse(version), parseBuild(build))
        }

        /** O valor de `request.getAttribute(`[CLIENT_APP_REQUEST_ATTRIBUTE]`)`; sem ele a chamada é [Legacy]. */
        fun fromRequestAttribute(attribute: Any?): ClientApp = attribute as? ClientApp ?: Legacy

        // Só dígitos e até 9: cabe num Int e cobre o CFBundleVersion e o versionCode reais com folga.
        private val BUILD_FORMAT = Regex("""\d{1,9}""")

        private fun parseBuild(raw: String?): Int? =
            raw?.takeIf { BUILD_FORMAT.matches(it) }?.toInt()
    }
}
