import XCTest

/**
 * FIN-016 (roteiro 13, R2–R11): o jogo que ganhou valor depois das confirmações mostra a faixa no caixa e no acerto; a
 * revisão cobra R$ 10 do avulso, marca o outro como já pago em dinheiro e não cobra a convidada; reabrir cobra a
 * convidada; o mesmo requestId não tem segundo efeito; o atleta não lê a revisão nem recebe aviso.
 * Igual ao UnchargedReviewE2eTest do Android.
 */
final class UnchargedReviewE2eTest: InstalledE2E {
    override var scenarioName: String { "uncharged-review" }
    private var game: String { string(data["game"]) }
    private var athleteId: String { string(actor("athlete")["id"]) }
    private var otherId: String { string(actor("other")["id"]) }
    private var mensalId: String { string((data["mensal"] as? [String: Any])?["id"]) }
    private var unchargedPath: String { "/api/groups/\(group)/games/\(game)/uncharged" }

    func testOrganizerReviewsWhoPlayedWithoutAChargeAndReopensTheSkippedGuest() {
        let inboxBefore = inbox()
        login("owner")
        openGroup()
        tap("group-details-cashbox")
        waitTag("group-cashbox-uncharged")
        waitText("3 jogaram sem cobrança")
        shot("caixa-faixa-retroativa")
        back()
        waitGone("group-cashbox")

        openSettlement()
        waitTag("game-settlement-uncharged-strip")
        waitText("JOGO ENCERRADO")
        waitText("Confirmaram antes do valor")
        waitContains("cobertos pela mensalidade")
        shot("acerto-faixa-retroativa")

        tap("game-settlement-uncharged-review")
        waitTag("uncharged-review")
        waitText("Quem jogou sem cobrança")
        waitText("O jogo não tinha valor quando confirmaram.")
        waitText("Mensalistas ficam de fora: já pagam a mensalidade.")
        waitContains("Convite · paga")
        waitContains("3 cobranças")
        // R5: o resumo só com as partes diferentes de zero.
        XCTAssertFalse(containing("0 já pagou").exists, "O resumo mostrou \"0 já pagou\"")
        XCTAssertFalse(containing("0 sem cobrança").exists, "O resumo mostrou \"0 sem cobrança\"")
        for key in ["\(athleteId)/0", "\(otherId)/0", "\(mensalId)/1"] { waitTag("uncharged-review-pill-\(key)") }
        shot("revisao-aberta")

        type("uncharged-review-amount", "10,00")
        choose("\(otherId)/0", "Já pagou")
        waitTag("uncharged-review-method-\(otherId)/0")
        waitContains("Entra no caixa em")
        // R6: o toque no meio abre o menu "Como pagou".
        tap("uncharged-review-method-\(otherId)/0")
        waitText("Como pagou")
        shot("revisao-como-pagou")
        tap("uncharged-review-method-option-Cash")
        waitGone("uncharged-review-method-option-Cash")
        waitContains("Dinheiro")
        choose("\(mensalId)/1", "Não cobrar")
        waitContains("1 cobrança")
        waitContains("1 já pagou")
        waitContains("1 sem cobrança")
        shot("revisao-decidida")
        tap("uncharged-review-confirm")
        waitGone("uncharged-review")
        waitTag("game-settlement")
        waitGone("game-settlement-uncharged-strip")
        waitTag("game-settlement-uncharged-\(mensalId)/1")
        waitText("Não cobrar")
        shot("acerto-sem-cobranca-bia")

        let charges = gameCharges()
        XCTAssertEqual(charges.count, 2)
        let own = charges.first { string($0["memberId"]) == athleteId } ?? [:]
        XCTAssertEqual(int(own["amountCents"]), 1000)
        XCTAssertEqual(string(own["status"]), "PENDING")
        XCTAssertEqual(string(own["dueDate"]), string(data["today"]))
        let paid = charges.first { string($0["memberId"]) == otherId } ?? [:]
        XCTAssertEqual(int(paid["amountCents"]), 1000)
        XCTAssertEqual(string(paid["status"]), "PAID")
        XCTAssertEqual(string(paid["paidMethod"]), "CASH")
        XCTAssertEqual(int(api("owner", "/api/groups/\(group)/finance/uncharged")["peopleCount"]), 0)
        let people = api("owner", unchargedPath)["people"] as? [[String: Any]] ?? []
        XCTAssertEqual(string(people.first { int($0["guestSeq"]) == 1 }?["decision"]), "SKIP")

        // Reabrir e cobrar a convidada.
        tapText("Reabrir a revisão")
        waitTag("uncharged-review")
        // Reaberta, a revisão volta a sugerir o valor do jogo (R$ 20); o roteiro espera os R$ 10 do lote: digita de novo.
        type("uncharged-review-amount", "10,00")
        choose("\(mensalId)/1", "Cobrar")
        shot("revisao-reaberta-bia")
        tap("uncharged-review-confirm")
        waitGone("uncharged-review")
        waitTag("game-settlement")
        eventually("cobrança da Bia") { gameCharges().count == 3 }
        let guest = gameCharges().first { string($0["guestDisplayName"]) == "Bia" } ?? [:]
        XCTAssertEqual(string(guest["memberId"]), mensalId)
        XCTAssertEqual(int(guest["amountCents"]), 1000)
        XCTAssertEqual(string(guest["status"]), "PENDING")
        waitGone("game-settlement-uncharged-\(mensalId)/1")
        shot("acerto-bia-cobrada")

        // Mesmo requestId: mesmo resultado, sem segundo efeito; o atleta não lê nem revisa.
        let repeated: [String: Any] = [
            "requestId": "6f1d2c3b-0000-4000-8000-000000000013", "amountCents": 1000,
            "people": [["memberId": athleteId, "guestSeq": 0, "decision": "CHARGE"]],
        ]
        let first = api("owner", "\(unchargedPath)/review", method: "POST", body: repeated)
        let second = api("owner", "\(unchargedPath)/review", method: "POST", body: repeated)
        XCTAssertEqual(NSDictionary(dictionary: first), NSDictionary(dictionary: second))
        XCTAssertEqual(int(first["ignored"]), 1)
        XCTAssertEqual(gameCharges().count, 3)
        api("athlete", unchargedPath, status: 403)
        // Nada vai ao atleta (FIN-016).
        XCTAssertEqual(inbox(), inboxBefore)

        backToShell()
        logout()
        login("athlete")
        openGroup()
        let tag = "group-details-own-charge-\(string(own["id"]))"
        let card = waitTag(tag)
        reveal(card)
        XCTAssertTrue(containing("R$ 10", in: card).exists || containing("R$ 10").exists)
        shot("atleta-minhas-cobrancas")
    }

