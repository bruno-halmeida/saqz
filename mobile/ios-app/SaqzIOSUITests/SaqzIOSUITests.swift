import XCTest

@MainActor
final class SaqzIOSUITests: XCTestCase {
    func testSharedComposePlaceholderIsAccessible() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 5))
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

    func testColdStartReachesComposeLoginWithoutProtectedContent() {
        let app = XCUIApplication()
        app.launch()

        // A signed-out cold start hands straight to the Compose auth root.
        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Continuar com Google"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Início"].exists)
        XCTAssertFalse(app.staticTexts["Componentes"].exists)
        XCTAssertFalse(app.staticTexts["Explorar componentes"].exists)
    }

    func testComposeAuthenticationActionsAreAccessibleFromOneSemanticsTree() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.buttons["Continuar com Google"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Esqueci minha senha"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Criar conta"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts.matching(identifier: "Saqz").count, 1)
    }

    func testNoSyntheticUIKitAccessibilityElement() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 10))
        // The synthetic 1x1 UILabel is gone: "Saqz" now comes only from the Compose heading.
        XCTAssertEqual(app.staticTexts.matching(identifier: "Saqz").count, 1)
    }
}
