package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WalletCursorCodec(secret: ByteArray) {
    private val key = SecretKeySpec(secret.copyOf().also { require(it.size >= 32) }, "HmacSHA256")
    fun encode(accountId: UUID, offset: Int): String {
        require(offset >= 0)
        val payload = "$accountId:$offset".toByteArray(UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload + sign(payload))
    }
    fun decode(accountId: UUID, cursor: String?): Int? {
        if (cursor == null) return 0
        return try {
            val packed = Base64.getUrlDecoder().decode(cursor)
            if (packed.size <= 32) return null
            val payload = packed.copyOfRange(0, packed.size - 32)
            val signature = packed.copyOfRange(packed.size - 32, packed.size)
            if (!java.security.MessageDigest.isEqual(signature, sign(payload))) return null
            val parts = String(payload, UTF_8).split(':')
            if (parts.size != 2 || UUID.fromString(parts[0]) != accountId) null else parts[1].toInt().takeIf { it >= 0 }
        } catch (_: Exception) { null }
    }
    private fun sign(value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(key); doFinal(value) }
}

class BankDestinationRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID,
    @JsonProperty("bankCode") val bankCode: String,
    @JsonProperty("accountType") val accountType: BankAccountType,
    @JsonProperty("ownerName") val ownerName: String,
    @JsonProperty("cpfCnpj") val cpfCnpj: String,
    @JsonProperty("agency") val agency: String,
    @JsonProperty("account") val account: String,
    @JsonProperty("accountDigit") val accountDigit: String,
)

class WithdrawalRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID,
    @JsonProperty("destinationId") val destinationId: UUID,
    @JsonProperty("amountCents") val amountCents: Long,
    @JsonProperty("explicitlyAuthorized") val explicitlyAuthorized: Boolean,
)

data class BankDestinationView(val id: UUID, val bankCode: String, val accountType: BankAccountType,
    val ownerName: String, val cpfCnpjSuffix: String, val agencySuffix: String, val accountSuffix: String,
    val verified: Boolean)
data class WithdrawalView(val id: UUID, val requestId: UUID, val destinationId: UUID, val amountCents: Long,
    val feeCents: Long, val status: WithdrawalStatus, val providerTransferId: String?)
data class WalletStatementView(val items: List<WalletStatementItem>, val nextCursor: String?)

