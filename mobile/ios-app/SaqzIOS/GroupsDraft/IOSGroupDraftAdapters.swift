import Foundation
import SaqzMobile

enum IOSDraftType: String, CaseIterable { case setup, game, monthly, expense }

struct IOSDraftRef: Equatable {
    let type: IOSDraftType
    let groupID: String?
    let resourceID: String?

    var storageKey: String {
        [type.rawValue, groupID.encodedDraftKey, resourceID.encodedDraftKey].joined(separator: ".")
    }

    static func from(storageKey: String) -> IOSDraftRef? {
        let parts = storageKey.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 3, let type = IOSDraftType(rawValue: parts[0]) else { return nil }
        return IOSDraftRef(type: type, groupID: parts[1].decodedDraftKey, resourceID: parts[2].decodedDraftKey)
    }
}

enum IOSDraftRead<Value> {
    case success(Value)
    case missing
    case corrupt
    case unsupportedSchema
}

protocol IOSDraftFileClient: AnyObject {
    func read(key: String) throws -> Data?
    func write(_ data: Data, key: String) throws
    func remove(key: String) throws
    func keys() throws -> [String]
}

final class IOSAppContainerDraftFileClient: IOSDraftFileClient {
    private let manager: FileManager
    private let directory: URL

    init(manager: FileManager = .default, directory: URL? = nil) {
        self.manager = manager
        self.directory = directory ?? manager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("GroupDrafts", isDirectory: true)
    }

    func read(key: String) throws -> Data? {
        let url = file(key)
        return manager.fileExists(atPath: url.path) ? try Data(contentsOf: url) : nil
    }

    func write(_ data: Data, key: String) throws {
        try manager.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: file(key), options: [.atomic, .completeFileProtection])
    }

    func remove(key: String) throws {
        let url = file(key)
        if manager.fileExists(atPath: url.path) { try manager.removeItem(at: url) }
    }

    func keys() throws -> [String] {
        guard manager.fileExists(atPath: directory.path) else { return [] }
        return try manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            .filter { $0.pathExtension == Self.extensionName }
            .map { $0.deletingPathExtension().lastPathComponent }
    }

    private func file(_ key: String) -> URL { directory.appendingPathComponent(key).appendingPathExtension(Self.extensionName) }
    private static let extensionName = "json"
}

final class IOSGroupDraftStore {
    private let files: IOSDraftFileClient
    private let codec: IOSGroupDraftCodec

    init(files: IOSDraftFileClient, codec: IOSGroupDraftCodec = IOSGroupDraftCodec()) {
        self.files = files
        self.codec = codec
    }

    func writeSetup(_ value: GroupSetupDraft) -> Bool {
        write(ref: setupRef(value), schema: value.schemaVersion, payload: codec.encodeSetup(value: value))
    }

    func readSetup(_ key: GroupDraftKey) -> IOSDraftRead<GroupSetupDraft> {
        decode(read(ref: setupRef(key)), schema: GroupSetupDraft.companion.CURRENT_SCHEMA_VERSION, version: { $0.schemaVersion }) { try codec.decodeSetup(value: $0) }
    }

    func writeGame(_ value: GameEditorDraft) -> Bool {
        write(ref: gameRef(value), schema: value.schemaVersion, payload: codec.encodeGame(value: value))
    }

    func readGame(groupID: String, resourceID: String?) -> IOSDraftRead<GameEditorDraft> {
        decode(read(ref: IOSDraftRef(type: .game, groupID: groupID, resourceID: resourceID)), schema: GameEditorDraft.companion.CURRENT_SCHEMA, version: { $0.schemaVersion }) { try codec.decodeGame(value: $0) }
    }

    func writeMonthly(_ value: MonthlyChargeDraft) -> Bool {
        write(ref: IOSDraftRef(type: .monthly, groupID: value.groupId, resourceID: nil), schema: value.schemaVersion, payload: codec.encodeMonthly(value: value))
    }

