package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.GetMySubscriptionResult
import br.com.saqz.subscriptions.application.ProcessAppStoreNotification
import br.com.saqz.subscriptions.application.ProcessAppStoreNotificationResult
import br.com.saqz.subscriptions.application.SubmitAppStoreTransaction
import br.com.saqz.subscriptions.application.SubmitAppStoreTransactionResult
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class AppStoreAccountTokenResponse(
    val appAccountToken: UUID,
)

data class SubmitAppStoreTransactionRequest @JsonCreator constructor(
    @JsonProperty("signedTransaction") val signedTransaction: String?,
)

data class AppStoreNotificationRequest @JsonCreator constructor(
    @JsonProperty("signedPayload") val signedPayload: String?,
)

class AppStoreTransactionInvalidException : RuntimeException()

class AppStoreTransactionOwnedByAnotherAccountException : RuntimeException()

class AppStoreNotificationInvalidException : RuntimeException()

/** Contrato em docs/subscriptions/app-store.md. */
@RestController
class AppStoreSubscriptionController(
    private val actors: AuthenticatedActorResolver,
    private val submitTransaction: SubmitAppStoreTransaction,
    private val getMySubscription: GetMySubscription,
) {
    /** Vai na compra como `appAccountToken`: amarra a assinatura da Apple a esta conta. */
    @GetMapping("/subscriptions/app-store/account-token")
    fun accountToken(@AuthenticationPrincipal identity: RequestIdentity) =
        AppStoreAccountTokenResponse(actors.resolve(identity).userId)

    @PostMapping("/subscriptions/app-store/transactions")
    fun submit(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: SubmitAppStoreTransactionRequest,
    ): MySubscriptionResponse {
        val ownerUserId = actors.resolve(identity).userId
        val signed = request.signedTransaction?.takeIf { it.isNotBlank() && it.length <= MAX_JWS_LENGTH }
            ?: throw AppStoreTransactionInvalidException()
        when (submitTransaction.execute(ownerUserId, signed)) {
            SubmitAppStoreTransactionResult.Invalid -> throw AppStoreTransactionInvalidException()
            SubmitAppStoreTransactionResult.OwnedByAnotherAccount -> throw AppStoreTransactionOwnedByAnotherAccountException()
            SubmitAppStoreTransactionResult.Accepted -> Unit
        }
        return when (val result = getMySubscription.execute(ownerUserId)) {
            GetMySubscriptionResult.NotFound -> throw SubscriptionNotFoundException()
            is GetMySubscriptionResult.Found -> result.subscription.toResponse()
        }
    }

    companion object {
        /** Um JWS do StoreKit tem uns 3 KB (três certificados no cabeçalho). */
        const val MAX_JWS_LENGTH = 16_384
    }
}

/** App Store Server Notifications V2 — URL de produção e de sandbox no App Store Connect. */
@RestController
class AppStoreNotificationController(
    private val processNotification: ProcessAppStoreNotification,
) {
    @PostMapping("/webhooks/app-store")
    @ResponseStatus(HttpStatus.OK)
    fun handle(@RequestBody request: AppStoreNotificationRequest) {
        val signed = request.signedPayload?.takeIf { it.isNotBlank() }
            ?: throw AppStoreNotificationInvalidException()
        when (processNotification.execute(signed)) {
            ProcessAppStoreNotificationResult.Invalid -> throw AppStoreNotificationInvalidException()
            ProcessAppStoreNotificationResult.Accepted -> Unit
        }
    }
}
