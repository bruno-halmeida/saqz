package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreProduct
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubmitAppStoreTransactionTest {
    private val owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val otherOwner = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val verifier = ScriptedAppStoreVerifier()
    private val store = InMemoryAppStoreSubscriptions()
    private val useCase = SubmitAppStoreTransaction(verifier, store, InlineTransactions)

    @Test
    fun `a verified purchase becomes the owner's subscription and a receipt`() {
        verifier.transactions["jws"] = appStoreTransaction(owner)

        assertEquals(SubmitAppStoreTransactionResult.Accepted, useCase.execute(owner, "jws"))

        val row = store.rows.getValue("2000000001")
        assertEquals(owner, row.ownerUserId)
        assertEquals(AppStoreProduct.ORGANIZADOR_MENSAL, row.product)
        assertTrue("2000000001" in store.transactions)
    }

    @Test
    fun `resubmitting the same transaction changes nothing`() {
        verifier.transactions["jws"] = appStoreTransaction(owner)
        useCase.execute(owner, "jws")
        val before = store.rows.getValue("2000000001")

        assertEquals(SubmitAppStoreTransactionResult.Accepted, useCase.execute(owner, "jws"))
        assertEquals(before, store.rows.getValue("2000000001"))
    }

    @Test
    fun `unverifiable data is invalid and stores nothing`() {
        assertEquals(SubmitAppStoreTransactionResult.Invalid, useCase.execute(owner, "forged"))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `a product that is not a Saqz subscription is invalid`() {
        verifier.transactions["jws"] = appStoreTransaction(owner).copy(productId = "app.saqz.moedas")

        assertEquals(SubmitAppStoreTransactionResult.Invalid, useCase.execute(owner, "jws"))
    }

    @Test
    fun `a purchase made by another Saqz account is refused`() {
        verifier.transactions["jws"] = appStoreTransaction(otherOwner)

        assertEquals(SubmitAppStoreTransactionResult.OwnedByAnotherAccount, useCase.execute(owner, "jws"))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `an expired subscription bought again by another account moves to that account`() {
        val expiredAt = Instant.parse("2026-10-01T12:00:00Z")
        val boughtAgainAt = Instant.parse("2026-10-09T16:41:26Z")
        verifier.transactions["first"] = appStoreTransaction(owner, purchaseDate = expiredAt.minusSeconds(30L * 24 * 3600))
        verifier.transactions["again"] = appStoreTransaction(otherOwner, transactionId = "2000000002", purchaseDate = boughtAgainAt)
        useCase.execute(owner, "first")

        assertEquals(SubmitAppStoreTransactionResult.Accepted, useCase.execute(otherOwner, "again"))

        val row = store.rows.getValue("2000000001")
        assertEquals(otherOwner, row.ownerUserId)
        assertEquals("2000000002", row.latestTransactionId)
        assertTrue(row.isEntitlingAt(boughtAgainAt.plusSeconds(1)))
        assertEquals(owner, store.transactions.getValue("2000000001").second)
        assertEquals(otherOwner, store.transactions.getValue("2000000002").second)
    }

    @Test
    fun `a subscription still giving access stays with the account that bought it`() {
        val boughtAt = Instant.parse("2026-10-01T12:00:00Z")
        verifier.transactions["first"] = appStoreTransaction(owner, purchaseDate = boughtAt)
        verifier.transactions["again"] = appStoreTransaction(otherOwner, transactionId = "2000000002", purchaseDate = boughtAt.plusSeconds(3600))
        useCase.execute(owner, "first")

        assertEquals(SubmitAppStoreTransactionResult.OwnedByAnotherAccount, useCase.execute(otherOwner, "again"))
        assertEquals(owner, store.rows.getValue("2000000001").ownerUserId)
        assertEquals("2000000001", store.rows.getValue("2000000001").latestTransactionId)
    }

    @Test
    fun `a subscription without account token stays with whoever linked it first`() {
        verifier.transactions["first"] = appStoreTransaction(owner = null)
        verifier.transactions["renewal"] = appStoreTransaction(owner = null, transactionId = "2000000002")

        assertEquals(SubmitAppStoreTransactionResult.Accepted, useCase.execute(owner, "first"))
        assertEquals(SubmitAppStoreTransactionResult.OwnedByAnotherAccount, useCase.execute(otherOwner, "renewal"))
        assertEquals(owner, store.rows.getValue("2000000001").ownerUserId)
    }
}
