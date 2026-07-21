import Foundation
import SaqzMobile
import Security
import UIKit

enum IOSAccessStateStoreError: Error { case unavailable }

@MainActor
protocol IOSAccessStateStore: AnyObject {
    func readSelectedGroupID() throws -> String?
    func writeSelectedGroupID(_ value: String?) throws
    func readPendingInvite() throws -> String?
    func writePendingInvite(_ value: String?) throws
    func readPendingAttendanceLink() throws -> String?
    func writePendingAttendanceLink(_ value: String?) throws
}

protocol IOSKeychainClient: AnyObject {
    func copyMatching(_ query: [String: Any]) -> (OSStatus, Data?)
    func add(_ attributes: [String: Any]) -> OSStatus
    func update(_ query: [String: Any], attributes: [String: Any]) -> OSStatus
    func delete(_ query: [String: Any]) -> OSStatus
}

final class LiveIOSKeychainClient: IOSKeychainClient {
    func copyMatching(_ query: [String: Any]) -> (OSStatus, Data?) {
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        return (status, result as? Data)
    }

    func add(_ attributes: [String: Any]) -> OSStatus { SecItemAdd(attributes as CFDictionary, nil) }
    func update(_ query: [String: Any], attributes: [String: Any]) -> OSStatus {
        SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
    }
    func delete(_ query: [String: Any]) -> OSStatus { SecItemDelete(query as CFDictionary) }
}

@MainActor
final class IOSUserDefaultsKeychainAccessStateStore: IOSAccessStateStore {
    static let selectedGroupKey = "saqz.access.selected-group-id"
    static let pendingInviteAccount = "pending-invite-v1"
    static let pendingAttendanceAccount = "pending-attendance-link-v1"
    private let defaults: UserDefaults
    private let keychain: IOSKeychainClient
    private let service: String

    init(defaults: UserDefaults = .standard, keychain: IOSKeychainClient = LiveIOSKeychainClient(), service: String = "app.saqz.access") {
        self.defaults = defaults; self.keychain = keychain; self.service = service
    }

    func readSelectedGroupID() throws -> String? { defaults.string(forKey: Self.selectedGroupKey) }

    func writeSelectedGroupID(_ value: String?) throws {
        if let value { defaults.set(value, forKey: Self.selectedGroupKey) }
        else { defaults.removeObject(forKey: Self.selectedGroupKey) }
    }

    func readPendingInvite() throws -> String? {
        try readSecret(account: Self.pendingInviteAccount)
    }

    func writePendingInvite(_ value: String?) throws {
        try writeSecret(value, account: Self.pendingInviteAccount)
    }

    func readPendingAttendanceLink() throws -> String? {
        try readSecret(account: Self.pendingAttendanceAccount)
    }

    func writePendingAttendanceLink(_ value: String?) throws {
        try writeSecret(value, account: Self.pendingAttendanceAccount)
    }

    private func readSecret(account: String) throws -> String? {
        var query = baseQuery(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        let (status, data) = keychain.copyMatching(query)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data, let value = String(data: data, encoding: .utf8) else {
            throw IOSAccessStateStoreError.unavailable
        }
        return value
    }

    private func writeSecret(_ value: String?, account: String) throws {
        guard let value else {
            let status = keychain.delete(baseQuery(account: account))
            guard status == errSecSuccess || status == errSecItemNotFound else { throw IOSAccessStateStoreError.unavailable }
            return
        }
        let data = Data(value.utf8)
        var updateAttributes: [String: Any] = [kSecValueData as String: data]
        updateAttributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        updateAttributes[kSecAttrSynchronizable as String] = false
        let status = keychain.update(baseQuery(account: account), attributes: updateAttributes)
        if status == errSecSuccess { return }
        guard status == errSecItemNotFound else { throw IOSAccessStateStoreError.unavailable }
        var addAttributes = baseQuery(account: account)
        updateAttributes.forEach { addAttributes[$0.key] = $0.value }
        guard keychain.add(addAttributes) == errSecSuccess else { throw IOSAccessStateStoreError.unavailable }
    }

    private func baseQuery(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrSynchronizable as String: false,
        ]
    }
}

@MainActor
final class IOSLocalAccessStateAdapter: @preconcurrency LocalAccessStatePort {
    private let store: IOSAccessStateStore
    init(store: IOSAccessStateStore) { self.store = store }

    func readSelectedGroupId(done: ValueCallback) { read(done, store.readSelectedGroupID) }
    func writeSelectedGroupId(value: String?, done: ResultCallback) { write(done) { try store.writeSelectedGroupID(value) } }
    func readPendingInvite(done: ValueCallback) { read(done, store.readPendingInvite) }
    func writePendingInvite(value: String?, done: ResultCallback) { write(done) { try store.writePendingInvite(value) } }

    private func read(_ done: ValueCallback, _ operation: () throws -> String?) {
        do { done.complete(result___: ValueResultSuccess(value: try operation())) }
        catch { done.complete(result___: ValueResultFailure(code: .providerUnavailable)) }
    }

    private func write(_ done: ResultCallback, _ operation: () throws -> Void) {
        do { try operation(); done.complete(result_: OperationResultSuccess.shared) }
        catch { done.complete(result_: OperationResultFailure(code: .providerUnavailable)) }
    }
}

@MainActor
final class IOSLocalGroupStateAdapter: @preconcurrency LocalGroupStatePort {
    private let store: IOSAccessStateStore
    init(store: IOSAccessStateStore) { self.store = store }

