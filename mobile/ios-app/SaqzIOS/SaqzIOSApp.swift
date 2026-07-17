import FirebaseAuth
import FirebaseCore
import SaqzMobile
import SwiftUI
import UIKit

struct LocalFirebaseConfiguration: Equatable {
    let projectID: String
    let apiKey: String
    let senderID: String
    let appID: String
    let bundleID: String
    let authEmulatorHost: String?
    let authEmulatorPort: Int?

    static let local = LocalFirebaseConfiguration(
        projectID: "saqz-local",
        apiKey: "fake-saqz-local-api-key",
        senderID: "123456789000",
        appID: "1:123456789000:ios:5a61717a6c6f6361",
        bundleID: "br.com.saqz.local",
        authEmulatorHost: "127.0.0.1",
        authEmulatorPort: 9099
    )

    static func bundled(bundle: Bundle = .main) -> LocalFirebaseConfiguration {
        guard
            let url = bundle.url(forResource: "GoogleService-Info", withExtension: "plist")
        else {
            return .local
        }

        guard
            let plist = NSDictionary(contentsOf: url) as? [String: Any],
            let projectID = plist["PROJECT_ID"] as? String,
            let apiKey = plist["API_KEY"] as? String,
            let senderID = plist["GCM_SENDER_ID"] as? String,
            let appID = plist["GOOGLE_APP_ID"] as? String,
            let bundleID = plist["BUNDLE_ID"] as? String
        else {
            preconditionFailure("Invalid bundled GoogleService-Info.plist")
        }

        return LocalFirebaseConfiguration(
            projectID: projectID,
            apiKey: apiKey,
            senderID: senderID,
            appID: appID,
            bundleID: bundleID,
            authEmulatorHost: nil,
            authEmulatorPort: nil
        )
    }
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
        configuration: LocalFirebaseConfiguration = .bundled(),
        root: () -> Root
    ) -> Root {
        client.configure(options: configuration)
        if let host = configuration.authEmulatorHost, let port = configuration.authEmulatorPort {
            client.useAuthEmulator(host: host, port: port)
        }
        return root()
    }
}

@main
struct SaqzIOSApp: App {
    private let root: ComposeRootView
    private let auth: IOSAuthAdapter

    init() {
        let composition = FirebaseBootstrap.makeRoot(client: LiveFirebaseBootstrapClient()) {
            let auth = IOSAuthComposition.makeLive()
            return (auth, ComposeRootView())
        }
        auth = composition.0
        root = composition.1
    }

    var body: some Scene {
        WindowGroup {
            root
                .onOpenURL { _ = auth.handleGoogleURL($0) }
        }
    }
}

@MainActor
enum IOSAuthComposition {
    static func makeLive(
        presenter: @escaping () -> UIViewController? = { IOSPresentationRoot.current }
    ) -> IOSAuthAdapter {
        IOSAuthAdapter(
            firebase: LiveFirebaseAuthClient(),
            google: LiveGoogleSignInClient(presentingViewController: presenter)
        )
    }
}

@MainActor
private enum IOSPresentationRoot {
    static var current: UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var controller = scene?.keyWindow?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    // Only the Compose controller: the app's accessibility tree comes entirely from Compose
    // semantics, with no synthetic UIKit accessibility element. The Reduce Motion / Reduce
    // Transparency observer lives in the coordinator and pushes two booleans into the same
    // Compose controller on launch and on every change.
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = SaqzAccessibilityController()
        let viewController = MainViewControllerKt.MainViewController(accessibilityController: controller)
        context.coordinator.start(controller: controller)
        return viewController
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    @MainActor
    final class Coordinator {
        private var observer: AccessibilityPreferencesObserver?

        func start(controller: SaqzAccessibilityController) {
            let observer = AccessibilityPreferencesObserver { reduceMotion, reduceTransparency in
                controller.update(reduceMotion: reduceMotion, reduceTransparency: reduceTransparency)
            }
            observer.start()
            self.observer = observer
        }
    }
}
