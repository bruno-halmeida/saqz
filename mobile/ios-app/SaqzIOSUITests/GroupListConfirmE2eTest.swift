import XCTest

/**
 * GER-003 (APP-LG): "Confirmar" na foto do grupo, na aba Grupos. O jogo do cenário está lotado (4 de 4), então o "Vou"
 * da folha entra na espera: a etiqueta troca para "Na espera" e a API confirma a posição 1. Igual ao
 * GroupListConfirmE2eTest do Android.
 */
final class GroupListConfirmE2eTest: InstalledE2E {
    override var scenarioName: String { "attendance-list" }

    func testConfirmOnTheGroupListOpensTheSheetAndGoingWaitlistsTheAthlete() {
        let attendancePath = "/api/groups/\(group)/games/\(string(data["game"]))/attendance"
        login("athlete")
        groups()
        waitTag("group-list-confirm-\(group)")
        shot("grupos-com-confirmar")
        tap("group-list-confirm-\(group)")
        waitTag("group-list-confirm-sheet")
        shot("folha-de-confirmar")
        tap("group-list-confirm-yes")

        waitTag("group-list-status-\(group)")
        waitText("Na espera")
        waitGone("group-list-confirm-\(group)")
        shot("na-espera")

        let waiting = api("athlete", attendancePath)
        let own = waiting["ownAttendance"] as? [String: Any] ?? [:]
        XCTAssertEqual(string(own["status"]), "WAITLISTED")
        XCTAssertEqual(int(own["waitlistPosition"]), 1)
        XCTAssertEqual(int(waiting["confirmedCount"]), 4)
    }
}
