@preconcurrency import BranchSDK
import Foundation
import SaqzMobile

@MainActor
protocol IOSBranchSessionClient: AnyObject {
    func initialize(callback: @escaping ([String: Any]?) -> Void)
    func handle(url: URL) -> Bool
    func continueActivity(_ activity: NSUserActivity) -> Bool
}

@MainActor
final class IOSLinkAdapter: @preconcurrency NativeLinkPort {
    private static let inviteParameter = "saqz_invite"
    private let branch: IOSBranchSessionClient
    private var listener: InviteCodeListener?
    private var pendingCode: String?
    private var lastAcceptedCode: String?

    init(branch: IOSBranchSessionClient) {
        self.branch = branch
    }

    func start(listener: InviteCodeListener) -> Cancelable {
        self.listener = listener
        if let pendingCode {
            listener.onInviteCode(code: pendingCode)
            self.pendingCode = nil
        }
        return IOSLinkCancellation { [weak self, weak listener] in
            guard self?.listener === listener else { return }
            self?.listener = nil
        }
    }

    func onColdStart(url: URL?) {
        accept(Self.directInviteCode(url))
        branch.initialize { [weak self] parameters in
            self?.accept(parameters?[Self.inviteParameter] as? String)
        }
    }

    @discardableResult
    func onOpenURL(_ url: URL) -> Bool {
        accept(Self.directInviteCode(url))
        return branch.handle(url: url)
    }

    @discardableResult
    func onContinueUserActivity(_ activity: NSUserActivity) -> Bool {
        accept(Self.directInviteCode(activity.webpageURL))
        return branch.continueActivity(activity)
    }

    private func accept(_ candidate: String?) {
        guard let code = candidate, Self.isValidInviteCode(code), code != lastAcceptedCode else { return }
        lastAcceptedCode = code
        if let listener {
            listener.onInviteCode(code: code)
        } else {
            pendingCode = code
        }
    }

    static func directInviteCode(_ url: URL?) -> String? {
        guard let url, url.scheme?.lowercased() == "https",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        return components.queryItems?.last { $0.name == inviteParameter }?.value
    }

    static func isValidInviteCode(_ value: String?) -> Bool {
        guard let value, value.count == 43,
              value.range(of: "^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$", options: .regularExpression) != nil else {
            return false
        }
        return true
    }
}

@MainActor
final class LiveBranchSessionClient: IOSBranchSessionClient {
    private let branch: Branch

    init(bundle: Bundle = .main, branch: Branch = .getInstance()) {
        self.branch = branch
        let configuredMode = bundle.object(forInfoDictionaryKey: "BranchTestMode")
        let usesTestKey = configuredMode as? Bool == true || (configuredMode as? String)?.uppercased() == "YES"
        if usesTestKey {
            Branch.setUseTestBranchKey(true)
        }
    }

    func initialize(callback: @escaping ([String: Any]?) -> Void) {
        branch.initSession(launchOptions: nil) { parameters, error in
            MainActor.assumeIsolated {
                callback(error == nil ? parameters as? [String: Any] : nil)
            }
        }
    }

    func handle(url: URL) -> Bool { branch.handleDeepLink(url) }

    func continueActivity(_ activity: NSUserActivity) -> Bool { branch.continue(activity) }
}

@MainActor
enum IOSLinkComposition {
    static func makeLive() -> IOSLinkAdapter {
        IOSLinkAdapter(branch: LiveBranchSessionClient())
    }
}

private final class IOSLinkCancellation: Cancelable {
    private var action: (() -> Void)?
    init(_ action: @escaping () -> Void) { self.action = action }
    func cancel() { action?(); action = nil }
}
