package br.com.saqz.composeapp.access

import br.com.saqz.access.domain.port.AppUpdateMemoryPort
import platform.Foundation.NSUserDefaults

/** Memória do pop-up de atualização em NSUserDefaults: leitura e escrita síncronas, callback imediato. */
class IosAppUpdateMemory(private val defaults: NSUserDefaults) : AppUpdateMemoryPort {
    constructor() : this(NSUserDefaults.standardUserDefaults)

    override fun readUpdateMinimum(done: (String?) -> Unit) {
        done(defaults.stringForKey(MINIMUM_KEY))
    }

    override fun writeUpdateMinimum(value: String?, done: (Boolean) -> Unit) {
        if (value == null) defaults.removeObjectForKey(MINIMUM_KEY) else defaults.setObject(value, forKey = MINIMUM_KEY)
        done(true)
    }

    override fun isRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) {
        done(defaults.boolForKey(dismissedKey(version)))
    }

    override fun markRecommendedUpdateDismissed(version: String, done: (Boolean) -> Unit) {
        defaults.setBool(true, forKey = dismissedKey(version))
        done(true)
    }

    private fun dismissedKey(version: String) = "$PREFIX.dismissed.$version"

    private companion object {
        const val PREFIX = "saqz.appUpdate"
        const val MINIMUM_KEY = "$PREFIX.minimum"
    }
}
