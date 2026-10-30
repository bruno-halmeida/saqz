import Foundation
import XCTest

/**
 * Base dos testes E2E instalados do iOS (tests/e2e/ios/run.mjs): app real do esquema SaqzDev, backend real em
 * 127.0.0.1:18080 e Firebase Auth Emulator em 127.0.0.1:9099. Espelha o InstalledE2e do Android: ação sob teste
 * pela tela, conferência do que ficou gravado pela API, com o token do próprio papel.
 *
 * O testTag do Compose aparece como accessibilityIdentifier. Fora do runner (sem a fixture) o teste é pulado:
 * o runner conta skip como falha.
 */
@MainActor
class InstalledE2E: XCTestCase {
    private static let apiBaseURL = "http://127.0.0.1:18080"
    private static let authURL = "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-saqz-local-api-key"
    private static let timeout: TimeInterval = 20

    var scenarioName: String { fatalError("Cada teste E2E declara o cenário da fixture") }

    private(set) var app = XCUIApplication()
    private var fixture: [String: Any] = [:]
    private var shotCount = 0
    private var tokens: [String: String] = [:]

    var data: [String: Any] { (fixture["scenarios"] as? [String: Any])?[scenarioName] as? [String: Any] ?? [:] }
    var group: String { string(data["group"]) }
    var secondGroup: String { string(data["secondGroup"]) }
    func actor(_ role: String) -> [String: Any] { data[role] as? [String: Any] ?? [:] }

    override func setUp() async throws {
        try await super.setUp()
        continueAfterFailure = false
        let environment = ProcessInfo.processInfo.environment
        guard let encoded = environment["SAQZ_E2E_FIXTURE"], let raw = Data(base64Encoded: encoded) else {
            throw XCTSkip("Rode pelo tests/e2e/ios/run.mjs: falta a fixture SAQZ_E2E_FIXTURE")
        }
        fixture = try XCTUnwrap(JSONSerialization.jsonObject(with: raw) as? [String: Any])
        XCTAssertFalse(data.isEmpty, "Fixture sem o cenário \(scenarioName)")
        app = XCUIApplication()
        app.launch()
    }

    override func tearDown() async throws {
        if let run = testRun, run.failureCount + run.unexpectedExceptionCount > 0 {
            let tree = XCTAttachment(string: app.debugDescription)
            tree.name = "\(scenarioName)-arvore-da-tela"
            tree.lifetime = .keepAlways
            add(tree)
            // Print de onde parou: entra na pasta de prints como o último passo.
            shot("falha")
        }
        try await super.tearDown()
    }

    // MARK: - Tela

