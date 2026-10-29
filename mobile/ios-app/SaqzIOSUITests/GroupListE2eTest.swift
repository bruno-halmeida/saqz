import XCTest

/**
 * DEB-145 / GER-003: três grupos (com foto e jogo sem resposta, sem foto com Vou, sem jogo) em ordem, com o cabeçalho
 * "3 grupos · 1 esperando sua resposta"; tocar fora da folha do Confirmar fecha sem responder; Vou → "Você vai";
 * Não vou pela API → "Não vai"; a etiqueta Admin só para o dono. Igual ao GroupListE2eTest do Android.
 */
final class GroupListE2eTest: InstalledE2E {
    override var scenarioName: String { "group-list" }
    private var third: String { string(data["thirdGroup"]) }
    private var attendancePath: String { "/api/groups/\(group)/games/\(string(data["game"]))/attendance" }

    func testListOrdersGroupsConfirmsFromTheSheetAndShowsAdminOnlyToTheOwner() {
        login("athlete")
        groups()
        waitTag("group-list-summary")
        waitContains("3 grupos")
        waitContains("1 esperando sua resposta")
        for id in [group, secondGroup, third] { waitTag("group-list-group-\(id)") }
        assertOrder([group, secondGroup, third])
        waitText("Nenhum jogo marcado por enquanto.")
        status(secondGroup, "Você vai")
        XCTAssertFalse(exists("group-list-admin-\(group)"))
        shot("atleta-lista-tres-grupos")

        tap("group-list-confirm-\(group)")
        waitTag("group-list-confirm-sheet")
        waitText("Você vai nesse jogo?")
        shot("folha-confirmar")
        // Toque fora da folha, na lista escurecida acima dela.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.3)).tap()
        waitGone("group-list-confirm-yes")
        waitTag("group-list-confirm-\(group)")
        let unanswered = api("athlete", attendancePath)["ownAttendance"]
        XCTAssertTrue(unanswered == nil || unanswered is NSNull)
        shot("folha-fechada-sem-resposta")

        tap("group-list-confirm-\(group)")
        tap("group-list-confirm-yes")
        status(group, "Você vai")
        waitGone("group-list-confirm-\(group)")
        waitTextGone("esperando sua resposta")
        XCTAssertEqual(string((api("athlete", attendancePath)["ownAttendance"] as? [String: Any])?["status"]), "CONFIRMED")
        shot("vou-voce-vai")

        api("athlete", attendancePath, method: "PUT", body: ["requestId": UUID().uuidString.lowercased(), "intent": "DECLINE"])
        // A aba relê ao voltar a ela.
        tab("Início")
        groups()
        status(group, "Não vai")
        shot("nao-vou-nao-vai")
        logout()

        login("owner")
        groups()
        waitTag("group-list-group-\(group)")
        waitTag("group-list-admin-\(group)")
        shot("dono-etiqueta-admin")
    }

    private func status(_ id: String, _ text: String) {
        let badge = waitTag("group-list-status-\(id)")
        eventually("\(text) no grupo \(id)") {
            badge.label.contains(text) || containing(text, in: badge).exists ||
                app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", text)).allElementsBoundByIndex
                    .contains { badge.frame.insetBy(dx: -8, dy: -8).contains(CGPoint(x: $0.frame.midX, y: $0.frame.midY)) }
        }
    }

    private func assertOrder(_ ids: [String]) {
        let tops = ids.map { element("group-list-group-\($0)").frame.minY }
        XCTAssertEqual(tops, tops.sorted(), "Ordem dos grupos")
    }
}
