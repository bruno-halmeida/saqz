package br.com.saqz.webapp.access

import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.port.GroupLinkEvent

/**
 * Leitura dos links do Saqz (convite, onboarding e presença), pura e sem DOM: é o espelho de
 * `AndroidLinkAdapter` e roda igual nos testes. Uma URL só é nossa se passa por [trustedLink].
 *
 * Regras que diferem do Android, por causa do navegador:
 * - o web app mora num subcaminho (`/app/`), então o código na query vale em qualquer path;
 * - o host é comparado com a autoridade inteira (`host[:porta]`) contra [allowedHosts], porque no dev o
 *   próprio origin é `localhost:8787`; qualquer outra porta continua rejeitada;
 * - `http` só em `localhost`/`127.0.0.1`;
 * - `/attendance/<código>` no path só vale no [linksHost] (a página de links manda sempre pela query).
 */
internal class WebLinkParser(allowedHosts: Set<String>, linksHost: String) {
    private val allowedAuthorities = allowedHosts.mapTo(mutableSetOf()) { it.lowercase() }
    private val linksAuthority = linksHost.lowercase()

    fun directEvent(url: String?): GroupLinkEvent? {
        val link = trustedLink(url) ?: return null
        val inviteEntries = link.query.filter { it.first == INVITE_PARAMETER }
        val attendanceEntries = link.query.filter { it.first == ATTENDANCE_PARAMETER }
        val intentEntries = link.query.filter { it.first == INTENT_PARAMETER }
        val intent = if (intentEntries.singleOrNull()?.second == DECLINE_INTENT) {
            AttendanceIntent.Decline
        } else {
            AttendanceIntent.Confirm
        }
        val inviteCodes = inviteEntries.map { it.second }.filter(::isValidInviteCode)
        val attendanceCodes = attendanceEntries.map { it.second }.filter(::isValidInviteCode)
        val duplicated = inviteEntries.size > 1 || attendanceEntries.size > 1 || intentEntries.size > 1
        val mixed = link.query.any { it.first == ONBOARDING_PARAMETER } ||
            (inviteCodes.isNotEmpty() && attendanceCodes.isNotEmpty())
        return when {
            duplicated || mixed -> null
            inviteCodes.size == 1 -> GroupLinkEvent.Invite(inviteCodes.single())
            attendanceCodes.size == 1 -> GroupLinkEvent.Attendance(attendanceCodes.single(), intent)
            else -> link.attendancePathCode?.let { GroupLinkEvent.Attendance(it, intent) }
        }
    }

    fun directOnboardingCode(url: String?): String? {
        val link = trustedLink(url) ?: return null
        val onboardingEntries = link.query.filter { it.first == ONBOARDING_PARAMETER }
        if (onboardingEntries.size != 1) return null
        val onboarding = onboardingEntries.map { it.second }.filter(::isValidInviteCode)
        val mixed = link.query.any { it.first == INVITE_PARAMETER || it.first == ATTENDANCE_PARAMETER }
        return if (mixed || onboarding.size != 1) null else onboarding.single()
    }

    /**
     * Endereço relativo (`path?query#hash`) da mesma página sem os parâmetros já consumidos, para a barra de
     * endereço não repetir o convite num reload. Preserva o pathname (ex.: `/app/`) e os demais parâmetros;
     * só o `/attendance/<código>` volta para `/`, porque o código estaria no próprio path.
     */
    fun cleanedAddress(url: String?): String? {
        val link = trustedLink(url) ?: return null
        val path = if (link.attendancePathCode != null) "/" else link.path
        val remaining = link.rawQuery?.split('&').orEmpty().filter { entry -> entry.isNotEmpty() && !isConsumed(entry) }
        return buildString {
            append(path)
            if (remaining.isNotEmpty()) append('?').append(remaining.joinToString("&"))
            if (!link.fragment.isNullOrEmpty()) append('#').append(link.fragment)
        }
    }

