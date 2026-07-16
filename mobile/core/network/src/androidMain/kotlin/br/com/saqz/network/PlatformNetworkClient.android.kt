package br.com.saqz.network

import io.ktor.client.engine.android.Android

actual fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient =
    NetworkClient(Android.create(), config)
