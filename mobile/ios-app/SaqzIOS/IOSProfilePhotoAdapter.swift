import Foundation
import AVFoundation
import SaqzMobile

/// A foto de perfil não tem pilha própria: quem escolhe é o `IOSPhotoSelectionAdapter` e quem
/// recorta e recodifica é o `IOSPhotoEncoderAdapter` — os mesmos de `GroupsPhoto/`. Aqui só se
/// traduz o par completionHandler em callback exportável e se apaga o arquivo de origem, que o
/// acesso não usa: o envio é por bytes.
@MainActor
final class IOSProfilePhotoAdapter: NSObject, @preconcurrency NativeProfilePhotoPort,
    @preconcurrency ProfilePhotoSelectionPort {
    private let selection: GroupPhotoSelectionPort
    private let encoder: GroupPhotoEncoderPort
    private let permissions: IOSProfilePhotoPermissions

    init(
        selection: GroupPhotoSelectionPort,
        encoder: GroupPhotoEncoderPort,
        permissions: IOSProfilePhotoPermissions = IOSSystemProfilePhotoPermissions()
    ) {
        self.selection = selection
        self.encoder = encoder
        self.permissions = permissions
        super.init()
    }

    func chooseCamera(done: ProfilePhotoCallback) -> Cancelable {
        choose(done) { self.selection.chooseCamera(completionHandler: $0) }
    }

    func chooseLibrary(done: ProfilePhotoCallback) -> Cancelable {
        choose(done) { self.selection.chooseLibrary(completionHandler: $0) }
    }

    func chooseCamera(done_ done: ProfilePhotoSelectionCallback) -> ProfilePhotoSelectionCancelable {
        chooseProfile(
            done,
            denied: ProfilePhotoSelectionResultCameraPermissionDenied.shared,
            permissionDenied: permissions.cameraPermissionDenied
        ) { self.selection.chooseCamera(completionHandler: $0) }
    }

    func chooseLibrary(done_ done: ProfilePhotoSelectionCallback) -> ProfilePhotoSelectionCancelable {
        openProfile(done) { self.selection.chooseLibrary(completionHandler: $0) }
    }

    private func choose(
        _ done: ProfilePhotoCallback,
        _ open: (@escaping (GroupPhotoSelectionResult?, Error?) -> Void) -> Void
    ) -> Cancelable {
        let request = IOSProfilePhotoRequest(done: done, selection: selection, encoder: encoder)
        // O objeto vindo do Kotlin não é Sendable e tem exatamente um consumidor: este salto
        // para a main. Copiar os bytes só para atravessar seria megabyte à toa.
        open { result, _ in
            nonisolated(unsafe) let chosen = result
            Task { @MainActor in request.chosen(chosen) }
        }
        return request
    }

    private func chooseProfile(
        _ done: ProfilePhotoSelectionCallback,
        denied: ProfilePhotoSelectionResult,
        permissionDenied: () -> Bool,
        _ open: (@escaping (GroupPhotoSelectionResult?, Error?) -> Void) -> Void
    ) -> ProfilePhotoSelectionCancelable {
        guard !permissionDenied() else {
            done.complete(result_________: denied)
            return IOSProfilePhotoSelectionCancellation()
        }
        return openProfile(done, open)
    }

    private func openProfile(
        _ done: ProfilePhotoSelectionCallback,
        _ open: (@escaping (GroupPhotoSelectionResult?, Error?) -> Void) -> Void
    ) -> ProfilePhotoSelectionCancelable {
        let request = IOSProfilePhotoSelectionRequest(done: done, selection: selection, encoder: encoder)
        open { result, _ in
            nonisolated(unsafe) let chosen = result
            Task { @MainActor in request.chosen(chosen) }
        }
        return request
    }
}

@MainActor
protocol IOSProfilePhotoPermissions {
    func cameraPermissionDenied() -> Bool
}

@MainActor
struct IOSSystemProfilePhotoPermissions: IOSProfilePhotoPermissions {
    func cameraPermissionDenied() -> Bool {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        return status == .denied || status == .restricted
    }
}

@MainActor
private final class IOSProfilePhotoSelectionCancellation: NSObject, @preconcurrency ProfilePhotoSelectionCancelable {
    func cancel() {}
}

