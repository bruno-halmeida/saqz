package br.com.saqz.network

/** Mesmos nomes que o `ClientAppFilter` do backend lê. */
internal const val PLATFORM_HEADER = "X-Saqz-Platform"
internal const val APP_VERSION_HEADER = "X-Saqz-App-Version"
internal const val APP_BUILD_HEADER = "X-Saqz-App-Build"

private val PLATFORMS = setOf("android", "ios")
private const val MAX_VERSION_LENGTH = 32
private const val MAX_BUILD_DIGITS = 9

/**
 * Quem está chamando a API: a plataforma, a versão e o build do app instalado. Vira os cabeçalhos
 * `X-Saqz-Platform`, `X-Saqz-App-Version` e `X-Saqz-App-Build` de toda requisição, para o backend saber
 * qual versão do app fez cada chamada (achar a versão com problema e, no futuro, responder no formato
 * certo para um app antigo).
 *
 * Valor fora do padrão que o backend aceita não quebra a chamada: só o cabeçalho dele é omitido.
 *  - [platform]: `android` ou `ios`;
 *  - [version]: `X.Y.Z` com até 32 caracteres (versionName no Android, CFBundleShortVersionString no iOS);
 *  - [build]: só dígitos (versionCode no Android, CFBundleVersion no iOS); `null` onde a plataforma não tem.
 */
data class AppClient(
    val platform: String,
    val version: String,
    val build: String? = null,
) {
    internal fun headers(): Map<String, String> = buildMap {
        if (platform in PLATFORMS) put(PLATFORM_HEADER, platform)
        if (version.isVersion()) put(APP_VERSION_HEADER, version)
        if (build != null && build.isBuild()) put(APP_BUILD_HEADER, build)
    }
}

// Sem regex: `\d` pode casar dígitos que não são ASCII conforme o motor (JVM, Native, JS), e o backend só aceita 0-9.
private fun String.isVersion(): Boolean {
    if (length > MAX_VERSION_LENGTH) return false
    val parts = split('.')
    return parts.size == 3 && parts.all { it.isNotEmpty() && it.all(Char::isAsciiDigit) }
}

private fun String.isBuild(): Boolean = length in 1..MAX_BUILD_DIGITS && all(Char::isAsciiDigit)

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