    func readMonthly(groupID: String) -> IOSDraftRead<MonthlyChargeDraft> {
        decode(read(ref: IOSDraftRef(type: .monthly, groupID: groupID, resourceID: nil)), schema: MonthlyChargeDraft.companion.CURRENT_SCHEMA, version: { $0.schemaVersion }) { try codec.decodeMonthly(value: $0) }
    }

    func writeExpense(_ value: ExpenseDraft) -> Bool {
        write(ref: IOSDraftRef(type: .expense, groupID: value.groupId, resourceID: nil), schema: value.schemaVersion, payload: codec.encodeExpense(value: value))
    }

    func readExpense(groupID: String) -> IOSDraftRead<ExpenseDraft> {
        decode(read(ref: IOSDraftRef(type: .expense, groupID: groupID, resourceID: nil)), schema: ExpenseDraft.companion.CURRENT_SCHEMA, version: { $0.schemaVersion }) { try codec.decodeExpense(value: $0) }
    }

    func clearSetup(_ key: GroupDraftKey, commandKey: String) -> Bool { clear(ref: setupRef(key), commandKey: commandKey) }
    func clearGame(groupID: String, resourceID: String?, commandKey: String) -> Bool { clear(ref: IOSDraftRef(type: .game, groupID: groupID, resourceID: resourceID), commandKey: commandKey) }
    func clearMonthly(groupID: String, commandKey: String) -> Bool { clear(ref: IOSDraftRef(type: .monthly, groupID: groupID, resourceID: nil), commandKey: commandKey) }
    func clearExpense(groupID: String, commandKey: String) -> Bool { clear(ref: IOSDraftRef(type: .expense, groupID: groupID, resourceID: nil), commandKey: commandKey) }

    func clearGroup(_ groupID: String) -> Bool { clearKeys { IOSDraftRef.from(storageKey: $0)?.groupID == groupID } }
    func clearAll() -> Bool { clearKeys { IOSDraftRef.from(storageKey: $0) != nil } }

    private func write(ref: IOSDraftRef, schema: Int32, payload: String) -> Bool {
        do {
            let payloadObject = try JSONSerialization.jsonObject(with: Data(payload.utf8))
            guard Self.safe(payloadObject) else { return false }
            let envelope: [String: Any] = [
                "storageVersion": Self.storageVersion,
                "type": ref.type.rawValue,
                "groupId": ref.groupID ?? NSNull(),
                "resourceId": ref.resourceID ?? NSNull(),
                "payload": payloadObject,
            ]
            try files.write(JSONSerialization.data(withJSONObject: envelope, options: [.sortedKeys]), key: ref.storageKey)
            return true
        } catch { return false }
    }

    private func read(ref: IOSDraftRef) -> IOSDraftRead<String> {
        do {
            guard let data = try files.read(key: ref.storageKey) else { return .missing }
            guard
                let envelope = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                Set(envelope.keys) == Set(["storageVersion", "type", "groupId", "resourceId", "payload"]),
                (envelope["storageVersion"] as? NSNumber)?.intValue == Self.storageVersion,
                envelope["type"] as? String == ref.type.rawValue,
                Self.optionalString(envelope["groupId"]) == ref.groupID,
                Self.optionalString(envelope["resourceId"]) == ref.resourceID,
                let payload = envelope["payload"] as? [String: Any],
                Self.safe(payload)
            else { return .corrupt }
            let encoded = try JSONSerialization.data(withJSONObject: payload, options: [.sortedKeys])
            guard let text = String(data: encoded, encoding: .utf8) else { return .corrupt }
            return .success(text)
        } catch { return .corrupt }
    }

    private func decode<Value>(_ result: IOSDraftRead<String>, schema: Int32, version: (Value) -> Int32, with operation: (String) throws -> Value) -> IOSDraftRead<Value> {
        switch result {
        case .success(let payload):
            do {
                let value = try operation(payload)
                return version(value) == schema ? .success(value) : .unsupportedSchema
            } catch { return .corrupt }
        case .missing: return .missing
        case .corrupt: return .corrupt
        case .unsupportedSchema: return .unsupportedSchema
        }
    }

