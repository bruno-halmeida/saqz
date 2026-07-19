import XCTest

@MainActor
final class SaqzIOSUITests: XCTestCase {
    func testSharedComposeLoginIsAccessible() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(loginHeadline(in: app).waitForExistence(timeout: 5))
    }

    func testAppRendersSentinelStringFromFramework() {
        let app = XCUIApplication()
        app.launchArguments = ["-saqzResourcePreflight"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Preflight Sentinel"].waitForExistence(timeout: 10))
    }

    func testAppRendersSentinelDrawableFromFramework() {
        let app = XCUIApplication()
        app.launchArguments = ["-saqzResourcePreflight"]
        app.launch()

        let drawable = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "preflight-sentinel-drawable"))
            .firstMatch
        XCTAssertTrue(drawable.waitForExistence(timeout: 10))
    }

    func testComposeRootDrawsAcrossTheFullIOSScreen() {
        let app = XCUIApplication()
        app.launchArguments = ["-saqzResourcePreflight"]
        app.launch()

        let root = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "preflight-full-screen-root"))
            .firstMatch
        XCTAssertTrue(root.waitForExistence(timeout: 10))
        XCTAssertEqual(root.frame.minY, app.frame.minY, accuracy: 1)
        XCTAssertEqual(root.frame.maxY, app.frame.maxY, accuracy: 1)
    }

    func testColdStartReachesComposeLoginWithoutProtectedContent() {
        let app = XCUIApplication()
        app.launch()

        // A signed-out cold start hands straight to the Compose auth root.
        XCTAssertTrue(loginHeadline(in: app).waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Entrar com Google"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Início"].exists)
        XCTAssertFalse(app.staticTexts["Componentes"].exists)
        XCTAssertFalse(app.staticTexts["Explorar componentes"].exists)
    }

    func testComposeAuthenticationActionsAreAccessibleFromOneSemanticsTree() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.buttons["Entrar com Google"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Esqueci minha senha"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Criar conta"].waitForExistence(timeout: 5))
        XCTAssertEqual(loginHeadlines(in: app).count, 1)
    }

    func testNoSyntheticUIKitAccessibilityElement() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(loginHeadline(in: app).waitForExistence(timeout: 10))
        XCTAssertEqual(loginHeadlines(in: app).count, 1)
    }

    private func loginHeadlines(in app: XCUIApplication) -> XCUIElementQuery {
        app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "Organize seu grupo."))
    }

    private func loginHeadline(in app: XCUIApplication) -> XCUIElement {
        loginHeadlines(in: app).firstMatch
    }
}
