import Foundation
import SaqzMobile

@MainActor
final class IOSLinkAdapter: @preconcurrency NativeGroupLinkPort, @preconcurrency NativeLinkPort {
    private static let inviteParameter = "saqz_invite"
    private static let attendanceParameter = "saqz_attendance"
    private static let onboardingParameter = "saqz_onboarding"
    private static let intentParameter = "saqz_intent"
    private static let declineIntent = "decline"
    private static let settleParameter = "saqz_settle"
    private static let settlePath = "acerto"
    private let allowedHosts: Set<String>
    private var listeners: [ObjectIdentifier: GroupLinkEventListener] = [:]
    private var accessListeners: [ObjectIdentifier: InviteCodeListener] = [:]
    private var pendingEvent: GroupLinkEvent?
    private var pendingAccessCode: String?
    private var pendingOnboardingCode: String?
    // Só escrito no init e lido no deinit; o token do NotificationCenter não é Sendable.
    nonisolated(unsafe) private var pushObserver: NSObjectProtocol?

    init(allowedHosts: Set<String> = ["links.saqz.app"]) {
        self.allowedHosts = allowedHosts
        pushObserver = NotificationCenter.default.addObserver(forName: .saqzPushOpened, object: nil, queue: .main) { [weak self] note in
            let groupId = note.userInfo?["groupId"] as? String
            let gameId = note.userInfo?["gameId"] as? String
            let channel = note.userInfo?["channel"] as? String
            let pickPosition = note.userInfo?["pickPosition"] as? String == "true"
            let settleGameId = note.userInfo?["settleGameId"] as? String
            let settleBillingMonth = note.userInfo?["settleBillingMonth"] as? String
            MainActor.assumeIsolated {
                self?.onNotificationOpen(groupId: groupId, gameId: gameId, channel: channel, pickPosition: pickPosition,
                                         settleGameId: settleGameId, settleBillingMonth: settleBillingMonth)
            }
        }
    }

    deinit {
        if let pushObserver { NotificationCenter.default.removeObserver(pushObserver) }
    }

