import XCTest

/**
 * PROM-010 (roteiro 13, A1–A17): "Vaga só com pagamento antecipado" só existe na Escolha manual; o avulso dá Vou e fica
 * na espera com "Pague R$ 20 para entrar" e a diária pendente; o gestor vê Falta pagar → Recebi → Pagou → Colocar sem
 * segunda diária; o convidado abre a folha com "Colocar, já pagou" e "Colocar e cobrar depois"; Não vou depois de pagar
 * não desfaz a baixa; voltar para Ordem de chegada desliga a opção com o aviso. Igual ao AdvancePaymentE2eTest do Android.
 */
final class AdvancePaymentE2eTest: InstalledE2E {
    override var scenarioName: String { "advance-payment" }
    private var athleteId: String { string(actor("athlete")["id"]) }
    private var mensalId: String { string(actor("mensal")["id"]) }
    private var gameId = ""

    func testAvulsoPaysBeforeTheOrganizerPlacesThemAndGuestsUseTheSheet() {
        login("owner")
        openGroup()
        openEditGroup()
        XCTAssertFalse(exists("group-game-config-advance-payment"))
        shot("ordem-de-chegada-sem-opcao")
        tapOption("group-game-config-promotion-mode", "Escolha manual")
        waitTag("group-game-config-advance-payment")
        waitText("O avulso paga antes de entrar.")
        // O interruptor é o próprio elemento identificado, mas o toque no centro cai no título e não liga: toca na
        // ponta direita (o botão) e confirma pelo apoio que só aparece ligado.
        let toggle = waitTag("group-game-config-advance-payment")
        let on = containing("Vale para avulso e convidado.")
        for _ in 0..<3 where !on.exists {
            reveal(toggle)
            toggle.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0.5)).tap()
            _ = on.waitForExistence(timeout: 3)
        }
        waitText("Vale para avulso e convidado. A diária nasce no Vou, com o Pix do grupo.")
        shot("escolha-manual-antecipado-ligado")
        tap("group-setup-submit")
        waitGone("group-setup-submit")
        let config = api("owner", "/api/groups/\(group)")["gameConfig"] as? [String: Any] ?? [:]
        XCTAssertEqual(config["advancePaymentRequired"] as? Bool, true)
        XCTAssertEqual(string(config["promotionMode"]), "MANUAL")

        gameId = publishGame()
        let gamePath = "/api/groups/\(group)/games/\(gameId)"
        XCTAssertEqual(int(api("owner", gamePath)["gameFeeCents"]), 2000)
        backToShell()
        logout()

        // Avulso: Vou → espera com a caixa de pagar e a diária pendente.
        login("athlete")
        openGroup()
        tap("group-details-view-game")
        waitTag("game-detail")
        tap("game-detail-attendance-going")
        dismissNotificationPrompt()
        waitTag("game-detail-advance-payment")
        waitContains("Pague R$ 20 para entrar")
        waitText("A vaga é liberada pelo organizador depois do pagamento.")
        shot("avulso-espera-pague-para-entrar")
        // O roteiro (A4) espera "Pix do grupo copiado."; o app não mostra aviso (registrado também no Android).
        // A área de transferência é conferida pelo runner no fim (simctl pbpaste): o teste não lê o que o app copiou.
        tap("game-detail-advance-payment-copy-pix")
        shot("avulso-pix-copiado")
        let own = api("athlete", "\(gamePath)/attendance")["ownAttendance"] as? [String: Any] ?? [:]
        XCTAssertEqual(string(own["status"]), "WAITLISTED")
        let athleteCharge = charges().first { string($0["memberId"]) == athleteId } ?? [:]
        XCTAssertEqual(int(athleteCharge["amountCents"]), 2000)
        XCTAssertEqual(string(athleteCharge["status"]), "PENDING")
        XCTAssertEqual(string(athleteCharge["dueDate"]), string(data["tomorrow"]))
        backToShell()
        logout()

        // Os outros pela API: mensalista com Bia e Caio, e o outro avulso.
        attend("mensal")
        guest("Bia")
        guest("Caio")
        attend("other")
        let pending = charges()
        XCTAssertEqual(pending.count, 4)
        XCTAssertTrue(pending.allSatisfy { string($0["status"]) == "PENDING" })
        XCTAssertFalse(pending.contains { string($0["memberId"]) == mensalId && isNull($0["guestDisplayName"]) })

        login("owner")
        openGroup()
        tap("group-details-view-game")
        waitTag("game-detail")
        tap("game-tab-waiting")
        lineContains(athleteId, "Falta pagar")
        lineContains("\(mensalId)#1", "não pagou")
        XCTAssertFalse(exists("game-detail-advance-line-\(mensalId)"))
        waitText("Você escolhe quem entra. Pix e dinheiro chegam por fora: marque Recebi.")
        shot("gestor-espera-falta-pagar")

        tap("game-detail-advance-receive-\(athleteId)")
        lineContains(athleteId, "Pagou")
        eventually("diária do avulso paga") {
            string(charges().first { string($0["memberId"]) == athleteId && isNull($0["guestDisplayName"]) }?["status"]) == "PAID"
        }
        shot("gestor-recebi-pagou")
        tap("game-detail-promote-\(athleteId)")
        eventually("avulso colocado") { status(athleteId) == "CONFIRMED" }
        XCTAssertFalse(exists("game-detail-advance-place-sheet"))
        XCTAssertEqual(charges().filter { string($0["memberId"]) == athleteId }.count, 1)

        tap("game-detail-promote-\(mensalId)#1")
        waitTag("game-detail-advance-place-paid")
        waitContains("ainda não pagou")
        waitText("Colocar e cobrar depois")
        shot("folha-colocar-convidada")
        tap("game-detail-advance-place-paid")
        eventually("Bia paga") { string(guestCharge("Bia")["status"]) == "PAID" }
        tap("game-detail-promote-\(mensalId)#2")
        tap("game-detail-advance-place-later")
        eventually("Caio colocado") { status(mensalId, guestSeq: 2) == "CONFIRMED" }
        XCTAssertEqual(string(guestCharge("Caio")["status"]), "PENDING")
        tap("game-detail-promote-\(mensalId)")
        eventually("mensalista colocado") { status(mensalId) == "CONFIRMED" }
        XCTAssertFalse(charges().contains { string($0["memberId"]) == mensalId && isNull($0["guestDisplayName"]) })
        shot("gestor-colocados")

        // Não vou depois de pagar não desfaz a baixa; o atleta não muda a regra do grupo.
        api("athlete", "\(gamePath)/attendance", method: "PUT", body: ["requestId": UUID().uuidString.lowercased(), "intent": "DECLINE"])
        let paid = charges().first { string($0["memberId"]) == athleteId } ?? [:]
        XCTAssertEqual(string(paid["status"]), "PAID")
        XCTAssertEqual(paid["withdrew"] as? Bool, true)
        XCTAssertEqual(charges().first { string($0["memberId"]) == string(actor("other")["id"]) }?["waitlisted"] as? Bool, true)
        // O atleta não dá Recebi: a rota de status com If-Match válido volta 403 e a cobrança não muda.
        let otherCharge = charges().first { string($0["memberId"]) == string(actor("other")["id"]) } ?? [:]
        api("athlete", "/api/groups/\(group)/charges/\(string(otherCharge["id"]))/status", method: "POST",
            body: ["status": "PAID", "paidMethod": "PIX"], status: 403, headers: ["If-Match": "\"\(int(otherCharge["version"]))\""])
        XCTAssertEqual(NSDictionary(dictionary: charges().first { string($0["id"]) == string(otherCharge["id"]) } ?? [:]),
                       NSDictionary(dictionary: otherCharge))

        // Encerrado pela API, o acerto mostra quem desistiu e quem ficou na espera. Encerrado antes do horário, o jogo
        // não está em "Jogos anteriores": a tela do jogo, ainda aberta, relê ao voltar ao primeiro plano.
        let version = int(api("owner", gamePath)["version"])
        api("owner", "\(gamePath)/complete", method: "POST", headers: ["If-Match": "\"\(version)\""])
        XCUIDevice.shared.press(.home)
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        app.activate()
        waitTag("game-detail")
        eventually("botão do acerto") { exists("game-detail-settle") || exists("game-detail-settle-view") }
        tap(exists("game-detail-settle") ? "game-detail-settle" : "game-detail-settle-view")
        waitTag("game-settlement")
        settlementRow(string(paid["id"]), ["Desistiu"])
        settlementRow(string(otherCharge["id"]), ["Na espera", "Não liberado"])
        shot("acerto-desistiu-e-na-espera")
        backToShell()

        // Ordem de chegada desliga a opção, com o aviso.
        let before = Set(charges().map { "\(string($0["id"]))=\(string($0["status"]))" })
        openGroup()
        openEditGroup()
        tapOption("group-game-config-promotion-mode", "Ordem de chegada")
        waitTag("group-game-config-advance-payment-turned-off")
        waitText("A vaga volta a ser por ordem de chegada; quem está na espera sobe pela fila.")
        shot("ordem-de-chegada-desliga")
        tap("group-setup-submit")
        waitGone("group-setup-submit")
        let after = api("owner", "/api/groups/\(group)")["gameConfig"] as? [String: Any] ?? [:]
        XCTAssertEqual(after["advancePaymentRequired"] as? Bool, false)
        XCTAssertEqual(string(after["promotionMode"]), "FIFO")
        XCTAssertEqual(Set(charges().map { "\(string($0["id"]))=\(string($0["status"]))" }), before)
    }

    /** Linha de "Não jogaram" no acerto com uma das etiquetas. */
    private func settlementRow(_ chargeId: String, _ labels: [String]) {
        let row = waitTag("game-settlement-not-played-\(chargeId)")
        reveal(row)
        let area = row.frame.insetBy(dx: -4, dy: -4)
        eventually("\(labels) na linha \(chargeId)") {
            labels.contains { label in
                row.label.contains(label) ||
                    app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", label)).allElementsBoundByIndex
                        .contains { area.contains(CGPoint(x: $0.frame.midX, y: $0.frame.midY)) }
            }
        }
    }

    private func openEditGroup() {
        tap("group-details-edit-group")
        waitTag("group-setup-submit")
        reveal(waitTag("group-game-config-promotion-mode"))
    }

    private func lineContains(_ rowId: String, _ text: String) {
        let line = waitTag("game-detail-advance-line-\(rowId)")
        reveal(line)
        eventually("\(text) na linha \(rowId)") {
            line.label.contains(text) || containing(text, in: line).exists ||
                app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).allElementsBoundByIndex
                    .contains { line.frame.insetBy(dx: -4, dy: -4).contains(CGPoint(x: $0.frame.midX, y: $0.frame.midY)) }
        }
    }

    private func attend(_ role: String) {
        api(role, "/api/groups/\(group)/games/\(gameId)/attendance", method: "PUT",
            body: ["requestId": UUID().uuidString.lowercased(), "intent": "CONFIRM"])
    }

    private func guest(_ name: String) {
        api("mensal", "/api/groups/\(group)/games/\(gameId)/attendance/guests", method: "POST",
            body: ["requestId": UUID().uuidString.lowercased(), "displayName": name], status: 201)
    }

    private func status(_ memberId: String, guestSeq: Int = 0) -> String {
        let roster = api("owner", "/api/groups/\(group)/games/\(gameId)/attendance/roster")
        for (list, result) in [("confirmed", "CONFIRMED"), ("waitlisted", "WAITLISTED")] {
            let items = roster[list] as? [[String: Any]] ?? []
            if items.contains(where: { string($0["memberId"]) == memberId && max(int($0["guestSeq"]), 0) == guestSeq }) { return result }
        }
        return "NONE"
    }

    private func guestCharge(_ name: String) -> [String: Any] {
        charges().first { string($0["guestDisplayName"]) == name } ?? [:]
    }

    private func charges() -> [[String: Any]] { apiArray("owner", "/api/groups/\(group)/charges", key: "charges") }

    private func isNull(_ value: Any?) -> Bool { value == nil || value is NSNull }

    private func publishGame() -> String {
        let date = string(data["tomorrow"])
        let starts = ISO8601DateFormatter().date(from: "\(date)T20:00:00-03:00")!
        let iso = ISO8601DateFormatter()
        let created = api("owner", "/api/groups/\(group)/games", method: "POST", body: [
            "requestId": UUID().uuidString.lowercased(), "title": "Quadra J3",
            "venue": ["name": "Quadra J3", "address": "Rua Teste 100"],
            "localDate": date, "localTime": "20:00", "zoneId": "America/Sao_Paulo",
            "startsAt": iso.string(from: starts), "durationMinutes": 90, "capacity": 6,
            "confirmationDeadline": iso.string(from: starts.addingTimeInterval(-7200)), "useDefaultGameFee": true,
        ], status: 201)
        let id = string(created["id"])
        api("owner", "/api/groups/\(group)/games/\(id)/publish", method: "POST", headers: ["If-Match": "\"\(int(created["version"]))\""])
        return id
    }
}
