import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSLinkAdapterTests: XCTestCase {
    func testReopeningAttendanceLinkDeliversAgain() {
        let fixture = Fixture(); fixture.start()
        let url = URL(string: "https://links.saqz.app/attendance/\(Self.codeA)")!
        fixture.adapter.onColdStart(url: url)
        fixture.adapter.onOpenURL(url)
        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
        activity.webpageURL = url
        fixture.adapter.onContinueUserActivity(activity)
        XCTAssertEqual(fixture.attendanceReceived, [Self.codeA, Self.codeA, Self.codeA])
    }

    func testOnboardingColdLinkUsesOnlyOnboardingListener() {
        let fixture = Fixture(); fixture.start(); fixture.startOnboarding()
        fixture.adapter.onColdStart(url: URL(string: "https://links.saqz.app/?saqz_onboarding=\(Self.codeA)"))
        XCTAssertEqual(fixture.onboardingReceived, [Self.codeA])
        XCTAssertTrue(fixture.received.isEmpty)
        XCTAssertTrue(fixture.attendanceReceived.isEmpty)
    }

    func testOnboardingBeforeListenerAndNewWarmCode() {
        let fixture = Fixture()
        fixture.adapter.onColdStart(url: URL(string: "https://links.saqz.app/?saqz_onboarding=\(Self.codeA)"))
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
        XCTAssertTrue(fixture.onboardingReceived.isEmpty)
    }

    func testOnboardingUsesConfiguredHost() {
        let adapter = IOSLinkAdapter(allowedHosts: ["configured.app.link"])
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
        XCTAssertEqual(fixture.received, [Self.codeA])
    }

    func testWarmURLUsesSameListener() {
        let fixture = Fixture(); fixture.start()
        let handled = fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeB)")!)
        XCTAssertTrue(handled); XCTAssertEqual(fixture.received, [Self.codeB])
    }

    func testUniversalLinkDeliversCode() {
        let fixture = Fixture(); fixture.start()
        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb)
        activity.webpageURL = URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)")
        XCTAssertTrue(fixture.adapter.onContinueUserActivity(activity)); XCTAssertEqual(fixture.received, [Self.codeA])
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

    func testNewerWarmLinkIsDelivered() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)")!)
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

    func testDeclineIntentTravelsOnlyWhenTheParameterSaysSo() {
        let fixture = Fixture(); fixture.start()
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/attendance/\(Self.codeA)?saqz_intent=decline")!)
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/?saqz_attendance=\(Self.codeB)&saqz_intent=maybe")!)
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/attendance/\(Self.codeA)?saqz_intent=decline&saqz_intent=decline")!)
        XCTAssertEqual(
            fixture.attendanceIntents,
            [AttendanceIntent.decline, AttendanceIntent.confirm]
        )
    }

    func testCancellationStopsDelivery() {
        let fixture = Fixture(); let cancellation = fixture.start(); cancellation.cancel()
        fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/invite?saqz_invite=\(Self.codeA)")!)
        XCTAssertTrue(fixture.received.isEmpty)
    }

    func testConfigurationDeclaresLinksDomainAndAppSchemeWithoutSecrets() throws {
        let sourceRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        let info = try String(contentsOf: sourceRoot.appendingPathComponent("SaqzIOS/Info.plist"), encoding: .utf8)
        let entitlements = try String(contentsOf: sourceRoot.appendingPathComponent("SaqzIOS/SaqzIOS.entitlements"), encoding: .utf8)
        XCTAssertTrue(info.contains("$(LINKS_DOMAIN)")); XCTAssertFalse(info.contains("saqz_invite"))
        XCTAssertTrue(info.contains("<key>CFBundleURLSchemes</key>")); XCTAssertTrue(info.contains("<string>saqz</string>"))
        XCTAssertTrue(entitlements.contains("applinks:$(LINKS_DOMAIN)"))
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
        let adapter = IOSLinkAdapter()
        var received: [String] = []
        var attendanceReceived: [String] = []
        var attendanceIntents: [AttendanceIntent] = []
        var onboardingReceived: [String] = []
        var notificationReceived: [String?] = []
        func startOnboarding() {
            _ = adapter.startAppOnboarding(listener: RecordingOnboardingListener { self.onboardingReceived.append($0) })
        }
        func start() -> GroupCancelable {
            adapter.start(listener_: RecordingLinkEventListener(
                invite: { self.received.append($0) },
                attendance: { self.attendanceReceived.append($0) },
                attendanceIntent: { self.attendanceIntents.append($0) },
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
private final class RecordingLinkEventListener: @preconcurrency GroupLinkEventListener {
    private let invite: (String) -> Void
    private let attendance: (String) -> Void
    private let attendanceIntent: (AttendanceIntent) -> Void
    private let notification: (String?) -> Void
    init(
        invite: @escaping (String) -> Void,
        attendance: @escaping (String) -> Void,
        attendanceIntent: @escaping (AttendanceIntent) -> Void = { _ in },
        notification: @escaping (String?) -> Void = { _ in }
    ) {
        self.invite = invite
        self.attendance = attendance
        self.attendanceIntent = attendanceIntent
        self.notification = notification
    }
    func onEvent(event: GroupLinkEvent) {
        if let inviteEvent = event as? GroupLinkEventInvite {
            invite(inviteEvent.code)
        } else if let attendanceEvent = event as? GroupLinkEventAttendance {
            attendance(attendanceEvent.code)
            attendanceIntent(attendanceEvent.intent)
        } else if let notificationEvent = event as? GroupLinkEventNotificationOpen {
            notification(notificationEvent.groupId)
        }
    }
}
