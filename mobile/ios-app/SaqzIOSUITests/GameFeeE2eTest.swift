import XCTest

/**
 * JOGO-008 (roteiro 13, V1–V10): o cartão "Defina o valor por jogo" só para o dono; o editor de jogo não salva sem
 * escolha nem com Cobrar diária vazio ou 0; Sem diária salva e o grupo sem valor passa a Sem diária. O avulso vê
 * "Diária de R$ 20, cobrada ao confirmar" no jogo com valor (não no Sem diária) e o Vou gera a diária de R$ 20.
 * Igual ao GameFeeE2eTest do Android.
 */
final class GameFeeE2eTest: InstalledE2E {
    override var scenarioName: String { "game-fee" }
    private var gamesPath: String { "/api/groups/\(group)/games" }

    func testOwnerMustChooseTheGameFeeAndTheAvulsoSeesAndPaysTheFee() {
        login("owner")
        openGroup()
        waitTag("group-details-game-fee-card")
        waitText("Só você e os admins veem")
        waitText("Defina o valor por jogo")
        waitText("Hoje o avulso joga sem diária.")
        shot("cartao-defina-o-valor")

        openEditor()
        pickTomorrowAt(20)
        type("game-editor-venue-name", "Quadra J1")
        waitTag("game-editor-game-fee")
        tap("game-editor-submit")
        waitTag("game-fee-error")
        waitText("Escolha o valor ou Sem diária.")
        shot("editor-sem-escolha")
        XCTAssertEqual(games().count, 0)

        tap("game-fee-choice-charge")
        tap("game-editor-submit")
        waitText("Informe um valor maior que R$ 0,00.")
        type("game-fee-amount", "0")
        tap("game-editor-submit")
        waitText("Informe um valor maior que R$ 0,00.")
        shot("editor-cobrar-zero")
        XCTAssertEqual(games().count, 0)

        tap("game-fee-choice-none")
        waitText("O avulso joga sem pagar por jogo.")
        shot("editor-sem-diaria")
        tap("game-editor-submit")
        waitGone("game-editor")
        // Depois de marcar, a tela volta ao grupo (ou ao jogo novo); o cartão do grupo some.
        let created = games()
        XCTAssertEqual(created.count, 1)
        let noFee = created.first ?? [:]
        XCTAssertEqual(noFee["noGameFee"] as? Bool, true)
        XCTAssertTrue(noFee["gameFeeCents"] == nil || noFee["gameFeeCents"] is NSNull)
        XCTAssertEqual(string(noFee["status"]), "PUBLISHED")
        let defaults = api("owner", "/api/groups/\(group)")["financeDefaults"] as? [String: Any] ?? [:]
        XCTAssertEqual(defaults["defaultNoGameFee"] as? Bool, true)
        XCTAssertEqual(defaults["gameFeeUndefined"] as? Bool, false)
        if !exists("group-details", timeout: 3) { back() }
        waitTag("group-details")
        waitGone("group-details-game-fee-card")
        shot("grupo-sem-cartao")

        // J2 com R$ 20 (precondição pela API: o valor no editor já foi coberto acima).
        let withFee = publishGame("22:00", fee: 2000)
        backToShell()
        logout()

        login("athlete")
        openGroup()
        waitTag("group-details-hero")
        XCTAssertFalse(exists("group-details-game-fee-card"))
        shot("atleta-grupo-sem-cartao")

        // O J1 (20h) é o próximo jogo, no topo do grupo; o J2 (22h) fica na agenda.
        tap("group-details-view-game")
        waitTag("game-detail")
        waitTag("game-detail-attendance-going")
        XCTAssertFalse(exists("game-detail-fee-note"))
        shot("atleta-jogo-sem-diaria")
        back()

        tap("group-details-agenda-game-\(withFee)")
        waitTag("game-detail")
        let note = waitTag("game-detail-fee-note")
        XCTAssertTrue(containing("cobrada ao confirmar").exists && note.exists)
        waitContains("R$ 20")
        shot("atleta-jogo-diaria-20")
        tap("game-detail-attendance-going")
        eventually("diária do Vou") { ownCharges().count == 1 }
        let charge = ownCharges().first ?? [:]
        XCTAssertEqual(string(charge["kind"]), "GAME")
        XCTAssertEqual(string(charge["gameId"]), withFee)
        XCTAssertEqual(int(charge["amountCents"]), 2000)
        XCTAssertEqual(string(charge["status"]), "PENDING")
        XCTAssertEqual(string(charge["dueDate"]), string(data["tomorrow"]))
        dismissNotificationPrompt()
        waitTag("game-detail-attendance-change")
        shot("atleta-confirmado-com-diaria")
    }

    private func openEditor() {
        eventually("botão de marcar jogo") { exists("group-details-agenda-create") || exists("group-details-create-next-game") }
        tap(exists("group-details-create-next-game") ? "group-details-create-next-game" : "group-details-agenda-create")
        waitTag("game-editor")
    }

    private func pickTomorrowAt(_ hour: Int) {
        tap("game-editor-date")
        waitTag("game-editor-date-time-picker")
        tap("game-picker-day-\(string(data["tomorrow"]))")
        tap("game-picker-hour-\(hour)")
        tap("game-picker-minute-0")
        tapText("Salvar data e horário")
        waitGone("game-picker-hour-\(hour)")
    }

    private func games() -> [[String: Any]] { apiList("owner", gamesPath) }

    private func ownCharges() -> [[String: Any]] { apiArray("athlete", "/api/groups/\(group)/charges/me", key: "charges") }

    private func publishGame(_ time: String, fee: Int) -> String {
        let date = string(data["tomorrow"])
        let starts = ISO8601DateFormatter().date(from: "\(date)T\(time):00-03:00")!
        let iso = ISO8601DateFormatter()
        let created = api("owner", gamesPath, method: "POST", body: [
            "requestId": UUID().uuidString.lowercased(), "title": "Quadra J2",
            "venue": ["name": "Quadra J2", "address": "Rua Teste 100"],
            "localDate": date, "localTime": time, "zoneId": "America/Sao_Paulo",
            "startsAt": iso.string(from: starts), "durationMinutes": 90, "capacity": 6,
            "confirmationDeadline": iso.string(from: starts.addingTimeInterval(-7200)),
            "gameFeeCents": fee, "useDefaultGameFee": false,
        ], status: 201)
        let id = string(created["id"])
        api("owner", "\(gamesPath)/\(id)/publish", method: "POST", headers: ["If-Match": "\"\(int(created["version"]))\""])
        return id
    }
}
