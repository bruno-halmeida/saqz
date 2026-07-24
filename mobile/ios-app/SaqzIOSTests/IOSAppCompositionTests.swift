import Foundation
import SaqzMobile
import UIKit
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSAppCompositionTests: XCTestCase {
    func testCompositionInjectsExactEnvironment() { XCTAssertEqual(makeFixture().composition.dependencies.environment, "dev") }
    func testCompositionInjectsExactAPIBaseURL() { XCTAssertEqual(makeFixture().composition.dependencies.apiBaseUrl, "http://127.0.0.1:8080") }

    func testCompositionInjectsExactAuthPort() {
        let fixture = makeFixture(); XCTAssertTrue((fixture.composition.dependencies.auth as AnyObject) === fixture.composition.auth)
    }

    func testCompositionInjectsExactGroupLinkPort() {
        let fixture = makeFixture(); XCTAssertTrue((fixture.composition.dependencies.groupLinks as AnyObject) === fixture.composition.links)
    }

    func testCompositionInjectsExactLocalStatePort() {
        let fixture = makeFixture(); XCTAssertTrue((fixture.composition.dependencies.localState as AnyObject) === fixture.composition.localState)
    }

    func testCompositionInjectsExactGroupStatePort() {
        let fixture = makeFixture(); XCTAssertTrue((fixture.composition.dependencies.groupState as AnyObject) === fixture.composition.groupState)
    }

    func testCompositionInjectsExactSharePort() {
        let fixture = makeFixture(); XCTAssertTrue((fixture.composition.dependencies.share as AnyObject) === fixture.composition.share)
    }

    func testCompositionInjectsAllExactGroupDraftPorts() {
        let composition = makeFixture().composition
        XCTAssertTrue((composition.dependencies.groupDrafts as AnyObject) === composition.drafts.setup)
        XCTAssertTrue((composition.dependencies.gameDrafts as AnyObject) === composition.drafts.game)
        XCTAssertTrue((composition.dependencies.monthlyChargeDrafts as AnyObject) === composition.drafts.monthly)
        XCTAssertTrue((composition.dependencies.expenseDrafts as AnyObject) === composition.drafts.expense)
    }

    func testBranchDeferredSessionStartsExactlyOnceBeforeComposeConsumption() {
        let fixture = makeFixture(); XCTAssertEqual(fixture.branch.initializeCount, 1)
    }

    func testDeferredInviteReceivedBeforeListenerSurvivesComposition() {
        let fixture = makeFixture(); fixture.branch.complete(["saqz_invite": Self.code])
        let listener = RecordingInviteListener(); _ = fixture.composition.dependencies.groupLinks.start(listener_: listener)
        XCTAssertEqual(listener.codes, [Self.code])
    }

    func testLifecycleRouterForwardsWarmURLToGoogleAndBranch() {
        let fixture = makeFixture(); let router = IOSLifecycleRouter(auth: fixture.composition.auth, links: fixture.composition.links)
        let url = URL(string: "https://saqz.test-app.link/invite?saqz_invite=\(Self.code)")!; router.open(url)
        XCTAssertEqual(fixture.google.urls, [url]); XCTAssertEqual(fixture.branch.urls, [url])
    }

    func testLifecycleRouterForwardsUniversalLinkToBranch() {
        let fixture = makeFixture(); let router = IOSLifecycleRouter(auth: fixture.composition.auth, links: fixture.composition.links)
        let activity = NSUserActivity(activityType: NSUserActivityTypeBrowsingWeb); activity.webpageURL = URL(string: "https://saqz.test-app.link/invite")
        router.continueActivity(activity); XCTAssertEqual(fixture.branch.activities.count, 1)
    }

    func testBackgroundForegroundUsesSameRetainedAdapterInstances() {
        let fixture = makeFixture(); let dependencies = fixture.composition.dependencies
        XCTAssertTrue((dependencies.auth as AnyObject) === fixture.composition.auth)
        XCTAssertTrue((dependencies.groupLinks as AnyObject) === fixture.composition.links)
    }

    func testComposeControllerHasNoSyntheticUIKitAccessibilityElement() {
        let fixture = makeFixture(); let accessibility = SaqzAccessibilityController()
        let controller = MainViewControllerKt.MainViewController(accessibilityController: accessibility, dependencies: fixture.composition.dependencies)
        XCTAssertFalse(controller.view.isAccessibilityElement); XCTAssertNil(controller.accessibilityLabel)
    }

    private func makeFixture() -> Fixture {
        let firebase = FakeFirebase(); let google = FakeGoogle(); let branch = FakeBranch(); let store = FakeStore(); let share = FakeShare()
        let auth = IOSAuthAdapter(firebase: firebase, google: google); let links = IOSLinkAdapter(branch: branch)
        let local = IOSLocalAccessStateAdapter(store: store); let groupState = IOSLocalGroupStateAdapter(store: store); let shareAdapter = IOSShareAdapter(launcher: share)
        let attendanceShare = IOSAttendanceShareAdapter(presenter: { nil })
        let drafts = IOSGroupDraftAdapters.make(files: FakeDraftFiles())
        let photos = IOSGroupPhotoAdapters.makeLive(presenter: { nil })
        let composition = IOSAppComposition.make(
            configuration: IOSAppConfiguration(environment: "dev", apiBaseURL: "http://127.0.0.1:8080"),
            auth: auth, links: links, localState: local, groupState: groupState,
            share: shareAdapter, attendanceShare: attendanceShare, photos: photos, drafts: drafts
        )
        return Fixture(composition: composition, google: google, branch: branch)
    }

    private struct Fixture { let composition: IOSAppComposition; let google: FakeGoogle; let branch: FakeBranch }
    private static let code = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
}

