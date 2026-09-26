// Activity não é Sendable; todo uso do card fica no @MainActor (LiveActivityAttendance).
@preconcurrency import ActivityKit
import Foundation
@preconcurrency import FirebaseMessaging
import FirebaseCore
import UIKit
import UserNotifications
import SaqzMobile

@MainActor
final class IOSNotificationPort: NSObject, @preconcurrency NativeNotificationPort, @preconcurrency MessagingDelegate {
    private var pending: ((NotificationDevice?) -> Void)?
    private var listeners: [UUID: () -> Void] = [:]
    private var observers: [NSObjectProtocol] = []
    /// Token de push-to-start da Live Activity (iOS 17.2+), em hex; vai no registro do aparelho.
    private var liveActivityStartToken: String?

    override init() {
        super.init()
        guard let app = FirebaseApp.app(), app.options.projectID != "saqz-local" else {
            NSLog("[SaqzPush] init: sem FirebaseApp ou projeto local — delegate/observers NAO registrados")
            return
        }
        NSLog("[SaqzPush] init: projeto \(app.options.projectID ?? "-") — delegate/observers registrados")
        Messaging.messaging().delegate = self
        observers.append(NotificationCenter.default.addObserver(forName: .saqzAPNsReady, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.readToken() }
        })
        observers.append(NotificationCenter.default.addObserver(forName: .saqzAPNsFailed, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.finish(nil) }
        })
        observeLiveActivityStartToken()
    }

    /// Token novo: guarda e avisa os listeners; o binding registra o aparelho de novo (VUL-264).
    private func observeLiveActivityStartToken() {
        guard #available(iOS 17.2, *) else { return }
        Task { [weak self] in
            for await data in Activity<SaqzGameAttributes>.pushToStartTokenUpdates {
                guard let self else { return }
                self.liveActivityStartToken = data.map { String(format: "%02x", $0) }.joined()
                NSLog("[SaqzPush] token de push-to-start da Live Activity recebido")
                for callback in self.listeners.values { callback() }
            }
        }
    }
    func device(done: @escaping (NotificationDevice?) -> Void) {
        NSLog("[SaqzPush] device: chamado")
        guard let app = FirebaseApp.app(), app.options.projectID != "saqz-local" else {
            NSLog("[SaqzPush] device: guard falhou (app=\(FirebaseApp.app() != nil) project=\(FirebaseApp.app()?.options.projectID ?? "-"))")
            done(nil); return
        }
        pending = done
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { [weak self] granted, _ in
            NSLog("[SaqzPush] device: permissao concedida=\(granted)")
            Task { @MainActor in
                guard granted else { self?.finish(nil); return }
                UIApplication.shared.registerForRemoteNotifications()
                if Messaging.messaging().apnsToken != nil { self?.readToken() }
            }
        }
    }
    private func readToken() {
        guard pending != nil else { return }
        NSLog("[SaqzPush] readToken: buscando FCM token")
        Messaging.messaging().token { [weak self] token, _ in
            NSLog("[SaqzPush] readToken: token=\(token != nil)")
            Task { @MainActor in self?.finish(token) }
        }
    }
    private func finish(_ token: String?) {
        guard let callback = pending else { return }
        pending = nil
        NSLog("[SaqzPush] finish: token=\(token != nil)")
        guard let token else { callback(nil); return }
        let defaults = UserDefaults.standard
        let installation = defaults.string(forKey: "saqz.notificationInstallation") ?? UUID().uuidString
        defaults.set(installation, forKey: "saqz.notificationInstallation")
        callback(NotificationDevice(installationId: installation, token: token, platform: "IOS", liveActivityStartToken: liveActivityStartToken))
    }
    func clear(done: @escaping (KotlinBoolean) -> Void) {
        NSLog("[SaqzPush] clear: chamado")
        guard let app = FirebaseApp.app(), app.options.projectID != "saqz-local" else { done(KotlinBoolean(bool: true)); return }
        // Sem token APNs não existe token FCM para revogar: revogação de nada é sucesso, não falha.
        guard Messaging.messaging().apnsToken != nil else {
            NSLog("[SaqzPush] clear: sem token APNs — nada a revogar (sucesso)")
            done(KotlinBoolean(bool: true)); return
        }
        let completion = NotificationClearCompletion(done)
        Messaging.messaging().deleteToken { error in
            // Code 6 = "checkin antes do registro": também é nada-a-revogar, não falha.
            let fcm = error as NSError?
            let ignorable = fcm?.domain == "com.google.fcm" && fcm?.code == 6
            NSLog("[SaqzPush] clear: deleteToken erro=\(error.map { String(describing: $0) } ?? "nenhum") ignoravel=\(ignorable)")
            Task { @MainActor in completion.finish(error == nil || ignorable) }
        }
    }
    func dismissAll() { UNUserNotificationCenter.current().removeAllDeliveredNotifications() }
    /// Resposta pelo app ou pelo link: some o push de presença daquele jogo e a Live Activity que ainda pedia resposta.
    func dismissAttendance(gameId: String) {
        Task {
            let center = UNUserNotificationCenter.current()
            let ids = await center.deliveredNotifications()
                .filter { ($0.request.content.userInfo["gameId"] as? String) == gameId }
                .map(\.request.identifier)
            center.removeDeliveredNotifications(withIdentifiers: ids)
        }
        if #available(iOS 17.0, *) { Task { await LiveActivityAttendance.dismiss(gameId: gameId) } }
    }
    func observe(changed: @escaping () -> Void) -> any NotificationSubscription {
        let id = UUID()
        listeners[id] = changed
        return IOSNotificationSubscription { [weak self] in self?.listeners.removeValue(forKey: id) }
    }
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        NSLog("[SaqzPush] didReceiveRegistrationToken: apns=\(messaging.apnsToken != nil) fcmToken=\(fcmToken != nil)")
        if messaging.apnsToken != nil { finish(fcmToken) }
        for callback in listeners.values { callback() }
    }
}

