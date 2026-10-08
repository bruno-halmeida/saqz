package br.com.saqz.androidapp.access

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Convite e presença que atravessam a instalação. Sem o app, a página de links manda a pessoa ao
 * Google Play com o link no `referrer` (`saqz_invite=<código>`, `saqz_attendance=<código>`). Na
 * primeira abertura o app lê esse valor e o entrega ao mesmo caminho de um link tocado; antes, o
 * link se perdia na instalação.
 */
internal class AndroidInstallReferrer(
    context: Context,
    private val linksDomain: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val context = context.applicationContext
    private val preferences: SharedPreferences = this.context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Uma consulta por instalação: o Play guarda o referrer por 90 dias e repetiria o convite. */
    fun readOnce(onLink: (String) -> Unit) {
        if (preferences.getBoolean(CONSUMED, false)) return
        preferences.edit().putBoolean(CONSUMED, true).apply()
        val main = Handler(Looper.getMainLooper())
        // Sem Play Store (ou com o serviço fora), o convite simplesmente não vem: nunca derruba a abertura.
        runCatching {
            val client = InstallReferrerClient.newBuilder(context).build()
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    val link = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer }.getOrNull()?.let { details ->
                            deferredLink(details.installReferrer, details.installBeginTimestampSeconds, clock() / 1000, linksDomain)
                        }
                    } else {
                        null
                    }
                    runCatching { client.endConnection() }
                    link?.let { main.post { onLink(it) } }
                }

                override fun onInstallReferrerServiceDisconnected() = Unit
            })
        }
    }

    internal companion object {
        private const val FILE = "saqz_install_referrer"
        private const val CONSUMED = "consumed"
        private val PARAMETERS = setOf("saqz_invite", "saqz_onboarding", "saqz_attendance")
        private const val ATTENDANCE = "saqz_attendance"
        private const val DECLINE = "saqz_intent=decline"
        private const val MAX_AGE_SECONDS = 7L * 24 * 60 * 60

        /**
         * Só vale o que a página põe: um único parâmetro de convite, onboarding ou presença, de uma
         * instalação recente; a presença pode trazer o "não vou" (`&saqz_intent=decline`), para
         * nunca virar confirmação. Ficam de fora o orgânico
         * ("utm_source=google-play&utm_medium=organic") e quem instalou há tempo e só agora abre a
         * versão que lê o referrer. O código em si é validado pelo [AndroidLinkAdapter], como em
         * qualquer link.
         */
        fun deferredLink(referrer: String?, installBeginSeconds: Long, nowSeconds: Long, domain: String): String? {
            if (referrer.isNullOrBlank()) return null
            if (installBeginSeconds <= 0 || nowSeconds - installBeginSeconds > MAX_AGE_SECONDS) return null
            val decoded = if ('=' in referrer) {
                referrer
            } else {
                runCatching { URLDecoder.decode(referrer, StandardCharsets.UTF_8.name()) }.getOrNull() ?: return null
            }
            val parameters = decoded.split('&')
            val accepted = when (parameters.size) {
                1 -> parameters[0].substringBefore('=') in PARAMETERS
                2 -> parameters[0].substringBefore('=') == ATTENDANCE && parameters[1] == DECLINE
                else -> false
            }
            return if (accepted) "https://$domain/?$decoded" else null
        }
    }
}
