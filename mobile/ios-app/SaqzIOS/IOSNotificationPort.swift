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
        guard let app = FirebaseApp.app(), app.options.projectID != "saqz-local" else { return }
        Messaging.messaging().delegate = self
        observers.append(NotificationCenter.default.addObserver(forName: .saqzAPNsReady, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.readToken() }
        })
        observers.append(NotificationCenter.default.addObserver(forName: .saqzAPNsFailed, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.finish(nil) }
        })
    }
    func device(done: @escaping (NotificationDevice?) -> Void) {
        guard let app = FirebaseApp.app(), app.options.projectID != "saqz-local" else { done(nil); return }
        pending = done
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { [weak self] granted, _ in
            Task { @MainActor in
                guard granted else { self?.finish(nil); return }
                UIApplication.shared.registerForRemoteNotifications()
                if Messaging.messaging().apnsToken != nil { self?.readToken() }
            }
        }
    }
    private func readToken() {
        guard pending != nil else { return }
        Messaging.messaging().token { [weak self] token, _ in
            Task { @MainActor in self?.finish(token) }
        }
    }
    private func finish(_ token: String?) {
        guard let callback = pending else { return }
        pending = nil
        guard let token else { callback(nil); return }
        let defaults = UserDefaults.standard
        let installation = defaults.string(forKey: "saqz.notificationInstallation") ?? UUID().uuidString
        defaults.set(installation, forKey: "saqz.notificationInstallation")
        callback(NotificationDevice(installationId: installation, token: token, platform: "IOS"))
    }
    func clear(done: @escaping (KotlinBoolean) -> Void) {
        guard let app = FirebaseApp.app(), app.options.projectID != "saqz-local" else { done(KotlinBoolean(bool: true)); return }
        let completion = NotificationClearCompletion(done)
        Messaging.messaging().deleteToken { error in
            Task { @MainActor in completion.finish(error == nil) }
        }
    }
    func observe(changed: @escaping () -> Void) -> any NotificationSubscription {
        let id = UUID()
        listeners[id] = changed
        return IOSNotificationSubscription { [weak self] in self?.listeners.removeValue(forKey: id) }
    }
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
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
}
@MainActor
final class SaqzPushDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
        NotificationCenter.default.post(name: .saqzAPNsReady, object: nil)
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: any Error) {
        NotificationCenter.default.post(name: .saqzAPNsFailed, object: nil)
    }
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }
}
