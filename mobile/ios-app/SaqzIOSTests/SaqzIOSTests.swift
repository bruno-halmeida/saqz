import XCTest
import SaqzMobile
@testable import SaqzIOS

@MainActor
final class SaqzIOSTests: XCTestCase {
    func testMapAdapterReportsTheNativeCompletionInsteadOfAssumingSuccess() {
        var openedURL: URL?
        var nativeCompletion: ((Bool) -> Void)?
        let adapter = IOSMapAdapter { url, completion in
            openedURL = url
            nativeCompletion = completion
        }
        let callback = RecordingMapCallback()
        adapter.open(url: "https://www.google.com/maps/search/?api=1&query=S%C3%A3o%20Paulo", done: callback)
        XCTAssertEqual(openedURL?.absoluteString, "https://www.google.com/maps/search/?api=1&query=S%C3%A3o%20Paulo")
        XCTAssertEqual(callback.values, [])
        nativeCompletion?(false)
        XCTAssertEqual(callback.values, [false])
    }

    func testMapAdapterRejectsAnUnsupportedURL() {
        let callback = RecordingMapCallback()
        IOSMapAdapter { _, _ in XCTFail("Must not open an unsupported scheme") }.open(url: "bad://map", done: callback)
        XCTAssertEqual(callback.values, [false])
    }

    func testLocalFirebaseOptionsEndpointAndInitializationOrder() {
        let client = RecordingFirebaseBootstrapClient()

        let root = FirebaseBootstrap.makeRoot(client: client, configuration: .local) {
            client.events.append(.composeRootCreated)
            return "root"
        }

        XCTAssertEqual(root, "root")
        XCTAssertEqual(
            client.events,
            [
                .configured(
                    projectID: "saqz-local",
                    apiKey: "fake-saqz-local-api-key",
                    senderID: "123456789000",
                    appID: "1:123456789000:ios:5a61717a6c6f6361",
                    bundleID: "br.com.saqz.local"
                ),
                .authEmulator(host: "127.0.0.1", port: 9099),
                .composeRootCreated,
            ]
        )
    }

    func testDevelopmentFirebaseOptionsDoNotUseAuthEmulator() {
        let client = RecordingFirebaseBootstrapClient()
        let configuration = LocalFirebaseConfiguration(
            projectID: "saqz-dev",
            apiKey: "dev-api-key",
            senderID: "464989826191",
            appID: "1:464989826191:ios:52fe6125e199b95209d4fa",
            bundleID: "app.saqz",
            authEmulatorHost: nil,
            authEmulatorPort: nil
        )

        _ = FirebaseBootstrap.makeRoot(client: client, configuration: configuration) {
            client.events.append(.composeRootCreated)
        }

        XCTAssertEqual(
            client.events,
            [
                .configured(
                    projectID: "saqz-dev",
                    apiKey: "dev-api-key",
                    senderID: "464989826191",
                    appID: "1:464989826191:ios:52fe6125e199b95209d4fa",
                    bundleID: "app.saqz"
                ),
                .composeRootCreated,
            ]
        )
    }

    func testPushOutcomeBecomesTheLiveActivityStatusAndUnknownFailuresKeepTheButtons() {
        guard #available(iOS 16.1, *) else { return }
        XCTAssertEqual(SaqzGameAttributes.Status(.confirmed), .confirmed)
        XCTAssertEqual(SaqzGameAttributes.Status(.waitlisted), .waitlisted)
        XCTAssertEqual(SaqzGameAttributes.Status(.declined), .declined)
        XCTAssertEqual(SaqzGameAttributes.Status(.closed), .closed)
        XCTAssertEqual(SaqzGameAttributes.Status(.noresponse), .noResponse)
        XCTAssertEqual(SaqzGameAttributes.Status(.failed), .failed)
    }
}

private final class RecordingMapCallback: GroupMapCallback {
    var values: [Bool] = []
    func complete(opened: Bool) { values.append(opened) }
}

@MainActor
private final class RecordingFirebaseBootstrapClient: FirebaseBootstrapClient {
    enum Event: Equatable {
        case configured(projectID: String, apiKey: String, senderID: String, appID: String, bundleID: String)
        case authEmulator(host: String, port: Int)
        case composeRootCreated
    }

    var events: [Event] = []

    func configure(options: LocalFirebaseConfiguration) {
        events.append(
            .configured(
                projectID: options.projectID,
                apiKey: options.apiKey,
                senderID: options.senderID,
                appID: options.appID,
                bundleID: options.bundleID
            )
        )
    }

    func useAuthEmulator(host: String, port: Int) {
        events.append(.authEmulator(host: host, port: port))
    }
}