@MainActor
private final class NotificationClearCompletion {
    private let callback: (KotlinBoolean) -> Void
    init(_ callback: @escaping (KotlinBoolean) -> Void) { self.callback = callback }
    func finish(_ success: Bool) { callback(KotlinBoolean(bool: success)) }
}
@MainActor
private final class IOSNotificationSubscription: NSObject, @preconcurrency NotificationSubscription {
    private let action: () -> Void
    init(_ action: @escaping () -> Void) { self.action = action }
    func cancel() { action() }
}
extension Notification.Name {
    static let saqzAPNsReady = Notification.Name("saqz.apns.ready")
    static let saqzAPNsFailed = Notification.Name("saqz.apns.failed")
    static let saqzPushOpened = Notification.Name("saqz.push.opened")
}
@MainActor
final class SaqzPushDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    /// Entregue pelo `SaqzIOSApp.init`: a ação do push pode rodar sem tela, e o Koin sobe com isto.
    static var dependencies: SaqzPlatformDependencies?
    static let attendanceCategory = "SAQZ_ATTENDANCE"

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let center = UNUserNotificationCenter.current()
        center.delegate = self
        let confirm = UNNotificationAction(identifier: "CONFIRM", title: "Confirmar", options: [])
        let decline = UNNotificationAction(identifier: "DECLINE", title: "Não vou", options: [.destructive])
        center.setNotificationCategories([
            UNNotificationCategory(identifier: Self.attendanceCategory, actions: [confirm, decline], intentIdentifiers: []),
        ])
        if #available(iOS 17.0, *) { AttendanceWindowIntent.handler = LiveActivityAttendance.respond }
        return true
    }
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        NSLog("[SaqzPush] APNs registrado (\(deviceToken.count) bytes)")
        Messaging.messaging().apnsToken = deviceToken
        NotificationCenter.default.post(name: .saqzAPNsReady, object: nil)
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: any Error) {
        NSLog("[SaqzPush] APNs FALHOU: \(error)")
        NotificationCenter.default.post(name: .saqzAPNsFailed, object: nil)
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        let groupId = userInfo["groupId"] as? String
        let intents = ["CONFIRM": true, "DECLINE": false]
        if let groupId, let gameId = userInfo["gameId"] as? String, let recipient = userInfo["recipient"] as? String,
           let confirm = intents[response.actionIdentifier] {
            NSLog("[SaqzPush] ação no push: confirm=\(confirm) gameId=\(gameId)")
            nonisolated(unsafe) let done = completionHandler
            Task { @MainActor in
                Self.respondAttendance(groupId: groupId, gameId: gameId, recipient: recipient, confirm: confirm, completion: done)
            }
            return
        }
        NSLog("[SaqzPush] toque no push: groupId=\(groupId ?? "-")")
        var opened: [String: String] = [:]
        opened["groupId"] = groupId
        opened["gameId"] = userInfo["gameId"] as? String
        NotificationCenter.default.post(name: .saqzPushOpened, object: nil, userInfo: opened)
        completionHandler()
    }
    /// O sistema já descarta a notificação tocada; o resultado volta como notificação local.
    private static func respondAttendance(groupId: String, gameId: String, recipient: String, confirm: Bool, completion: @escaping () -> Void) {
        guard let dependencies else { completion(); return }
        PushAttendanceIosKt.respondPushAttendance(
            dependencies: dependencies, groupId: groupId, gameId: gameId, recipient: recipient, confirm: confirm, surface: "push"
        ) { outcome in
            let content = UNMutableNotificationContent()
            content.title = "Saqz"
            content.body = outcome.message
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: "attendance-\(gameId)", content: content, trigger: nil))
            completion()
        }
    }
}

