import CryptoKit
import UIKit
import XCTest

// Contract over the native iOS launch screen: a static UILaunchScreen backed by asset-catalog
// members, with the symbol derived byte-for-byte from the unchanged T18 landing source.
@MainActor
final class LaunchContractTests: XCTestCase {
    private var repoRoot: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // SaqzIOSTests
            .deletingLastPathComponent() // ios-app
            .deletingLastPathComponent() // mobile
            .deletingLastPathComponent() // repo root
    }

    private func sha256(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }

    private func pathData(in svg: String) -> [String] {
        let regex = try! NSRegularExpression(pattern: "\\sd=\"([^\"]+)\"")
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
        let landing = repoRoot.appendingPathComponent("landing-page/assets/saqz-logo.svg")
        let landingData = try Data(contentsOf: landing)
        XCTAssertEqual(
            sha256(landingData),
            "0c732546309e7143f60203472c368a3cebbb3a53721f142898724023aa33a473",
            "landing source must remain unchanged (same pin as T18)"
        )

        let symbol = repoRoot.appendingPathComponent(
            "mobile/ios-app/SaqzIOS/Assets.xcassets/LaunchSymbol.imageset/saqz-symbol.svg"
        )
        let symbolPaths = pathData(in: try String(contentsOf: symbol, encoding: .utf8))
        let landingPaths = pathData(in: String(data: landingData, encoding: .utf8)!)
        // The launch symbol reuses the exact landing path data, derived and never redrawn.
        XCTAssertEqual(landingPaths.count, 3)
        for d in landingPaths {
            XCTAssertTrue(symbolPaths.contains(d), "launch symbol must reuse the landing path data")
        }
    }
}
