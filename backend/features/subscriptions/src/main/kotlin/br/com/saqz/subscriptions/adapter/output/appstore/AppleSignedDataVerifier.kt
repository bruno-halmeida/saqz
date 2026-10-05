package br.com.saqz.subscriptions.adapter.output.appstore

import br.com.saqz.subscriptions.application.AppStoreNotification
import br.com.saqz.subscriptions.application.AppStoreSignedDataVerifier
import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreRenewalInfo
import br.com.saqz.subscriptions.domain.AppStoreTransaction
import com.apple.itunes.storekit.model.AutoRenewStatus
import com.apple.itunes.storekit.model.Environment
import com.apple.itunes.storekit.model.JWSRenewalInfoDecodedPayload
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload
import com.apple.itunes.storekit.verification.SignedDataVerifier
import com.apple.itunes.storekit.verification.VerificationException
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class AppStoreVerifierSettings(
    val bundleId: String,
    /** Apple ID numérico do app no App Store Connect; sem ele, produção não é aceita. */
    val appAppleId: Long?,
    val environments: Set<AppStoreEnvironment>,
    /** OCSP e validade dos certificados pela hora atual. Desligar só em teste. */
    val onlineChecks: Boolean,
)

/**
 * Verificação com a App Store Server Library da Apple. Cada ambiente aceito tem seu verificador,
 * que confere de novo o ambiente depois de validar a assinatura — ler o ambiente antes só escolhe
 * qual verificador usar.
 *
 * Produção aceita SANDBOX de propósito: a revisão da App Store e o TestFlight compram em sandbox
 * contra o servidor de produção.
 */
class AppleSignedDataVerifier(
    settings: AppStoreVerifierSettings,
    rootCertificates: List<ByteArray> = appleRootCertificates(),
) : AppStoreSignedDataVerifier {
    private val verifiers: Map<AppStoreEnvironment, SignedDataVerifier> = settings.environments
        .filter { it != AppStoreEnvironment.PRODUCTION || settings.appAppleId != null }
        .associateWith { environment ->
            SignedDataVerifier(
                rootCertificates.map(::ByteArrayInputStream).toSet(),
                settings.bundleId,
                settings.appAppleId,
                environment.toApple(),
                settings.onlineChecks,
            )
        }

    init {
        if (AppStoreEnvironment.PRODUCTION in settings.environments && settings.appAppleId == null) {
            log.warn("App Store: sem SAQZ_APP_STORE_APP_APPLE_ID, compras de produção serão recusadas")
        }
    }

    override fun verifyTransaction(signedTransaction: String): AppStoreTransaction? {
        val environment = claimedEnvironment(signedTransaction, "environment") ?: return null
        val verifier = verifierFor(environment) ?: return null
        return verified("transação") { verifier.verifyAndDecodeTransaction(signedTransaction).toDomain(environment) }
    }

    override fun verifyNotification(signedPayload: String): AppStoreNotification? {
        val environment = claimedEnvironment(signedPayload, "data", "environment") ?: return null
        val verifier = verifierFor(environment) ?: return null
        return verified("notificação") {
            val payload = verifier.verifyAndDecodeNotification(signedPayload)
            val data = payload.data
            AppStoreNotification(
                notificationUuid = UUID.fromString(payload.notificationUUID),
                type = payload.rawNotificationType,
                subtype = payload.rawSubtype,
                environment = environment,
                signedAt = Instant.ofEpochMilli(payload.signedDate),
                transaction = data?.signedTransactionInfo
                    ?.let { verifier.verifyAndDecodeTransaction(it).toDomain(environment) },
                renewalInfo = data?.signedRenewalInfo
                    ?.let { verifier.verifyAndDecodeRenewalInfo(it).toDomain() },
            )
        }
    }

    private fun verifierFor(environment: AppStoreEnvironment): SignedDataVerifier? =
        verifiers[environment] ?: null.also { log.warn("App Store: ambiente {} não aceito", environment) }

    private fun <T> verified(what: String, block: () -> T): T? = try {
        block()
    } catch (failure: VerificationException) {
        log.warn("App Store: {} recusada ({})", what, failure.status)
        null
    } catch (failure: IllegalArgumentException) {
        log.warn("App Store: {} com campo inválido", what, failure)
        null
    } catch (failure: NullPointerException) {
        log.warn("App Store: {} sem campo obrigatório", what, failure)
        null
    }

    /** Lê o ambiente sem verificar, só para escolher o verificador (que confere de novo). */
    private fun claimedEnvironment(jws: String, vararg path: String): AppStoreEnvironment? = runCatching {
        val payload = objectMapper.readTree(Base64.getUrlDecoder().decode(jws.split('.')[1]))
        path.fold(payload) { node, field -> node.path(field) }.asText(null)
    }.getOrNull()
        ?.let(Environment::fromValue)
        ?.let { apple -> AppStoreEnvironment.entries.firstOrNull { it.toApple() == apple } }

    private fun JWSTransactionDecodedPayload.toDomain(environment: AppStoreEnvironment) = AppStoreTransaction(
        transactionId = transactionId,
        originalTransactionId = originalTransactionId,
        productId = productId,
        purchaseDate = Instant.ofEpochMilli(purchaseDate),
        expiresDate = expiresDate?.let(Instant::ofEpochMilli),
        revocationDate = revocationDate?.let(Instant::ofEpochMilli),
        appAccountToken = appAccountToken,
        environment = environment,
        priceMillis = price,
        currency = currency,
        signedAt = Instant.ofEpochMilli(signedDate),
    )

    private fun JWSRenewalInfoDecodedPayload.toDomain() = AppStoreRenewalInfo(
        originalTransactionId = originalTransactionId,
        autoRenew = autoRenewStatus == AutoRenewStatus.ON,
        autoRenewProductId = autoRenewProductId,
        inBillingRetry = isInBillingRetryPeriod == true,
        gracePeriodExpiresAt = gracePeriodExpiresDate?.let(Instant::ofEpochMilli),
        signedAt = Instant.ofEpochMilli(signedDate),
    )

    private companion object {
        val log = LoggerFactory.getLogger(AppleSignedDataVerifier::class.java)
        val objectMapper = ObjectMapper()

        fun AppStoreEnvironment.toApple(): Environment = when (this) {
            AppStoreEnvironment.PRODUCTION -> Environment.PRODUCTION
            AppStoreEnvironment.SANDBOX -> Environment.SANDBOX
            AppStoreEnvironment.XCODE -> Environment.XCODE
        }

        /** Raiz que assina os JWS do StoreKit 2 (https://www.apple.com/certificateauthority/). */
        fun appleRootCertificates(): List<ByteArray> = listOf(
            checkNotNull(AppleSignedDataVerifier::class.java.getResourceAsStream("/app-store/AppleRootCA-G3.cer"))
                .use { it.readBytes() },
        )
    }
}
