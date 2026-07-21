import Foundation
import SaqzMobile
import Security
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSLocalAccessAdaptersTests: XCTestCase {
    func testSelectedGroupRoundTripsThroughUserDefaults() throws {
        let fixture = try StoreFixture(); try fixture.store.writeSelectedGroupID("group-42")
        XCTAssertEqual(try fixture.store.readSelectedGroupID(), "group-42")
    }

    func testSelectedGroupDeleteIsIdempotent() throws {
        let fixture = try StoreFixture(); try fixture.store.writeSelectedGroupID("group-42")
        try fixture.store.writeSelectedGroupID(nil); try fixture.store.writeSelectedGroupID(nil)
        XCTAssertNil(try fixture.store.readSelectedGroupID())
    }

    func testPendingInviteRoundTripsOnlyThroughKeychain() throws {
        let fixture = try StoreFixture(); try fixture.store.writePendingInvite("opaque-code")
        XCTAssertEqual(try fixture.store.readPendingInvite(), "opaque-code")
        XCTAssertNil(fixture.defaults.object(forKey: "pending-invite-v1"))
    }

    func testPendingInviteDeleteIsIdempotent() throws {
        let fixture = try StoreFixture(); try fixture.store.writePendingInvite(nil); try fixture.store.writePendingInvite(nil)
        XCTAssertEqual(fixture.keychain.deleteCount, 2); XCTAssertNil(try fixture.store.readPendingInvite())
    }

    func testKeychainIsAfterFirstUnlockThisDeviceOnlyAndNeverSynchronizable() throws {
        let fixture = try StoreFixture(); try fixture.store.writePendingInvite("opaque-code")
        let attributes = try XCTUnwrap(fixture.keychain.lastAdd)
        XCTAssertEqual(attributes[kSecAttrAccessible as String] as? String, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as String)
        XCTAssertEqual(attributes[kSecAttrSynchronizable as String] as? Bool, false)
    }

    func testExistingKeychainItemUpdatesWithoutDuplicateAdd() throws {
        let fixture = try StoreFixture(); try fixture.store.writePendingInvite("first"); try fixture.store.writePendingInvite("second")
        XCTAssertEqual(fixture.keychain.addCount, 1); XCTAssertEqual(try fixture.store.readPendingInvite(), "second")
    }

    func testRestartReadsBothStoresAndLogoutClearsBoth() throws {
        let fixture = try StoreFixture(); try fixture.store.writeSelectedGroupID("group-42"); try fixture.store.writePendingInvite("invite")
        let restarted = IOSUserDefaultsKeychainAccessStateStore(defaults: fixture.defaults, keychain: fixture.keychain, service: fixture.service)
        XCTAssertEqual(try restarted.readSelectedGroupID(), "group-42"); XCTAssertEqual(try restarted.readPendingInvite(), "invite")
        try restarted.writeSelectedGroupID(nil); try restarted.writePendingInvite(nil)
        XCTAssertNil(try restarted.readSelectedGroupID()); XCTAssertNil(try restarted.readPendingInvite())
    }

    func testStorageReadFailureMapsToProviderNeutralError() {
        let store = FakeAccessStateStore(); store.failReads = true; let adapter = IOSLocalAccessStateAdapter(store: store)
        let callback = RecordingValueCallback(); adapter.readPendingInvite(done: callback)
        XCTAssertEqual((callback.result as? ValueResultFailure)?.code, .providerUnavailable)
    }

    func testStorageWriteFailureMapsToProviderNeutralError() {
        let store = FakeAccessStateStore(); store.failWrites = true; let adapter = IOSLocalAccessStateAdapter(store: store)
        let callback = RecordingResultCallback(); adapter.writePendingInvite(value: "sensitive", done: callback)
        XCTAssertEqual((callback.result as? OperationResultFailure)?.code, .providerUnavailable)
    }

    func testSharePassesCompleteURLAndControllerAddsNoSyntheticAccessibilityNode() {
        let launcher = FakeShareLauncher(); let adapter = IOSShareAdapter(launcher: launcher); let callback = RecordingResultCallback()
        let url = "https://saqz.test-app.link/invite?saqz_invite=opaque_code"
        adapter.share(text: url, done: callback)
        XCTAssertEqual(launcher.values, [url]); XCTAssertTrue(callback.result is OperationResultSuccess)
        XCTAssertFalse(IOSActivityShareLauncher.makeController(text: url).view.isAccessibilityElement)
    }

    func testShareFailureIsRecoverableAndDescriptionRedactsText() {
        let launcher = FakeShareLauncher(); launcher.fail = true; let adapter = IOSShareAdapter(launcher: launcher); let callback = RecordingResultCallback()
        adapter.share(text: "https://example.test/?saqz_invite=sensitive", done: callback)
        XCTAssertEqual((callback.result as? OperationResultFailure)?.code, .providerUnavailable)
        XCTAssertEqual(adapter.description, "IOSShareAdapter"); XCTAssertFalse(adapter.description.contains("sensitive"))
    }
}

