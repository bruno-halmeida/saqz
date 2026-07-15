import XCTest

// Dynamic Type is owned by Compose and applied exactly once: at the largest category the content
// reflows and stays reachable, and the heading scales up without a doubled multiplier.
@MainActor
final class AccessibilityUITests: XCTestCase {
    private func launch(category: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-UIPreferredContentSizeCategoryName", category]
        app.launch()
        return app
    }

    func testLargestDynamicTypeReflows() {
        let app = launch(category: "UICTContentSizeCategoryAccessibilityXXXL")
        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 10))
        let action = app.staticTexts["Explorar componentes"]
        XCTAssertTrue(action.waitForExistence(timeout: 5))
        XCTAssertTrue(action.isHittable)
    }

    func testDynamicTypeIsAppliedOnce() {
        let large = launch(category: "UICTContentSizeCategoryAccessibilityXXXL")
        XCTAssertTrue(large.staticTexts["Saqz"].waitForExistence(timeout: 10))
        let largeHeight = large.staticTexts["Saqz"].frame.height
        large.terminate()

        let normal = launch(category: "UICTContentSizeCategoryL")
        XCTAssertTrue(normal.staticTexts["Saqz"].waitForExistence(timeout: 10))
        let normalHeight = normal.staticTexts["Saqz"].frame.height

        // Scaled up exactly once: larger than the default, but bounded — a double application
        // would multiply the scale and blow past this ceiling.
        XCTAssertGreaterThan(largeHeight, normalHeight)
        XCTAssertLessThan(largeHeight, normalHeight * 4)
    }
}
