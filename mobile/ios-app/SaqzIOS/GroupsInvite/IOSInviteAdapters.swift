import Foundation
import SaqzMobile
import UIKit

@MainActor
final class IOSInviteUrlStore: @preconcurrency GroupInviteUrlStorePort {
    private let defaults: UserDefaults
    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    func read(groupId: String, done: GroupInviteUrlReadCallback) {
        done.complete(result_____: GroupInviteUrlReadResultSuccess(inviteUrl: defaults.string(forKey: key(groupId))))
    }

    func write(groupId: String, inviteUrl: String?, done: GroupInviteUrlWriteCallback) {
        if let inviteUrl { defaults.set(inviteUrl, forKey: key(groupId)) }
        else { defaults.removeObject(forKey: key(groupId)) }
        done.complete(result______: GroupInviteUrlWriteResultSuccess.shared)
    }

    private func key(_ groupId: String) -> String { "invite-url:\(groupId)" }
}

@MainActor
final class IOSInviteShareAdapter: @preconcurrency NativeInviteSharePort, NativeInviteClipboardPort {
    private let presenter: () -> UIViewController?
    init(presenter: @escaping () -> UIViewController?) { self.presenter = presenter }

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
        UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil)
        done(InviteNativeOperationResultSuccess.shared)
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
        presenter.present(UIActivityViewController(activityItems: items, applicationActivities: nil), animated: true)
        done(InviteNativeOperationResultSuccess.shared)
    }
}

private extension Data {
    init?(_ bytes: KotlinByteArray) {
        self.init((0..<Int(bytes.size)).map { UInt8(bitPattern: bytes.get(index: Int32($0))) })
    }
}
