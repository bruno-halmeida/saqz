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
final class IOSLinkAdapter: @preconcurrency NativeGroupLinkPort {
    private static let inviteParameter = "saqz_invite"
    private static let attendanceParameter = "saqz_attendance"
    private let branch: IOSBranchSessionClient
    private var listener: GroupLinkEventListener?
    private var pendingEvent: GroupLinkEvent?
    private var lastAcceptedEventKey: String?

    init(branch: IOSBranchSessionClient) {
        self.branch = branch
    }

    func start(listener_ listener: GroupLinkEventListener) -> GroupCancelable {
        self.listener = listener
        if let pendingEvent {
            listener.onEvent(event: pendingEvent)
            self.pendingEvent = nil
        }
        return IOSLinkCancellation { [weak self, weak listener] in
            guard self?.listener === listener else { return }
            self?.listener = nil
        }
    }

    func onColdStart(url: URL?) {
        accept(Self.directEvent(url))
        branch.initialize { [weak self] parameters in
            self?.accept(Self.branchEvent(parameters))
        }
    }

    @discardableResult
    func onOpenURL(_ url: URL) -> Bool {
        accept(Self.directEvent(url))
        return branch.handle(url: url)
    }

    @discardableResult
    func onContinueUserActivity(_ activity: NSUserActivity) -> Bool {
        accept(Self.directEvent(activity.webpageURL))
        return branch.continueActivity(activity)
    }

    private func accept(_ event: GroupLinkEvent?) {
        guard let event else { return }
        let eventKey: String = switch event {
        case let invite as GroupLinkEventInvite: "invite:\(invite.code)"
        case let attendance as GroupLinkEventAttendance: "attendance:\(attendance.code)"
        default: return
        }
        guard eventKey != lastAcceptedEventKey else { return }
        lastAcceptedEventKey = eventKey
        if let listener {
            listener.onEvent(event: event)
        } else {
            pendingEvent = event
        }
    }

    static func directEvent(_ url: URL?) -> GroupLinkEvent? {
        guard let url, url.scheme?.lowercased() == "https",
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let inviteValues = components.queryItems?.filter { $0.name == inviteParameter }.compactMap(\ .value).filter(isValidInviteCode) ?? []
        let attendanceValues = components.queryItems?.filter { $0.name == attendanceParameter }.compactMap(\ .value).filter(isValidInviteCode) ?? []
        if !inviteValues.isEmpty && !attendanceValues.isEmpty { return nil }
        if let invite = inviteValues.last { return GroupLinkEvent.Invite(code: invite) }
        if let attendance = attendanceValues.last { return GroupLinkEvent.Attendance(code: attendance) }
        let parts = url.path.split(separator: "/").map(String.init)
        if parts.count == 2, parts[0] == "attendance", isValidInviteCode(parts[1]) {
            return GroupLinkEvent.Attendance(code: parts[1])
        }
        return nil
    }

    static func branchEvent(_ parameters: [String: Any]?) -> GroupLinkEvent? {
        let invite = (parameters?[inviteParameter] as? String).flatMap { isValidInviteCode($0) ? $0 : nil }
        let attendance = (parameters?[attendanceParameter] as? String).flatMap { isValidInviteCode($0) ? $0 : nil }
        if invite != nil && attendance != nil { return nil }
        if let invite { return GroupLinkEvent.Invite(code: invite) }
        if let attendance { return GroupLinkEvent.Attendance(code: attendance) }
        return nil
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

private final class IOSLinkCancellation: GroupCancelable {
    private var action: (() -> Void)?
    init(_ action: @escaping () -> Void) { self.action = action }
    func cancel() { action?(); action = nil }
}
