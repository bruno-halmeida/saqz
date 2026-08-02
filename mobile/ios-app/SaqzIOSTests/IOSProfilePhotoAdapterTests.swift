import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSProfilePhotoAdapterTests: XCTestCase {
    func testCameraDeliversEncodedBytesAndCleansSource() {
        let fixture = Fixture()
        _ = fixture.adapter.chooseCamera(done: fixture.done)
        fixture.selection.deliver(.selected)
        fixture.encoder.deliver(Fixture.encoded([1, 2, 3]))

        let selected = fixture.done.result as? ProfilePhotoResultSelected
        XCTAssertEqual(selected.map { Data($0.bytes.toByteArray()) }, Data([1, 2, 3]))
        XCTAssertEqual(selected?.mediaType, "image/jpeg")
        XCTAssertEqual(fixture.selection.cleaned, [Fixture.source])
        XCTAssertEqual(fixture.selection.calls, ["camera"])
    }

    func testLibraryUsesLibrarySelection() {
        let fixture = Fixture()
        _ = fixture.adapter.chooseLibrary(done: fixture.done)
        fixture.selection.deliver(.selected)
        fixture.encoder.deliver(Fixture.encoded([9]))

        XCTAssertTrue(fixture.done.result is ProfilePhotoResultSelected)
        XCTAssertEqual(fixture.selection.calls, ["library"])
    }

    func testCameraAndLibraryPermissionDenialsAreDistinct() {
        let adapter = IOSProfilePhotoAdapter(
            selection: StubSelectionPort(),
            encoder: StubEncoderPort(),
            permissions: DeniedPermissions()
        )
        let camera = SelectionRecordingCallback()
        let library = SelectionRecordingCallback()

        _ = adapter.chooseCamera(done_: camera)
        _ = adapter.chooseLibrary(done_: library)

        XCTAssertTrue(camera.result is ProfilePhotoSelectionResultCameraPermissionDenied)
        XCTAssertTrue(library.result is ProfilePhotoSelectionResultLibraryPermissionDenied)
    }

    func testPersonCancellationIsDistinctFromFailure() {
        let cancelled = Fixture()
        _ = cancelled.adapter.chooseCamera(done: cancelled.done)
        cancelled.selection.deliver(.cancelled)
        XCTAssertTrue(cancelled.done.result is ProfilePhotoResultCancelled)
        XCTAssertTrue(cancelled.selection.cleaned.isEmpty)

        let failed = Fixture()
        _ = failed.adapter.chooseCamera(done: failed.done)
        failed.selection.deliver(.failed)
        XCTAssertTrue(failed.done.result is ProfilePhotoResultFailed)
    }

    func testEncodingFailureStillCleansSource() {
        let fixture = Fixture()
        _ = fixture.adapter.chooseCamera(done: fixture.done)
        fixture.selection.deliver(.selected)
        fixture.encoder.deliver(GroupPhotoEncodingResultFailed.shared)

        XCTAssertTrue(fixture.done.result is ProfilePhotoResultFailed)
        XCTAssertEqual(fixture.selection.cleaned, [Fixture.source])
    }

    func testScreenCancellationStopsDeliveryAndStillCleansSource() {
        let fixture = Fixture()
        let request = fixture.adapter.chooseCamera(done: fixture.done)
        request.cancel()
        fixture.selection.deliver(.selected)

        XCTAssertNil(fixture.done.result)
        XCTAssertNil(fixture.encoder.pending)
        XCTAssertEqual(fixture.selection.cleaned, [Fixture.source])
    }

    func testScreenCancellationDuringEncodingStopsDelivery() {
        let fixture = Fixture()
        let request = fixture.adapter.chooseCamera(done: fixture.done)
        fixture.selection.deliver(.selected)
        request.cancel()
        fixture.encoder.deliver(Fixture.encoded([1]))

        XCTAssertNil(fixture.done.result)
        XCTAssertEqual(fixture.selection.cleaned, [Fixture.source])
    }

    @MainActor
    private struct Fixture {
        static let source = "source-profile.img"
        let selection = StubSelectionPort()
        let encoder = StubEncoderPort()
        let done = RecordingCallback()
        let adapter: IOSProfilePhotoAdapter

        init() {
            adapter = IOSProfilePhotoAdapter(selection: selection, encoder: encoder)
        }

        static func encoded(_ bytes: [UInt8]) -> GroupPhotoEncodingResult {
            let payload = KotlinByteArray(size: Int32(bytes.count))
            bytes.enumerated().forEach { payload.set(index: Int32($0.offset), value: Int8(bitPattern: $0.element)) }
            return GroupPhotoEncodingResultEncoded(
                value: EncodedGroupPhoto(
                    mediaType: GroupPhotoMediaType.jpeg,
                    contentLength: Int64(max(bytes.count, 1)),
                    source: StubByteSource(payload: payload)
                )
            )
        }
    }

    private enum StubSelection { case selected, cancelled, failed }

    @MainActor
    private final class StubSelectionPort: NSObject, @preconcurrency GroupPhotoSelectionPort {
        private(set) var calls: [String] = []
        private(set) var cleaned: [String] = []
        private var pending: ((GroupPhotoSelectionResult?, Error?) -> Void)?

        func chooseCamera(completionHandler: @escaping (GroupPhotoSelectionResult?, Error?) -> Void) {
            calls.append("camera")
            pending = completionHandler
        }

        func chooseLibrary(completionHandler: @escaping (GroupPhotoSelectionResult?, Error?) -> Void) {
            calls.append("library")
            pending = completionHandler
        }

        func cleanup(source: String) { cleaned.append(source) }

        func deliver(_ result: StubSelection) {
            let next = pending
            pending = nil
            next?(Self.value(result), nil)
            drainMainQueue()
        }

        private static func value(_ result: StubSelection) -> GroupPhotoSelectionResult {
            switch result {
            case .selected:
                GroupPhotoSelectionResultSelected(
                    value: GroupPhotoSelection(
                        source: GroupPhotoSourceHandle(value: Fixture.source),
                        preview: GroupPhotoPreviewHandle(value: Fixture.source),
                        width: 800,
                        height: 600
                    )
                )
            case .cancelled: GroupPhotoSelectionResultCancelled.shared
            case .failed: GroupPhotoSelectionResultFailed.shared
            }
        }
    }

    @MainActor
    private final class StubEncoderPort: NSObject, @preconcurrency GroupPhotoEncoderPort {
        private(set) var pending: ((GroupPhotoEncodingResult?, Error?) -> Void)?

        func encode(
            source: String,
            crop: GroupPhotoCrop,
            completionHandler: @escaping (GroupPhotoEncodingResult?, Error?) -> Void
        ) {
            XCTAssertEqual(crop, GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 1))
            pending = completionHandler
        }

        func cancel(source: String) {}

        func deliver(_ result: GroupPhotoEncodingResult) {
            let next = pending
            pending = nil
            next?(result, nil)
            drainMainQueue()
        }
    }

    private final class StubByteSource: GroupPhotoByteSource {
        private let payload: KotlinByteArray
        init(payload: KotlinByteArray) { self.payload = payload }
        func read() -> KotlinByteArray { payload }
    }

    private final class RecordingCallback: NSObject, ProfilePhotoCallback {
        private(set) var result: ProfilePhotoResult?
        func complete(result_ result: ProfilePhotoResult) { self.result = result }
    }

    @MainActor
    private struct DeniedPermissions: IOSProfilePhotoPermissions {
        func cameraPermissionDenied() -> Bool { true }
        func libraryPermissionDenied() -> Bool { true }
    }

    private final class SelectionRecordingCallback: NSObject, ProfilePhotoSelectionCallback {
        private(set) var result: ProfilePhotoSelectionResult?

        func complete(result_______ result: ProfilePhotoSelectionResult) {
            self.result = result
        }
    }
}

/// O adapter salta para a main queue antes de tocar no estado da escolha; o teste roda a fila
/// até esvaziar para observar o resultado sem `expectation`.
@MainActor
private func drainMainQueue() {
    RunLoop.main.run(until: Date().addingTimeInterval(0.05))
}

private extension KotlinByteArray {
    func toByteArray() -> [UInt8] {
        (0..<size).map { UInt8(bitPattern: get(index: $0)) }
    }
}