    private func openSettlement() {
        tap("group-details-agenda-past")
        waitTag("past-games-list")
        tap("past-games-game-\(game)")
        waitTag("game-detail")
        eventually("botão do acerto") { exists("game-detail-settle") || exists("game-detail-settle-view") || seekEither() }
        tap(exists("game-detail-settle") ? "game-detail-settle" : "game-detail-settle-view")
        waitTag("game-settlement")
    }

    private func seekEither() -> Bool {
        app.swipeUp(velocity: .slow)
        return exists("game-detail-settle") || exists("game-detail-settle-view")
    }

    /** Toca na pílula da pessoa e escolhe a opção na folha (a opção não leva identificador: vai pelo texto). */
    private func choose(_ key: String, _ label: String) {
        let pill = "uncharged-review-pill-\(key)"
        tap(pill)
        let options = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ AND identifier != %@", label, pill))
        eventually("opção \(label)") { options.count > 0 && options.element(boundBy: options.count - 1).isHittable }
        options.element(boundBy: options.count - 1).tap()
        RunLoop.current.run(until: Date().addingTimeInterval(0.8))
    }

    private func gameCharges() -> [[String: Any]] {
        apiArray("owner", "/api/groups/\(group)/charges", key: "charges").filter { string($0["gameId"]) == game }
    }

    private func inbox() -> Int { apiArray("athlete", "/api/me/notifications", key: "items").count }
}
