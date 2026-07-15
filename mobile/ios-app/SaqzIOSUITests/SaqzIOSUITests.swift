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

    func testColdStartReachesComposeHome() {
        let app = XCUIApplication()
        app.launch()

        // The native launch screen hands straight to the real Compose Home shell.
        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Explorar componentes"].waitForExistence(timeout: 5))
    }

    func testComposeNavIsAccessible() {
        let app = XCUIApplication()
        app.launch()

        // Bottom navigation is exposed via Compose semantics, not a UIKit shim.
        XCTAssertTrue(app.staticTexts["Início"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Componentes"].waitForExistence(timeout: 5))
    }

    func testNoSyntheticUIKitAccessibilityElement() {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(app.staticTexts["Saqz"].waitForExistence(timeout: 10))
        // The synthetic 1x1 UILabel is gone: "Saqz" now comes only from the Compose heading.
        XCTAssertEqual(app.staticTexts.matching(identifier: "Saqz").count, 1)
    }
}
