import Foundation
import SaqzMobile
import UIKit

@MainActor
final class IOSInviteUrlStore: @preconcurrency GroupInviteUrlStorePort {
    private let defaults: UserDefaults
    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    func read(groupId: String, done: GroupInviteUrlReadCallback) {
        let cache = defaults.string(forKey: urlKey(groupId)).map {
            GroupInviteUrlCache(inviteUrl: $0, expiresAt: defaults.string(forKey: expiresAtKey(groupId)))
        }
        done.complete(result_____: GroupInviteUrlReadResultSuccess(cache: cache))
    }

    func write(groupId: String, cache: GroupInviteUrlCache?, done: GroupInviteUrlWriteCallback) {
        if let cache {
            defaults.set(cache.inviteUrl, forKey: urlKey(groupId))
            if let expiresAt = cache.expiresAt { defaults.set(expiresAt, forKey: expiresAtKey(groupId)) }
            else { defaults.removeObject(forKey: expiresAtKey(groupId)) }
        } else {
            defaults.removeObject(forKey: urlKey(groupId))
            defaults.removeObject(forKey: expiresAtKey(groupId))
        }
        done.complete(result______: GroupInviteUrlWriteResultSuccess.shared)
    }

    private func urlKey(_ groupId: String) -> String { "invite-url:\(groupId)" }
    private func expiresAtKey(_ groupId: String) -> String { "invite-expires-at:\(groupId)" }
}

@MainActor
final class IOSInviteShareAdapter: NSObject, @preconcurrency NativeInviteSharePort, NativeInviteClipboardPort {
    private let presenter: () -> UIViewController?
    init(presenter: @escaping () -> UIViewController?) {
        self.presenter = presenter
        super.init()
    }

    func shareText(text: String, done: @escaping (any InviteNativeOperationResult) -> Void) {
        present(items: [text], done: done)
    }

    func shareImage(image: InviteShareImage, done_ done: @escaping (any InviteNativeOperationResult) -> Void) {
        guard let data = Data(image.pngBytes) else {
            done(InviteNativeOperationResultFailure(code: .providerUnavailable))
            return
        }
        present(items: [data], done: done)
    }

    func saveImage(image: InviteShareImage, done: @escaping (any InviteNativeOperationResult) -> Void) {
        guard let data = Data(image.pngBytes), let image = UIImage(data: data) else {
            done(InviteNativeOperationResultFailure(code: .providerUnavailable))
            return
        }
        let context = Unmanaged.passRetained(SaveContext(done: done))
        UIImageWriteToSavedPhotosAlbum(
            image,
            self,
            #selector(image(_:didFinishSavingWithError:contextInfo:)),
            context.toOpaque(),
        )
    }

    func doCopyText(text: String, done: @escaping (any InviteNativeOperationResult) -> Void) {
        UIPasteboard.general.string = text
        done(InviteNativeOperationResultSuccess.shared)
    }

    private func present(items: [Any], done: @escaping (any InviteNativeOperationResult) -> Void) {
        guard let presenter = presenter() else {
            done(InviteNativeOperationResultFailure(code: .providerUnavailable))
            return
        }
        let controller = UIActivityViewController(activityItems: items, applicationActivities: nil)
        if let popover = controller.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = presenter.view.bounds
        }
        presenter.present(controller, animated: true)
        done(InviteNativeOperationResultSuccess.shared)
    }

    @objc private func image(
        _ image: UIImage,
        didFinishSavingWithError error: Error?,
        contextInfo: UnsafeMutableRawPointer?,
    ) {
        guard let contextInfo else { return }
        let context = Unmanaged<SaveContext>.fromOpaque(contextInfo).takeRetainedValue()
        if error == nil {
            context.done(InviteNativeOperationResultSuccess.shared)
        } else {
            context.done(InviteNativeOperationResultFailure(code: .providerUnavailable))
        }
    }

    private final class SaveContext {
        let done: (any InviteNativeOperationResult) -> Void

        init(done: @escaping (any InviteNativeOperationResult) -> Void) {
            self.done = done
        }
    }
}

private extension Data {
    init?(_ bytes: KotlinByteArray) {
        self.init((0..<Int(bytes.size)).map { UInt8(bitPattern: bytes.get(index: Int32($0))) })
    }
}
