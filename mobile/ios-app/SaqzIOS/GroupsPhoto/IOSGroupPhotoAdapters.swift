import Foundation
import ImageIO
import PhotosUI
import SaqzMobile
import UIKit
import UniformTypeIdentifiers

enum IOSPhotoRequestKind { case camera, library }

final class IOSPhotoRequestGate {
    private(set) var active: IOSPhotoRequestKind?
    func begin(_ kind: IOSPhotoRequestKind) -> Bool {
        guard active == nil else { return false }
        active = kind
        return true
    }
    func complete(_ kind: IOSPhotoRequestKind) -> Bool {
        guard active == kind else { return false }
        active = nil
        return true
    }
    func isActive(_ kind: IOSPhotoRequestKind) -> Bool { active == kind }
    @discardableResult func cancel() -> IOSPhotoRequestKind? {
        defer { active = nil }
        return active
    }
}

struct IOSPhotoMetadata: Equatable { let width: Int; let height: Int; let type: String }

final class IOSPhotoFiles {
    static let maximumBytes = 5 * 1024 * 1024
    static let maximumDimension = 4096
    private let manager: FileManager
    private let directory: URL

    init(manager: FileManager = .default, directory: URL? = nil) {
        self.manager = manager
        self.directory = directory ?? manager.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("GroupPhotos", isDirectory: true)
    }

    func store(_ data: Data) throws -> URL {
        guard !data.isEmpty, data.count <= Self.maximumBytes else { throw IOSPhotoFileError.invalid }
        try manager.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = directory.appendingPathComponent("source-\(UUID().uuidString).img")
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        return url
    }

    func copySelected(_ source: URL) throws -> URL {
        let size = try source.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? Self.maximumBytes + 1
        guard (1...Self.maximumBytes).contains(size) else { throw IOSPhotoFileError.invalid }
        return try store(Data(contentsOf: source, options: .mappedIfSafe))
    }

    func file(_ handle: GroupPhotoSourceHandle) -> URL? {
        let candidate = directory.appendingPathComponent(handle.value).standardizedFileURL
        guard candidate.deletingLastPathComponent() == directory.standardizedFileURL,
              manager.fileExists(atPath: candidate.path) else { return nil }
        return candidate
    }

    func handle(_ url: URL) -> GroupPhotoSourceHandle { GroupPhotoSourceHandle(value: url.lastPathComponent) }
    func cleanup(_ handle: GroupPhotoSourceHandle) { if let url = file(handle) { try? manager.removeItem(at: url) } }
    func cleanup(_ url: URL?) { if let url, url.deletingLastPathComponent() == directory { try? manager.removeItem(at: url) } }
    func purge() {
        (try? manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil))?.forEach { try? manager.removeItem(at: $0) }
    }

    func metadata(_ url: URL) -> IOSPhotoMetadata? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil), CGImageSourceGetCount(source) == 1,
              let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let width = properties[kCGImagePropertyPixelWidth] as? Int,
              let height = properties[kCGImagePropertyPixelHeight] as? Int,
              width > 0, height > 0, width <= Self.maximumDimension, height <= Self.maximumDimension,
              let type = CGImageSourceGetType(source) as String?, Self.acceptedTypes.contains(type)
        else { return nil }
        return IOSPhotoMetadata(width: width, height: height, type: type)
    }

    private static let acceptedTypes = [UTType.jpeg.identifier, UTType.png.identifier, "org.webmproject.webp"]
}

enum IOSPhotoFileError: Error { case invalid }

struct IOSPixelCrop: Equatable { let x: Int; let y: Int; let side: Int }

enum IOSSquareCrop {
    static func calculate(width: Int, height: Int, crop: GroupPhotoCrop) -> IOSPixelCrop {
        let maximum = min(width, height)
        let side = max(1, min(maximum, Int((Double(maximum) / Double(crop.zoom)).rounded())))
        let x = max(0, min(width - side, Int((Double(crop.centerX) * Double(width) - Double(side) / 2).rounded())))
        let y = max(0, min(height - side, Int((Double(crop.centerY) * Double(height) - Double(side) / 2).rounded())))
        return IOSPixelCrop(x: x, y: y, side: side)
    }
}

