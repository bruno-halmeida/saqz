import UIKit
import XCTest
@testable import SaqzIOS

@MainActor
final class AccessibilityPreferencesObserverTests: XCTestCase {
    // Sendable box the injected closures capture, so the observer never captures the test case.
    @MainActor
    private final class Recorder {
        var motion = false
        var transparency = false
        var applied: [(Bool, Bool)] = []
    }

    private func makeObserver(_ recorder: Recorder, center: NotificationCenter) -> AccessibilityPreferencesObserver {
        AccessibilityPreferencesObserver(
            center: center,
            isReduceMotionEnabled: { recorder.motion },
            isReduceTransparencyEnabled: { recorder.transparency },
            apply: { recorder.applied.append(($0, $1)) }
        )
    }

    func testInitialValuesReachCompose() {
        let recorder = Recorder()
        recorder.motion = true
        recorder.transparency = true
        let observer = makeObserver(recorder, center: NotificationCenter())
        observer.start()
        defer { observer.stop() }
        XCTAssertEqual(recorder.applied.count, 1)
        XCTAssertEqual(recorder.applied.first?.0, true)
        XCTAssertEqual(recorder.applied.first?.1, true)
    }

    func testMotionNotificationUpdatesCompose() {
        let recorder = Recorder()
        let center = NotificationCenter()
        let observer = makeObserver(recorder, center: center)
        observer.start()
        defer { observer.stop() }
        recorder.motion = true
        center.post(name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
        XCTAssertEqual(recorder.applied.last?.0, true)
        XCTAssertEqual(recorder.applied.last?.1, false)
    }

    func testTransparencyNotificationUpdatesCompose() {
        let recorder = Recorder()
        let center = NotificationCenter()
        let observer = makeObserver(recorder, center: center)
        observer.start()
        defer { observer.stop() }
        recorder.transparency = true
        center.post(name: UIAccessibility.reduceTransparencyStatusDidChangeNotification, object: nil)
        XCTAssertEqual(recorder.applied.last?.0, false)
        XCTAssertEqual(recorder.applied.last?.1, true)
    }

    func testObserverStopsCleanly() {
        let recorder = Recorder()
        let center = NotificationCenter()
        let observer = makeObserver(recorder, center: center)
        observer.start()
        let countAfterStart = recorder.applied.count
        observer.stop()
        recorder.motion = true
        center.post(name: UIAccessibility.reduceMotionStatusDidChangeNotification, object: nil)
        XCTAssertEqual(recorder.applied.count, countAfterStart)
    }

    func testSwiftBoundaryHasOnlyTwoBooleans() {
        // Every push carries exactly the two booleans, in order — no third value crosses.
        let recorder = Recorder()
        let center = NotificationCenter()
        recorder.motion = true
        let observer = makeObserver(recorder, center: center)
        observer.start()
        defer { observer.stop() }
        recorder.transparency = true
        center.post(name: UIAccessibility.reduceTransparencyStatusDidChangeNotification, object: nil)
        XCTAssertEqual(recorder.applied.map { [$0.0, $0.1] }, [[true, false], [true, true]])
    }

    func testNoTypographyApiInSwift() throws {
        let sourceDir = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // SaqzIOSTests
            .deletingLastPathComponent() // ios-app
            .appendingPathComponent("SaqzIOS")
        let forbidden = ["UIFont", "TextStyle", "preferredContentSize", "ContentSizeCategory", "multiplier", ".clamp"]
        for name in ["AccessibilityPreferencesObserver.swift", "SaqzIOSApp.swift"] {
            let body = try String(contentsOf: sourceDir.appendingPathComponent(name), encoding: .utf8)
            for token in forbidden {
                XCTAssertFalse(body.contains(token), "\(name) must not reference \(token)")
            }
        }
    }
}
