import XCTest
@testable import SaqzIOS

@MainActor
final class SaqzIOSTests: XCTestCase {
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