final class IOSPhotoByteSource: GroupPhotoByteSource {
    private let data: Data
    init(data: Data) { self.data = data }
    func read() -> KotlinByteArray {
        let bytes = KotlinByteArray(size: Int32(data.count))
        data.enumerated().forEach { bytes.set(index: Int32($0.offset), value: Int8(bitPattern: $0.element)) }
        return bytes
    }
}

final class IOSPhotoEncoderAdapter: @preconcurrency GroupPhotoEncoderPort {
    private let files: IOSPhotoFiles
    init(files: IOSPhotoFiles) { self.files = files }
    func cancel(source: GroupPhotoSourceHandle) {}

    func encode(
        source: GroupPhotoSourceHandle,
        crop: GroupPhotoCrop,
        completionHandler: @escaping (GroupPhotoEncodingResult?, Error?) -> Void
    ) {
        DispatchQueue.global(qos: .userInitiated).async { [files] in
            guard let url = files.file(source), files.metadata(url) != nil,
                  let imageSource = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let image = CGImageSourceCreateThumbnailAtIndex(imageSource, 0, [
                    kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true,
                    kCGImageSourceThumbnailMaxPixelSize: IOSPhotoFiles.maximumDimension
                  ] as CFDictionary)
            else { return completionHandler(GroupPhotoEncodingResultFailed.shared, nil) }
            let region = IOSSquareCrop.calculate(width: image.width, height: image.height, crop: crop)
            guard let square = image.cropping(to: CGRect(x: region.x, y: region.y, width: region.side, height: region.side)),
                  let data = Self.encodeBounded(square)
            else { return completionHandler(GroupPhotoEncodingResultFailed.shared, nil) }
            let payload = EncodedGroupPhoto(
                mediaType: GroupPhotoMediaType.jpeg,
                contentLength: Int64(data.count),
                source: IOSPhotoByteSource(data: data)
            )
            completionHandler(GroupPhotoEncodingResultEncoded(value: payload), nil)
        }
    }

    private static func encodeBounded(_ image: CGImage) -> Data? {
        for quality in [0.92, 0.85, 0.75, 0.65] {
            let data = NSMutableData()
            guard let destination = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else { continue }
            CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
            if CGImageDestinationFinalize(destination), data.length <= IOSPhotoFiles.maximumBytes { return data as Data }
        }
        return nil
    }
}

