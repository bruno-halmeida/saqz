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

internal data class AndroidFirebaseConfiguration(
    val projectId: String,
    val apiKey: String,
    val messagingSenderId: String,
    val applicationId: String,
    val useAuthEmulator: Boolean,
    val emulatorHost: String = LocalFirebaseConfiguration.emulatorHost,
    val emulatorPort: Int = LocalFirebaseConfiguration.emulatorPort,
) {
    companion object {
        val current = AndroidFirebaseConfiguration(
            projectId = BuildConfig.FIREBASE_PROJECT_ID,
            apiKey = BuildConfig.FIREBASE_API_KEY,
            messagingSenderId = BuildConfig.FIREBASE_MESSAGING_SENDER_ID,
            applicationId = BuildConfig.FIREBASE_APPLICATION_ID,
            useAuthEmulator = BuildConfig.FIREBASE_USE_EMULATOR,
        )
    }
}

internal fun interface FirebaseAuthClient {
    fun useEmulator(host: String, port: Int)
}

internal fun interface FirebaseAuthFactory {
    fun create(): FirebaseAuthClient
}

internal class FirebaseAuthBootstrap(
    private val factory: FirebaseAuthFactory,
    private val configuration: AndroidFirebaseConfiguration,
) {
    fun initialize(): FirebaseAuthClient = factory.create().also {
        if (configuration.useAuthEmulator) {
            it.useEmulator(configuration.emulatorHost, configuration.emulatorPort)
        }
    }
}

internal object AndroidFirebaseBootstrap {
    fun initialize(
        context: Context,
        configuration: AndroidFirebaseConfiguration = AndroidFirebaseConfiguration.current,
    ): FirebaseAuth {
        lateinit var sdkAuth: FirebaseAuth
        FirebaseAuthBootstrap(
        factory = {
            val options = FirebaseOptions.Builder()
                .setProjectId(configuration.projectId)
                .setApiKey(configuration.apiKey)
                .setGcmSenderId(configuration.messagingSenderId)
                .setApplicationId(configuration.applicationId)
                .build()
            val appName = if (configuration.useAuthEmulator) "saqz-local" else "saqz"
            val applicationContext = context.applicationContext
            // Named app initializes once per process: reuse it across activity re-launch
            // and rotation instead of re-initializing (EDGE-05).
            val app = FirebaseApp.getApps(applicationContext).firstOrNull { it.name == appName }
                ?: FirebaseApp.initializeApp(applicationContext, options, appName)
            sdkAuth = FirebaseAuth.getInstance(app)
            FirebaseAuthClient { host, port -> sdkAuth.useEmulator(host, port) }
        },
        configuration = configuration,
        ).initialize()
        return sdkAuth
    }
}
