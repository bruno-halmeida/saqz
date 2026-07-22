package br.com.saqz.network

import android.util.Log
import io.ktor.client.engine.android.Android

actual fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient =
    NetworkClient(
        Android.create(),
        config,
        logger = if (config.environment == NetworkEnvironment.Prod) {
            NetworkLogger {}
        } else {
            NetworkLogger { Log.d("SaqzNetwork", it) }
        },
    )
