package br.com.saqz.androidapp

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

internal object LocalFirebaseConfiguration {
    const val projectId = "saqz-local"
    const val apiKey = "fake-saqz-local-api-key"
    const val messagingSenderId = "123456789000"
    const val applicationId = "1:123456789000:android:saqzlocal"
    const val emulatorHost = "10.0.2.2"
    const val emulatorPort = 9099
}

internal fun interface FirebaseAuthClient {
    fun useEmulator(host: String, port: Int)
}

internal fun interface FirebaseAuthFactory {
    fun create(): FirebaseAuthClient
}

internal class FirebaseAuthBootstrap(
    private val factory: FirebaseAuthFactory,
) {
    fun initialize(): FirebaseAuthClient = factory.create().also {
        it.useEmulator(LocalFirebaseConfiguration.emulatorHost, LocalFirebaseConfiguration.emulatorPort)
    }
}

internal object AndroidFirebaseBootstrap {
    fun initialize(context: Context): FirebaseAuthClient = FirebaseAuthBootstrap {
        val options = FirebaseOptions.Builder()
            .setProjectId(LocalFirebaseConfiguration.projectId)
            .setApiKey(LocalFirebaseConfiguration.apiKey)
            .setGcmSenderId(LocalFirebaseConfiguration.messagingSenderId)
            .setApplicationId(LocalFirebaseConfiguration.applicationId)
            .build()
        val app = FirebaseApp.initializeApp(context, options, "saqz-local")
        val auth = FirebaseAuth.getInstance(app)
        FirebaseAuthClient { host, port -> auth.useEmulator(host, port) }
    }.initialize()
}
