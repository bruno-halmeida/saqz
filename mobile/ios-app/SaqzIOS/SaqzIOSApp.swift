import FirebaseAuth
import FirebaseCore
import SaqzMobile
import SwiftUI
import UIKit

struct IOSAppConfiguration: Equatable {
    let environment: String
    let apiBaseURL: String

    static func bundled(bundle: Bundle = .main) -> IOSAppConfiguration {
        IOSAppConfiguration(
            environment: bundle.object(forInfoDictionaryKey: "SaqzEnvironment") as? String ?? "local",
            apiBaseURL: bundle.object(forInfoDictionaryKey: "SaqzAPIBaseURL") as? String ?? "http://127.0.0.1:8080"
        )
    }
}

@MainActor
struct IOSAppComposition {
    let auth: IOSAuthAdapter
    let links: IOSLinkAdapter
    let localState: IOSLocalAccessStateAdapter
    let groupState: IOSLocalGroupStateAdapter
    let share: IOSShareAdapter
    let photos: IOSGroupPhotoAdapters
    let drafts: IOSGroupDraftAdapters
    let dependencies: SaqzAppDependencies

    static func makeLive(configuration: IOSAppConfiguration = .bundled()) -> IOSAppComposition {
        let auth = IOSAuthComposition.makeLive()
        let links = IOSLinkComposition.makeLive()
        let store = IOSUserDefaultsKeychainAccessStateStore()
        let localState = IOSLocalAccessStateAdapter(store: store)
        let groupState = IOSLocalGroupStateAdapter(store: store)
        let share = IOSLocalAccessComposition.makeShare { IOSPresentationRoot.current }
        let photos = IOSGroupPhotoAdapters.makeLive(presenter: { IOSPresentationRoot.current })
        let drafts = IOSGroupDraftAdapters.makeLive()
        return make(configuration: configuration, auth: auth, links: links, localState: localState, groupState: groupState, share: share, photos: photos, drafts: drafts)
    }

    static func make(
        configuration: IOSAppConfiguration,
        auth: IOSAuthAdapter,
        links: IOSLinkAdapter,
        localState: IOSLocalAccessStateAdapter,
        groupState: IOSLocalGroupStateAdapter,
        share: IOSShareAdapter,
        photos: IOSGroupPhotoAdapters,
        drafts: IOSGroupDraftAdapters
    ) -> IOSAppComposition {
        links.onColdStart(url: nil)
        let dependencies = SaqzAppDependencies(
            environment: configuration.environment,
            apiBaseUrl: configuration.apiBaseURL,
            auth: auth,
            links: IOSNoOpAccessLinkPort(),
            localState: localState,
            share: share,
            groupPhotos: GroupPhotoRuntimeDependencies(
                selection: photos.selection,
                encoder: photos.encoder,
                previews: photos.previews
            ),
            groupLinks: links,
            groupState: groupState,
            groupDrafts: drafts.setup,
            gameDrafts: drafts.game,
            monthlyChargeDrafts: drafts.monthly,
            expenseDrafts: drafts.expense
        )
        return IOSAppComposition(auth: auth, links: links, localState: localState, groupState: groupState, share: share, photos: photos, drafts: drafts, dependencies: dependencies)
    }
}

private final class IOSNoOpAccessLinkPort: @preconcurrency NativeLinkPort {
    func start(listener: InviteCodeListener) -> Cancelable { IOSNoOpAccessLinkCancellation() }
}

private final class IOSNoOpAccessLinkCancellation: Cancelable {
    func cancel() {}
}

@MainActor
final class IOSLifecycleRouter {
    private let auth: IOSAuthAdapter
    private let links: IOSLinkAdapter
    init(auth: IOSAuthAdapter, links: IOSLinkAdapter) { self.auth = auth; self.links = links }
    func open(_ url: URL) {
        _ = auth.handleGoogleURL(url)
        _ = links.onOpenURL(url)
    }
    func continueActivity(_ activity: NSUserActivity) { _ = links.onContinueUserActivity(activity) }
}

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
    private let router: IOSLifecycleRouter

    init() {
        let composition = FirebaseBootstrap.makeRoot(client: LiveFirebaseBootstrapClient()) {
            IOSAppComposition.makeLive()
        }
        router = IOSLifecycleRouter(auth: composition.auth, links: composition.links)
        root = ComposeRootView(dependencies: composition.dependencies)
    }

    var body: some Scene {
        WindowGroup {
            root
                .ignoresSafeArea()
                .onOpenURL(perform: router.open)
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb, perform: router.continueActivity)
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
enum IOSPresentationRoot {
    static var current: UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
        var controller = scene?.keyWindow?.rootViewController
        while let presented = controller?.presentedViewController { controller = presented }
        return controller
    }
}

struct ComposeRootView: UIViewControllerRepresentable {
    let dependencies: SaqzAppDependencies
    func makeCoordinator() -> Coordinator { Coordinator() }

    // Only the Compose controller: the app's accessibility tree comes entirely from Compose
    // semantics, with no synthetic UIKit accessibility element. The Reduce Motion / Reduce
    // Transparency observer lives in the coordinator and pushes two booleans into the same
    // Compose controller on launch and on every change.
    func makeUIViewController(context: Context) -> UIViewController {
        let controller = SaqzAccessibilityController()
        let viewController = MainViewControllerKt.MainViewController(
            accessibilityController: controller,
            dependencies: dependencies
        )
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
