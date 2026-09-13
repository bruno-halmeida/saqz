import Foundation
import UIKit
import UniformTypeIdentifiers
import SaqzMobile

@MainActor
final class IOSReceiptDocumentPicker: NSObject, @preconcurrency ReceiptDocumentPicker, UIDocumentPickerDelegate {
    private let presenter: () -> UIViewController?
    private var picker: UIDocumentPickerViewController?
    private var callback: ReceiptFileCallback?

    init(presenter: @escaping () -> UIViewController?) { self.presenter = presenter }

    func choose(done: ReceiptFileCallback) -> ReceiptFileCancellation {
        guard picker == nil, let host = presenter() else {
            done.onFileSelected(selection: ReceiptFileSelectionInvalid.shared)
            return IOSReceiptFileCancellation({})
        }
        let next = UIDocumentPickerViewController(forOpeningContentTypes: [.pdf, .jpeg, .png], asCopy: false)
        next.allowsMultipleSelection = false
        next.delegate = self
        picker = next
        callback = done
        host.present(next, animated: true)
        return IOSReceiptFileCancellation { [weak self, weak next] in
            guard let self, self.picker === next else { return }
            self.callback = nil
            self.picker = nil
            next?.dismiss(animated: true)
        }
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish(controller, ReceiptFileSelectionCancelled.shared)
    }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard picker === controller, let url = urls.first else { return }
        let accessed = url.startAccessingSecurityScopedResource()
        defer { if accessed { url.stopAccessingSecurityScopedResource() } }
        do {
            let type = try url.resourceValues(forKeys: [.contentTypeKey]).contentType
            guard let mime = type?.preferredMIMEType, ["application/pdf", "image/jpeg", "image/png"].contains(mime) else {
                finish(controller, ReceiptFileSelectionInvalid.shared); return
            }
            let file = try FileHandle(forReadingFrom: url)
            defer { try? file.close() }
            let data = try file.read(upToCount: 5 * 1024 * 1024 + 1) ?? Data()
            guard !data.isEmpty, data.count <= 5 * 1024 * 1024 else {
                finish(controller, ReceiptFileSelectionInvalid.shared); return
            }
            let bytes = KotlinByteArray(size: Int32(data.count))
            for (index, byte) in data.enumerated() { bytes.set(index: Int32(index), value: Int8(bitPattern: byte)) }
            finish(controller, ReceiptFileSelectionSelected(file: ReceiptDocumentFile(bytes: bytes, contentType: mime)))
        } catch { finish(controller, ReceiptFileSelectionInvalid.shared) }
    }
    private func finish(_ controller: UIDocumentPickerViewController, _ result: ReceiptFileSelection) {
        guard picker === controller else { return }
        let done = callback
        callback = nil; picker = nil
        done?.onFileSelected(selection: result)
    }
}

@MainActor
private final class IOSReceiptFileCancellation: NSObject, @preconcurrency ReceiptFileCancellation {
    private var action: (() -> Void)?
    init(_ action: @escaping () -> Void) { self.action = action }
    func cancel() { let run = action; action = nil; run?() }
}