    func readSelectedGroupId(done_ done: GroupValueCallback) { read(done, store.readSelectedGroupID) }
    func writeSelectedGroupId(value: String?, done_ done: GroupResultCallback) { write(done) { try store.writeSelectedGroupID(value) } }
    func readPendingInvite(done_ done: GroupValueCallback) { read(done, store.readPendingInvite) }
    func writePendingInvite(value: String?, done_ done: GroupResultCallback) { write(done) { try store.writePendingInvite(value) } }
    func readPendingAttendanceLink(done: GroupValueCallback) { read(done, store.readPendingAttendanceLink) }
    func writePendingAttendanceLink(value: String?, done: GroupResultCallback) { write(done) { try store.writePendingAttendanceLink(value) } }

    private func read(_ done: GroupValueCallback, _ operation: () throws -> String?) {
        do { done.complete(result_____: GroupValueResultSuccess(value: try operation())) }
        catch { done.complete(result_____: GroupValueResultFailure(code: .unknown)) }
    }

    private func write(_ done: GroupResultCallback, _ operation: () throws -> Void) {
        do { try operation(); done.complete(result____: GroupOperationResultSuccess.shared) }
        catch { done.complete(result____: GroupOperationResultFailure(code: .unknown)) }
    }
}

@MainActor
protocol IOSShareLauncher: AnyObject { func launch(text: String) throws }

@MainActor
final class IOSShareAdapter: @preconcurrency NativeSharePort {
    private let launcher: IOSShareLauncher
    init(launcher: IOSShareLauncher) { self.launcher = launcher }
    func share(text: String, done: ResultCallback) {
        do { try launcher.launch(text: text); done.complete(result_: OperationResultSuccess.shared) }
        catch { done.complete(result_: OperationResultFailure(code: .providerUnavailable)) }
    }
    nonisolated var description: String { "IOSShareAdapter" }
}

@MainActor
final class IOSActivityShareLauncher: IOSShareLauncher {
    private let presenter: () -> UIViewController?
    init(presenter: @escaping () -> UIViewController?) { self.presenter = presenter }
    func launch(text: String) throws {
        guard let presenter = presenter() else { throw IOSAccessStateStoreError.unavailable }
        let controller = Self.makeController(text: text)
        presenter.present(controller, animated: true)
    }
    static func makeController(text: String) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        controller.view.isAccessibilityElement = false
        return controller
    }
}

@MainActor
enum IOSLocalAccessComposition {
    static func makeState() -> IOSLocalAccessStateAdapter {
        IOSLocalAccessStateAdapter(store: IOSUserDefaultsKeychainAccessStateStore())
    }
    static func makeShare(presenter: @escaping () -> UIViewController?) -> IOSShareAdapter {
        IOSShareAdapter(launcher: IOSActivityShareLauncher(presenter: presenter))
    }
}

@MainActor
final class IOSAttendanceShareAdapter: @preconcurrency GroupAttendanceSharePort {
    private let presenter: () -> UIViewController?
    private let directory: URL

    init(
        presenter: @escaping () -> UIViewController?,
        directory: URL = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("AttendanceShare", isDirectory: true)
    ) {
        self.presenter = presenter
        self.directory = directory
    }

    func shareLink(url: String, done: GroupResultCallback) {
        do {
            try present(items: [url])
            done.complete(result____: GroupOperationResultSuccess.shared)
        } catch {
            done.complete(result____: GroupOperationResultFailure(code: .unknown))
        }
    }

    func shareImage(image: AttendanceShareImageModel, done: GroupResultCallback) {
        do {
            let file = try render(image)
            try present(items: [file])
            done.complete(result____: GroupOperationResultSuccess.shared)
        } catch {
            done.complete(result____: GroupOperationResultFailure(code: .unknown))
        }
    }

    private func present(items: [Any]) throws {
        guard let presenter = presenter() else { throw IOSAccessStateStoreError.unavailable }
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        presenter.present(controller, animated: true)
    }

    private func render(_ image: AttendanceShareImageModel) throws -> URL {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let width: CGFloat = 1080
        let rowHeight: CGFloat = 56
        let height = CGFloat(image.heightUnits) * rowHeight
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height))
        let data = renderer.pngData { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width, height: height))
            let titleAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 48, weight: .semibold),
                .foregroundColor: UIColor.black,
            ]
            let bodyAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 32),
                .foregroundColor: UIColor.darkGray,
            ]
            var y: CGFloat = 48
            image.title.draw(at: CGPoint(x: 48, y: y), withAttributes: titleAttrs)
            y += 56
            image.scheduleLine.draw(at: CGPoint(x: 48, y: y), withAttributes: bodyAttrs)
            y += 40
            image.venueLine.draw(at: CGPoint(x: 48, y: y), withAttributes: bodyAttrs)
            y += 40
            image.capacityLine.draw(at: CGPoint(x: 48, y: y), withAttributes: bodyAttrs)
            y += 72
            image.sections.forEach { section in
                "\(section.title) (\(section.countLabel))".draw(at: CGPoint(x: 48, y: y), withAttributes: titleAttrs)
                y += 48
                if section.entries.isEmpty {
                    section.emptyLabel.draw(at: CGPoint(x: 48, y: y), withAttributes: bodyAttrs)
                    y += 56
                } else {
                    section.entries.forEach { entry in
                        entry.draw(at: CGPoint(x: 48, y: y), withAttributes: bodyAttrs)
                        y += 56
                    }
                }
                y += 24
            }
        }
        let url = directory.appendingPathComponent("attendance-share-\(UUID().uuidString).png")
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        return url
    }
}
