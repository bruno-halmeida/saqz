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
}
