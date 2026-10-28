package br.com.saqz.androidapp.access

import android.content.Context
import android.content.SharedPreferences
import br.com.saqz.access.domain.port.AppUpdateMemoryPort

/** Memória do pop-up de atualização em SharedPreferences: leitura e escrita síncronas, callback imediato. */
class AndroidAppUpdateMemory(context: Context) : AppUpdateMemoryPort {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun readUpdateMinimum(done: (String?) -> Unit) {
        done(preferences.getString(MINIMUM_KEY, null))
    }

    override fun writeUpdateMinimum(value: String?, done: (Boolean) -> Unit) {
        preferences.edit().apply { if (value == null) remove(MINIMUM_KEY) else putString(MINIMUM_KEY, value) }.apply()
        done(true)
    }

    override fun isRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) {
        done(preferences.getBoolean(dismissedKey(version), false))
    }

    override fun markRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) {
        preferences.edit().putBoolean(dismissedKey(version), true).apply()
        done(true)
    }

    private fun dismissedKey(version: String) = "dismissed:$version"

    private companion object {
        const val FILE = "saqz-app-update"
        const val MINIMUM_KEY = "minimum"
    }
}