    /** Único ponto que decide se uma URL é um link nosso: https (ou http em loopback), host permitido, sem userinfo. */
    private fun trustedLink(url: String?): TrustedLink? = runCatching {
        val parsed = parseUrl(url ?: return null) ?: return null
        val secure = parsed.scheme == "https" || (parsed.scheme == "http" && parsed.hostName in LOOPBACK_HOSTS)
        if (!secure || parsed.authority !in allowedAuthorities) return null
        val query = parsed.rawQuery
            ?.split('&')
            ?.mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator < 0) return@mapNotNull null
                decode(entry.substring(0, separator), plusAsSpace = true) to
                    decode(entry.substring(separator + 1), plusAsSpace = true)
            }
            .orEmpty()
        val segments = decode(parsed.path, plusAsSpace = false).trim('/').split('/').filter(String::isNotBlank)
        val attendancePathCode = segments.getOrNull(1)?.takeIf {
            parsed.authority == linksAuthority &&
                segments.size == 2 && segments[0] == ATTENDANCE_PATH_SEGMENT && isValidInviteCode(it)
        }
        // /attendance/<código> não aceita código concorrente na query.
        if (attendancePathCode != null && query.any { it.first in CODE_PARAMETERS }) return null
        TrustedLink(query, attendancePathCode, parsed.path, parsed.rawQuery, parsed.fragment)
    }.getOrNull()

    private fun isConsumed(rawEntry: String): Boolean {
        val rawKey = rawEntry.substringBefore('=')
        val key = runCatching { decode(rawKey, plusAsSpace = true) }.getOrDefault(rawKey)
        return key in CONSUMED_PARAMETERS
    }

    private companion object {
        const val INVITE_PARAMETER = "saqz_invite"
        const val ATTENDANCE_PARAMETER = "saqz_attendance"
        const val ONBOARDING_PARAMETER = "saqz_onboarding"
        const val INTENT_PARAMETER = "saqz_intent"
        const val DECLINE_INTENT = "decline"
        const val ATTENDANCE_PATH_SEGMENT = "attendance"
        val CODE_PARAMETERS = setOf(INVITE_PARAMETER, ATTENDANCE_PARAMETER, ONBOARDING_PARAMETER)
        val CONSUMED_PARAMETERS = CODE_PARAMETERS + INTENT_PARAMETER
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1")
        val INVITE_CODE = Regex("[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]")

        fun isValidInviteCode(value: String?): Boolean = value?.matches(INVITE_CODE) == true
    }
}

private class TrustedLink(
    val query: List<Pair<String, String>>,
    val attendancePathCode: String?,
    val path: String,
    val rawQuery: String?,
    val fragment: String?,
)

private class ParsedUrl(
    val scheme: String,
    /** `host[:porta]` em minúsculas, exatamente como `window.location.host`. */
    val authority: String,
    val hostName: String,
    val path: String,
    val rawQuery: String?,
    val fragment: String?,
)

/** `scheme://authority/path?query#fragment`; nulo para o que não tem essa forma ou carrega userinfo/espaços. */
private fun parseUrl(url: String): ParsedUrl? {
    if (url.any { it <= ' ' || it == '\u007f' }) return null
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = url.substring(0, schemeEnd).lowercase()
    var rest = url.substring(schemeEnd + 3)
    val hashIndex = rest.indexOf('#')
    val fragment = if (hashIndex >= 0) rest.substring(hashIndex + 1) else null
    if (hashIndex >= 0) rest = rest.substring(0, hashIndex)
    val queryIndex = rest.indexOf('?')
    val rawQuery = if (queryIndex >= 0) rest.substring(queryIndex + 1) else null
    if (queryIndex >= 0) rest = rest.substring(0, queryIndex)
    val slashIndex = rest.indexOf('/')
    val authority = (if (slashIndex >= 0) rest.substring(0, slashIndex) else rest).lowercase()
    val path = if (slashIndex >= 0) rest.substring(slashIndex) else "/"
    if (authority.isEmpty() || '@' in authority) return null
    return ParsedUrl(scheme, authority, authority.substringBefore(':'), path, rawQuery, fragment)
}

/** `application/x-www-form-urlencoded` como o `URLDecoder` do Java: `%XX` em UTF-8, `+` vira espaço na query; `%` malformado lança. */
private fun decode(value: String, plusAsSpace: Boolean): String {
    if ('%' !in value && (!plusAsSpace || '+' !in value)) return value
    val out = ByteArray(value.length * 3)
    var size = 0
    var index = 0
    while (index < value.length) {
        val char = value[index]
        when {
            char == '%' -> {
                require(index + 2 < value.length) { "percent-encoding truncado" }
                val high = value[index + 1].digitToIntOrNull(16)
                val low = value[index + 2].digitToIntOrNull(16)
                require(high != null && low != null) { "percent-encoding inválido" }
                out[size++] = (high * 16 + low).toByte()
                index += 3
            }
            char == '+' && plusAsSpace -> {
                out[size++] = ' '.code.toByte()
                index++
            }
            else -> {
                val length = if (char.isHighSurrogate() && index + 1 < value.length) 2 else 1
                val bytes = value.substring(index, index + length).encodeToByteArray()
                bytes.copyInto(out, size)
                size += bytes.size
                index += length
            }
        }
    }
    return out.decodeToString(0, size)
}
