import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSInviteUrlStoreTests: XCTestCase {
    func testPermanentCacheSurvivesRecreationAndDeletionIsGroupScoped() {
        let suite = "saqz.invite.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = IOSInviteUrlStore(defaults: defaults)
        let first = GroupInviteUrlCache(inviteUrl: "https://saqz.test/first", expiresAt: nil, revision: "revision-1")
        let second = GroupInviteUrlCache(inviteUrl: "https://saqz.test/second", expiresAt: nil, revision: "revision-2")
        store.write(groupId: "first", cache: first, done: InviteWriteAssertion())
        store.write(groupId: "second", cache: second, done: InviteWriteAssertion())
        let recreated = IOSInviteUrlStore(defaults: defaults)
        assertRead(recreated, groupId: "first", expected: first)
        recreated.write(groupId: "first", cache: nil, done: InviteWriteAssertion())
        assertRead(recreated, groupId: "first", expected: nil)
        assertRead(recreated, groupId: "second", expected: second)
        XCTAssertNil(defaults.string(forKey: "invite-revision:first"))
    }

    func testLegacyCacheHasNoRevisionAndReplacementRemovesDeadline() {
        let suite = "saqz.invite.tests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        defaults.set("https://saqz.test/legacy", forKey: "invite-url:legacy")
        defaults.set("2099-01-01T00:00:00Z", forKey: "invite-expires-at:legacy")
        let store = IOSInviteUrlStore(defaults: defaults)
        let legacy = GroupInviteUrlCache(inviteUrl: "https://saqz.test/legacy", expiresAt: "2099-01-01T00:00:00Z", revision: nil)
        assertRead(store, groupId: "legacy", expected: legacy)
        let permanent = GroupInviteUrlCache(inviteUrl: "https://saqz.test/new", expiresAt: nil, revision: "new-revision")
        store.write(groupId: "legacy", cache: permanent, done: InviteWriteAssertion())
        assertRead(IOSInviteUrlStore(defaults: defaults), groupId: "legacy", expected: permanent)
        XCTAssertNil(defaults.string(forKey: "invite-expires-at:legacy"))
    }

    private func assertRead(_ store: IOSInviteUrlStore, groupId: String, expected: GroupInviteUrlCache?) {
        let collector = InviteReadCollector()
        store.read(groupId: groupId, done: collector)
        XCTAssertEqual(collector.results.count, 1)
        guard let success = collector.results.first as? GroupInviteUrlReadResultSuccess else { return XCTFail("Expected cache read success") }
        XCTAssertEqual(success.cache, expected)
    }
}

private final class InviteReadCollector: GroupInviteUrlReadCallback {
    var results: [any GroupInviteUrlReadResult] = []
    func complete(result_____: any GroupInviteUrlReadResult) {
        results.append(result_____)
    }
}

private final class InviteWriteAssertion: GroupInviteUrlWriteCallback {
    func complete(result______: any GroupInviteUrlWriteResult) {
        XCTAssertTrue(result______ is GroupInviteUrlWriteResultSuccess)
    }
}
