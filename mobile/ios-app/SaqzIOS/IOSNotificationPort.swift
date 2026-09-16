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
        callback(NotificationDevice(installationId: installation, token: token, platform: "IOS"))
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
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
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
        NSLog("[SaqzPush] toque no push: groupId=\(groupId ?? "-")")
        NotificationCenter.default.post(name: .saqzPushOpened, object: nil, userInfo: groupId.map { ["groupId": $0] })
        completionHandler()
    }
}
