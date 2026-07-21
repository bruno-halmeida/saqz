package br.com.saqz.androidapp

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Establishes the signed-out precondition before a Compose activity rule launches MainActivity. */
internal class SignedOutAccessRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            AndroidFirebaseBootstrap.initialize(context).signOut()
            check(context.getSharedPreferences(ACCESS_STATE_PREFERENCES, 0).edit().clear().commit())
            base.evaluate()
        }
    }

    private companion object {
        const val ACCESS_STATE_PREFERENCES = "saqz_access_state"
    }
}