private extension PushAttendanceOutcome {
    var message: String {
        switch self {
        case .confirmed: return "Presença confirmada."
        case .waitlisted: return "Jogo lotado: você entrou na lista de espera."
        case .declined: return "Ausência registrada."
        case .closed: return "Prazo encerrado. Abra o app para conferir."
        case .noresponse: return "Sem resposta do servidor. Abra o app para conferir."
        default: return "Não deu para registrar. Abra o app e tente de novo."
        }
    }
}

/// Botões do card da janela (VUL-268): "Enviando…", responde pelo mesmo caminho do push e encerra
/// mostrando o resultado por 2 min. Falha de rede mantém os botões (decisão de produto).
@available(iOS 17.0, *)
@MainActor
enum LiveActivityAttendance {
    static func respond(gameId: String, confirm: Bool) async {
        guard let activity = Activity<SaqzGameAttributes>.activities.first(where: { $0.attributes.gameId == gameId }),
              let dependencies = SaqzPushDelegate.dependencies else { return }
        let state = activity.content.state
        let staleDate = activity.content.staleDate
        await activity.update(ActivityContent(state: state.with(.sending), staleDate: staleDate))
        let status = await withCheckedContinuation { (continuation: CheckedContinuation<SaqzGameAttributes.Status, Never>) in
            PushAttendanceIosKt.respondPushAttendance(
                dependencies: dependencies, groupId: activity.attributes.groupId, gameId: gameId,
                recipient: activity.attributes.recipient, confirm: confirm, surface: "live_activity"
            ) { outcome in continuation.resume(returning: SaqzGameAttributes.Status(outcome)) }
        }
        if status == .failed {
            await activity.update(ActivityContent(state: state.with(.failed), staleDate: staleDate))
        } else {
            await activity.end(ActivityContent(state: state.with(status), staleDate: nil),
                dismissalPolicy: .after(Date().addingTimeInterval(120)))
        }
    }

    /// Resposta pelo app ou pelo link (VUL-267): encerra na hora o card que ainda pedia resposta.
    static func dismiss(gameId: String) async {
        for activity in Activity<SaqzGameAttributes>.activities
        where activity.attributes.gameId == gameId && [.pending, .failed].contains(activity.content.state.status) {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }
}

@available(iOS 16.1, *)
extension SaqzGameAttributes.Status {
    /// Resultado do push de presença no estado do card; qualquer outro vira FAILED, que devolve os botões.
    init(_ outcome: PushAttendanceOutcome) {
        switch outcome {
        case .confirmed: self = .confirmed
        case .waitlisted: self = .waitlisted
        case .declined: self = .declined
        case .closed: self = .closed
        case .noresponse: self = .noResponse
        default: self = .failed
        }
    }
}

@available(iOS 16.1, *)
private extension SaqzGameAttributes.ContentState {
    func with(_ status: SaqzGameAttributes.Status) -> Self {
        var copy = self
        copy.status = status
        return copy
    }
}