    func element(_ tag: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: tag).firstMatch
    }

    func textElement(_ text: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR value == %@", text, text)).firstMatch
    }

    /**
     * Espera o identificador e, se não aparecer, rola procurando: no iOS a lista do Compose só põe no accessibility o
     * que está composto, então item abaixo da dobra não existe até a rolagem chegar nele.
     */
    @discardableResult
    func waitTag(_ tag: String, timeout: TimeInterval = timeout, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let found = element(tag)
        if !seek(found, timeout: timeout) {
            XCTFail("Não apareceu o identificador \(tag)", file: file, line: line)
        }
        return found
    }

    /** Espera um pouco parado; depois rola para baixo e, se preciso, de volta para cima, conferindo a cada passo. */
    func seek(_ target: XCUIElement, timeout: TimeInterval = timeout) -> Bool {
        if target.waitForExistence(timeout: min(timeout, 4)) { return true }
        for up in [true, true, true, true, true, true, false, false, false, false, false, false, false, false, false, false] {
            up ? app.swipeUp(velocity: .slow) : app.swipeDown(velocity: .slow)
            if target.waitForExistence(timeout: 0.7) { return true }
        }
        return target.waitForExistence(timeout: max(timeout - 4, 1))
    }

    @discardableResult
    func waitText(_ text: String, timeout: TimeInterval = timeout, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let found = textElement(text)
        if !seek(found, timeout: timeout) {
            XCTFail("Não apareceu o texto \"\(text)\"", file: file, line: line)
        }
        return found
    }

    func waitGone(_ tag: String, timeout: TimeInterval = timeout, file: StaticString = #filePath, line: UInt = #line) {
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: element(tag))
        if XCTWaiter.wait(for: [gone], timeout: timeout) != .completed {
            XCTFail("Continuou na tela: \(tag)", file: file, line: line)
        }
    }

    /** Texto que contém [text] (a tela usa espaço inseparável em "R$ 20"; compare sem ele). */
    @discardableResult
    func waitContains(_ text: String, timeout: TimeInterval = timeout, file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let found = containing(text)
        if !seek(found, timeout: timeout) {
            XCTFail("Não apareceu texto contendo \"\(text)\"", file: file, line: line)
        }
        return found
    }

    func containing(_ text: String, in scope: XCUIElement? = nil) -> XCUIElement {
        let normalized = text.replacingOccurrences(of: "\u{00A0}", with: " ")
        let variants = [normalized, normalized.replacingOccurrences(of: "R$ ", with: "R$\u{00A0}")]
        let predicate = NSCompoundPredicate(orPredicateWithSubpredicates: variants.map {
            NSPredicate(format: "label CONTAINS %@ OR value CONTAINS %@", $0, $0)
        })
        return (scope ?? app).descendants(matching: .any).matching(predicate).firstMatch
    }

    func waitTextGone(_ text: String, timeout: TimeInterval = timeout, file: StaticString = #filePath, line: UInt = #line) {
        let gone = expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: containing(text))
        if XCTWaiter.wait(for: [gone], timeout: timeout) != .completed {
            XCTFail("Continuou na tela o texto \"\(text)\"", file: file, line: line)
        }
    }

    /** Espera a condição (consulta à API, em geral) virar verdadeira. */
    func eventually(_ label: String, timeout: TimeInterval = timeout, file: StaticString = #filePath, line: UInt = #line,
                    _ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if condition() { return }
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }
        XCTFail("Não aconteceu: \(label)", file: file, line: line)
    }

    /**
     * Opção [label] do segmentado/grupo com o identificador [tag]. No iOS as opções vêm como irmãs do contêiner
     * identificado, não filhas: vale a que está dentro do retângulo dele.
     */
    func option(_ tag: String, _ label: String) -> XCUIElement {
        let container = waitTag(tag)
        let area = container.frame.insetBy(dx: -8, dy: -8)
        let candidates = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", label))
        for index in 0..<candidates.count {
            let candidate = candidates.element(boundBy: index)
            if candidate.exists && candidate.identifier != tag && area.contains(CGPoint(x: candidate.frame.midX, y: candidate.frame.midY)) {
                return candidate
            }
        }
        return containing(label, in: container)
    }

    /** Selecionado (traço de acessibilidade): no próprio elemento com o identificador ou no irmão dentro do retângulo. */
    func isSelected(_ tag: String) -> Bool {
        let container = waitTag(tag)
        if container.isSelected { return true }
        let area = container.frame.insetBy(dx: -4, dy: -4)
        return app.descendants(matching: .any).matching(NSPredicate(format: "selected == true")).allElementsBoundByIndex
            .contains { area.contains(CGPoint(x: $0.frame.midX, y: $0.frame.midY)) }
    }

    /** Valor do campo editável que está no retângulo do identificador. */
    func fieldValue(_ tag: String) -> String {
        string(editable(in: waitTag(tag)).value)
    }

    func tapOption(_ tag: String, _ label: String, file: StaticString = #filePath, line: UInt = #line) {
        let target = option(tag, label)
        XCTAssertTrue(target.waitForExistence(timeout: Self.timeout), "Sem a opção \(label) em \(tag)", file: file, line: line)
        reveal(target)
        target.tap()
    }

    /** Volta até a aba do shell (o editor pode deixar mais de uma tela empilhada sobre o grupo). */
    func backToShell(max: Int = 4) {
        for _ in 0..<max {
            if exists("saqz-shell-content") && !exists("group-details") && !exists("game-detail") { return }
            back()
            RunLoop.current.run(until: Date().addingTimeInterval(0.8))
        }
    }

    func exists(_ tag: String, timeout: TimeInterval = 0) -> Bool {
        timeout > 0 ? element(tag).waitForExistence(timeout: timeout) : element(tag).exists
    }

    /** Rola até o elemento ficar tocável; `up` rola o conteúdo para cima (o elemento está mais abaixo). */
    func reveal(_ target: XCUIElement, up: Bool = true) {
        for _ in 0..<10 where !target.isHittable {
            up ? app.swipeUp(velocity: .slow) : app.swipeDown(velocity: .slow)
        }
    }

    func tap(_ tag: String, file: StaticString = #filePath, line: UInt = #line) {
        let target = waitTag(tag, file: file, line: line)
        reveal(target)
        let enabled = expectation(for: NSPredicate(format: "isEnabled == true AND isHittable == true"), evaluatedWith: target)
        if XCTWaiter.wait(for: [enabled], timeout: Self.timeout) != .completed {
            XCTFail("\(tag) não ficou habilitado e tocável", file: file, line: line)
        }
        target.tap()
    }

    func tapText(_ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let target = waitText(text, file: file, line: line)
        reveal(target)
        target.tap()
    }

    /**
     * Substitui o conteúdo do campo. No iOS o identificador fica num "Other" e o campo editável (TextView/TextField,
     * com o valor) vem como irmão dentro do mesmo retângulo: é nele que se toca, e o valor dele diz o que apagar.
     */
    func type(_ tag: String, _ text: String, file: StaticString = #filePath, line: UInt = #line) {
        let container = waitTag(tag, file: file, line: line)
        reveal(container)
        let field = editable(in: container)
        field.tap()
        if !app.keyboards.firstMatch.waitForExistence(timeout: 5) {
            field.tap()
            XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5), "Teclado não abriu em \(tag)", file: file, line: line)
        }
        // Campo já preenchido (o login guarda o último e-mail): cursor no fim e apaga, conferindo até esvaziar.
        for _ in 0..<3 {
            let current = (field.value as? String).map { $0 == field.placeholderValue ? 0 : $0.count } ?? 0
            if current == 0 { break }
            field.coordinate(withNormalizedOffset: CGVector(dx: 0.97, dy: 0.5)).tap()
            app.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current + 8))
        }
        app.typeText(text)
    }

    private func editable(in container: XCUIElement) -> XCUIElement {
        let area = container.frame.insetBy(dx: -4, dy: -4)
        for query in [app.textViews, app.textFields, app.secureTextFields] {
            for index in 0..<query.count {
                let candidate = query.element(boundBy: index)
                if candidate.exists && area.contains(CGPoint(x: candidate.frame.midX, y: candidate.frame.midY)) { return candidate }
            }
        }
        return container
    }

    /** Voltar do app (seta da barra); sem ela, o gesto de borda do iOS. */
    func back() {
        let arrow = app.buttons.matching(NSPredicate(format: "label == %@", "Voltar")).firstMatch
        if arrow.exists && arrow.isHittable {
            arrow.tap()
        } else {
            let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.01, dy: 0.5))
            start.press(forDuration: 0.05, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.5)))
        }
    }

    /** Toque fora de folha/diálogo, no topo da tela. */
    func tapOutside() {
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.08)).tap()
    }

    func tab(_ label: String) {
        waitTag("saqz-shell-content")
        let item = app.buttons.matching(NSPredicate(format: "label == %@", label)).firstMatch
        if item.waitForExistence(timeout: Self.timeout) && item.isHittable {
            item.tap()
        } else {
            tapText(label)
        }
    }

    func groups() { tab("Grupos") }

    func openGroup(_ id: String? = nil) {
        groups()
        tap("group-list-group-\(id ?? group)")
        waitTag("group-details")
    }

    func login(_ role: String) {
        type("login-email", string(actor(role)["email"]))
        type("login-password", string(fixture["password"]))
        tap("login-submit")
        waitTag("saqz-shell-content", timeout: 30)
        // Como o Android confere o uid: o Perfil mostra o nome que a fixture deu a esta conta.
        tab("Perfil")
        waitContains("E2E \(scenarioName)-\(role)")
    }

    func logout() {
        tab("Perfil")
        tap("own-profile-sign-out")
        tap("profile-exit-logout")
        waitTag("login-submit")
        XCTAssertFalse(exists("saqz-shell-content"))
    }

    /** Fecha a folha "Quer saber quando tem jogo?" só se ela subiu (a memória é do aparelho). */
    func dismissNotificationPrompt() {
        guard exists("notification-prompt-not-now", timeout: 5) else { return }
        tap("notification-prompt-not-now")
        waitGone("notification-prompt")
    }

    /** Print da tela parada, anexado ao xcresult como `<cenário>-<NN>-<passo>`. */
    func shot(_ step: String) {
        shotCount += 1
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = String(format: "%@-%02d-%@", scenarioName, shotCount, step)
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    // MARK: - API (oráculo independente: confere o que ficou gravado, nunca usa o app)

    @discardableResult
    func api(_ role: String, _ path: String, method: String = "GET", body: [String: Any]? = nil, status: Int = 200,
             headers: [String: String] = [:], file: StaticString = #filePath, line: UInt = #line) -> [String: Any] {
        let token = idToken(role)
        let (code, json) = http(Self.apiBaseURL + path, method: method, body: body, token: token, headers: headers)
        XCTAssertEqual(code, status, "\(method) \(path)", file: file, line: line)
        return json as? [String: Any] ?? [:]
    }

    func apiArray(_ role: String, _ path: String, key: String, file: StaticString = #filePath, line: UInt = #line) -> [[String: Any]] {
        api(role, path, file: file, line: line)[key] as? [[String: Any]] ?? []
    }

    func apiList(_ role: String, _ path: String, file: StaticString = #filePath, line: UInt = #line) -> [[String: Any]] {
        let (code, json) = http(Self.apiBaseURL + path, method: "GET", body: nil, token: idToken(role), headers: [:])
        XCTAssertEqual(code, 200, "GET \(path)", file: file, line: line)
        return json as? [[String: Any]] ?? []
    }

    func membershipIds(_ role: String) -> Set<String> {
        let memberships = api(role, "/api/session", method: "PUT")["memberships"] as? [[String: Any]] ?? []
        return Set(memberships.map { string($0["groupId"]) })
    }

    private func idToken(_ role: String) -> String {
        if let cached = tokens[role] { return cached }
        let (code, json) = http(Self.authURL, method: "POST", body: [
            "email": string(actor(role)["email"]), "password": string(fixture["password"]), "returnSecureToken": true,
        ], token: nil, headers: [:])
        XCTAssertEqual(code, 200, "Login do \(role) no Auth Emulator")
        let token = string((json as? [String: Any])?["idToken"])
        tokens[role] = token
        return token
    }

    private func http(_ url: String, method: String, body: [String: Any]?, token: String?, headers: [String: String]) -> (Int, Any?) {
        var request = URLRequest(url: URL(string: url)!, timeoutInterval: 15)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        if let body { request.httpBody = try? JSONSerialization.data(withJSONObject: body) }
        let box = ResponseBox()
        let done = DispatchSemaphore(value: 0)
        URLSession.shared.dataTask(with: request) { data, response, error in
            box.status = (response as? HTTPURLResponse)?.statusCode ?? -1
            box.data = data
            box.error = error
            done.signal()
        }.resume()
        done.wait()
        if let error = box.error { XCTFail("\(method) \(url): \(error.localizedDescription)") }
        guard let data = box.data, !data.isEmpty else { return (box.status, nil) }
        return (box.status, try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]))
    }
}

func string(_ value: Any?) -> String { value as? String ?? (value.map { "\($0)" } ?? "") }

func int(_ value: Any?) -> Int { (value as? NSNumber)?.intValue ?? Int(string(value)) ?? Int.min }

private final class ResponseBox: @unchecked Sendable {
    var status = -1
    var data: Data?
    var error: Error?
}
