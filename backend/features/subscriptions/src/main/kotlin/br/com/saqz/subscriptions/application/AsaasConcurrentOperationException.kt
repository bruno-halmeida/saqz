package br.com.saqz.subscriptions.application

class AsaasConcurrentOperationException(
    val idempotencyKey: String,
) : RuntimeException(
    "Asaas operation with idempotency key '$idempotencyKey' is already in progress",
)