    private func clear(ref: IOSDraftRef, commandKey: String) -> Bool {
        do {
            guard let data = try files.read(key: ref.storageKey) else { return true }
            guard let envelope = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let payload = envelope["payload"] as? [String: Any],
                  payload["commandKey"] as? String == commandKey else { return true }
            try files.remove(key: ref.storageKey)
            return true
        } catch { return false }
    }

    private func clearKeys(where predicate: (String) -> Bool) -> Bool {
        do {
            for key in try files.keys() where predicate(key) { try files.remove(key: key) }
            return true
        } catch { return false }
    }

    private func setupRef(_ value: GroupSetupDraft) -> IOSDraftRef { IOSDraftRef(type: .setup, groupID: value.groupId, resourceID: value.resource.name) }
    private func setupRef(_ key: GroupDraftKey) -> IOSDraftRef { IOSDraftRef(type: .setup, groupID: key.groupId, resourceID: key.resource.name) }
    private func gameRef(_ value: GameEditorDraft) -> IOSDraftRef { IOSDraftRef(type: .game, groupID: value.groupId, resourceID: value.gameId) }

    private static func optionalString(_ value: Any?) -> String? { value is NSNull ? nil : value as? String }
    private static func safe(_ value: Any) -> Bool {
        if let object = value as? [String: Any] {
            return object.keys.allSatisfy { !forbiddenKeys.contains($0) } && object.values.allSatisfy(safe)
        }
        if let array = value as? [Any] { return array.allSatisfy(safe) }
        return true
    }

    private static let storageVersion = 1
    private static let forbiddenKeys = Set(["bearerToken", "inviteCode", "photoBytes", "photoHandle", "paymentCredential", "rawServerError"])
}

struct IOSGroupDraftAdapters {
    let setup: IOSSetupDraftAdapter
    let game: IOSGameDraftAdapter
    let monthly: IOSMonthlyDraftAdapter
    let expense: IOSExpenseDraftAdapter
    let store: IOSGroupDraftStore

    static func makeLive() -> IOSGroupDraftAdapters {
        make(files: IOSAppContainerDraftFileClient())
    }

    static func make(files: IOSDraftFileClient) -> IOSGroupDraftAdapters {
        let store = IOSGroupDraftStore(files: files)
        return IOSGroupDraftAdapters(
            setup: IOSSetupDraftAdapter(store: store), game: IOSGameDraftAdapter(store: store),
            monthly: IOSMonthlyDraftAdapter(store: store), expense: IOSExpenseDraftAdapter(store: store), store: store
        )
    }
}

final class IOSSetupDraftAdapter: @preconcurrency GroupDraftStorePort {
    private let store: IOSGroupDraftStore
    init(store: IOSGroupDraftStore) { self.store = store }
    func read(key: GroupDraftKey, done: @escaping (any GroupDraftReadResult) -> Void) {
        switch store.readSetup(key) {
        case .success(let value): done(GroupDraftReadResultSuccess(draft: value))
        case .missing: done(GroupDraftReadResultSuccess(draft: nil))
        case .corrupt: done(GroupDraftReadResultFailure(reason: .corrupt))
        case .unsupportedSchema: done(GroupDraftReadResultFailure(reason: .unsupportedSchema))
        }
    }
    func write(draft: GroupSetupDraft, done: @escaping (any GroupDraftWriteResult) -> Void) { done(store.writeSetup(draft) ? GroupDraftWriteResultSuccess.shared : GroupDraftWriteResultFailure(reason: .unavailable)) }
    func clear(key: GroupDraftKey, commandKey: String, done: @escaping (any GroupDraftWriteResult) -> Void) { done(store.clearSetup(key, commandKey: commandKey) ? GroupDraftWriteResultSuccess.shared : GroupDraftWriteResultFailure(reason: .unavailable)) }
}

