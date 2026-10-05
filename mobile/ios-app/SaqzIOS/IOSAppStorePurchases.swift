import Foundation
import SaqzMobile
import StoreKit
import UIKit

/// Uma transação do StoreKit como o Kotlin precisa dela: o JWS que vai ao backend e o
/// `finish()` que só roda depois que o backend respondeu.
struct IOSAppStoreTransaction: Sendable {
    let id: String
    let jws: String
    let finish: @Sendable () async -> Void
}

struct IOSAppStoreProductInfo: Equatable, Sendable {
    let id: String
    let displayName: String
    let displayPrice: String
}

enum IOSAppStorePurchaseOutcome: Sendable {
    case purchased(IOSAppStoreTransaction)
    case pending
    case cancelled
}

enum IOSAppStoreFailure: Error, Equatable {
    case productNotFound
    case noActiveScene
}

/// StoreKit 2 atrás de um protocolo: o adapter abaixo cuida do que o Kotlin vê (buffer de
/// `Transaction.updates`, `finish()` por id) e é testado com um cliente falso.
@MainActor
protocol IOSAppStoreClient: AnyObject {
    func canMakePayments() -> Bool
    func products(ids: [String]) async throws -> [IOSAppStoreProductInfo]
    func purchase(productId: String, appAccountToken: UUID) async throws -> IOSAppStorePurchaseOutcome
    func unfinished() async -> [IOSAppStoreTransaction]
    func sync() async throws
    func currentEntitlements() async -> [IOSAppStoreTransaction]
    func updates() -> AsyncStream<IOSAppStoreTransaction>
    func showManageSubscriptions() async throws
}

/// `AppStorePurchasesPort` do Kotlin. Escuta `Transaction.updates` desde a abertura do app,
/// como a Apple pede, e guarda o que chegar até o Kotlin registrar o listener. Transações não
/// verificadas também vão ao backend: é ele quem verifica a assinatura da Apple e decide.
@MainActor
final class IOSAppStorePurchases: NSObject, @preconcurrency AppStorePurchasesPort {
    private let client: IOSAppStoreClient
    private var listener: AppStoreTransactionListener?
    private var buffered: [AppStoreSignedTransaction] = []
    private var finishers: [String: @Sendable () async -> Void] = [:]
    private var updatesTask: Task<Void, Never>?

    init(client: IOSAppStoreClient) {
        self.client = client
        super.init()
        let updates = client.updates()
        updatesTask = Task { [weak self] in
            for await transaction in updates {
                self?.receive(transaction)
            }
        }
    }

    func canMakeAppStorePayments() -> Bool { client.canMakePayments() }

    func loadAppStoreProducts(productIds: [String], done: AppStoreProductsCallback) {
        Task {
            do {
                let products = try await client.products(ids: productIds).map {
                    AppStoreProduct(id: $0.id, displayName: $0.displayName, displayPrice: $0.displayPrice)
                }
                done.onAppStoreProducts(result: AppStoreProductsResultLoaded(products: products))
            } catch {
                done.onAppStoreProducts(result: AppStoreProductsResultFailed(message: String(describing: error)))
            }
        }
    }

    func purchaseAppStoreProduct(productId: String, appAccountToken: String, done: AppStorePurchaseCallback) {
        guard let token = UUID(uuidString: appAccountToken) else {
            done.onAppStorePurchase(result: AppStorePurchaseResultFailed(message: "appAccountToken inválido"))
            return
        }
        Task {
            do {
                switch try await client.purchase(productId: productId, appAccountToken: token) {
                case .purchased(let transaction):
                    done.onAppStorePurchase(result: AppStorePurchaseResultPurchased(transaction: track(transaction)))
                case .pending:
                    done.onAppStorePurchase(result: AppStorePurchaseResultPending.shared)
                case .cancelled:
                    done.onAppStorePurchase(result: AppStorePurchaseResultCancelled.shared)
                }
            } catch {
                done.onAppStorePurchase(result: AppStorePurchaseResultFailed(message: String(describing: error)))
            }
        }
    }

    func finishAppStoreTransaction(transactionId: String) {
        guard let finish = finishers.removeValue(forKey: transactionId) else { return }
        Task { await finish() }
    }

