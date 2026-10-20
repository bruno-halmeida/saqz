import Foundation
import SaqzMobile
import UIKit

/// O que o leitor precisa da área de transferência. `UIPasteboard` implementa os dois passos; o teste usa um falso.
@MainActor
protocol IOSPasteboardLinkSource {
    /// Pergunta ao sistema se há uma URL provável **sem ler o conteúdo**: é o que evita o aviso de colar do iOS.
    func hasProbableWebURL(_ completion: @escaping (Bool) -> Void)
    /// Lê o texto. É esta chamada que mostra o aviso de colar.
    func text() -> String?
}

extension UIPasteboard: IOSPasteboardLinkSource {
    func hasProbableWebURL(_ completion: @escaping (Bool) -> Void) {
        // O sistema chama de uma fila própria: o fechamento é @Sendable (não herda o ator principal)
        // e só volta à fila principal para entregar o resultado.
        nonisolated(unsafe) let completion = completion
        detectPatterns(for: [.probableWebURL]) { @Sendable result in
            let found = (try? result.get())?.contains(.probableWebURL) == true
            DispatchQueue.main.async { completion(found) }
        }
    }

    func text() -> String? { string }
}

/// A página de links copia o link do convite ou da presença antes de abrir a App Store. No primeiro
/// acesso depois da instalação, procura esse link e o entrega ao mesmo caminho do link universal.
@MainActor
final class IOSClipboardLinkReader: @preconcurrency ClipboardLinkPort {
    static let consumedKey = "saqz.clipboardLink.consumed"
    private let links: IOSLinkAdapter
    private let pasteboard: IOSPasteboardLinkSource
    private let defaults: UserDefaults
    private let host: String

    init(
        links: IOSLinkAdapter,
        pasteboard: IOSPasteboardLinkSource = UIPasteboard.general,
        defaults: UserDefaults = .standard,
        host: String = "links.saqz.app"
    ) {
        self.links = links
        self.pasteboard = pasteboard
        self.defaults = defaults
        self.host = host.lowercased()
    }

    /// Uma consulta por instalação, feita ou não a leitura: depois dela o conteúdo da área de
    /// transferência não é mais assunto do app.
    func readOnce() {
        guard !defaults.bool(forKey: Self.consumedKey) else { return }
        defaults.set(true, forKey: Self.consumedKey)
        pasteboard.hasProbableWebURL { [weak self] found in
            guard found, let self else { return }
            MainActor.assumeIsolated { self.deliver(self.pasteboard.text()) }
        }
    }

    private func deliver(_ text: String?) {
        guard let text = text?.trimmingCharacters(in: .whitespacesAndNewlines),
              let url = URL(string: text),
              url.scheme?.lowercased() == "https",
              url.host?.lowercased() == host else { return }
        links.onOpenURL(url)
    }
}
