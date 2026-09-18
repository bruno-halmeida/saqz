package br.com.saqz.androidapp

import android.content.Context
import android.os.Bundle
import br.com.saqz.composeapp.analytics.AnalyticsSink
import com.google.firebase.analytics.FirebaseAnalytics

/** Firebase Analytics do app padrão, que o [SaqzApplication] só inicializa fora do emulador (dev sem google-services e e2e). */
internal class AndroidAnalyticsSink(context: Context) : AnalyticsSink {
    private val firebase: FirebaseAnalytics? =
        if (BuildConfig.FIREBASE_USE_EMULATOR) null else FirebaseAnalytics.getInstance(context.applicationContext)

    override fun track(name: String, params: Map<String, String>) {
        firebase?.logEvent(name, Bundle().apply { params.forEach { (key, value) -> putString(key, value) } })
    }

    override fun setUserId(id: String?) {
        firebase?.setUserId(id)
    }
}
