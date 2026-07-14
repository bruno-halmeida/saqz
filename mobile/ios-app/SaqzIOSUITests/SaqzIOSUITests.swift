import XCTest

@MainActor
final class SaqzIOSUITests: XCTestCase {
    func testSharedComposePlaceholderIsAccessible() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 5))
    }
}
