package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FinancialAccount
import br.com.saqz.receivables.domain.FinancialDelegation
import br.com.saqz.receivables.domain.RegistrationStatus
import br.com.saqz.sharedkernel.RequestIdentity
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertContains

class WalletControllerTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val owner = UUID.randomUUID()
    private val account = FinancialAccount(UUID.randomUUID(), owner, RegistrationStatus.APPROVED, false)
    private val cursor = WalletCursorCodec(ByteArray(32) { 3 })

    @Test
    fun `stale verified bearer cannot change destination`() {
        val mvc = mvc(RequestIdentity(owner.toString(), authenticatedAtEpochSeconds = now.minusSeconds(301).epochSecond))
        mvc.post("/api/receivables/accounts/${account.id}/bank-destinations") {
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"requestId":"${UUID.randomUUID()}","bankCode":"001","accountType":"CHECKING","ownerName":"Maria Silva","cpfCnpj":"12345678901","agency":"1234","account":"98765","accountDigit":"0"}"""
        }.andExpect { status { isForbidden() } }.andReturn().response.contentAsString.also {
            assertContains(it, "RECENT_AUTHENTICATION_REQUIRED")
        }
    }

    @Test
    fun `missing future and stale authentication cannot change bank destination`() {
        for (time in listOf(null, now.plusSeconds(1).epochSecond, now.plusSeconds(30).epochSecond,
            now.minusSeconds(301).epochSecond)) {
            mvc(RequestIdentity(owner.toString(), authenticatedAtEpochSeconds = time))
                .post("/api/receivables/accounts/${account.id}/bank-destinations") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"requestId":"${UUID.randomUUID()}","bankCode":"001","accountType":"CHECKING","ownerName":"Maria Silva","cpfCnpj":"12345678901","agency":"1234","account":"98765","accountDigit":"0"}"""
                }.andExpect { status { isForbidden() } }
        }
    }

    @Test
    fun `verified authentication exactly five minutes old remains recent`() {
        for (time in listOf(now.minusSeconds(300).epochSecond, now.epochSecond)) {
            mvc(RequestIdentity(owner.toString(), authenticatedAtEpochSeconds = time))
                .post("/api/receivables/accounts/${account.id}/withdrawals") {
                    contentType = org.springframework.http.MediaType.APPLICATION_JSON
                    content = """{"requestId":"${UUID.randomUUID()}","destinationId":"${UUID.randomUUID()}","amountCents":0,"explicitlyAuthorized":false}"""
                }.andExpect { status { isBadRequest() } }.andReturn().response.contentAsString.also {
                    assertContains(it, "INVALID_INPUT")
                }
        }
    }

    @Test
    fun `statement cursor is opaque account-bound and tamper evident`() {
        val mvc = mvc(RequestIdentity(owner.toString(), authenticatedAtEpochSeconds = now.epochSecond))
        val otherAccount = UUID.randomUUID()
        mvc.get("/api/receivables/accounts/$otherAccount/wallet/statement") {
            param("cursor", cursor.encode(account.id, 20))
        }.andExpect { status { isBadRequest() } }.andReturn().response.contentAsString.also {
            assertContains(it, "INVALID_INPUT")
        }
        val tampered = cursor.encode(otherAccount, 20).dropLast(1) + "A"
        mvc.get("/api/receivables/accounts/$otherAccount/wallet/statement") { param("cursor", tampered) }
            .andExpect { status { isBadRequest() } }
    }

    private fun mvc(identity: RequestIdentity): org.springframework.test.web.servlet.MockMvc {
        val accounts = object : FinancialAccountRepository {
            override fun listForUser(userId: UUID) = listOf(account)
            override fun findById(accountId: UUID) = account.takeIf { it.id == accountId }
            override fun findByOwner(ownerUserId: UUID) = account.takeIf { it.ownerUserId == ownerUserId }
            override fun findDelegation(accountId: UUID, userId: UUID): FinancialDelegation? = null
        }
        val groups = object : ReceivablesGroups {
            override fun isOwner(groupId: UUID, userId: UUID) = false
            override fun isCurrentAdministratorOfOwner(userId: UUID, ownerUserId: UUID) = false
        }
        val onboarding = object : FinancialOnboardingStore {
            override fun begin(request: FinancialRequest, termsVersion: String, registration: LegalRegistration, now: Instant) = account
            override fun findOwned(ownerUserId: UUID) = account
            override fun creationOperation(accountId: UUID) = error("unused")
            override fun registration(accountId: UUID) = error("unused")
            override fun credentials(accountId: UUID) = AccountCredentials("secret", now, "provider")
            override fun saveProviderAccount(accountId: UUID, providerAccount: ProviderAccount, now: Instant) = Unit
            override fun updateStatus(accountId: UUID, status: RegistrationStatus, now: Instant) = Unit
        }
        val store = object : WalletStore {
            override fun listDestinations(accountId: UUID) = emptyList<BankDestination>()
            override fun findDestinationByRequest(accountId: UUID, requestId: UUID): BankDestination? = null
            override fun saveDestination(accountId: UUID, request: FinancialRequest, details: BankDestinationDetails, now: Instant) = error("must not reach")
            override fun prepareWithdrawal(accountId: UUID, request: FinancialRequest, destinationId: UUID, amountCents: Long, availableBalanceCents: Long, now: Instant) = error("unused")
            override fun claimWithdrawal(accountId: UUID, withdrawalId: UUID, now: Instant): WithdrawalClaim? = null
            override fun finishWithdrawal(claim: WithdrawalClaim, result: ProviderWithdrawalResult, now: Instant) = error("unused")
            override fun findWithdrawal(accountId: UUID, withdrawalId: UUID): Withdrawal? = null
            override fun findWithdrawalByRequest(accountId: UUID, requestId: UUID): Withdrawal? = null
        }
        val provider = object : WalletProvider {
            override fun balance(apiKey: String) = 0L to 0L
            override fun statement(apiKey: String, offset: Int, limit: Int) = WalletStatementPage(emptyList(), null)
            override fun withdraw(apiKey: String, operationId: UUID, amountCents: Long, destination: BankDestinationDetails) = ProviderWithdrawalResult.Unknown
            override fun recoverWithdrawal(apiKey: String, operationId: UUID) = ProviderWithdrawalResult.Unknown
        }
        val controller = WalletController(FinancialActorResolver { UUID.fromString(it.subject) },
            ManageWallet(accounts, groups, onboarding, store, provider), cursor, Clock.fixed(now, ZoneOffset.UTC))
        return MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter) = parameter.parameterType == RequestIdentity::class.java
            override fun resolveArgument(parameter: MethodParameter, mavContainer: ModelAndViewContainer?,
                webRequest: NativeWebRequest, binderFactory: WebDataBinderFactory?) = identity
        }).build()
    }
}
