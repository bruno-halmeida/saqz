import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSClipboardLinkReaderTests: XCTestCase {
    private static let code = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    func testCopiedInviteLinkReachesTheInviteListener() {
        let fixture = Fixture(text: "https://links.saqz.app/?saqz_invite=\(Self.code)")
        fixture.reader.readOnce()
        XCTAssertEqual(fixture.invites, [Self.code])
        XCTAssertEqual(fixture.pasteboard.reads, 1)
    }

    func testCopiedAttendanceLinkKeepsTheDeclineIntent() {
        let fixture = Fixture(text: " https://links.saqz.app/attendance/\(Self.code)?saqz_intent=decline \n")
        fixture.reader.readOnce()
        XCTAssertEqual(fixture.attendance, [Self.code])
        XCTAssertEqual(fixture.intents, [AttendanceIntent.decline])
    }

    func testNoProbableURLNeverReadsTheClipboard() {
        let fixture = Fixture(text: "https://links.saqz.app/?saqz_invite=\(Self.code)", detects: false)
        fixture.reader.readOnce()
        XCTAssertEqual(fixture.pasteboard.reads, 0, "ler sem URL mostraria o aviso de colar à toa")
        XCTAssertTrue(fixture.invites.isEmpty)
    }

    func testLinksFromOtherSitesAndSchemesAreIgnored() {
        for text in [
            "https://evil.example/?saqz_invite=\(Self.code)",
            "http://links.saqz.app/?saqz_invite=\(Self.code)",
            "saqz:///?saqz_invite=\(Self.code)",
            "https://links.saqz.app/",
            "só um texto",
        ] {
            let fixture = Fixture(text: text)
            fixture.reader.readOnce()
            XCTAssertTrue(fixture.invites.isEmpty, text)
            XCTAssertTrue(fixture.attendance.isEmpty, text)
        }
    }

    func testReadsOnlyOncePerInstallEvenWithoutALink() {
        let fixture = Fixture(text: "nada", detects: false)
        fixture.reader.readOnce()
        fixture.pasteboard.detects = true
        fixture.pasteboard.stored = "https://links.saqz.app/?saqz_invite=\(Self.code)"
        fixture.reader.readOnce()
        XCTAssertEqual(fixture.pasteboard.detections, 1)
        XCTAssertTrue(fixture.invites.isEmpty)
        let again = IOSClipboardLinkReader(links: fixture.links, pasteboard: fixture.pasteboard, defaults: fixture.defaults)
        again.readOnce()
        XCTAssertEqual(fixture.pasteboard.detections, 1)
    }

    @MainActor private final class FakePasteboard: IOSPasteboardLinkSource {
        var stored: String?
        var detects: Bool
        var detections = 0
        var reads = 0
        init(text: String?, detects: Bool) { stored = text; self.detects = detects }
        func hasProbableWebURL(_ completion: @escaping (Bool) -> Void) { detections += 1; completion(detects) }
        func text() -> String? { reads += 1; return stored }
    }

    @MainActor private final class Fixture {
        let pasteboard: FakePasteboard
        let links = IOSLinkAdapter()
        let defaults: UserDefaults
        let reader: IOSClipboardLinkReader
        var invites: [String] = []
        var attendance: [String] = []
        var intents: [AttendanceIntent] = []
        private var subscription: GroupCancelable?

        init(text: String?, detects: Bool = true) {
            let suite = "IOSClipboardLinkReaderTests.\(UUID().uuidString)"
            defaults = UserDefaults(suiteName: suite)!
            defaults.removePersistentDomain(forName: suite)
            pasteboard = FakePasteboard(text: text, detects: detects)
            reader = IOSClipboardLinkReader(links: links, pasteboard: pasteboard, defaults: defaults)
            subscription = links.start(listener_: RecordingLinkEventListener(
                invite: { self.invites.append($0) },
                attendance: { self.attendance.append($0) },
                attendanceIntent: { self.intents.append($0) }
            ))
        }
    }
}
