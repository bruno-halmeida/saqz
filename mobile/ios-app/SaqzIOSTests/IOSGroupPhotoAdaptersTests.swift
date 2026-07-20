import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

final class IOSGroupPhotoAdaptersTests: XCTestCase {
    func testLandscapeCenterUsesFullShortAxis() { XCTAssertEqual(crop(400, 200), IOSPixelCrop(x: 100, y: 0, side: 200)) }
    func testPortraitCenterUsesFullShortAxis() { XCTAssertEqual(crop(200, 400), IOSPixelCrop(x: 0, y: 100, side: 200)) }
    func testSquareSourceRemainsSquare() { XCTAssertEqual(crop(300, 300), IOSPixelCrop(x: 0, y: 0, side: 300)) }
    func testZoomProducesSmallerSquare() { XCTAssertEqual(crop(400, 200, GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 2)), IOSPixelCrop(x: 150, y: 50, side: 100)) }
    func testCropClampsLeftEdge() { XCTAssertEqual(crop(400, 200, GroupPhotoCrop(centerX: 0, centerY: 0.5, zoom: 1)).x, 0) }
    func testCropClampsRightEdge() { XCTAssertEqual(crop(400, 200, GroupPhotoCrop(centerX: 1, centerY: 0.5, zoom: 1)).x, 200) }

    func testGateRejectsOverlappingRequest() {
        let gate = IOSPhotoRequestGate(); XCTAssertTrue(gate.begin(.camera)); XCTAssertFalse(gate.begin(.library))
    }
    func testWrongCallbackCannotConsumeRequest() {
        let gate = IOSPhotoRequestGate(); XCTAssertTrue(gate.begin(.camera)); XCTAssertFalse(gate.complete(.library)); XCTAssertEqual(gate.active, .camera)
    }
    func testRequestStaysActiveUntilMatchingProcessingCompletes() {
        let gate = IOSPhotoRequestGate(); XCTAssertTrue(gate.begin(.library)); XCTAssertTrue(gate.isActive(.library)); XCTAssertFalse(gate.isActive(.camera))
    }
    func testCompletionIsDeliveredOnlyOnce() {
        let gate = IOSPhotoRequestGate(); XCTAssertTrue(gate.begin(.library)); XCTAssertTrue(gate.complete(.library)); XCTAssertFalse(gate.complete(.library))
    }
    func testCancellationClearsActiveRequest() {
        let gate = IOSPhotoRequestGate(); XCTAssertTrue(gate.begin(.camera)); XCTAssertEqual(gate.cancel(), .camera); XCTAssertNil(gate.active)
    }

    func testFilesStayInsidePrivateDirectoryAndCleanup() throws {
        let fixture = try filesFixture(); let url = try fixture.files.store(Data([1, 2, 3]))
        XCTAssertTrue(url.path.hasPrefix(fixture.directory.path)); fixture.files.cleanup(fixture.files.handle(url)); XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }
    func testFilesRejectEmptyAndOversizedPayload() throws {
        let fixture = try filesFixture(); XCTAssertThrowsError(try fixture.files.store(Data()))
        XCTAssertThrowsError(try fixture.files.store(Data(count: IOSPhotoFiles.maximumBytes + 1)))
    }
    func testInvalidBytesFailMetadataBeforeDecode() throws {
        let fixture = try filesFixture(); let url = try fixture.files.store(Data([1, 2, 3])); XCTAssertNil(fixture.files.metadata(url))
    }
    func testPreviewReaderReturnsOnlyPrivateSourceBytes() throws {
        let fixture = try filesFixture(); let expected = Data([1, 2, 3]); let url = try fixture.files.store(expected)
        let bytes = IOSPhotoPreviewAdapter(files: fixture.files).read(preview: GroupPhotoPreviewHandle(value: url.lastPathComponent))
        let actual = bytes.map { array in
            Data((0..<array.size).map { index in UInt8(bitPattern: array.get(index: index)) })
        }
        XCTAssertEqual(actual, expected)
        XCTAssertNil(IOSPhotoPreviewAdapter(files: fixture.files).read(preview: GroupPhotoPreviewHandle(value: "missing.img")))
    }

    func testEncoderProducesBoundedStaticSquareJPEG() throws {
        let fixture = try filesFixture(); let renderer = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 200))
        let data = renderer.jpegData(withCompressionQuality: 1) { context in UIColor.red.setFill(); context.fill(CGRect(x: 0, y: 0, width: 400, height: 200)) }
        let url = try fixture.files.store(data); let expectation = expectation(description: "encoded")
        IOSPhotoEncoderAdapter(files: fixture.files).encode(source: fixture.files.handle(url), crop: GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 1)) { result, error in
            XCTAssertNil(error); let encoded = result as? GroupPhotoEncodingResultEncoded
            XCTAssertNotNil(encoded); XCTAssertLessThanOrEqual(encoded?.value.contentLength ?? .max, Int64(IOSPhotoFiles.maximumBytes))
            if let bytes = encoded?.value.source.read() {
                let output = Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
                let outputURL = try? fixture.files.store(output); let metadata = outputURL.flatMap(fixture.files.metadata)
                XCTAssertEqual(metadata?.width, metadata?.height)
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 3)
    }

    private func crop(_ width: Int, _ height: Int, _ value: GroupPhotoCrop = GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 1)) -> IOSPixelCrop {
        IOSSquareCrop.calculate(width: width, height: height, crop: value)
    }
    private func filesFixture() throws -> (files: IOSPhotoFiles, directory: URL) {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return (IOSPhotoFiles(directory: directory), directory)
    }
}
