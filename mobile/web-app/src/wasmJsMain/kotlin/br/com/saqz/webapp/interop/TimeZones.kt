package br.com.saqz.webapp.interop

import kotlinx.datetime.TimeZone

// Importar o módulo registra o banco de fusos no js-joda que o kotlinx-datetime usa por baixo.
@JsModule("@js-joda/timezone")
private external object JsJodaTimeZoneModule : JsAny

@JsFun("(module) => module != null")
private external fun isLoaded(module: JsAny): Boolean

/**
 * Carrega o banco de fusos antes de qualquer data passar pelo app. Precisa ser chamado: um
 * `@JsModule` que ninguém usa sai do bundle. Devolve o fuso do aparelho, para conferência.
 */
internal fun loadTimeZoneDatabase(): String {
    check(isLoaded(JsJodaTimeZoneModule)) { "@js-joda/timezone não carregou" }
    return TimeZone.currentSystemDefault().id
}