@MainActor
final class IOSPhotoSelectionAdapter: NSObject, @preconcurrency GroupPhotoSelectionPort,
    PHPickerViewControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    typealias Completion = (GroupPhotoSelectionResult?, Error?) -> Void
    private let files: IOSPhotoFiles
    private let presenter: () -> UIViewController?
    private let gate = IOSPhotoRequestGate()
    private var completion: Completion?
    private weak var presented: UIViewController?
    private var backgroundObserver: NSObjectProtocol?

    init(files: IOSPhotoFiles, presenter: @escaping () -> UIViewController?) {
        self.files = files; self.presenter = presenter
        super.init()
        backgroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main
        ) { [weak self] _ in Task { @MainActor in self?.cancelActive() } }
        files.purge()
    }
    func chooseLibrary(completionHandler: @escaping Completion) {
        guard begin(.library, completionHandler) else {
            completionHandler(GroupPhotoSelectionResultFailed.shared, nil); return
        }
        guard let root = presenter() else { failPendingStart(); return }
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images; configuration.selectionLimit = 1
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self; presented = picker; root.present(picker, animated: true)
    }

    func chooseCamera(completionHandler: @escaping Completion) {
        guard UIImagePickerController.isSourceTypeAvailable(.camera) else {
            completionHandler(GroupPhotoSelectionResultFailed.shared, nil); return
        }
        guard begin(.camera, completionHandler) else {
            completionHandler(GroupPhotoSelectionResultFailed.shared, nil); return
        }
        guard let root = presenter() else { failPendingStart(); return }
        let picker = UIImagePickerController()
        picker.sourceType = .camera; picker.cameraCaptureMode = .photo; picker.mediaTypes = [UTType.image.identifier]
        picker.delegate = self; picker.modalPresentationStyle = .fullScreen; presented = picker
        root.present(picker, animated: true)
    }

    func cleanup(source: GroupPhotoSourceHandle) { files.cleanup(source) }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true); presented = nil
        guard gate.isActive(.library) else { return }
        guard let provider = results.first?.itemProvider else {
            gate.complete(.library)
            finish(GroupPhotoSelectionResultCancelled.shared)
            return
        }
        provider.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) { [weak self] url, _ in
            guard let self else { return }
            let copied = url.flatMap { try? self.files.copySelected($0) }
            Task { @MainActor in
                guard self.gate.complete(.library) else { self.files.cleanup(copied); return }
                self.finishSelection(copied)
            }
        }
    }

    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true); presented = nil
        guard gate.complete(.camera) else { return }
        finish(GroupPhotoSelectionResultCancelled.shared)
    }

    func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        picker.dismiss(animated: true); presented = nil
        guard gate.isActive(.camera) else { return }
        guard let image = info[.originalImage] as? UIImage else {
            gate.complete(.camera); finish(GroupPhotoSelectionResultFailed.shared); return
        }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            let url = image.jpegData(compressionQuality: 0.95).flatMap { try? self.files.store($0) }
            Task { @MainActor in
                guard self.gate.complete(.camera) else { self.files.cleanup(url); return }
                self.finishSelection(url)
            }
        }
    }

    private func begin(_ kind: IOSPhotoRequestKind, _ next: @escaping Completion) -> Bool {
        guard completion == nil, gate.begin(kind) else { return false }
        completion = next; return true
    }
    private func failPendingStart() {
        gate.cancel()
        finish(GroupPhotoSelectionResultFailed.shared)
    }
    private func finishSelection(_ url: URL?) {
        guard let url, let metadata = files.metadata(url) else {
            files.cleanup(url)
            finish(GroupPhotoSelectionResultFailed.shared)
            return
        }
        let selection = GroupPhotoSelection(
            source: files.handle(url), preview: GroupPhotoPreviewHandle(value: url.absoluteString),
            width: Int32(metadata.width), height: Int32(metadata.height)
        )
        guard finish(GroupPhotoSelectionResultSelected(value: selection)) else { files.cleanup(url); return }
    }
    @discardableResult private func finish(_ result: GroupPhotoSelectionResult) -> Bool {
        guard let next = completion else { return false }
        completion = nil; next(result, nil); return true
    }
    private func cancelActive() {
        guard gate.cancel() != nil else { return }
        presented?.dismiss(animated: false); presented = nil; files.purge()
        finish(GroupPhotoSelectionResultCancelled.shared)
    }
}

final class IOSPhotoCacheAdapter: GroupPhotoCachePort {
    private let manager: FileManager; private let directory: URL
    init(manager: FileManager = .default) {
        self.manager = manager
        directory = manager.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("GroupPhotoCache")
    }
    func evict(groupId: String) {
        let prefix = String(groupId.hashValue) + "-"
        (try? manager.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil))?
            .filter { $0.lastPathComponent.hasPrefix(prefix) }.forEach { try? manager.removeItem(at: $0) }
    }
    func clearAll() { try? manager.removeItem(at: directory) }
}

struct IOSGroupPhotoAdapters {
    let selection: IOSPhotoSelectionAdapter
    let encoder: IOSPhotoEncoderAdapter
    let cache: IOSPhotoCacheAdapter
    @MainActor static func makeLive(presenter: @escaping () -> UIViewController?) -> IOSGroupPhotoAdapters {
        let files = IOSPhotoFiles()
        return IOSGroupPhotoAdapters(
            selection: IOSPhotoSelectionAdapter(files: files, presenter: presenter),
            encoder: IOSPhotoEncoderAdapter(files: files), cache: IOSPhotoCacheAdapter()
        )
    }
}