@MainActor
private final class StoreFixture {
    let defaults: UserDefaults; let keychain = FakeKeychainClient(); let service: String
    let store: IOSUserDefaultsKeychainAccessStateStore
    init() throws {
        service = "app.saqz.tests.\(UUID().uuidString)"
        defaults = try XCTUnwrap(UserDefaults(suiteName: service)); defaults.removePersistentDomain(forName: service)
        store = IOSUserDefaultsKeychainAccessStateStore(defaults: defaults, keychain: keychain, service: service)
    }
}

private final class FakeKeychainClient: IOSKeychainClient {
    var data: Data?; var addCount = 0; var deleteCount = 0; var lastAdd: [String: Any]?
    func copyMatching(_ query: [String: Any]) -> (OSStatus, Data?) { data.map { (errSecSuccess, $0) } ?? (errSecItemNotFound, nil) }
    func add(_ attributes: [String: Any]) -> OSStatus { addCount += 1; lastAdd = attributes; data = attributes[kSecValueData as String] as? Data; return errSecSuccess }
    func update(_ query: [String: Any], attributes: [String: Any]) -> OSStatus {
        guard data != nil else { return errSecItemNotFound }; data = attributes[kSecValueData as String] as? Data; return errSecSuccess
    }
    func delete(_ query: [String: Any]) -> OSStatus { deleteCount += 1; guard data != nil else { return errSecItemNotFound }; data = nil; return errSecSuccess }
}

@MainActor
private final class FakeAccessStateStore: IOSAccessStateStore {
    var selected: String?; var pending: String?; var pendingAttendance: String?; var failReads = false; var failWrites = false
    func readSelectedGroupID() throws -> String? { if failReads { throw IOSAccessStateStoreError.unavailable }; return selected }
    func writeSelectedGroupID(_ value: String?) throws { if failWrites { throw IOSAccessStateStoreError.unavailable }; selected = value }
    func readPendingInvite() throws -> String? { if failReads { throw IOSAccessStateStoreError.unavailable }; return pending }
    func writePendingInvite(_ value: String?) throws { if failWrites { throw IOSAccessStateStoreError.unavailable }; pending = value }
    func readPendingAttendanceLink() throws -> String? { if failReads { throw IOSAccessStateStoreError.unavailable }; return pendingAttendance }
    func writePendingAttendanceLink(_ value: String?) throws { if failWrites { throw IOSAccessStateStoreError.unavailable }; pendingAttendance = value }
}

@MainActor
private final class FakeShareLauncher: IOSShareLauncher {
    var values: [String] = []; var fail = false
    func launch(text: String) throws { if fail { throw IOSAccessStateStoreError.unavailable }; values.append(text) }
}

@MainActor
private final class RecordingValueCallback: @preconcurrency ValueCallback {
    var result: ValueResult?; func complete(result___: ValueResult) { result = result___ }
}

@MainActor
private final class RecordingResultCallback: @preconcurrency ResultCallback {
    var result: OperationResult?; func complete(result_: OperationResult) { result = result_ }
}
