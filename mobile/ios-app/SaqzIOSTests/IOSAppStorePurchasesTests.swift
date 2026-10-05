import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSAppStorePurchasesTests: XCTestCase {
    func testUpdatesBeforeKotlinListensAreDeliveredOnRegistration() async {
        let client = FakeAppStoreClient()
        let adapter = IOSAppStorePurchases(client: client)
        client.emitUpdate(FakeAppStoreClient.transaction("early"))
        await Task.yield()

        let listener = RecordingTransactionListener()
        adapter.listenForAppStoreTransactions(listener: listener)
        client.emitUpdate(FakeAppStoreClient.transaction("late"))
        await waitUntil { listener.ids.count == 2 }

        XCTAssertEqual(listener.ids, ["early", "late"])
        XCTAssertEqual(listener.jws, ["jws-early", "jws-late"])
    }

    func testPurchaseSendsTheAccountTokenAndFinishesOnlyWhenKotlinAsks() async {
        let client = FakeAppStoreClient()
        client.purchaseOutcome = .purchased(FakeAppStoreClient.transaction("tx-1"))
        let adapter = IOSAppStorePurchases(client: client)
        let callback = RecordingPurchaseCallback()

        adapter.purchaseAppStoreProduct(productId: "app.saqz.organizador.mensal", appAccountToken: Self.token, done: callback)
        await waitUntil { callback.result != nil }

        let purchased = try? XCTUnwrap(callback.result as? AppStorePurchaseResultPurchased)
        XCTAssertEqual(purchased?.transaction.transactionId, "tx-1")
        XCTAssertEqual(client.purchases.first?.0, "app.saqz.organizador.mensal")
        XCTAssertEqual(client.purchases.first?.1, UUID(uuidString: Self.token))
        XCTAssertTrue(client.finished.isEmpty)

        adapter.finishAppStoreTransaction(transactionId: "tx-1")
        adapter.finishAppStoreTransaction(transactionId: "tx-1")
        await waitUntil { client.finished == ["tx-1"] }
        XCTAssertEqual(client.finished, ["tx-1"])
    }

    func testInvalidAccountTokenFailsWithoutOpeningTheStore() {
        let client = FakeAppStoreClient()
        let adapter = IOSAppStorePurchases(client: client)
        let callback = RecordingPurchaseCallback()

        adapter.purchaseAppStoreProduct(productId: "app.saqz.organizador.mensal", appAccountToken: "não-é-uuid", done: callback)

        XCTAssertTrue(callback.result is AppStorePurchaseResultFailed)
        XCTAssertTrue(client.purchases.isEmpty)
    }

    func testPendingAndCancelledPurchasesMapToKotlin() async {
        let client = FakeAppStoreClient()
        let adapter = IOSAppStorePurchases(client: client)

        client.purchaseOutcome = .pending
        let pending = RecordingPurchaseCallback()
        adapter.purchaseAppStoreProduct(productId: "p", appAccountToken: Self.token, done: pending)
        await waitUntil { pending.result != nil }
        client.purchaseOutcome = .cancelled
        let cancelled = RecordingPurchaseCallback()
        adapter.purchaseAppStoreProduct(productId: "p", appAccountToken: Self.token, done: cancelled)
        await waitUntil { cancelled.result != nil }

        XCTAssertTrue(pending.result is AppStorePurchaseResultPending)
        XCTAssertTrue(cancelled.result is AppStorePurchaseResultCancelled)
    }

    func testRestoreSyncsBeforeReadingEntitlements() async {
        let client = FakeAppStoreClient()
        client.entitlements = [FakeAppStoreClient.transaction("current")]
        let adapter = IOSAppStorePurchases(client: client)
        let callback = RecordingTransactionsCallback()

        adapter.restoreAppStorePurchases(done: callback)
        await waitUntil { callback.result != nil }

        XCTAssertEqual(client.calls, ["sync", "currentEntitlements"])
        let loaded = callback.result as? AppStoreTransactionsResultLoaded
        XCTAssertEqual(loaded?.transactions.map(\.transactionId), ["current"])
    }

    func testProductsCarryStoreKitPrices() async {
        let client = FakeAppStoreClient()
        client.products = [IOSAppStoreProductInfo(id: "p", displayName: "Organizador mensal", displayPrice: "R$ 59,90")]
        let adapter = IOSAppStorePurchases(client: client)
        let callback = RecordingProductsCallback()

        adapter.loadAppStoreProducts(productIds: ["p"], done: callback)
        await waitUntil { callback.result != nil }

        let loaded = callback.result as? AppStoreProductsResultLoaded
        XCTAssertEqual(loaded?.products.first?.displayPrice, "R$ 59,90")
    }

    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<200 where !condition() { await Task.yield() }
    }

    private static let token = "8F0C2C1E-0000-4000-8000-000000000001"
}

