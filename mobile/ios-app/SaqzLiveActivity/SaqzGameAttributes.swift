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
    /// Jogo com vagas por posição (5-1, DEB-107). Opcional: um payload sem a chave (backend ou app antigo) decodifica
    /// como `nil`, que é jogo sem posições.
    var positions: Bool? = nil

    var startDate: Date { Date(timeIntervalSince1970: TimeInterval(startsAt)) }

    /// Dia do jogo no título do cartão (DEB-113: a janela abre 8 h antes do prazo, então o jogo pode ser hoje):
    /// "Hoje", "Amanhã" ou o dia da semana ("Quarta"), no calendário do aparelho.
    func dayLabel(now: Date = Date(), calendar: Calendar = .current) -> String {
        if calendar.isDate(startDate, inSameDayAs: now) { return "Hoje" }
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now), calendar.isDate(startDate, inSameDayAs: tomorrow) {
            return "Amanhã"
        }
        let weekdays = ["Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado"]
        return weekdays[calendar.component(.weekday, from: startDate) - 1]
    }

    /// O que o "Confirmar" do card faz: responde por baixo (jogo sem posições) ou abre o app na tela de escolher
    /// posição, sem confirmar nada (jogo com posições). "Não vou" responde sempre.
    enum ConfirmAction: Equatable { case respond, openPositionPicker }

    var confirmAction: ConfirmAction { positions == true ? .openPositionPicker : .respond }

    /// `saqz://game?group=<id>&game=<id>&pickPosition=1`: o mesmo caminho do toque com `pickPosition` no push.
    var pickPositionURL: URL? {
        var components = URLComponents()
        components.scheme = "saqz"
        components.host = "game"
        components.queryItems = [
            URLQueryItem(name: "group", value: groupId),
            URLQueryItem(name: "game", value: gameId),
            URLQueryItem(name: "pickPosition", value: "1"),
        ]
        return components.url
    }
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
