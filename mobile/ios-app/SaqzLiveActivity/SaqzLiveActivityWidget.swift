import ActivityKit
import AppIntents
import SwiftUI
import WidgetKit

/// Card da janela de presença das 24 h: tela de bloqueio e Dynamic Island (VUL-268).
struct SaqzLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SaqzGameAttributes.self) { context in
            WindowCard(context: context)
                .padding(16)
                .activityBackgroundTint(Palette.navy)
                .activitySystemActionForegroundColor(.white)
                .widgetURL(context.attributes.gameURL)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label(context.attributes.startDate.saqzTime, systemImage: "volleyball.fill")
                        .font(.headline)
                        .foregroundStyle(Palette.accent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("\(context.state.confirmed)/\(context.state.capacity)")
                        .font(.headline)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(context.attributes.venue).font(.subheadline).lineLimit(1)
                        WindowActions(context: context)
                    }
                }
            } compactLeading: {
                Image(systemName: "volleyball.fill").foregroundStyle(Palette.accent)
            } compactTrailing: {
                Text("\(context.state.confirmed)/\(context.state.capacity)")
            } minimal: {
                Image(systemName: "volleyball.fill").foregroundStyle(Palette.accent)
            }
            .widgetURL(context.attributes.gameURL)
        }
    }
}

private struct WindowCard: View {
    let context: ActivityViewContext<SaqzGameAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("Saqz", systemImage: "volleyball.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Palette.accent)
                Spacer()
                Text(context.state.countLabel)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white.opacity(0.8))
            }
            Text("Amanhã às \(context.attributes.startDate.saqzTime)")
                .font(.headline)
                .foregroundStyle(.white)
            Text(context.attributes.venue)
                .font(.subheadline)
                .foregroundStyle(.white.opacity(0.8))
                .lineLimit(1)
            WindowActions(context: context)
        }
    }
}

private struct WindowActions: View {
    let context: ActivityViewContext<SaqzGameAttributes>

    var body: some View {
        if context.isStale {
            Text("Janela encerrada. Confirme pelo app.")
                .font(.subheadline)
                .foregroundStyle(.white)
        } else if context.state.status.showsButtons {
            VStack(alignment: .leading, spacing: 8) {
                Text(context.state.status.message)
                    .font(.footnote)
                    .foregroundStyle(.white)
                HStack(spacing: 8) {
                    Button(intent: AttendanceWindowIntent(gameId: context.attributes.gameId, confirm: true)) {
                        Text("Confirmar").frame(maxWidth: .infinity)
                    }
                    .tint(Palette.accent)
                    .foregroundStyle(Palette.navy)
                    Button(intent: AttendanceWindowIntent(gameId: context.attributes.gameId, confirm: false)) {
                        Text("Não vou").frame(maxWidth: .infinity)
                    }
                    .tint(.white.opacity(0.2))
                    .foregroundStyle(.white)
                }
                .buttonStyle(.borderedProminent)
            }
        } else {
            Text(context.state.status.message)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
        }
    }
}

/// Tokens do design system (`SaqzColorTokens.kt`): textPrimary como fundo e accent nos destaques.
private enum Palette {
    static let navy = Color(red: 0x0E / 255, green: 0x17 / 255, blue: 0x38 / 255)
    static let accent = Color(red: 0xC7 / 255, green: 0xF3 / 255, blue: 0x00 / 255)
}

extension SaqzGameAttributes.Status {
    /// Botões só enquanto a pessoa não respondeu, ou quando o envio falhou (decisão de produto).
    var showsButtons: Bool { self == .pending || self == .failed }

    /// Resultados com os mesmos textos do push de hoje (`IOSNotificationPort.swift`).
    var message: String {
        switch self {
        case .pending: return "Você vai?"
        case .sending: return "Enviando…"
        case .confirmed: return "Presença confirmada."
        case .waitlisted: return "Jogo lotado: você entrou na lista de espera."
        case .declined: return "Ausência registrada."
        case .closed: return "Prazo encerrado. Abra o app para conferir."
        case .failed: return "Não deu para registrar. Abra o app e tente de novo."
        case .noResponse: return "Sem resposta do servidor. Abra o app para conferir."
        }
    }
}

extension SaqzGameAttributes.ContentState {
    var countLabel: String {
        let base = "\(confirmed)/\(capacity) confirmados"
        return waitlisted > 0 ? "\(base) · \(waitlisted) na espera" : base
    }
}

extension SaqzGameAttributes {
    /// Toque no card abre a tela do jogo (o VUL-271 trata o endereço).
    var gameURL: URL? {
        var components = URLComponents()
        components.scheme = "saqz"
        components.host = "game"
        components.queryItems = [URLQueryItem(name: "group", value: groupId), URLQueryItem(name: "game", value: gameId)]
        return components.url
    }

    static var preview: SaqzGameAttributes {
        SaqzGameAttributes(groupId: "g1", gameId: "game1", recipient: "sub", venue: "Arena Central", startsAt: 1_790_000_000)
    }
}

private extension Date {
    var saqzTime: String { formatted(.dateTime.hour().minute().locale(Locale(identifier: "pt_BR"))) }
}

#Preview("Tela de bloqueio", as: .content, using: SaqzGameAttributes.preview) {
    SaqzLiveActivityWidget()
} contentStates: {
    SaqzGameAttributes.ContentState(confirmed: 9, capacity: 12, waitlisted: 2, status: .pending)
    SaqzGameAttributes.ContentState(confirmed: 9, capacity: 12, waitlisted: 2, status: .sending)
    SaqzGameAttributes.ContentState(confirmed: 10, capacity: 12, waitlisted: 2, status: .confirmed)
    SaqzGameAttributes.ContentState(confirmed: 12, capacity: 12, waitlisted: 3, status: .waitlisted)
    SaqzGameAttributes.ContentState(confirmed: 9, capacity: 12, waitlisted: 2, status: .failed)
}

#Preview("Dynamic Island", as: .dynamicIsland(.expanded), using: SaqzGameAttributes.preview) {
    SaqzLiveActivityWidget()
} contentStates: {
    SaqzGameAttributes.ContentState(confirmed: 9, capacity: 12, waitlisted: 2, status: .pending)
}
