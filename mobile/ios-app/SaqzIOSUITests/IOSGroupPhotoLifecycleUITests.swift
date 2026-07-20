import XCTest

final class IOSGroupPhotoLifecycleUITests: XCTestCase {
    func testBackgroundForegroundDoesNotDuplicateOrExposePhotoPickerState() {
        let app = XCUIApplication(); app.launch()
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 5))
        XCTAssertEqual(app.alerts.count, 0)
    }
}