    /** Push tap: game pushes open the game, the rest the notification center; no dedup across taps. */
    func onNotificationOpen(groupId: String?, gameId: String?, channel: String? = nil, pickPosition: Bool = false,
                            settleGameId: String? = nil, settleBillingMonth: String? = nil) {
        let event = GroupLinkEventNotificationOpen(groupId: groupId, gameId: gameId, channel: channel, pickPosition: pickPosition,
                                                   settleGameId: settleGameId, settleBillingMonth: settleBillingMonth)
        if listeners.isEmpty {
            pendingEvent = event
        } else {
            pendingEvent = nil
            listeners.values.forEach { $0.onEvent(event: event) }
        }
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

    /// Devolve se a URL era um link do Saqz (convite, presença ou onboarding).
    @discardableResult
    func onColdStart(url: URL?) -> Bool {
        if let opened = Self.gameEvent(url) { accept(opened); return true }
        let onboarding = Self.directOnboardingCode(url, allowedHosts: allowedHosts)
        let event = Self.directEvent(url, allowedHosts: allowedHosts)
        acceptOnboarding(onboarding)
        accept(event)
        return onboarding != nil || event != nil
    }

    @discardableResult
    func onOpenURL(_ url: URL) -> Bool { onColdStart(url: url) }

    /// Card da Live Activity (VUL-268): `saqz://game?group=<id>&game=<id>` abre o jogo, como o toque no push; com
    /// `pickPosition=1` (jogo com posições, DEB-107) abre a tela de escolher posição.
    private static func gameEvent(_ url: URL?) -> GroupLinkEvent? {
        guard let url, url.scheme == "saqz", url.host == "game",
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems,
              let group = items.first(where: { $0.name == "group" })?.value,
              let game = items.first(where: { $0.name == "game" })?.value else { return nil }
        let pickPosition = items.first(where: { $0.name == "pickPosition" })?.value == "1"
        return GroupLinkEventNotificationOpen(groupId: group, gameId: game, channel: nil, pickPosition: pickPosition,
                                              settleGameId: nil, settleBillingMonth: nil)
    }

    @discardableResult
    func onContinueUserActivity(_ activity: NSUserActivity) -> Bool { onColdStart(url: activity.webpageURL) }

    private func accept(_ event: GroupLinkEvent?) {
        guard let event else { return }
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
        if onboardingListeners.isEmpty { pendingOnboardingCode = code }
        else { onboardingListeners.values.forEach { $0.onAppOnboardingCode(code: code) } }
    }

    static func directEvent(_ url: URL?) -> GroupLinkEvent? {
        directEvent(url, allowedHosts: ["links.saqz.app"])
    }

    private static func directEvent(_ url: URL?, allowedHosts: Set<String>) -> GroupLinkEvent? {
        guard let url, isDirectLinkScheme(url.scheme),
              (url.scheme?.lowercased() == "saqz" || allowedHosts.map { $0.lowercased() }.contains(url.host?.lowercased() ?? "")),
              url.user == nil, url.port == nil,
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        let inviteItems = components.queryItems?.filter { $0.name == inviteParameter } ?? []
        let attendanceItems = components.queryItems?.filter { $0.name == attendanceParameter } ?? []
        guard inviteItems.count <= 1, attendanceItems.count <= 1 else { return nil }
        let intentItems = components.queryItems?.filter { $0.name == intentParameter } ?? []
        guard intentItems.count <= 1 else { return nil }
        let intent = intentItems.last?.value == declineIntent ? AttendanceIntent.decline : AttendanceIntent.confirm
        let parts = url.path.split(separator: "/").map(String.init)
        let hasAttendancePath = parts.count == 2 && parts[0] == "attendance" && isValidInviteCode(parts[1])
        if hasAttendancePath && components.queryItems?.contains(where: {
            $0.name == inviteParameter || $0.name == attendanceParameter || $0.name == onboardingParameter ||
                $0.name == settleParameter
        }) == true { return nil }
        // Hora do acerto (FIN-013): /acerto/<código> ou ?saqz_settle=<código>, sozinho.
        let settleItems = components.queryItems?.filter { $0.name == settleParameter } ?? []
        let otherCodes = components.queryItems?.contains {
            $0.name == inviteParameter || $0.name == attendanceParameter || $0.name == onboardingParameter
        } == true
        if parts.count == 2 && parts[0] == settlePath {
            guard settleItems.isEmpty, !otherCodes, isValidSettleCode(parts[1]) else { return nil }
            return GroupLinkEventSettle(code: parts[1])
        }
        if !settleItems.isEmpty {
            guard settleItems.count == 1, !otherCodes, !hasAttendancePath, let code = settleItems[0].value,
                  isValidSettleCode(code) else { return nil }
            return GroupLinkEventSettle(code: code)
        }
        let inviteValues = inviteItems.compactMap(\.value).compactMap(normalizeInviteCode)
        let attendanceValues = attendanceItems.compactMap(\.value).filter(isValidInviteCode)
        let hasOnboardingParameter = components.queryItems?.contains { $0.name == onboardingParameter } == true
        if hasOnboardingParameter ||
            !inviteValues.isEmpty && !attendanceValues.isEmpty ||
            components.queryItems?.contains { $0.name == inviteParameter && $0.value != nil } == true &&
                components.queryItems?.contains { $0.name == attendanceParameter && $0.value != nil } == true { return nil }
        if let invite = inviteValues.last { return GroupLinkEventInvite(code: invite) }
        if let attendance = attendanceValues.last { return GroupLinkEventAttendance(code: attendance, intent: intent) }
        if hasAttendancePath {
            return GroupLinkEventAttendance(code: parts[1], intent: intent)
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
        let settle = components.queryItems?.contains { $0.name == settleParameter } == true
        guard !invite, !attendance, !settle, values.count == 1 else { return nil }
        return values.last
    }

    /// Convite de grupo: o token longo dos links antigos (como está) ou o código curto de 8
    /// caracteres (`44DF-8SA8`, aceito em minúscula e com hífen; segue adiante em maiúscula, sem hífen).
    static func normalizeInviteCode(_ value: String) -> String? {
        if isValidInviteCode(value) { return value }
        let short = value.filter { $0 != "-" && !$0.isWhitespace }.uppercased()
        return short.range(of: "^[2-9A-HJKMNP-Z]{8}$", options: .regularExpression) != nil ? short : nil
    }

    static func isValidSettleCode(_ value: String?) -> Bool {
        guard let value else { return false }
        return value.range(of: "^[A-Za-z0-9_-]{6,64}$", options: .regularExpression) != nil
    }

    static func isValidInviteCode(_ value: String?) -> Bool {
        guard let value, value.count == 43,
              value.range(of: "^[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]$", options: .regularExpression) != nil else {
            return false
        }
        return true
    }

    // HTTPS is the public invite link. `saqz` is the registered app scheme so
    // `simctl openurl` can launch the app; iOS sends https to Safari without AASA.
    private static func isDirectLinkScheme(_ scheme: String?) -> Bool {
        switch scheme?.lowercased() {
        case "https", "saqz": return true
        default: return false
        }
    }
}

@MainActor
enum IOSLinkComposition {
    static func makeLive(bundle: Bundle = .main) -> IOSLinkAdapter {
        let domain = bundle.object(forInfoDictionaryKey: "SaqzLinksDomain") as? String ?? "links.saqz.app"
        return IOSLinkAdapter(allowedHosts: [domain])
    }
}

private final class IOSLinkCancellation: GroupCancelable, Cancelable {
    private var action: (() -> Void)?
    init(_ action: @escaping () -> Void) { self.action = action }
    func cancel() { action?(); action = nil }
}
