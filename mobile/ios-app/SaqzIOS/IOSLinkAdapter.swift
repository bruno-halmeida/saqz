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
final class IOSLinkAdapter: @preconcurrency NativeGroupLinkPort, @preconcurrency NativeLinkPort {
    private static let inviteParameter = "saqz_invite"
    private static let attendanceParameter = "saqz_attendance"
    private static let onboardingParameter = "saqz_onboarding"
    private let branch: IOSBranchSessionClient
    private let allowedHosts: Set<String>
    private var listeners: [ObjectIdentifier: GroupLinkEventListener] = [:]
    private var accessListeners: [ObjectIdentifier: InviteCodeListener] = [:]
    private var pendingEvent: GroupLinkEvent?
    private var pendingAccessCode: String?
    private var pendingOnboardingCode: String?
    private var lastAcceptedEventKey: String?

    init(branch: IOSBranchSessionClient, allowedHosts: Set<String> = ["saqz.test-app.link"]) {
        self.branch = branch
        self.allowedHosts = allowedHosts
    }

    func start(listener: InviteCodeListener) -> Cancelable {
        let id = ObjectIdentifier(listener)
        accessListeners[id] = listener
        if let pendingAccessCode {
            listener.onInviteCode(code: pendingAccessCode)
            self.pendingAccessCode = nil
        }
        return IOSLinkCancellation { [weak self, weak listener] in
            guard let listener else { return }
            self?.accessListeners.removeValue(forKey: ObjectIdentifier(listener))
        }
    }

    func startAppOnboarding(listener: AppOnboardingCodeListener) -> Cancelable {
        let id = ObjectIdentifier(listener)
        onboardingListeners[id] = listener
        if let pendingOnboardingCode {
            listener.onAppOnboardingCode(code: pendingOnboardingCode)
            self.pendingOnboardingCode = nil
        }
        return IOSLinkCancellation { [weak self, weak listener] in
            guard let listener else { return }
            self?.onboardingListeners.removeValue(forKey: ObjectIdentifier(listener))
        }
    }

    func start(listener_ listener: GroupLinkEventListener) -> GroupCancelable {
        self.listeners[ObjectIdentifier(listener)] = listener
        if let pendingEvent {
            listener.onEvent(event: pendingEvent)
        }
        return IOSLinkCancellation { [weak self, weak listener] in
            guard let listener else { return }
            self?.listeners.removeValue(forKey: ObjectIdentifier(listener))
        }
    }

    func onColdStart(url: URL?) {
        lastAcceptedEventKey = nil // Deduplicate direct/Branch copies within this opening only.
        acceptOnboarding(Self.directOnboardingCode(url, allowedHosts: allowedHosts))
        accept(Self.directEvent(url, allowedHosts: allowedHosts))
        branch.initialize { [weak self] parameters in
            self?.acceptOnboarding(Self.branchOnboardingCode(parameters))
            self?.accept(Self.branchEvent(parameters))
        }
    }

    @discardableResult
    func onOpenURL(_ url: URL) -> Bool {
        lastAcceptedEventKey = nil // Deduplicate direct/Branch copies within this opening only.
        acceptOnboarding(Self.directOnboardingCode(url, allowedHosts: allowedHosts))
        accept(Self.directEvent(url, allowedHosts: allowedHosts))
        return branch.handle(url: url)
    }

    @discardableResult
    func onContinueUserActivity(_ activity: NSUserActivity) -> Bool {
        lastAcceptedEventKey = nil // Deduplicate direct/Branch copies within this opening only.
        acceptOnboarding(Self.directOnboardingCode(activity.webpageURL, allowedHosts: allowedHosts))
        accept(Self.directEvent(activity.webpageURL, allowedHosts: allowedHosts))
        return branch.continueActivity(activity)
    }

    private func accept(_ event: GroupLinkEvent?) {
        guard let event else { return }
        let eventKey: String
        if let invite = event as? GroupLinkEventInvite {
            eventKey = "invite:\(invite.code)"
        } else if let attendance = event as? GroupLinkEventAttendance {
            eventKey = "attendance:\(attendance.code)"
        } else {
            return
        }
        guard eventKey != lastAcceptedEventKey else { return }
        lastAcceptedEventKey = eventKey
        if listeners.isEmpty {
            pendingEvent = event
        } else {
            pendingEvent = nil
            listeners.values.forEach { $0.onEvent(event: event) }
        }
        if let invite = event as? GroupLinkEventInvite {
            if accessListeners.isEmpty { pendingAccessCode = invite.code }
            else { accessListeners.values.forEach { $0.onInviteCode(code: invite.code) } }
        }
    }

    private var onboardingListeners: [ObjectIdentifier: AppOnboardingCodeListener] = [:]

    private func acceptOnboarding(_ code: String?) {
        guard let code else { return }
        let key = "onboarding:\(code)"
        guard key != lastAcceptedEventKey else { return }
        lastAcceptedEventKey = key
        if onboardingListeners.isEmpty { pendingOnboardingCode = code }
        else { onboardingListeners.values.forEach { $0.onAppOnboardingCode(code: code) } }
    }

    static func directEvent(_ url: URL?) -> GroupLinkEvent? {
        directEvent(url, allowedHosts: ["saqz.test-app.link"])
    }

