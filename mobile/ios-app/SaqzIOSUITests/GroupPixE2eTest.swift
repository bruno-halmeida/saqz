import XCTest

/**
 * FIN-008 (roteiro 13, X2–X7): com cobrança em aberto o atleta não vê a seção "Pix do grupo" (o cartão da cobrança traz
 * a chave); recebida a diária, a seção aparece depois de Minhas cobranças com chave, tipo e Copiar; o gestor a vê
 * depois da Galera; o grupo sem chave não tem a seção. Igual ao GroupPixE2eTest do Android.
 */
final class GroupPixE2eTest: InstalledE2E {
    override var scenarioName: String { "group-pix" }
    private var charge: String { string(data["charge"]) }
    private var pixKey: String { string(data["pixKey"]) }

    func testGroupPixShowsOnlyWithoutOpenChargesAndCopiesTheKey() {
        login("athlete")
        openGroup()
        reveal(waitTag("group-details-own-charge-\(charge)"))
        XCTAssertFalse(exists("group-details-group-pix"))
        waitContains(pixKey)
        shot("atleta-com-cobranca-sem-secao")
        backToShell()
        logout()

        login("owner")
        openGroup()
        tap("group-details-cashbox")
        tap("group-cashbox-tab-due")
        tap("group-cashbox-received-\(charge)")
        waitTag("finance-receipt-sheet")
        shot("recebi-diaria")
        tap("finance-receipt-confirm")
        eventually("diária paga") {
            string(apiArray("owner", "/api/groups/\(group)/charges", key: "charges").first?["status"]) == "PAID"
        }
        waitText("Ninguém devendo. Tudo recebido.")
        back()
        waitGone("group-cashbox")
        reveal(waitTag("group-details-group-pix"))
        assertBelow("group-details-group-pix", "group-details-people")
        shot("gestor-secao-depois-da-galera")
        backToShell()
        logout()

        login("athlete")
        openGroup()
        let section = waitTag("group-details-group-pix")
        reveal(section)
        XCTAssertTrue(containing(pixKey).exists)
        XCTAssertTrue(containing("E-mail").exists)
        // Com tudo pago, "Minhas cobranças" pode ficar só com o histórico: a seção vem depois do que houver.
        let own = ["group-details-own-charges-settled", "group-details-own-charges", "group-details-own-charges-history"]
            .first { exists($0) }
        XCTAssertNotNil(own, "Sem a seção Minhas cobranças")
        if let own { assertBelow("group-details-group-pix", own) }
        shot("atleta-secao-pix-do-grupo")
        // A área de transferência é conferida pelo runner no fim (simctl pbpaste): o teste não lê o que o app copiou.
        tap("group-details-group-pix-copy")
        shot("atleta-pix-copiado")
        backToShell()

        // Sem chave Pix (o grupo de praia), a seção não existe.
        openGroup(secondGroup)
        waitTag("group-details-people")
        XCTAssertFalse(exists("group-details-group-pix"))
        shot("grupo-sem-chave-sem-secao")
    }

    private func assertBelow(_ lower: String, _ upper: String) {
        XCTAssertGreaterThan(element(lower).frame.minY, element(upper).frame.minY, "\(lower) deve vir depois de \(upper)")
    }
}
