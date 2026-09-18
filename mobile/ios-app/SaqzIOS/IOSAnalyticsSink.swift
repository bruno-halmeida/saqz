import FirebaseAnalytics
import FirebaseCore
import FirebaseCrashlytics
import SaqzMobile

/// Firebase Analytics e Crashlytics do app padrão; inertes no projeto local (mesma guarda do IOSNotificationPort).
final class IOSAnalyticsSink: AnalyticsSink {
    private let enabled: Bool

    init() {
        enabled = FirebaseApp.app().map { $0.options.projectID != "saqz-local" } ?? false
    }

    func track(name: String, params: [String: String]) {
        guard enabled else { return }
        Analytics.logEvent(name, parameters: params)
    }

    func setUserId(id: String?) {
        guard enabled else { return }
        Analytics.setUserID(id)
        Crashlytics.crashlytics().setUserID(id ?? "")
    }

    func log(message: String) {
        guard enabled else { return }
        Crashlytics.crashlytics().log(message)
    }
}