final class IOSGameDraftAdapter: @preconcurrency GameDraftStorePort {
    private let store: IOSGroupDraftStore
    init(store: IOSGroupDraftStore) { self.store = store }
    func read(groupId: String, resourceId: String?, done: @escaping (any GameDraftReadResult) -> Void) {
        switch store.readGame(groupID: groupId, resourceID: resourceId) { case .success(let value): done(GameDraftReadResultSuccess(draft: value)); case .missing: done(GameDraftReadResultSuccess(draft: nil)); default: done(GameDraftReadResultFailure.shared) }
    }
    func write(draft: GameEditorDraft, done___: @escaping (any GameDraftWriteResult) -> Void) { done___(store.writeGame(draft) ? GameDraftWriteResultSuccess.shared : GameDraftWriteResultFailure.shared) }
    func clear(groupId: String, resourceId: String?, commandKey: String, done: @escaping (any GameDraftWriteResult) -> Void) { done(store.clearGame(groupID: groupId, resourceID: resourceId, commandKey: commandKey) ? GameDraftWriteResultSuccess.shared : GameDraftWriteResultFailure.shared) }
}

final class IOSMonthlyDraftAdapter: @preconcurrency MonthlyChargeDraftStorePort {
    private let store: IOSGroupDraftStore
    init(store: IOSGroupDraftStore) { self.store = store }
    func read(groupId: String, done: @escaping (any MonthlyDraftReadResult) -> Void) { switch store.readMonthly(groupID: groupId) { case .success(let value): done(MonthlyDraftReadResultSuccess(draft: value)); case .missing: done(MonthlyDraftReadResultSuccess(draft: nil)); default: done(MonthlyDraftReadResultFailure.shared) } }
    func write(draft: MonthlyChargeDraft, done_: @escaping (any MonthlyDraftWriteResult) -> Void) { done_(store.writeMonthly(draft) ? MonthlyDraftWriteResultSuccess.shared : MonthlyDraftWriteResultFailure.shared) }
    func clear(groupId: String, commandKey: String, done: @escaping (any MonthlyDraftWriteResult) -> Void) { done(store.clearMonthly(groupID: groupId, commandKey: commandKey) ? MonthlyDraftWriteResultSuccess.shared : MonthlyDraftWriteResultFailure.shared) }
}

final class IOSExpenseDraftAdapter: @preconcurrency ExpenseDraftStorePort {
    private let store: IOSGroupDraftStore
    init(store: IOSGroupDraftStore) { self.store = store }
    func read(groupId: String, done_: @escaping (any ExpenseDraftReadResult) -> Void) { switch store.readExpense(groupID: groupId) { case .success(let value): done_(ExpenseDraftReadResultSuccess(draft: value)); case .missing: done_(ExpenseDraftReadResultSuccess(draft: nil)); default: done_(ExpenseDraftReadResultFailure.shared) } }
    func write(draft: ExpenseDraft, done__: @escaping (any ExpenseDraftWriteResult) -> Void) { done__(store.writeExpense(draft) ? ExpenseDraftWriteResultSuccess.shared : ExpenseDraftWriteResultFailure.shared) }
    func clear(groupId: String, expenseId: String?, commandKey: String, done: @escaping (any ExpenseDraftWriteResult) -> Void) { done(store.clearExpense(groupID: groupId, commandKey: commandKey) ? ExpenseDraftWriteResultSuccess.shared : ExpenseDraftWriteResultFailure.shared) }
}

private extension Optional where Wrapped == String {
    var encodedDraftKey: String { self?.data(using: .utf8)?.base64EncodedString().replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "=", with: "") ?? "_" }
}

private extension String {
    var decodedDraftKey: String? {
        guard self != "_" else { return nil }
        var value = replacingOccurrences(of: "_", with: "/").replacingOccurrences(of: "-", with: "+")
        value += String(repeating: "=", count: (4 - value.count % 4) % 4)
        return Data(base64Encoded: value).flatMap { String(data: $0, encoding: .utf8) }
    }
}
