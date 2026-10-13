package br.com.saqz.network

import io.ktor.client.engine.js.Js

actual fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient =
    NetworkClient(
        Js.create(),
        config,
        logger = if (config.environment == NetworkEnvironment.Prod) {
            NetworkLogger {}
        } else {
            NetworkLogger { println("SaqzNetwork: $it") }
        },
    )
