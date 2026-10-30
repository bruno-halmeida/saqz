import XCTest

/**
 * DEB-141/142/149 com o recebimento pelo app desligado (padrão do stack e da produção): o gestor não vê oferta nem tela
 * de recebimento no grupo, no caixa e no editar grupo (só a chave Pix); o atleta com cobrança copia o Pix do grupo e
 * não tem "Pagar". Igual ao ReceivablesOffE2eTest do Android.
 */
final class ReceivablesOffE2eTest: InstalledE2E {
    override var scenarioName: String { "receivables-off" }
    private let receivingTags = ["group-receiving", "group-receiving-offer", "cashbox-wallet", "cashbox-review", "group-fee-preview"]

    func testOrganizerSeesNoInAppReceivingAndAthleteCopiesTheGroupPix() {
        login("owner")
        openGroup()
        waitTag("group-details-people")
        assertNoReceiving()
        shot("gestor-grupo-sem-recebimento")
        tap("group-details-cashbox")
        waitTag("group-cashbox-hero")
        assertNoReceiving()
        shot("gestor-caixa-sem-recebimento")
        back()
        waitGone("group-cashbox")
        tap("group-details-edit-group")
        waitTag("group-setup-submit")
        reveal(waitTag("group-setup-pix-key"))
        assertNoReceiving()
        shot("gestor-editar-grupo-so-chave-pix")
        backToShell()
        logout()

        login("athlete")
        openGroup()
        reveal(waitTag("group-details-own-charge-\(string(data["charge"]))"))
        assertAbsent(["group-details-own-charges-pay"], buttons: ["Pagar"])
        // A área de transferência é conferida pelo runner no fim (simctl pbpaste): o teste não lê o que o app copiou.
        tap("group-details-own-charges-pix-copy")
        // O aviso some sozinho: espera logo depois do toque, sem rolar a tela.
        XCTAssertTrue(containing("Pix do grupo copiado.").waitForExistence(timeout: 10), "Sem o aviso de Pix copiado")
        shot("atleta-copia-pix-sem-pagar")
    }

    private func assertNoReceiving() {
        assertAbsent(receivingTags)
    }
}