@MainActor
final class FakeAppStoreClient: IOSAppStoreClient {
    var products: [IOSAppStoreProductInfo] = []
    var purchaseOutcome: IOSAppStorePurchaseOutcome = .cancelled
    var entitlements: [IOSAppStoreTransaction] = []
    var unfinishedTransactions: [IOSAppStoreTransaction] = []
    private(set) var purchases: [(String, UUID)] = []
    private(set) var calls: [String] = []
    private(set) var finished: [String] = []
    private let stream: AsyncStream<IOSAppStoreTransaction>
    private let continuation: AsyncStream<IOSAppStoreTransaction>.Continuation

    init() {
        (stream, continuation) = AsyncStream.makeStream(of: IOSAppStoreTransaction.self)
    }

    static func transaction(_ id: String) -> IOSAppStoreTransaction {
        IOSAppStoreTransaction(id: id, jws: "jws-\(id)", finish: {})
    }

    func emitUpdate(_ transaction: IOSAppStoreTransaction) { continuation.yield(transaction) }

    func canMakePayments() -> Bool { true }
    func products(ids: [String]) async throws -> [IOSAppStoreProductInfo] { products }

    func purchase(productId: String, appAccountToken: UUID) async throws -> IOSAppStorePurchaseOutcome {
        purchases.append((productId, appAccountToken))
        guard case .purchased(let transaction) = purchaseOutcome else { return purchaseOutcome }
        return .purchased(IOSAppStoreTransaction(id: transaction.id, jws: transaction.jws, finish: { [weak self] in
            await self?.recordFinish(transaction.id)
        }))
    }

    func unfinished() async -> [IOSAppStoreTransaction] { unfinishedTransactions }
    func sync() async throws { calls.append("sync") }

    func currentEntitlements() async -> [IOSAppStoreTransaction] {
        calls.append("currentEntitlements")
        return entitlements
    }

    func updates() -> AsyncStream<IOSAppStoreTransaction> { stream }
    func showManageSubscriptions() async throws {}

    private func recordFinish(_ id: String) { finished.append(id) }
}

@MainActor
private final class RecordingTransactionListener: NSObject, @preconcurrency AppStoreTransactionListener {
    var ids: [String] = []
    var jws: [String] = []
    func onAppStoreTransactionUpdate(transaction: AppStoreSignedTransaction) {
        ids.append(transaction.transactionId)
        jws.append(transaction.signedTransaction)
    }
}

@MainActor
private final class RecordingPurchaseCallback: NSObject, @preconcurrency AppStorePurchaseCallback {
    var result: AppStorePurchaseResult?
    func onAppStorePurchase(result: AppStorePurchaseResult) { self.result = result }
}

@MainActor
private final class RecordingTransactionsCallback: NSObject, @preconcurrency AppStoreTransactionsCallback {
    var result: AppStoreTransactionsResult?
    func onAppStoreTransactions(result: AppStoreTransactionsResult) { self.result = result }
}

@MainActor
private final class RecordingProductsCallback: NSObject, @preconcurrency AppStoreProductsCallback {
    var result: AppStoreProductsResult?
    func onAppStoreProducts(result: AppStoreProductsResult) { self.result = result }
}
