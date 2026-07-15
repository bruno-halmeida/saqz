import FirebaseAuth
import FirebaseCore
import SaqzMobile
import SwiftUI
import UIKit

struct LocalFirebaseConfiguration: Equatable {
    let projectID = "saqz-local"
    let apiKey = "fake-saqz-local-api-key"
    let senderID = "123456789000"
    let appID = "1:123456789000:ios:5a61717a6c6f6361"
    let bundleID = "br.com.saqz.local"
    let authEmulatorHost = "127.0.0.1"
    let authEmulatorPort = 9099
}

@MainActor
protocol FirebaseBootstrapClient {
    func configure(options: LocalFirebaseConfiguration)
    func useAuthEmulator(host: String, port: Int)
}

@MainActor
private struct LiveFirebaseBootstrapClient: FirebaseBootstrapClient {
    func configure(options configuration: LocalFirebaseConfiguration) {
        let options = FirebaseOptions(
            googleAppID: configuration.appID,
            gcmSenderID: configuration.senderID
        )
        options.projectID = configuration.projectID
        options.apiKey = configuration.apiKey
        options.bundleID = configuration.bundleID
        FirebaseApp.configure(options: options)
    }

    func useAuthEmulator(host: String, port: Int) {
        Auth.auth().useEmulator(withHost: host, port: port)
    }
}

@MainActor
enum FirebaseBootstrap {
    static func makeRoot<Root>(
        client: FirebaseBootstrapClient,
        configuration: LocalFirebaseConfiguration = LocalFirebaseConfiguration(),
        root: () -> Root
    ) -> Root {
        client.configure(options: configuration)
        client.useAuthEmulator(
            host: configuration.authEmulatorHost,
            port: configuration.authEmulatorPort
        )
        return root()
    }
}

@main
struct SaqzIOSApp: App {
    private let root: ComposeRootView

    init() {
        root = FirebaseBootstrap.makeRoot(client: LiveFirebaseBootstrapClient()) {
            ComposeRootView()
        }
    }

    var body: some Scene {
        WindowGroup {
            root
        }
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController()
        let accessibilityText = UILabel(frame: CGRect(x: 0, y: 0, width: 1, height: 1))
        accessibilityText.accessibilityLabel = "Saqz"
        accessibilityText.isAccessibilityElement = true
        controller.view.addSubview(accessibilityText)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
