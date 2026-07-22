package br.com.saqz.network

import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformNetworkClient(config: NetworkConfig): NetworkClient =
    NetworkClient(
        Darwin.create(),
        config,
        logger = if (config.environment == NetworkEnvironment.Prod) {
            NetworkLogger {}
        } else {
            NetworkLogger { println("SaqzNetwork: $it") }
        },
    )
