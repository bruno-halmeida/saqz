import ActivityKit
import AppIntents
import Foundation

/// Janela de presença das 24 h. Contrato com o push-to-start do backend (VUL-266): nomes idênticos,
/// `startsAt` em segundos Unix (o `Date` padrão do Codable conta a partir de 2001) e `status` começa em PENDING.
@available(iOS 16.1, *)
struct SaqzGameAttributes: ActivityAttributes {
    enum Status: String, Codable, Hashable {
        case pending = "PENDING"
        case sending = "SENDING"
        case confirmed = "CONFIRMED"
        case waitlisted = "WAITLISTED"
        case declined = "DECLINED"
        case closed = "CLOSED"
        case failed = "FAILED"
        case noResponse = "NO_RESPONSE"
    }

    struct ContentState: Codable, Hashable {
        var confirmed: Int
        var capacity: Int
        var waitlisted: Int
        var status: Status
    }

    var groupId: String
    var gameId: String
    var recipient: String
    var venue: String
    var startsAt: Int

    var startDate: Date { Date(timeIntervalSince1970: TimeInterval(startsAt)) }
}

/// "Confirmar" / "Não vou" do card. Como `LiveActivityIntent`, roda no processo do app, que instala
/// [handler] no launch (VUL-269). Na extensão ele nunca roda; sem handler, não faz nada.
@available(iOS 17.0, *)
struct AttendanceWindowIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Responder presença"
    @MainActor static var handler: ((_ gameId: String, _ confirm: Bool) async -> Void)?

    @Parameter(title: "Jogo") var gameId: String
    @Parameter(title: "Vou") var confirm: Bool

    init() {}

    init(gameId: String, confirm: Bool) {
        self.gameId = gameId
        self.confirm = confirm
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        await Self.handler?(gameId, confirm)
        return .result()
    }
}
