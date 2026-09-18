package br.com.saqz.androidapp

import android.content.Context
import android.os.Bundle
import br.com.saqz.composeapp.analytics.AnalyticsSink
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Firebase Analytics e Crashlytics do app padrão, que o [SaqzApplication] só inicializa fora do
 * emulador (dev sem google-services e e2e). Sem plugin Gradle do Crashlytics: o app não minifica,
 * então não há mapping para subir.
 */
internal class AndroidAnalyticsSink(context: Context) : AnalyticsSink {
    private val enabled = !BuildConfig.FIREBASE_USE_EMULATOR
    private val firebase: FirebaseAnalytics? =
        if (enabled) FirebaseAnalytics.getInstance(context.applicationContext) else null
    private val crashlytics: FirebaseCrashlytics? = if (enabled) FirebaseCrashlytics.getInstance() else null

    override fun track(name: String, params: Map<String, String>) {
        firebase?.logEvent(name, Bundle().apply { params.forEach { (key, value) -> putString(key, value) } })
    }

    override fun setUserId(id: String?) {
        firebase?.setUserId(id)
        crashlytics?.setUserId(id.orEmpty())
    }

    override fun log(message: String) {
        crashlytics?.log(message)
    }

    override fun setUserProperty(name: String, value: String?) {
        firebase?.setUserProperty(name, value)
    }
}