    private static func directEvent(_ url: URL?, allowedHosts: Set<String>) -> GroupLinkEvent? {
        guard let url, isDirectLinkScheme(url.scheme),
              (url.scheme?.lowercased() == "saqz" || allowedHosts.map { $0.lowercased() }.contains(url.host?.lowercased() ?? "")),
              url.user == nil, url.port == nil,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let inviteItems = components.queryItems?.filter { $0.name == inviteParameter } ?? []
        let attendanceItems = components.queryItems?.filter { $0.name == attendanceParameter } ?? []
        guard inviteItems.count <= 1, attendanceItems.count <= 1 else { return nil }
        let parts = url.path.split(separator: "/").map(String.init)
        let hasAttendancePath = parts.count == 2 && parts[0] == "attendance" && isValidInviteCode(parts[1])
        if hasAttendancePath && components.queryItems?.contains(where: {
            $0.name == inviteParameter || $0.name == attendanceParameter || $0.name == onboardingParameter
        }) == true { return nil }
        let inviteValues = inviteItems.compactMap(\.value).filter(isValidInviteCode)
        let attendanceValues = attendanceItems.compactMap(\.value).filter(isValidInviteCode)
        let hasOnboardingParameter = components.queryItems?.contains { $0.name == onboardingParameter } == true
        if hasOnboardingParameter ||
            !inviteValues.isEmpty && !attendanceValues.isEmpty ||
            components.queryItems?.contains { $0.name == inviteParameter && $0.value != nil } == true &&
                components.queryItems?.contains { $0.name == attendanceParameter && $0.value != nil } == true { return nil }
        if let invite = inviteValues.last { return GroupLinkEventInvite(code: invite) }
        if let attendance = attendanceValues.last { return GroupLinkEventAttendance(code: attendance) }
        if hasAttendancePath {
            return GroupLinkEventAttendance(code: parts[1])
        }
        return nil
    }

    private static func directOnboardingCode(_ url: URL?, allowedHosts: Set<String>) -> String? {
        guard let url, isDirectLinkScheme(url.scheme),
              (url.scheme?.lowercased() == "saqz" || allowedHosts.map { $0.lowercased() }.contains(url.host?.lowercased() ?? "")),
              url.user == nil, url.port == nil,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let onboardingItems = components.queryItems?.filter { $0.name == onboardingParameter } ?? []
        guard onboardingItems.count == 1 else { return nil }
        let parts = url.path.split(separator: "/").map(String.init)
        let hasAttendancePath = parts.count == 2 && parts[0] == "attendance" && isValidInviteCode(parts[1])
        if hasAttendancePath && components.queryItems?.contains(where: {
            $0.name == inviteParameter || $0.name == attendanceParameter || $0.name == onboardingParameter
        }) == true { return nil }
        let values = onboardingItems.compactMap(\.value).filter(isValidInviteCode)
        let invite = components.queryItems?.contains { $0.name == inviteParameter } == true
        let attendance = components.queryItems?.contains { $0.name == attendanceParameter } == true
        guard !invite, !attendance, values.count == 1 else { return nil }
        return values.last
    }

    static func branchEvent(_ parameters: [String: Any]?) -> GroupLinkEvent? {
        if parameters?[onboardingParameter] != nil { return nil }
        let invite = (parameters?[inviteParameter] as? String).flatMap { isValidInviteCode($0) ? $0 : nil }
        let attendance = (parameters?[attendanceParameter] as? String).flatMap { isValidInviteCode($0) ? $0 : nil }
        if invite != nil && attendance != nil { return nil }
        if let invite { return GroupLinkEventInvite(code: invite) }
        if let attendance { return GroupLinkEventAttendance(code: attendance) }
        return nil
    }

    private static func branchOnboardingCode(_ parameters: [String: Any]?) -> String? {
        let onboarding = (parameters?[onboardingParameter] as? String).flatMap { isValidInviteCode($0) ? $0 : nil }
        let invite = parameters?[inviteParameter] != nil
        let attendance = parameters?[attendanceParameter] != nil
        return !invite && !attendance ? onboarding : nil
    }

    static func isValidInviteCode(_ value: String?) -> Bool {
        guard let value, value.count == 43,
              value.range(of: "^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$", options: .regularExpression) != nil else {
            return false
        }
        return true
    }

    // HTTPS is the public Branch invite. `saqz` is the registered app scheme so
    // `simctl openurl` can launch the app; iOS sends https to Safari without AASA.
    private static func isDirectLinkScheme(_ scheme: String?) -> Bool {
        switch scheme?.lowercased() {
        case "https", "saqz": return true
        default: return false
        }
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
        // Formed on the MainActor: an isolated function value is Sendable, so only it
        // crosses the Branch callback boundary (same shape as LiveGoogleSignInClient.signIn).
        let deliver: @MainActor ([String: Any]?) -> Void = { callback($0) }
        branch.initSession(launchOptions: nil) { parameters, error in
            MainActor.assumeIsolated {
                deliver(error == nil ? parameters as? [String: Any] : nil)
            }
        }
    }

    func handle(url: URL) -> Bool { branch.handleDeepLink(url) }

    func continueActivity(_ activity: NSUserActivity) -> Bool { branch.continue(activity) }
}

@MainActor
enum IOSLinkComposition {
    static func makeLive(bundle: Bundle = .main) -> IOSLinkAdapter {
        let domain = (bundle.object(forInfoDictionaryKey: "branch_universal_link_domains") as? [String])?.first
            ?? "saqz.test-app.link"
        return IOSLinkAdapter(branch: LiveBranchSessionClient(), allowedHosts: [domain])
    }
}

private final class IOSLinkCancellation: GroupCancelable, Cancelable {
    private var action: (() -> Void)?
    init(_ action: @escaping () -> Void) { self.action = action }
    func cancel() { action?(); action = nil }
}
