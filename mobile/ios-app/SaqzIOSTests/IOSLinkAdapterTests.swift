import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSLinkAdapterTests: XCTestCase {
    func testReopeningAttendanceLinkDeliversAgainButBranchCopyDoesNot() {
        let fixture = Fixture(); fixture.start()
        let url = URL(string: "https://links.saqz.app/attendance/\(Self.codeA)")!
        fixture.adapter.onColdStart(url: url)
        fixture.branch.complete(["saqz_attendance": Self.codeA])
        fixture.adapter.onOpenURL(url)
        fixture.branch.complete(["saqz_attendance": Self.codeA])
        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
        activity.webpageURL = url
        fixture.adapter.onContinueUserActivity(activity)
        fixture.branch.complete(["saqz_attendance": Self.codeA])
        XCTAssertEqual(fixture.attendanceReceived, [Self.codeA, Self.codeA, Self.codeA])
    }

    func testOnboardingColdAndDeferredCopiesUseOnlyAccessListenerOnce() {
        let fixture = Fixture(); fixture.start(); fixture.startOnboarding()
        fixture.adapter.onColdStart(url: URL(string: "https://links.saqz.app/?saqz_onboarding=\(Self.codeA)"))
        fixture.branch.complete(["saqz_onboarding": Self.codeA])
        XCTAssertEqual(fixture.onboardingReceived, [Self.codeA])
        XCTAssertTrue(fixture.received.isEmpty)
        XCTAssertTrue(fixture.attendanceReceived.isEmpty)
    }

    func testDeferredOnboardingBeforeListenerAndNewWarmCode() {
        let fixture = Fixture()
        fixture.adapter.onColdStart(url: nil)
        fixture.branch.complete(["saqz_onboarding": Self.codeA])
        fixture.startOnboarding()
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/?saqz_onboarding=\(Self.codeB)")!)
        XCTAssertEqual(fixture.onboardingReceived, [Self.codeA, Self.codeB])
    }

    func testOnboardingRejectsUntrustedDuplicateAndMixedLinks() {
        let fixture = Fixture(); fixture.startOnboarding(); fixture.adapter.onColdStart(url: nil)
        for raw in [
            "https://evil.example/?saqz_onboarding=\(Self.codeA)",
            "https://links.saqz.app/?saqz_onboarding=\(Self.codeA)&saqz_onboarding=\(Self.codeA)",
            "https://links.saqz.app/?saqz_onboarding=\(Self.codeA)&saqz_invite=\(Self.codeB)",
            "https://links.saqz.app/attendance/\(Self.codeB)?saqz_onboarding=\(Self.codeA)"
        ] { fixture.adapter.onOpenURL(URL(string: raw)!) }
        fixture.branch.complete(["saqz_onboarding": Self.codeA, "saqz_attendance": Self.codeB])
        XCTAssertTrue(fixture.onboardingReceived.isEmpty)
    }

    func testOnboardingUsesConfiguredHost() {
        let branch = FakeBranchSessionClient()
        let adapter = IOSLinkAdapter(branch: branch, allowedHosts: ["configured.app.link"])
        var received: [String] = []
        let subscription = adapter.startAppOnboarding(listener: RecordingOnboardingListener { received.append($0) })
        adapter.onOpenURL(URL(string: "https://links.saqz.app/?saqz_onboarding=\(Self.codeA)")!)
        XCTAssertTrue(received.isEmpty)
        adapter.onOpenURL(URL(string: "https://configured.app.link/?saqz_onboarding=\(Self.codeA)")!)
        XCTAssertEqual(received, [Self.codeA])
        subscription.cancel()
    }

    func testColdAppLinkDeliversOnlyOpaqueInviteCode() {
        let fixture = Fixture(); fixture.start()
        let url = URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)&groupId=secret")!
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
        let handled = fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeB)")!)
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
        activity.webpageURL = URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)")
        XCTAssertTrue(fixture.adapter.onContinueUserActivity(activity)); XCTAssertEqual(fixture.received, [Self.codeA])
        XCTAssertEqual(fixture.branch.activities.count, 1)
    }

    func testNativeLinkDenialIsNoOpAndLaterLinkRecovers() {
        let fixture = Fixture(); fixture.start(); fixture.adapter.onColdStart(url: nil); fixture.branch.complete(nil)
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)")!)
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
            fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\($0)")!)
        }
        XCTAssertTrue(fixture.received.isEmpty)
    }

    func testNonHTTPSDirectURLIsRejected() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onOpenURL(URL(string: "http://links.saqz.app/invite?saqz_invite=\(Self.codeA)")!)
        fixture.adapter.onOpenURL(URL(string: "otherapp://invite?saqz_invite=\(Self.codeA)")!)
        XCTAssertTrue(fixture.received.isEmpty)
    }

    func testRegisteredAppSchemeDeliversInviteCode() {
        let fixture = Fixture(); fixture.start()
        let handled = fixture.adapter.onOpenURL(URL(string: "saqz://invite?saqz_invite=\(Self.codeA)")!)
        XCTAssertTrue(handled); XCTAssertEqual(fixture.received, [Self.codeA])
    }

    func testDirectAndBranchCopiesAreDeliveredOnce() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onColdStart(url: URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)"))
        fixture.branch.complete(["saqz_invite": Self.codeA])
        XCTAssertEqual(fixture.received, [Self.codeA])
    }

    func testRepeatedBranchCopiesAreDeliveredOnce() {
        let fixture = Fixture(); fixture.start(); fixture.adapter.onColdStart(url: nil)
        fixture.branch.complete(["saqz_invite": Self.codeA])
        fixture.branch.complete(["saqz_invite": Self.codeA])
        XCTAssertEqual(fixture.received, [Self.codeA])
    }

    func testNewerWarmLinkAfterDuplicateIsDelivered() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)")!)
        fixture.branch.complete(["saqz_invite": Self.codeA])
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeB)")!)
        XCTAssertEqual(fixture.received, [Self.codeA, Self.codeB])
    }

    func testLatestEventBeforeListenerWins() {
        let fixture = Fixture()
        fixture.adapter.onColdStart(url: URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)"))
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeB)")!)
        fixture.start(); XCTAssertEqual(fixture.received, [Self.codeB])
    }

    func testAttendanceLinkDispatchesTypedEvent() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/attendance?saqz_attendance=\(Self.codeA)")!)
        XCTAssertEqual(fixture.attendanceReceived, [Self.codeA])
        XCTAssertEqual(fixture.received, [])
    }

    func testCancellationStopsDeliveryWithoutStoppingBranchLifecycle() {
        let fixture = Fixture(); let cancellation = fixture.start(); cancellation.cancel()
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)")!)
        XCTAssertTrue(fixture.received.isEmpty); XCTAssertEqual(fixture.branch.urls.count, 1)
    }

    func testConfigurationSeparatesTestLiveKeysAndUniversalDomainWithoutSecrets() throws {
        let sourceRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        let info = try String(contentsOf: sourceRoot.appendingPathComponent("SaqzIOS/Info.plist"), encoding: .utf8)
        let entitlements = try String(contentsOf: sourceRoot.appendingPathComponent("SaqzIOS/SaqzIOS.entitlements"), encoding: .utf8)
        XCTAssertTrue(info.contains("<key>branch_key</key>")); XCTAssertTrue(info.contains("$(BRANCH_TEST_KEY)"))
        XCTAssertTrue(info.contains("$(BRANCH_LIVE_KEY)")); XCTAssertFalse(info.contains("saqz_invite"))
        XCTAssertTrue(info.contains("<key>CFBundleURLSchemes</key>")); XCTAssertTrue(info.contains("<string>saqz</string>"))
        XCTAssertTrue(entitlements.contains("applinks:$(BRANCH_DOMAIN)"))
    }

    func testNotificationTapIsBufferedUntilListenerAndDeliveredOnEveryTap() {
        let fixture = Fixture()
        fixture.adapter.onNotificationOpen(groupId: "group-1")
        fixture.start()
        fixture.adapter.onNotificationOpen(groupId: nil)
        XCTAssertEqual(fixture.notificationReceived, ["group-1", nil])
    }

    func testPushOpenedNotificationRoutesGroupIdToTheLinkPort() {
        let fixture = Fixture(); fixture.start()
        NotificationCenter.default.post(name: .saqzPushOpened, object: nil, userInfo: ["groupId": "group-9"])
        XCTAssertEqual(fixture.notificationReceived, ["group-9"])
    }

    @MainActor
    private final class Fixture {
        let branch = FakeBranchSessionClient(); lazy var adapter = IOSLinkAdapter(branch: branch)
        var received: [String] = []
        var attendanceReceived: [String] = []
        var onboardingReceived: [String] = []
        var notificationReceived: [String?] = []
        func startOnboarding() {
            _ = adapter.startAppOnboarding(listener: RecordingOnboardingListener { self.onboardingReceived.append($0) })
        }
        func start() -> GroupCancelable {
            adapter.start(listener_: RecordingLinkEventListener(
                invite: { self.received.append($0) },
                attendance: { self.attendanceReceived.append($0) },
                notification: { self.notificationReceived.append($0) },
            ))
        }
    }

    private static let codeA = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private static let codeB = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE"
}

@MainActor
private final class RecordingOnboardingListener: @preconcurrency AppOnboardingCodeListener {
    private let receive: (String) -> Void
    init(_ receive: @escaping (String) -> Void) { self.receive = receive }
    func onAppOnboardingCode(code: String) { receive(code) }
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
private final class RecordingLinkEventListener: @preconcurrency GroupLinkEventListener {
    private let invite: (String) -> Void
    private let attendance: (String) -> Void
    private let notification: (String?) -> Void
    init(invite: @escaping (String) -> Void, attendance: @escaping (String) -> Void, notification: @escaping (String?) -> Void = { _ in }) {
        self.invite = invite
        self.attendance = attendance
        self.notification = notification
    }
    func onEvent(event: GroupLinkEvent) {
        if let inviteEvent = event as? GroupLinkEventInvite {
            invite(inviteEvent.code)
        } else if let attendanceEvent = event as? GroupLinkEventAttendance {
            attendance(attendanceEvent.code)
        } else if let notificationEvent = event as? GroupLinkEventNotificationOpen {
            notification(notificationEvent.groupId)
        }
    }
}
