import UIKit
import XCTest

// Contract over the native iOS launch screen: a static UILaunchScreen backed by asset-catalog
// members, with the symbol matching the checked-in mobile brand derivative.
@MainActor
final class LaunchContractTests: XCTestCase {
    private var mobileRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // SaqzIOSTests
            .deletingLastPathComponent() // ios-app
            .deletingLastPathComponent() // mobile
    }

    private func pathData(in svg: String) -> [String] {
        let regex = try! NSRegularExpression(pattern: "(?:\\sd|android:pathData)=\"([^\"]+)\"")
        let range = NSRange(svg.startIndex..., in: svg)
        return regex.matches(in: svg, range: range).compactMap {
            Range($0.range(at: 1), in: svg).map { r in String(svg[r]) }
        }
    }

    func testPlistUsesUILaunchScreen() {
        let launch = Bundle.main.object(forInfoDictionaryKey: "UILaunchScreen") as? [String: Any]
        XCTAssertNotNil(launch, "Info.plist must declare a static UILaunchScreen")
        XCTAssertEqual(launch?["UIColorName"] as? String, "LaunchBackground")
        XCTAssertEqual(launch?["UIImageName"] as? String, "LaunchSymbol")
    }

    func testBackgroundAssetIsTargetMember() {
        // Compiled into the app's asset catalog only if the colorset is a target member.
        XCTAssertNotNil(UIColor(named: "LaunchBackground"))
    }

    func testSymbolAssetIsTargetMember() {
        XCTAssertNotNil(UIImage(named: "LaunchSymbol"))
    }

    func testSourceHashMatchesT18() throws {
        let provenance = try String(
            contentsOf: mobileRoot.appendingPathComponent("brand/PROVENANCE.md"),
            encoding: .utf8
        )
        XCTAssertTrue(provenance.contains("landing-page/assets/saqz-logo.svg"))
        XCTAssertTrue(provenance.contains("0c732546309e7143f60203472c368a3cebbb3a53721f142898724023aa33a473"))

        let symbol = mobileRoot.appendingPathComponent(
            "ios-app/SaqzIOS/Assets.xcassets/LaunchSymbol.imageset/saqz-symbol.svg"
        )
        let designSystemSymbol = mobileRoot.appendingPathComponent(
            "core/design-system/src/commonMain/composeResources/drawable/saqz_symbol.xml"
        )
        let symbolPaths = pathData(in: try String(contentsOf: symbol, encoding: .utf8))
        let designSystemPaths = pathData(in: try String(contentsOf: designSystemSymbol, encoding: .utf8))
        // The launch symbol reuses the exact mobile brand path data, derived and never redrawn.
        XCTAssertEqual(designSystemPaths.count, 3)
        for path in designSystemPaths {
            XCTAssertTrue(symbolPaths.contains(path), "launch symbol must reuse the mobile brand path data")
        }
    }
}
