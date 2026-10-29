import XCTest

/**
 * DEB-117 / FLG-007 (roteiro 11, F5): mínima do iOS acima da versão do build → folha que não fecha ("Atualize o Saqz",
 * "Atualizar na App Store"; tocar fora e o gesto de voltar não fecham); recomendada → folha fechável, uma vez por versão;
 * sem versão configurada → nada. O botão da loja não abre a App Store no simulador: só se confere que ele está lá.
 * Igual ao AppUpdateE2eTest do Android, com as versões do iOS.
 */
final class AppUpdateE2eTest: InstalledE2E {
    override var scenarioName: String { "app-update" }

    func testRequiredBlocksRecommendedClosesOnceAndNoneShowsNothing() throws {
        let version = try XCTUnwrap(ProcessInfo.processInfo.environment["SAQZ_E2E_APP_VERSION"], "O runner passa a versão do build")
        let parts = version.split(separator: ".").map { Int($0.prefix { $0.isNumber }) ?? 0 }
        let (major, minor, patch) = (parts[0], parts.count > 1 ? parts[1] : 0, parts.count > 2 ? parts[2] : 0)
        defer { setVersions(minimum: nil, recommended: nil) }

        waitTag("login-submit")
        setVersions(minimum: "\(major).\(minor).\(patch + 1)", recommended: nil)
        returnToApp()
        waitTag("app-update")
        waitText("Atualize o Saqz")
        waitText("Tem versão nova do app. Para continuar usando, é só atualizar.")
        waitTag("app-update-store")
        waitText("Atualizar na App Store")
        XCTAssertFalse(exists("app-update-not-now"))
        shot("obrigatoria-na-entrada")
        back()
        tapOutside()
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        waitTag("app-update")
        waitTag("app-update-store")
        shot("obrigatoria-nao-fecha")

        // Recomendada própria desta execução: "Agora não" vale por versão e a memória é do aparelho.
        let recommended = "\(major).\(minor).\(patch + 100 + Int(Date().timeIntervalSince1970 / 60) % 800)"
        setVersions(minimum: nil, recommended: recommended)
        returnToApp()
        waitTag("app-update-not-now")
        waitText("Tem versão nova do Saqz")
        waitText("Atualize para ter as novidades e as correções.")
        shot("recomendada")
        tap("app-update-not-now")
        waitGone("app-update-not-now")
        returnToApp()
        assertStaysClosed()
        shot("recomendada-nao-volta")

        setVersions(minimum: nil, recommended: nil)
        returnToApp()
        assertStaysClosed()
        waitTag("login-submit")
        login("owner")
        assertStaysClosed()
        shot("sem-versao-abre-normal")
    }

    private func setVersions(minimum: String?, recommended: String?) {
        let current = int(api("owner", "/admin/app-versions")["version"])
        let ios: [String: Any] = ["minimum": minimum ?? NSNull(), "recommended": recommended ?? NSNull()]
        api("owner", "/admin/app-versions", method: "PUT", body: [
            "expectedVersion": current, "reason": "E2E app-update iOS",
            "android": ["minimum": NSNull(), "recommended": NSNull()], "ios": ios,
        ])
    }

    /** A checagem roda ao abrir e a cada volta ao primeiro plano. */
    private func returnToApp() {
        XCUIDevice.shared.press(.home)
        RunLoop.current.run(until: Date().addingTimeInterval(1))
        app.activate()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 10))
    }

    private func assertStaysClosed() {
        RunLoop.current.run(until: Date().addingTimeInterval(3))
        XCTAssertFalse(exists("app-update-store"))
        XCTAssertFalse(exists("app-update-not-now"))
    }
}
