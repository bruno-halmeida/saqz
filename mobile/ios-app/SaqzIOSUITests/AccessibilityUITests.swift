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
        XCTAssertTrue(loginHeadline(in: app).waitForExistence(timeout: 10))
        assertButtonIsReachable("Esqueci minha senha", in: app)
        assertButtonIsReachable("Entrar com Google", in: app)
        assertButtonIsReachable("Criar conta", in: app)
    }

    func testDynamicTypeIsAppliedOnce() {
        let large = launch(category: "UICTContentSizeCategoryAccessibilityXXXL")
        XCTAssertTrue(loginHeadline(in: large).waitForExistence(timeout: 10))
        let largeHeight = loginHeadline(in: large).frame.height
        large.terminate()

        let normal = launch(category: "UICTContentSizeCategoryL")
        XCTAssertTrue(loginHeadline(in: normal).waitForExistence(timeout: 10))
        let normalHeight = loginHeadline(in: normal).frame.height

        // Scaled up exactly once: larger than the default, but bounded — a double application
        // would multiply the scale and blow past this ceiling.
        XCTAssertGreaterThan(largeHeight, normalHeight)
        XCTAssertLessThan(largeHeight, normalHeight * 4)
    }

    private func assertButtonIsReachable(_ label: String, in app: XCUIApplication) {
        let button = app.buttons[label]
        for _ in 0..<8 where !button.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(button.exists, "Expected \(label) to remain in the accessibility tree")
        XCTAssertTrue(button.isHittable, "Expected \(label) to be reachable by scrolling")
    }

    private func loginHeadline(in app: XCUIApplication) -> XCUIElement {
        app.staticTexts
            .matching(NSPredicate(format: "label CONTAINS %@", "Organize seu grupo."))
            .firstMatch
    }
}