@RestController
@RequestMapping("/api/receivables/accounts/{accountId}")
class WalletController(
    private val actors: FinancialActorResolver,
    private val wallet: ManageWallet,
    private val cursors: WalletCursorCodec,
    private val clock: Clock,
) {
    @GetMapping("/wallet")
    fun balance(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID) =
        respond(wallet.balance(accountId, request(identity), clock.instant()))

    @GetMapping("/wallet/statement")
    fun statement(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                  @RequestParam(defaultValue = "20") limit: Int, @RequestParam(required = false) cursor: String?): ResponseEntity<*> {
        val request = request(identity)
        val offset = cursors.decode(accountId, cursor)
            ?: return respond(FinancialResult.Failure(FinancialError.INVALID_INPUT, request.requestId))
        return when (val result = wallet.statement(accountId, request, offset, limit)) {
            is FinancialResult.Success -> respond(FinancialResult.Success(WalletStatementView(result.value.items,
                result.value.nextOffset?.let { cursors.encode(accountId, it) }), result.requestId))
            is FinancialResult.Failure -> respond(result)
        }
    }

    @GetMapping("/bank-destinations")
    fun destinations(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID): ResponseEntity<*> =
        when (val result = wallet.destinations(accountId, request(identity))) {
            is FinancialResult.Success -> respond(FinancialResult.Success(result.value.map(::destinationView), result.requestId))
            is FinancialResult.Failure -> respond(result)
        }

    @PostMapping("/bank-destinations")
    fun saveDestination(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                        @RequestBody body: BankDestinationRequest): ResponseEntity<*> {
        val request = FinancialRequest(body.requestId, actors.resolve(identity))
        val result = wallet.saveDestination(accountId, request, BankDestinationDetails(body.bankCode,
            body.accountType, body.ownerName, body.cpfCnpj, body.agency, body.account, body.accountDigit),
            recentlyAuthenticated(identity, clock.instant()), clock.instant())
        return when (result) {
            is FinancialResult.Success -> respond(FinancialResult.Success(destinationView(result.value), result.requestId))
            is FinancialResult.Failure -> respond(result)
        }
    }

    @GetMapping("/bank-destinations/by-request/{originalRequestId}")
    fun recoverDestination(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                           @PathVariable originalRequestId: UUID): ResponseEntity<*> =
        when (val result = wallet.recoverDestination(accountId, originalRequestId, request(identity))) {
            is FinancialResult.Success -> respond(FinancialResult.Success(destinationView(result.value), result.requestId))
            is FinancialResult.Failure -> respond(result)
        }

    @PostMapping("/withdrawals")
    fun withdraw(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                 @RequestBody body: WithdrawalRequest): ResponseEntity<*> {
        val request = FinancialRequest(body.requestId, actors.resolve(identity))
        val result = wallet.withdraw(accountId, request, body.destinationId, body.amountCents,
            body.explicitlyAuthorized, recentlyAuthenticated(identity, clock.instant()), clock.instant())
        return withdrawalResponse(result)
    }

    @GetMapping("/withdrawals/{withdrawalId}")
    fun recover(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                @PathVariable withdrawalId: UUID): ResponseEntity<*> =
        withdrawalResponse(wallet.recover(accountId, withdrawalId, request(identity), clock.instant()))

    @GetMapping("/withdrawals/by-request/{originalRequestId}")
    fun recoverByRequest(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                         @PathVariable originalRequestId: UUID): ResponseEntity<*> =
        withdrawalResponse(wallet.recoverByRequest(accountId, originalRequestId, request(identity), clock.instant()))

    private fun request(identity: RequestIdentity) = FinancialRequest(UUID.randomUUID(), actors.resolve(identity))
    private fun recentlyAuthenticated(identity: RequestIdentity, now: Instant): Boolean =
        identity.authenticatedAtEpochSeconds?.let { it <= now.epochSecond && it >= now.minusSeconds(300).epochSecond } == true
    private fun destinationView(value: BankDestination) = BankDestinationView(value.id, value.details.bankCode,
        value.details.accountType, maskName(value.details.ownerName), suffix(value.details.cpfCnpj),
        suffix(value.details.agency), suffix(value.details.account), value.verifiedAt != null)
    private fun withdrawalView(value: Withdrawal) = WithdrawalView(value.id, value.requestId, value.destinationId,
        value.amountCents, value.feeCents, value.status, value.providerTransferId)
    private fun withdrawalResponse(result: FinancialResult<Withdrawal>) = when (result) {
        is FinancialResult.Success -> respond(FinancialResult.Success(withdrawalView(result.value), result.requestId))
        is FinancialResult.Failure -> respond(result)
    }
    private fun maskName(value: String) = value.trim().split(Regex("\\s+")).joinToString(" ") {
        it.take(1) + "*".repeat((it.length - 1).coerceAtLeast(2))
    }
    private fun suffix(value: String) = value.filter(Char::isLetterOrDigit).takeLast(4).padStart(4, '*')

    private fun respond(result: FinancialResult<*>): ResponseEntity<*> {
        val status = when (result) {
            is FinancialResult.Success -> 200
            is FinancialResult.Failure -> when (result.error) {
                FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
                FinancialError.INVALID_INPUT -> 400
                FinancialError.RECENT_AUTHENTICATION_REQUIRED -> 403
                FinancialError.CONFLICT, FinancialError.REGISTRATION_RESTRICTED -> 409
                FinancialError.INSUFFICIENT_BALANCE -> 422
                FinancialError.RESULT_PENDING -> 202
                else -> 503
            }
        }
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(result)
    }
}