@MainActor private final class FakeFirebase: IOSFirebaseAuthClient {
    func observe(_ listener: @escaping (IOSAuthUser?) -> Void) -> IOSAuthObservation { IOSAuthObservation(id: 1) }
    func removeObservation(_ observation: IOSAuthObservation) {}
    func createAccount(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {}
    func signInWithPassword(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {}
    func signInWithGoogle(idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {}
    func sendVerification(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {}
    func reloadUser(completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {}
    func sendPasswordReset(email: String, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {}
    func updateDisplayName(_ name: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {}
    func idToken(forceRefresh: Bool, completion: @escaping (Result<String, IOSAuthFailure>) -> Void) {}
    func signOut(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {}
}

@MainActor private final class FakeGoogle: IOSGoogleSignInClient {
    var urls: [URL] = []; func signIn(completion: @escaping @MainActor (IOSGoogleSignInResult) -> Void) {}
    func handle(url: URL) -> Bool { urls.append(url); return true }
}

@MainActor private final class FakeBranch: IOSBranchSessionClient {
    var initializeCount = 0; var urls: [URL] = []; var activities: [NSUserActivity] = []; var callback: (([String: Any]?) -> Void)?
    func initialize(callback: @escaping ([String: Any]?) -> Void) { initializeCount += 1; self.callback = callback }
    func handle(url: URL) -> Bool { urls.append(url); return true }
    func continueActivity(_ activity: NSUserActivity) -> Bool { activities.append(activity); return true }
    func complete(_ parameters: [String: Any]?) { callback?(parameters) }
}

@MainActor private final class FakeStore: IOSAccessStateStore {
    func readSelectedGroupID() throws -> String? { nil }; func writeSelectedGroupID(_ value: String?) throws {}
    func readPendingInvite() throws -> String? { nil }; func writePendingInvite(_ value: String?) throws {}
    func readPendingAttendanceLink() throws -> String? { nil }; func writePendingAttendanceLink(_ value: String?) throws {}
}

@MainActor private final class FakeShare: IOSShareLauncher { func launch(text: String) throws {} }

private final class FakeDraftFiles: IOSDraftFileClient {
    var values: [String: Data] = [:]
    func read(key: String) throws -> Data? { values[key] }
    func write(_ data: Data, key: String) throws { values[key] = data }
    func remove(key: String) throws { values.removeValue(forKey: key) }
    func keys() throws -> [String] { Array(values.keys) }
}

@MainActor private final class RecordingInviteListener: @preconcurrency GroupLinkEventListener {
    var codes: [String] = []
    func onEvent(event: GroupLinkEvent) {
        if let invite = event as? GroupLinkEventInvite { codes.append(invite.code) }
    }
}
