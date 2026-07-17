import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSLinkAdapterTests: XCTestCase {
    func testColdAppLinkDeliversOnlyOpaqueInviteCode() {
        let fixture = Fixture(); fixture.start()
        let url = URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeA)&groupId=secret")!
        fixture.adapter.onColdStart(url: url)
        XCTAssertEqual(fixture.received, [Self.codeA]); XCTAssertEqual(fixture.branch.initializeCount, 1)
    }

    func testDeferredBranchResultDeliversCodeWithoutLaunchURL() {
        let fixture = Fixture(); fixture.start(); fixture.adapter.onColdStart(url: nil)
        fixture.branch.complete(["saqz_invite": Self.codeA, "groupId": "secret"])
        XCTAssertEqual(fixture.received, [Self.codeA])
    }

    func testWarmURLUsesBranchAndSameListener() {
        let fixture = Fixture(); fixture.start()
        let handled = fixture.adapter.onOpenURL(URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeB)")!)
        XCTAssertTrue(handled); XCTAssertEqual(fixture.received, [Self.codeB]); XCTAssertEqual(fixture.branch.urls.count, 1)
    }

    func testWarmBranchCallbackUsesSameListenerAsDirectAndDeferred() {
        let fixture = Fixture(); fixture.start(); fixture.adapter.onColdStart(url: nil)
        fixture.branch.complete(["saqz_invite": Self.codeB])
        XCTAssertEqual(fixture.received, [Self.codeB])
    }

    func testUniversalLinkForwardsActivityAndDeliversCode() {
        let fixture = Fixture(); fixture.start()
        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
        activity.webpageURL = URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeA)")
        XCTAssertTrue(fixture.adapter.onContinueUserActivity(activity)); XCTAssertEqual(fixture.received, [Self.codeA])
        XCTAssertEqual(fixture.branch.activities.count, 1)
    }

    func testNativeLinkDenialIsNoOpAndLaterLinkRecovers() {
        let fixture = Fixture(); fixture.start(); fixture.adapter.onColdStart(url: nil); fixture.branch.complete(nil)
        fixture.adapter.onOpenURL(URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeA)")!)
        XCTAssertEqual(fixture.received, [Self.codeA])
    }

    func testUnrelatedParametersAndPIINeverBecomeInviteCode() {
        let fixture = Fixture(); fixture.start(); fixture.adapter.onColdStart(url: nil)
        fixture.branch.complete(["groupId": Self.codeA, "email": "person@example.test"])
        XCTAssertTrue(fixture.received.isEmpty)
    }

    func testInvalidBase64URLAlphabetPaddingAndLengthAreRejected() {
        let fixture = Fixture(); fixture.start()
        ["short", String(repeating: "A", count: 42) + "+", String(repeating: "A", count: 42) + "=", String(repeating: "A", count: 42) + "B"].forEach {
            fixture.adapter.onOpenURL(URL(string: "https://saqz.test-app.link/invite?saqz_invite=\($0)")!)
        }
        XCTAssertTrue(fixture.received.isEmpty)
    }

    func testNonHTTPSDirectURLIsRejected() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onOpenURL(URL(string: "saqz://invite?saqz_invite=\(Self.codeA)")!)
        XCTAssertTrue(fixture.received.isEmpty)
    }

    func testDirectAndBranchCopiesAreDeliveredOnce() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onColdStart(url: URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeA)"))
        fixture.branch.complete(["saqz_invite": Self.codeA])
        XCTAssertEqual(fixture.received, [Self.codeA])
    }

    func testLatestEventBeforeListenerWins() {
        let fixture = Fixture()
        fixture.adapter.onColdStart(url: URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeA)"))
        fixture.adapter.onOpenURL(URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeB)")!)
        fixture.start(); XCTAssertEqual(fixture.received, [Self.codeB])
    }

    func testCancellationStopsDeliveryWithoutStoppingBranchLifecycle() {
        let fixture = Fixture(); let cancellation = fixture.start(); cancellation.cancel()
        fixture.adapter.onOpenURL(URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.codeA)")!)
        XCTAssertTrue(fixture.received.isEmpty); XCTAssertEqual(fixture.branch.urls.count, 1)
    }

    func testConfigurationSeparatesTestLiveKeysAndUniversalDomainWithoutSecrets() throws {
        let sourceRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        let info = try String(contentsOf: sourceRoot.appendingPathComponent("SaqzIOS/Info.plist"), encoding: .utf8)
        let entitlements = try String(contentsOf: sourceRoot.appendingPathComponent("SaqzIOS/SaqzIOS.entitlements"), encoding: .utf8)
        XCTAssertTrue(info.contains("<key>branch_key</key>")); XCTAssertTrue(info.contains("$(BRANCH_TEST_KEY)"))
        XCTAssertTrue(info.contains("$(BRANCH_LIVE_KEY)")); XCTAssertFalse(info.contains("saqz_invite"))
        XCTAssertTrue(entitlements.contains("applinks:$(BRANCH_DOMAIN)"))
    }

    @MainActor
    private final class Fixture {
        let branch = FakeBranchSessionClient(); lazy var adapter = IOSLinkAdapter(branch: branch)
        var received: [String] = []
        func start() -> Cancelable { adapter.start(listener: RecordingInviteCodeListener { self.received.append($0) }) }
    }

    private static let codeA = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private static let codeB = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE"
}

@MainActor
private final class FakeBranchSessionClient: IOSBranchSessionClient {
    var initializeCount = 0; var urls: [URL] = []; var activities: [NSUserActivity] = []
    private var callback: (([String: Any]?) -> Void)?
    func initialize(callback: @escaping ([String: Any]?) -> Void) { initializeCount += 1; self.callback = callback }
    func handle(url: URL) -> Bool { urls.append(url); return true }
    func continueActivity(_ activity: NSUserActivity) -> Bool { activities.append(activity); return true }
    func complete(_ parameters: [String: Any]?) { callback?(parameters) }
}

@MainActor
private final class RecordingInviteCodeListener: @preconcurrency InviteCodeListener {
    private let action: (String) -> Void
    init(_ action: @escaping (String) -> Void) { self.action = action }
    func onInviteCode(code: String) { action(code) }
}
