import XCTest

@MainActor
final class IOSAuthenticatedLifecycleUITests: XCTestCase {
    func testCompositionKeepsSingleComposeSemanticsTree() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "Saqz").count, 1)
    }

    func testBackgroundForegroundKeepsAuthenticationActionsReachable() {
        let app = XCUIApplication(); app.launch()
        XCTAssertTrue(app.buttons["Continuar com Google"].waitForExistence(timeout: 10))
        XCUIDevice.shared.press(.home); app.activate()
        XCTAssertTrue(app.buttons["Criar conta"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Esqueci minha senha"].isHittable)
    }

    func testMaximumDynamicTypeWithReducedMotionKeepsActionsReachable() {
        let app = XCUIApplication()
        app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"]
        app.launchEnvironment["UI_TEST_REDUCE_MOTION"] = "1"
        app.launch()
        XCTAssertTrue(app.buttons["Continuar com Google"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Esqueci minha senha"].isHittable)
        XCTAssertTrue(app.buttons["Criar conta"].isHittable)
    }
}