@MainActor
private final class IOSProfilePhotoSelectionRequest: NSObject, @preconcurrency ProfilePhotoSelectionCancelable {
    private let selection: GroupPhotoSelectionPort
    private let encoder: GroupPhotoEncoderPort
    private var done: ProfilePhotoSelectionCallback?

    init(done: ProfilePhotoSelectionCallback, selection: GroupPhotoSelectionPort, encoder: GroupPhotoEncoderPort) {
        self.done = done
        self.selection = selection
        self.encoder = encoder
        super.init()
    }

    func cancel() { done = nil }

    func chosen(_ result: GroupPhotoSelectionResult?) {
        guard let selected = result as? GroupPhotoSelectionResultSelected else {
            finish(result is GroupPhotoSelectionResultCancelled
                ? ProfilePhotoSelectionResultCancelled.shared
                : ProfilePhotoSelectionResultFailed.shared)
            return
        }
        encode(selected.value.source.value)
    }

    private func encode(_ source: String) {
        guard done != nil else { selection.cleanup(source: source); return }
        encoder.encode(source: source, crop: GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 1)) { result, _ in
            nonisolated(unsafe) let payload = result
            Task { @MainActor in self.encoded(source, payload) }
        }
    }

    private func encoded(_ source: String, _ result: GroupPhotoEncodingResult?) {
        selection.cleanup(source: source)
        guard let encoded = result as? GroupPhotoEncodingResultEncoded else {
            finish(ProfilePhotoSelectionResultFailed.shared)
            return
        }
        let bytes = encoded.value.source.read()
        finish(bytes.size > 0
            ? ProfilePhotoSelectionResultSelected(bytes: bytes, mediaType: encoded.value.mediaType.value)
            : ProfilePhotoSelectionResultFailed.shared)
    }

    private func finish(_ result: ProfilePhotoSelectionResult) {
        guard let next = done else { return }
        done = nil
        next.complete(result_________: result)
    }
}

/// Uma escolha em andamento. `cancel()` é a desistência da tela: ninguém mais recebe callback,
/// mas a origem ainda é apagada quando a escolha chegar. Desistência da pessoa é
/// `ProfilePhotoResult.Cancelled`, entregue pelo callback.
@MainActor
final class IOSProfilePhotoRequest: NSObject, @preconcurrency Cancelable {
    private let selection: GroupPhotoSelectionPort
    private let encoder: GroupPhotoEncoderPort
    private var done: ProfilePhotoCallback?

    init(done: ProfilePhotoCallback, selection: GroupPhotoSelectionPort, encoder: GroupPhotoEncoderPort) {
        self.done = done
        self.selection = selection
        self.encoder = encoder
        super.init()
    }

    func cancel() { done = nil }

    func chosen(_ result: GroupPhotoSelectionResult?) {
        guard let selected = result as? GroupPhotoSelectionResultSelected else {
            finish(result is GroupPhotoSelectionResultCancelled
                ? ProfilePhotoResultCancelled.shared
                : ProfilePhotoResultFailed.shared)
            return
        }
        encode(selected.value.source.value)
    }

    private func encode(_ source: String) {
        guard done != nil else { selection.cleanup(source: source); return }
        encoder.encode(source: source, crop: GroupPhotoCrop(centerX: 0.5, centerY: 0.5, zoom: 1)) { result, _ in
            nonisolated(unsafe) let payload = result
            Task { @MainActor in self.encoded(source, payload) }
        }
    }

    private func encoded(_ source: String, _ result: GroupPhotoEncodingResult?) {
        selection.cleanup(source: source)
        guard let encoded = result as? GroupPhotoEncodingResultEncoded else {
            finish(ProfilePhotoResultFailed.shared)
            return
        }
        let bytes = encoded.value.source.read()
        finish(bytes.size > 0
            ? ProfilePhotoResultSelected(bytes: bytes, mediaType: encoded.value.mediaType.value)
            : ProfilePhotoResultFailed.shared)
    }

    private func finish(_ result: ProfilePhotoResult) {
        guard let next = done else { return }
        done = nil
        next.complete(result_: result)
    }
}
