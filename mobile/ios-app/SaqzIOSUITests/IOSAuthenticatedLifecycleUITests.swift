import XCTest

@MainActor
final class IOSAuthenticatedLifecycleUITests: XCTestCase {
    func testCompositionKeepsSingleComposeSemanticsTree() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(loginHeadline(in: app).waitForExistence(timeout: 10))
        XCTAssertEqual(loginHeadlines(in: app).count, 1)
    }

    func testBackgroundForegroundKeepsAuthenticationActionsReachable() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.buttons["Entrar com Google"].waitForExistence(timeout: 10))
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.buttons["Criar conta"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Esqueci minha senha"].isHittable)
    }

    func testMaximumDynamicTypeWithReducedMotionKeepsActionsReachable() {
        let app = XCUIApplication()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launchEnvironment["UI_TEST_REDUCE_MOTION"] = "1"
        app.launch()
        assertButtonIsReachable("Esqueci minha senha", in: app)
        assertButtonIsReachable("Entrar com Google", in: app)
        assertButtonIsReachable("Criar conta", in: app)
    }

    private func assertButtonIsReachable(_ label: String, in app: XCUIApplication) {
        let button = app.buttons[label]
        for _ in 0..<8 where !button.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(button.exists, "Expected \(label) to remain in the accessibility tree")
        XCTAssertTrue(button.isHittable, "Expected \(label) to be reachable by scrolling")
    }

    private func loginHeadlines(in app: XCUIApplication) -> XCUIElementQuery {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Organize seu grupo."))
    }

    private func loginHeadline(in app: XCUIApplication) -> XCUIElement {
        loginHeadlines(in: app).firstMatch
    }
}
