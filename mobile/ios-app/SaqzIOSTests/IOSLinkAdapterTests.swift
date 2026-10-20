import Foundation
import SaqzMobile
import XCTest
import UserNotifications
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

    func testShortGroupCodeIsDeliveredNormalizedWithOrWithoutHyphen() {
        let fixture = Fixture(); fixture.start()
        ["44DF8SA8", "44DF-8SA8", "44df-8sa8", "44df8sa8"].forEach {
            fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/?saqz_invite=\($0)")!)
        }
        XCTAssertEqual(fixture.received, Array(repeating: "44DF8SA8", count: 4))
    }

    func testShortCodeWithAmbiguousCharactersOrWrongSizeIsRejected() {
        let fixture = Fixture(); fixture.start()
        ["44DF8SA0", "44DF8SAO", "44DF8SA1", "44DF8SAI", "44DF8SAL", "44DF8SA", "44DF8SA88"].forEach {
            fixture.adapter.onOpenURL(URL(string: "https://links.saqz.app/?saqz_invite=\($0)")!)
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
        fixture.adapter.onNotificationOpen(groupId: "group-1", gameId: nil)
        fixture.start()
        fixture.adapter.onNotificationOpen(groupId: nil, gameId: nil)
        XCTAssertEqual(fixture.notificationReceived, ["group-1", nil])
    }

    func testPushOpenedNotificationRoutesGroupIdToTheLinkPort() {
        let fixture = Fixture(); fixture.start()
        NotificationCenter.default.post(name: .saqzPushOpened, object: nil, userInfo: ["groupId": "group-9"])
        XCTAssertEqual(fixture.notificationReceived, ["group-9"])
    }

    func testPushOpenedNotificationCarriesTheGameToTheLinkPort() {
        let fixture = Fixture(); fixture.start()
        NotificationCenter.default.post(name: .saqzPushOpened, object: nil, userInfo: ["groupId": "group-9", "gameId": "game-9"])
        XCTAssertEqual(fixture.notificationReceived, ["group-9"])
        XCTAssertEqual(fixture.notificationGames, ["game-9"])
    }

    /// DEB-107: o "Confirmar" do push de jogo com posições abre o app na tela de posição; o toque comum, o jogo.
    func testPositionsConfirmActionAsksForThePositionScreen() {
        let fixture = Fixture(); fixture.start()
        let picked = SaqzPushDelegate.openedInfo(
            actionIdentifier: SaqzPushDelegate.pickPositionAction, userInfo: ["groupId": "group-9", "gameId": "game-9"])
        NotificationCenter.default.post(name: .saqzPushOpened, object: nil, userInfo: picked)
        let tapped = SaqzPushDelegate.openedInfo(
            actionIdentifier: UNNotificationDefaultActionIdentifier, userInfo: ["groupId": "group-9", "gameId": "game-9"])
        NotificationCenter.default.post(name: .saqzPushOpened, object: nil, userInfo: tapped)
        XCTAssertEqual(fixture.notificationGames, ["game-9", "game-9"])
        XCTAssertEqual(fixture.notificationPicks, [true, false])
    }

    /// As duas categorias de presença: a de jogo sem posições responde em segundo plano; a de jogo com posições abre o app.
    func testAttendanceCategoriesByGameKind() throws {
        let categories = SaqzPushDelegate.notificationCategories()
        let plain = try XCTUnwrap(categories.first { $0.identifier == "SAQZ_ATTENDANCE" })
        XCTAssertEqual(plain.actions.map(\.identifier), ["CONFIRM", "DECLINE"])
        XCTAssertFalse(plain.actions[0].options.contains(.foreground))
        let positions = try XCTUnwrap(categories.first { $0.identifier == "SAQZ_ATTENDANCE_POSITIONS" })
        XCTAssertEqual(positions.actions.map(\.identifier), ["PICK_POSITION", "DECLINE"])
        XCTAssertEqual(positions.actions.map(\.title), ["Confirmar", "Não vou"])
        XCTAssertTrue(positions.actions[0].options.contains(.foreground))
        XCTAssertFalse(positions.actions[1].options.contains(.foreground))
    }

    func testLiveActivityGameURLOpensTheGame() {
        let fixture = Fixture(); fixture.start()
        XCTAssertTrue(fixture.adapter.onOpenURL(URL(string: "saqz://game?group=g1&game=game1")!))
        XCTAssertEqual(fixture.notificationReceived, ["g1"])
        XCTAssertEqual(fixture.notificationGames, ["game1"])
    }

    @MainActor
    private final class Fixture {
        let adapter = IOSLinkAdapter()
        var received: [String] = []
        var attendanceReceived: [String] = []
        var attendanceIntents: [AttendanceIntent] = []
        var onboardingReceived: [String] = []
        var notificationReceived: [String?] = []
        var notificationGames: [String?] = []
        var notificationPicks: [Bool] = []
        func startOnboarding() {
            _ = adapter.startAppOnboarding(listener: RecordingOnboardingListener { self.onboardingReceived.append($0) })
        }
        func start() -> GroupCancelable {
            adapter.start(listener_: RecordingLinkEventListener(
                invite: { self.received.append($0) },
                attendance: { self.attendanceReceived.append($0) },
                attendanceIntent: { self.attendanceIntents.append($0) },
                notification: { self.notificationReceived.append($0) },
                notificationGame: { self.notificationGames.append($0) },
                notificationPick: { self.notificationPicks.append($0) },
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
final class RecordingLinkEventListener: @preconcurrency GroupLinkEventListener {
    private let invite: (String) -> Void
    private let attendance: (String) -> Void
    private let attendanceIntent: (AttendanceIntent) -> Void
    private let notification: (String?) -> Void
    private let notificationGame: (String?) -> Void
    private let notificationPick: (Bool) -> Void
    init(
        invite: @escaping (String) -> Void,
        attendance: @escaping (String) -> Void,
        attendanceIntent: @escaping (AttendanceIntent) -> Void = { _ in },
        notification: @escaping (String?) -> Void = { _ in },
        notificationGame: @escaping (String?) -> Void = { _ in },
        notificationPick: @escaping (Bool) -> Void = { _ in }
    ) {
        self.invite = invite
        self.attendance = attendance
        self.attendanceIntent = attendanceIntent
        self.notification = notification
        self.notificationGame = notificationGame
        self.notificationPick = notificationPick
    }
    func onEvent(event: GroupLinkEvent) {
        if let inviteEvent = event as? GroupLinkEventInvite {
            invite(inviteEvent.code)
        } else if let attendanceEvent = event as? GroupLinkEventAttendance {
            attendance(attendanceEvent.code)
            attendanceIntent(attendanceEvent.intent)
        } else if let notificationEvent = event as? GroupLinkEventNotificationOpen {
            notification(notificationEvent.groupId)
            notificationGame(notificationEvent.gameId)
            notificationPick(notificationEvent.pickPosition)
        }
    }
}