    func readUnfinishedAppStoreTransactions(done: AppStoreTransactionsCallback) {
        Task {
            let transactions = await client.unfinished().map(track)
            done.onAppStoreTransactions(result: AppStoreTransactionsResultLoaded(transactions: transactions))
        }
    }

    func restoreAppStorePurchases(done: AppStoreTransactionsCallback) {
        Task {
            do {
                try await client.sync()
                let transactions = await client.currentEntitlements().map(track)
                done.onAppStoreTransactions(result: AppStoreTransactionsResultLoaded(transactions: transactions))
            } catch {
                done.onAppStoreTransactions(result: AppStoreTransactionsResultFailed(message: String(describing: error)))
            }
        }
    }

    func listenForAppStoreTransactions(listener: AppStoreTransactionListener) {
        self.listener = listener
        let pending = buffered
        buffered = []
        pending.forEach { listener.onAppStoreTransactionUpdate(transaction: $0) }
    }

    func showAppStoreSubscriptionManagement(done: AppStoreManagementCallback) {
        Task {
            try? await client.showManageSubscriptions()
            done.onAppStoreManagementClosed()
        }
    }

    private func receive(_ transaction: IOSAppStoreTransaction) {
        let signed = track(transaction)
        if let listener {
            listener.onAppStoreTransactionUpdate(transaction: signed)
        } else {
            buffered.append(signed)
        }
    }

    private func track(_ transaction: IOSAppStoreTransaction) -> AppStoreSignedTransaction {
        finishers[transaction.id] = transaction.finish
        return AppStoreSignedTransaction(transactionId: transaction.id, signedTransaction: transaction.jws)
    }
}

@MainActor
final class LiveAppStoreClient: IOSAppStoreClient {
    func canMakePayments() -> Bool { AppStore.canMakePayments }

    func products(ids: [String]) async throws -> [IOSAppStoreProductInfo] {
        try await Product.products(for: ids).map {
            IOSAppStoreProductInfo(id: $0.id, displayName: $0.displayName, displayPrice: $0.displayPrice)
        }
    }

    func purchase(productId: String, appAccountToken: UUID) async throws -> IOSAppStorePurchaseOutcome {
        guard let product = try await Product.products(for: [productId]).first else {
            throw IOSAppStoreFailure.productNotFound
        }
        let options: Set<Product.PurchaseOption> = [.appAccountToken(appAccountToken)]
        let result: Product.PurchaseResult
        if #available(iOS 17.0, *), let scene = Self.activeScene {
            result = try await product.purchase(confirmIn: scene, options: options)
        } else {
            result = try await product.purchase(options: options)
        }
        switch result {
        case .success(let verification): return .purchased(Self.wrap(verification))
        case .pending: return .pending
        case .userCancelled: return .cancelled
        @unknown default: return .cancelled
        }
    }

    func unfinished() async -> [IOSAppStoreTransaction] {
        var transactions: [IOSAppStoreTransaction] = []
        for await result in Transaction.unfinished { transactions.append(Self.wrap(result)) }
        return transactions
    }

    func sync() async throws { try await AppStore.sync() }

    func currentEntitlements() async -> [IOSAppStoreTransaction] {
        var transactions: [IOSAppStoreTransaction] = []
        for await result in Transaction.currentEntitlements { transactions.append(Self.wrap(result)) }
        return transactions
    }

    func updates() -> AsyncStream<IOSAppStoreTransaction> {
        AsyncStream { continuation in
            let task = Task {
                for await result in Transaction.updates { continuation.yield(Self.wrap(result)) }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    func showManageSubscriptions() async throws {
        guard let scene = Self.activeScene else { throw IOSAppStoreFailure.noActiveScene }
        try await AppStore.showManageSubscriptions(in: scene)
    }

    private static var activeScene: UIWindowScene? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
    }

    /// O payload vai mesmo sem verificação local: o backend valida a cadeia da Apple.
    nonisolated private static func wrap(_ result: VerificationResult<Transaction>) -> IOSAppStoreTransaction {
        let transaction = result.unsafePayloadValue
        return IOSAppStoreTransaction(
            id: String(transaction.id),
            jws: result.jwsRepresentation,
            finish: { await transaction.finish() }
        )
    }
}
