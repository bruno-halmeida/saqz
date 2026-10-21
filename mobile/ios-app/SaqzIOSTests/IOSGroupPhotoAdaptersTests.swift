import Foundation
import ImageIO
import UniformTypeIdentifiers
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
        let bytes = IOSPhotoPreviewAdapter(files: fixture.files).read(preview: url.lastPathComponent)
        let actual = bytes.map { array in
            Data((0..<array.size).map { index in UInt8(bitPattern: array.get(index: index)) })
        }
        XCTAssertEqual(actual, expected)
        XCTAssertNil(IOSPhotoPreviewAdapter(files: fixture.files).read(preview: "missing.img"))
    }

    func testEncoderProducesBoundedStaticSquareJPEG() throws {
        let fixture = try filesFixture(); let renderer = UIGraphicsImageRenderer(size: CGSize(width: 400, height: 200))
        let data = renderer.jpegData(withCompressionQuality: 1) { context in UIColor.red.setFill(); context.fill(CGRect(x: 0, y: 0, width: 400, height: 200)) }
        let url = try fixture.files.store(data); let expectation = expectation(description: "encoded")
        IOSPhotoEncoderAdapter(files: fixture.files).encode(source: fixture.files.handle(url).value, crop: GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 1)) { result, error in
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

    // DEB-125: a original vem como o iPhone salva; a escolha reduz e confere o resultado.
    func testHeic12MegapixelIsAccepted() throws { try assertAccepted(fixture(4032, 3024, type: "public.heic")) }
    func testJpeg24MegapixelIsAccepted() throws { try assertAccepted(fixture(5712, 4284, type: UTType.jpeg.identifier)) }
    func testHeic48MegapixelIsAccepted() throws { try assertAccepted(fixture(8064, 6048, type: "public.heic")) }
    func testJpegOf8MegabytesIsAccepted() throws {
        let url = try fixture(4000, 3000, type: UTType.jpeg.identifier, noise: true)
        XCTAssertGreaterThan(try XCTUnwrap(url.resourceValues(forKeys: [.fileSizeKey]).fileSize), 8 * 1024 * 1024)
        try assertAccepted(url)
    }
    func testOriginalAbove50MegabytesIsRejected() throws {
        let url = scratch().appendingPathComponent("huge.jpg")
        try Data(count: IOSPhotoFiles.maximumSourceBytes + 1).write(to: url)
        XCTAssertThrowsError(try filesFixture().files.copySelected(url))
    }
    func testNonImageIsRejected() throws {
        let url = scratch().appendingPathComponent("text.jpg"); try Data("nada".utf8).write(to: url)
        XCTAssertThrowsError(try filesFixture().files.copySelected(url))
    }
    func testExifRotationIsApplied() throws {
        for type in [UTType.jpeg.identifier, "public.heic"] {
            let fixture = try filesFixture()
            let stored = try fixture.files.copySelected(try self.fixture(400, 200, type: type, orientation: 6))
            let metadata = try XCTUnwrap(fixture.files.metadata(stored))
            XCTAssertEqual([metadata.width, metadata.height], [200, 400])
            let image = try XCTUnwrap(CGImageSourceCreateWithURL(stored as CFURL, nil).flatMap { CGImageSourceCreateImageAtIndex($0, 0, nil) })
            XCTAssertGreaterThan(red(image, x: 100, y: 50), 200, type)
            XCTAssertLessThan(red(image, x: 100, y: 350), 60, type)
        }
    }
    func testCameraPhotoIsReducedBeforeJPEG() {
        let format = UIGraphicsImageRendererFormat.default(); format.scale = 1
        let image = UIGraphicsImageRenderer(size: CGSize(width: 8000, height: 6000), format: format).image { _ in }
        let reduced = IOSPhotoSelectionAdapter.reduced(image)
        XCTAssertEqual(reduced.size.width * reduced.scale, 4096)
    }

    private func assertAccepted(_ original: URL) throws {
        let fixture = try filesFixture()
        let stored = try fixture.files.copySelected(original)
        let metadata = try XCTUnwrap(fixture.files.metadata(stored))
        XCTAssertLessThanOrEqual(max(metadata.width, metadata.height), IOSPhotoFiles.maximumDimension)
        XCTAssertEqual(metadata.width > metadata.height, true)
        for (dimension, encoder) in [
            (IOSPhotoFiles.maximumDimension, IOSPhotoEncoderAdapter(files: fixture.files)),
            (1024, IOSPhotoEncoderAdapter(files: fixture.files, maximumDimension: IOSPhotoEncoderAdapter.profileDimension))
        ] {
            let done = expectation(description: "encoded \(dimension)")
            encoder.encode(source: fixture.files.handle(stored).value, crop: GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 1)) { result, _ in
                let encoded = result as? GroupPhotoEncodingResultEncoded
                XCTAssertLessThanOrEqual(encoded?.value.contentLength ?? .max, Int64(IOSPhotoFiles.maximumBytes))
                if let bytes = encoded?.value.source.read() {
                    let data = Data((0..<bytes.size).map { UInt8(bitPattern: bytes.get(index: $0)) })
                    let properties = CGImageSourceCreateWithData(data as CFData, nil)
                        .flatMap { CGImageSourceCopyPropertiesAtIndex($0, 0, nil) as? [CFString: Any] }
                    let width = properties?[kCGImagePropertyPixelWidth] as? Int ?? .max
                    XCTAssertLessThanOrEqual(width, dimension)
                    XCTAssertEqual(width, properties?[kCGImagePropertyPixelHeight] as? Int)
                }
                done.fulfill()
            }
            wait(for: [done], timeout: 30)
        }
    }

    /// Imagem gerada em tempo de execução: metade esquerda vermelha, direita azul.
    private func fixture(_ width: Int, _ height: Int, type: String, orientation: Int = 1, noise: Bool = false) throws -> URL {
        let context = try XCTUnwrap(CGContext(
            data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ))
        context.setFillColor(red: 1, green: 0, blue: 0, alpha: 1); context.fill(CGRect(x: 0, y: 0, width: width / 2, height: height))
        context.setFillColor(red: 0, green: 0, blue: 1, alpha: 1); context.fill(CGRect(x: width / 2, y: 0, width: width - width / 2, height: height))
        if noise, let data = context.data { arc4random_buf(data, context.bytesPerRow * height) }
        let image = try XCTUnwrap(context.makeImage())
        let url = scratch().appendingPathComponent(UUID().uuidString)
        let destination = try XCTUnwrap(CGImageDestinationCreateWithURL(url as CFURL, type as CFString, 1, nil))
        CGImageDestinationAddImage(destination, image, [
            kCGImagePropertyOrientation: orientation, kCGImageDestinationLossyCompressionQuality: noise ? 1.0 : 0.8
        ] as CFDictionary)
        XCTAssertTrue(CGImageDestinationFinalize(destination))
        return url
    }
    private func red(_ image: CGImage, x: Int, y: Int) -> Int {
        var pixel = [UInt8](repeating: 0, count: 4)
        let context = CGContext(
            data: &pixel, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        )
        context?.draw(image, in: CGRect(x: -x, y: y - image.height + 1, width: image.width, height: image.height))
        return Int(pixel[0])
    }
    private func scratch() -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return directory
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
