package br.com.saqz.subscriptions.adapter.output.appstore

import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import com.fasterxml.jackson.databind.ObjectMapper
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cadeia falsa com as mesmas extensões da Apple (WWDR intermediária e receipt signer), assinada
 * por uma raiz de teste: exercita a verificação real da biblioteca sem depender da Apple.
 */
class AppleSignedDataVerifierTest {
    private val now = Instant.now()
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val chain = TestChain.create()
    private val verifier = verifier(setOf(AppStoreEnvironment.PRODUCTION, AppStoreEnvironment.SANDBOX))

    @Test
    fun `a sandbox transaction signed through the Apple chain is decoded`() {
        val transaction = assertNotNull(verifier.verifyTransaction(chain.sign(transactionPayload())))

        assertEquals("2000000002", transaction.transactionId)
        assertEquals("2000000001", transaction.originalTransactionId)
        assertEquals("app.saqz.organizador.mensal", transaction.productId)
        assertEquals(owner, transaction.appAccountToken)
        assertEquals(AppStoreEnvironment.SANDBOX, transaction.environment)
        assertEquals(59_900L, transaction.priceMillis)
        assertEquals(now.plus(Duration.ofDays(30)).toEpochMilli(), transaction.expiresDate?.toEpochMilli())
    }

    @Test
    fun `a chain from another root is refused`() {
        val forged = TestChain.create().sign(transactionPayload())

        assertNull(verifier.verifyTransaction(forged))
    }

    @Test
    fun `a payload changed after signing is refused`() {
        val signed = chain.sign(transactionPayload())
        val parts = signed.split('.')
        val tampered = listOf(parts[0], base64Url(json(transactionPayload(productId = "app.saqz.ilimitado.anual"))), parts[2])

        assertNull(verifier.verifyTransaction(tampered.joinToString(".")))
    }

    @Test
    fun `another app's transaction is refused`() {
        assertNull(verifier.verifyTransaction(chain.sign(transactionPayload(bundleId = "com.outro.app"))))
    }

    @Test
    fun `production is refused while the app Apple ID is not configured`() {
        assertNull(verifier.verifyTransaction(chain.sign(transactionPayload(environment = "Production"))))
    }

    @Test
    fun `production is accepted with the app Apple ID`() {
        val production = verifier(setOf(AppStoreEnvironment.PRODUCTION), appAppleId = 6_700_000_000)

        val transaction = production.verifyTransaction(chain.sign(transactionPayload(environment = "Production")))

        assertEquals(AppStoreEnvironment.PRODUCTION, transaction?.environment)
    }

    @Test
    fun `unsigned Xcode transactions are accepted only where Xcode is configured`() {
        val xcode = unsignedJws(transactionPayload(environment = "Xcode"))

        assertNull(verifier.verifyTransaction(xcode))
        assertNotNull(verifier(setOf(AppStoreEnvironment.XCODE)).verifyTransaction(xcode))
    }

    @Test
    fun `garbage is refused`() {
        assertNull(verifier.verifyTransaction("not-a-jws"))
        assertNull(verifier.verifyNotification("not.a.jws"))
    }

    @Test
    fun `a notification carries its verified transaction and renewal info`() {
        val notificationUuid = UUID.randomUUID()
        val payload = mapOf(
            "notificationType" to "DID_CHANGE_RENEWAL_STATUS",
            "subtype" to "AUTO_RENEW_DISABLED",
            "notificationUUID" to notificationUuid.toString(),
            "version" to "2.0",
            "signedDate" to now.toEpochMilli(),
            "data" to mapOf(
                "bundleId" to BUNDLE_ID,
                "environment" to "Sandbox",
                "status" to 1,
                "signedTransactionInfo" to chain.sign(transactionPayload()),
                "signedRenewalInfo" to chain.sign(
                    mapOf(
                        "originalTransactionId" to "2000000001",
                        "autoRenewProductId" to "app.saqz.organizador.mensal",
                        "productId" to "app.saqz.organizador.mensal",
                        "autoRenewStatus" to 0,
                        "isInBillingRetryPeriod" to false,
                        "signedDate" to now.toEpochMilli(),
                        "environment" to "Sandbox",
                    ),
                ),
            ),
        )

        val notification = assertNotNull(verifier.verifyNotification(chain.sign(payload)))

        assertEquals(notificationUuid, notification.notificationUuid)
        assertEquals("DID_CHANGE_RENEWAL_STATUS", notification.type)
        assertEquals("AUTO_RENEW_DISABLED", notification.subtype)
        assertEquals("2000000002", notification.transaction?.transactionId)
        assertEquals(false, notification.renewalInfo?.autoRenew)
    }

