package br.com.saqz.groups.data.setup

import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.datetime.TimeZone

/**
 * Validação de fuso contra a base IANA. Fica em `data` — e não no domínio — porque a base de fusos
 * é um recurso de plataforma/biblioteca, do mesmo jeito que uma resposta HTTP.
 */
object GroupTimeZones {
    fun parse(raw: String): GroupTimeZone.ParseResult = runCatching { TimeZone.of(raw) }
        .fold(
            onSuccess = { GroupTimeZone.ParseResult.Valid(GroupTimeZone(it.id)) },
            onFailure = { GroupTimeZone.ParseResult.Invalid },
        )
}

class DefaultGroupSystemTimeZonePort : GroupSystemTimeZonePort {
    override fun detect(done: (GroupSystemTimeZoneResult) -> Unit) {
        val result = runCatching { TimeZone.currentSystemDefault().id }
            .fold(
                onSuccess = { raw ->
                    when (val parsed = GroupTimeZones.parse(raw)) {
                        is GroupTimeZone.ParseResult.Valid -> GroupSystemTimeZoneResult.Available(parsed.value)
                        GroupTimeZone.ParseResult.Invalid -> GroupSystemTimeZoneResult.Unavailable
                    }
                },
                onFailure = { GroupSystemTimeZoneResult.Unavailable },
            )
        done(result)
    }
}
