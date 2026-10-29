import XCTest

/**
 * FIN-006 (roteiro 13, C1–C13): o caixa zerado pergunta o saldo inicial; ⋯ → Saldo inicial corrige; Saída de quadra com
 * o atalho de R$ 120 e a data de ontem pelo calendário; Entrada de patrocínio sem atalhos; o placar troca a lista; o
 * extrato fixa o saldo inicial e filtra as saídas. O atleta recebe 403 no saldo inicial. Igual ao CashboxE2eTest do Android.
 */
final class CashboxE2eTest: InstalledE2E {
    override var scenarioName: String { "cashbox" }
    private var openingPath: String { "/api/groups/\(group)/finance/opening-balance" }

    func testOpeningBalanceExpenseYesterdayIncomeScoreboardAndStatement() {
        XCTAssertTrue(api("owner", openingPath)["amountCents"] is NSNull || api("owner", openingPath)["amountCents"] == nil)
        login("owner")
        openGroup()
        tap("group-details-cashbox")
        waitTag("group-cashbox-opening-question")
        waitText("Quanto o grupo já tem em caixa?")
        waitText("Entra como saldo inicial e soma no saldo.")
        waitTag("group-cashbox-income")
        waitTag("group-cashbox-expense")
        shot("caixa-zerado-pergunta")

        type("group-cashbox-opening-input", "250,00")
        tap("group-cashbox-opening-save")
        waitGone("group-cashbox-opening-question")
        waitContains("Inclui o saldo inicial de R$ 250")
        XCTAssertEqual(int(api("owner", openingPath)["amountCents"]), 25000)
        shot("saldo-inicial-250")

        tap("group-cashbox-menu")
        waitTag("group-cashbox-menu-opening")
        waitText("O Pix do grupo fica na página do grupo.")
        shot("menu-do-caixa")
        tap("group-cashbox-menu-opening")
        waitText("Quanto o grupo tinha antes do Saqz. Soma no saldo.")
        type("group-cashbox-opening-input", "300,00")
        tap("group-cashbox-opening-save")
        eventually("saldo inicial 300") { int(api("owner", openingPath)["amountCents"]) == 30000 }
        waitContains("Inclui o saldo inicial de R$ 300")
        shot("saldo-inicial-300")

        // Saída: atalhos, quatro categorias e a data de ontem pelo calendário.
        tap("group-cashbox-expense")
        waitTag("finance-new-entry")
        waitText("Nova saída")
        waitText("Toque para escolher outro dia.")
        for label in ["Quadra", "Material", "Árbitro", "Outros"] {
            XCTAssertTrue(option("finance-new-entry-category", label).exists, "Sem a categoria \(label)")
        }
        shot("nova-saida")
        tap("finance-new-entry-date")
        waitTag("finance-new-entry-date-picker")
        waitText("Data do lançamento")
        tap("game-picker-day-\(string(data["yesterday"]))")
        shot("calendario-ontem")
        tap("finance-new-entry-date-save")
        waitGone("finance-new-entry-date-save")
        tapOption("finance-new-entry-category", "Quadra")
        tapOption("finance-new-entry-shortcuts", "120")
        type("finance-new-entry-description", "quadra de terça")
        shot("saida-preenchida")
        tap("finance-new-entry-save")
        waitTag("group-cashbox-tab-out")
        let expense = expenses().first ?? [:]
        XCTAssertEqual(expenses().count, 1)
        XCTAssertEqual(string(expense["category"]), "VENUE")
        XCTAssertEqual(string(expense["direction"]), "OUT")
        XCTAssertEqual(int(expense["amountCents"]), 12000)
        XCTAssertEqual(string(expense["expenseDate"]), string(data["yesterday"]))
        waitContains("R$ 180,00")
        shot("caixa-saiu-120")

        // Entrada: Patrocínio, Venda e Outros, sem atalhos de valor.
        tap("group-cashbox-income")
        waitTag("finance-new-entry")
        waitText("Nova entrada")
        for label in ["Patrocínio", "Venda", "Outros"] {
            XCTAssertTrue(option("finance-new-entry-category", label).exists, "Sem a categoria \(label)")
        }
        XCTAssertFalse(exists("finance-new-entry-shortcuts"))
        type("finance-new-entry-amount", "50,00")
        tapOption("finance-new-entry-category", "Patrocínio")
        type("finance-new-entry-description", "patrocínio da loja")
        shot("nova-entrada")
        tap("finance-new-entry-save")
        waitTag("group-cashbox-tab-in")
        let income = expenses().first { string($0["direction"]) == "IN" } ?? [:]
        XCTAssertEqual(string(income["category"]), "SPONSORSHIP")
        XCTAssertEqual(int(income["amountCents"]), 5000)
        waitContains("R$ 230,00")

        // O placar é aba: cada toque troca a lista de baixo.
        tap("group-cashbox-tab-in")
        waitContains("Entradas de")
        waitText("patrocínio da loja")
        shot("aba-entrou")
        tap("group-cashbox-tab-out")
        waitContains("Saídas de")
        waitText("quadra de terça")
        shot("aba-saiu")
        tap("group-cashbox-tab-due")
        waitText("Ninguém devendo. Tudo recebido.")

        // Saída antiga de Racha (app antigo) e categoria de entrada com OUT recusada.
        var racha: [String: Any] = [
            "requestId": UUID().uuidString.lowercased(), "description": "racha antigo", "amountCents": 3000,
            "expenseDate": string(data["today"]), "category": "RACHA", "direction": "OUT",
        ]
        api("owner", "/api/groups/\(group)/expenses", method: "POST", body: racha, status: 201)
        racha["requestId"] = UUID().uuidString.lowercased()
        racha["category"] = "SPONSORSHIP"
        api("owner", "/api/groups/\(group)/expenses", method: "POST", body: racha, status: 400)

        // Reabre para ler a saída nova e vai ao extrato.
        back()
        waitGone("group-cashbox")
        tap("group-details-cashbox")
        // "Ver extrato" fica embaixo da lista de lançamentos (abas Entrou e Saiu).
        tap("group-cashbox-tab-out")
        tap("group-cashbox-statement")
        waitTag("finance-statement-opening")
        waitText("Antes do Saqz")
        waitText("quadra de terça")
        waitText("racha antigo")
        shot("extrato-tudo")
        tapOption("finance-statement-filter", "Saídas")
        waitTextGone("patrocínio da loja")
        waitText("quadra de terça")
        waitText("racha antigo")
        shot("extrato-saidas")

        let summary = api("owner", "/api/groups/\(group)/finance/statement")["summary"] as? [String: Any] ?? [:]
        XCTAssertEqual(int(summary["openingBalanceCents"]), 30000)
        XCTAssertEqual(int(summary["accumulatedBalanceCents"]), 30000 + 5000 - 12000 - 3000)
        let version = int(api("owner", openingPath)["version"])
        api("owner", openingPath, method: "PUT", body: ["amountCents": -100], status: 400, headers: ["If-Match": "\"\(version)\""])
        api("athlete", openingPath, status: 403)
    }

    private func expenses() -> [[String: Any]] {
        apiArray("owner", "/api/groups/\(group)/expenses", key: "expenses")
    }
}