    private fun verifier(environments: Set<AppStoreEnvironment>, appAppleId: Long? = null) = AppleSignedDataVerifier(
        AppStoreVerifierSettings(BUNDLE_ID, appAppleId, environments, onlineChecks = false),
        rootCertificates = listOf(chain.root.encoded),
    )

    private fun transactionPayload(
        productId: String = "app.saqz.organizador.mensal",
        bundleId: String = BUNDLE_ID,
        environment: String = "Sandbox",
    ) = mapOf(
        "transactionId" to "2000000002",
        "originalTransactionId" to "2000000001",
        "bundleId" to bundleId,
        "productId" to productId,
        "subscriptionGroupIdentifier" to "21000000",
        "purchaseDate" to now.toEpochMilli(),
        "originalPurchaseDate" to now.toEpochMilli(),
        "expiresDate" to now.plus(Duration.ofDays(30)).toEpochMilli(),
        "quantity" to 1,
        "type" to "Auto-Renewable Subscription",
        "appAccountToken" to owner.toString(),
        "inAppOwnershipType" to "PURCHASED",
        "signedDate" to now.toEpochMilli(),
        "environment" to environment,
        "transactionReason" to "PURCHASE",
        "storefront" to "BRA",
        "price" to 59_900,
        "currency" to "BRL",
    )

    private fun unsignedJws(payload: Map<String, Any?>) =
        "${base64Url(json(mapOf("alg" to "ES256")))}.${base64Url(json(payload))}.c2lnbmF0dXJl"

    private class TestChain(val root: X509Certificate, val intermediate: X509Certificate, val leaf: X509Certificate, val leafKey: KeyPair) {
        fun sign(payload: Map<String, Any?>): String {
            val x5c = listOf(leaf, intermediate, root).map { Base64.getEncoder().encodeToString(it.encoded) }
            val signingInput = "${base64Url(json(mapOf("alg" to "ES256", "x5c" to x5c)))}.${base64Url(json(payload))}"
            val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initSign(leafKey.private)
                update(signingInput.toByteArray())
                sign()
            }
            return "$signingInput.${Base64.getUrlEncoder().withoutPadding().encodeToString(signature)}"
        }

        companion object {
            private const val WWDR_INTERMEDIATE_OID = "1.2.840.113635.100.6.2.1"
            private const val RECEIPT_SIGNER_OID = "1.2.840.113635.100.6.11.1"

            fun create(): TestChain {
                val rootKey = ecKey()
                val intermediateKey = ecKey()
                val leafKey = ecKey()
                val root = certificate("CN=Raiz de teste", rootKey, "CN=Raiz de teste", rootKey, ca = true, oid = null)
                val intermediate = certificate("CN=WWDR de teste", intermediateKey, "CN=Raiz de teste", rootKey, ca = true, oid = WWDR_INTERMEDIATE_OID)
                val leaf = certificate("CN=StoreKit de teste", leafKey, "CN=WWDR de teste", intermediateKey, ca = false, oid = RECEIPT_SIGNER_OID)
                return TestChain(root, intermediate, leaf, leafKey)
            }

            private fun ecKey(): KeyPair = KeyPairGenerator.getInstance("EC").run {
                initialize(ECGenParameterSpec("secp256r1"))
                generateKeyPair()
            }

            private fun certificate(
                subject: String,
                subjectKey: KeyPair,
                issuer: String,
                issuerKey: KeyPair,
                ca: Boolean,
                oid: String?,
            ): X509Certificate {
                val builder = JcaX509v3CertificateBuilder(
                    X500Name(issuer),
                    BigInteger.valueOf(System.nanoTime()),
                    Date.from(Instant.now().minus(Duration.ofDays(1))),
                    Date.from(Instant.now().plus(Duration.ofDays(365))),
                    X500Name(subject),
                    subjectKey.public,
                )
                builder.addExtension(Extension.basicConstraints, true, BasicConstraints(ca))
                if (ca) builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
                if (oid != null) builder.addExtension(ASN1ObjectIdentifier(oid), false, DERNull.INSTANCE)
                val signer = JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey.private)
                return JcaX509CertificateConverter().getCertificate(builder.build(signer))
            }
        }
    }

    private companion object {
        const val BUNDLE_ID = "app.saqz"
        val mapper = ObjectMapper()

        fun json(value: Any): ByteArray = mapper.writeValueAsBytes(value)

        fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
