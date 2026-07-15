import UIKit

// Minimal native adapter for the two accessibility preferences that Compose cannot read through
// a common API. It reads the current UIKit values, forwards them as two booleans, and re-forwards
// on each change notification. It knows nothing about typography, tokens, components or navigation.
@MainActor
final class AccessibilityPreferencesObserver {
    private let center: NotificationCenter
    private let isReduceMotionEnabled: @MainActor @Sendable () -> Bool
    private let isReduceTransparencyEnabled: @MainActor @Sendable () -> Bool
    private let apply: @MainActor @Sendable (_ reduceMotion: Bool, _ reduceTransparency: Bool) -> Void
    private var tokens: [NSObjectProtocol] = []

    init(
        center: NotificationCenter = .default,
        isReduceMotionEnabled: @escaping @MainActor @Sendable () -> Bool = { UIAccessibility.isReduceMotionEnabled },
        isReduceTransparencyEnabled: @escaping @MainActor @Sendable () -> Bool = { UIAccessibility.isReduceTransparencyEnabled },
        apply: @escaping @MainActor @Sendable (_ reduceMotion: Bool, _ reduceTransparency: Bool) -> Void
    ) {
        self.center = center
        self.isReduceMotionEnabled = isReduceMotionEnabled
        self.isReduceTransparencyEnabled = isReduceTransparencyEnabled
        self.apply = apply
    }

    func start() {
        push()
        observe(UIAccessibility.reduceMotionStatusDidChangeNotification)
        observe(UIAccessibility.reduceTransparencyStatusDidChangeNotification)
    }

    func stop() {
        tokens.forEach { center.removeObserver($0) }
        tokens.removeAll()
    }

    private func observe(_ name: Notification.Name) {
        // queue: nil delivers synchronously on the posting thread; the accessibility
        // notifications are posted on the main thread.
        tokens.append(
            center.addObserver(forName: name, object: nil, queue: nil) { [weak self] _ in
                MainActor.assumeIsolated { self?.push() }
            }
        )
    }

    private func push() {
        apply(isReduceMotionEnabled(), isReduceTransparencyEnabled())
    }
}
