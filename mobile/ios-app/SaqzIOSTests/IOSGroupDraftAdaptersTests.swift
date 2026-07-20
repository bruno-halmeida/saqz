import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

final class IOSGroupDraftAdaptersTests: XCTestCase {
    func testSetupRoundTripsExactVersionGroupResourceEtagKeyAndForm() {
        let fixture = makeFixture(); let draft = setup()
        XCTAssertTrue(fixture.store.writeSetup(draft))
        guard case .success(let restored) = fixture.store.readSetup(setupKey()) else { return XCTFail("Expected setup draft") }
        XCTAssertEqual(restored.schemaVersion, 1); XCTAssertEqual(restored.groupId, group)
        XCTAssertEqual(restored.groupVersion?.int64Value, 7); XCTAssertEqual(restored.resource, .updateGroup)
        XCTAssertEqual(restored.etag, "\"7\""); XCTAssertEqual(restored.commandKey, commandKey)
        XCTAssertEqual(restored.form.name, "Vôlei"); XCTAssertEqual(restored.form.city, "Recife")
    }

    func testGameRoundTripsExactResourceEtagKeyAndAllowedForm() {
        let fixture = makeFixture(); let draft = gameDraft()
        XCTAssertTrue(fixture.store.writeGame(draft))
        guard case .success(let restored) = fixture.store.readGame(groupID: group, resourceID: game) else { return XCTFail("Expected game draft") }
        XCTAssertEqual(restored.gameId, game); XCTAssertEqual(restored.etag, "\"4\"")
        XCTAssertEqual(restored.commandKey, commandKey); XCTAssertEqual(restored.form.title, "Treino")
        XCTAssertEqual(restored.form.localTime, "19:30:00"); XCTAssertEqual(restored.form.gameFeeBrl, "25,00")
    }

    func testMonthlyRoundTripsSelectionReviewVersionAndKey() {
        let fixture = makeFixture(); let draft = monthly()
        XCTAssertTrue(fixture.store.writeMonthly(draft))
        guard case .success(let restored) = fixture.store.readMonthly(groupID: group) else { return XCTFail("Expected monthly draft") }
        XCTAssertEqual(restored.schemaVersion, 1); XCTAssertEqual(restored.groupId, group)
        XCTAssertEqual(restored.commandKey, commandKey); XCTAssertEqual(restored.amountBrl, "70,00")
        XCTAssertEqual(restored.selectedMemberIds, Set(["member-1", "member-2"])); XCTAssertTrue(restored.reviewed)
    }

    func testExpenseRoundTripsConditionalFormVersionEtagAndKey() {
        let fixture = makeFixture(); let draft = expense()
        XCTAssertTrue(fixture.store.writeExpense(draft))
        guard case .success(let restored) = fixture.store.readExpense(groupID: group) else { return XCTFail("Expected expense draft") }
        XCTAssertEqual(restored.schemaVersion, 1); XCTAssertEqual(restored.expenseId, expenseID)
        XCTAssertEqual(restored.etag, "\"3\""); XCTAssertEqual(restored.commandKey, commandKey)
        XCTAssertEqual(restored.form.category, .other); XCTAssertEqual(restored.form.customCategory, "Água")
    }

    func testAdapterRecreationReadsPreviouslyCommittedDraft() {
        let files = RecordingDraftFiles(); XCTAssertTrue(IOSGroupDraftStore(files: files).writeGame(gameDraft()))
        let recreated = IOSGroupDraftStore(files: files)
        guard case .success(let restored) = recreated.readGame(groupID: group, resourceID: game) else { return XCTFail("Expected recreated draft") }
        XCTAssertEqual(restored.commandKey, commandKey); XCTAssertEqual(restored.form.capacity, "24")
    }

    func testFailedAtomicWritePreservesPreviousCommittedValue() {
        let fixture = makeFixture(); XCTAssertTrue(fixture.store.writeMonthly(monthly()))
        fixture.files.failWrites = true
        XCTAssertFalse(fixture.store.writeMonthly(monthly(amount: "80,00")))
        guard case .success(let restored) = fixture.store.readMonthly(groupID: group) else { return XCTFail("Expected old draft") }
        XCTAssertEqual(restored.amountBrl, "70,00")
    }

    func testCorruptEnvelopeReturnsTypedCorruptWithoutDraft() {
        let fixture = makeFixture(); XCTAssertTrue(fixture.store.writeExpense(expense()))
        fixture.files.values[fixture.files.values.keys.first!] = Data("not-json".utf8)
        guard case .corrupt = fixture.store.readExpense(groupID: group) else { return XCTFail("Expected corrupt") }
    }

    func testOldPayloadSchemaReturnsTypedUnsupportedWithoutDispatch() {
        let fixture = makeFixture(); XCTAssertTrue(fixture.store.writeMonthly(monthly(schema: 99)))
        guard case .unsupportedSchema = fixture.store.readMonthly(groupID: group) else { return XCTFail("Expected unsupported schema") }
    }

    func testSerializedStructureExcludesSensitiveMediaPaymentAndRawErrors() {
        let fixture = makeFixture(); XCTAssertTrue(fixture.store.writeSetup(setup())); XCTAssertTrue(fixture.store.writeGame(gameDraft()))
        XCTAssertTrue(fixture.store.writeMonthly(monthly())); XCTAssertTrue(fixture.store.writeExpense(expense()))
        let raw = fixture.files.values.values.compactMap { String(data: $0, encoding: .utf8) }.joined()
        ["bearerToken", "inviteCode", "photoBytes", "photoHandle", "paymentCredential", "rawServerError"].forEach { XCTAssertFalse(raw.contains("\"\($0)\"")) }
    }

    func testMismatchedSuccessPreservesDraftAndMatchingSuccessClearsOnlyTarget() {
        let fixture = makeFixture(); XCTAssertTrue(fixture.store.writeMonthly(monthly())); XCTAssertTrue(fixture.store.writeExpense(expense()))
        XCTAssertTrue(fixture.store.clearMonthly(groupID: group, commandKey: "other"))
        guard case .success = fixture.store.readMonthly(groupID: group) else { return XCTFail("Mismatch must preserve") }
        XCTAssertTrue(fixture.store.clearMonthly(groupID: group, commandKey: commandKey))
        guard case .missing = fixture.store.readMonthly(groupID: group) else { return XCTFail("Match must clear") }
        guard case .success(let remaining) = fixture.store.readExpense(groupID: group) else { return XCTFail("Unrelated draft must remain") }
        XCTAssertEqual(remaining.expenseId, expenseID)
    }

    func testGroupLossClearsOnlyMatchingGroupDrafts() {
        let fixture = makeFixture(); XCTAssertTrue(fixture.store.writeMonthly(monthly()))
        XCTAssertTrue(fixture.store.writeExpense(expense(groupID: otherGroup)))
        XCTAssertTrue(fixture.store.clearGroup(group))
        guard case .missing = fixture.store.readMonthly(groupID: group) else { return XCTFail("Group draft must clear") }
        guard case .success(let remaining) = fixture.store.readExpense(groupID: otherGroup) else { return XCTFail("Other group must remain") }
        XCTAssertEqual(remaining.groupId, otherGroup)
    }

    func testLogoutClearsAllDraftsAndLeavesUnrelatedFiles() {
        let fixture = makeFixture(); XCTAssertTrue(fixture.store.writeGame(gameDraft())); XCTAssertTrue(fixture.store.writeMonthly(monthly()))
        fixture.files.values["unrelated"] = Data("keep".utf8)
        XCTAssertTrue(fixture.store.clearAll()); XCTAssertEqual(fixture.files.values["unrelated"], Data("keep".utf8))
        guard case .missing = fixture.store.readGame(groupID: group, resourceID: game) else { return XCTFail("Game must clear") }
        guard case .missing = fixture.store.readMonthly(groupID: group) else { return XCTFail("Monthly must clear") }
    }

    private func makeFixture() -> Fixture { let files = RecordingDraftFiles(); return Fixture(files: files, store: IOSGroupDraftStore(files: files)) }
    private func setupKey() -> GroupDraftKey { GroupDraftKey(resource: .updateGroup, groupId: group) }
    private func setup() -> GroupSetupDraft {
        GroupSetupDraft(schemaVersion: 1, resource: .updateGroup, groupId: group, groupVersion: KotlinLong(value: 7), etag: "\"7\"", commandKey: commandKey,
            form: GroupSetupForm(name: "Vôlei", modality: .courtVolleyball, composition: .mixed, description: nil, city: "Recife", level: .custom, customLevel: "Intermediário +", playStyle: .fiveOne, customPlayStyle: nil, defaultVenue: nil, regularSlots: [], defaultCapacity: KotlinInt(value: 24), defaultConfirmationLeadMinutes: nil, defaultGameFeeCents: nil, monthlyFeeCents: KotlinLong(value: 7000), monthlyDueDay: KotlinInt(value: 10)))
    }
    private func gameDraft() -> GameEditorDraft {
        GameEditorDraft(schemaVersion: 1, groupId: group, gameId: game, seriesId: nil, commandKey: commandKey, etag: "\"4\"", mode: .oneTime,
            form: GameEditorForm(title: "Treino", venue: GameVenueDto(venueId: nil, name: "Arena", address: "Rua 1", court: nil), localDate: "2026-08-12", localTime: "19:30:00", zoneId: "America/Sao_Paulo", startsAt: "2026-08-12T22:30:00Z", durationMinutes: "90", capacity: "24", confirmationDeadline: "2026-08-12T19:00:00Z", gameFeeBrl: "25,00", notes: "Notas", localEndDate: "", slots: []), scope: nil)
    }
    private func monthly(schema: Int32 = 1, amount: String = "70,00") -> MonthlyChargeDraft {
        MonthlyChargeDraft(schemaVersion: schema, groupId: group, commandKey: commandKey, month: "2026-08", amountBrl: amount, dueDate: "2026-08-10", selectedMemberIds: Set(["member-1", "member-2"]), reviewed: true)
    }
    private func expense(groupID: String? = nil) -> ExpenseDraft {
        ExpenseDraft(schemaVersion: 1, groupId: groupID ?? group, expenseId: expenseID, etag: "\"3\"", commandKey: commandKey,
            form: ExpenseForm(description: "Água do jogo", amountBrl: "123,45", expenseDate: "2026-08-12", category: .other, customCategory: "Água", notes: "Compra manual"))
    }

    private struct Fixture { let files: RecordingDraftFiles; let store: IOSGroupDraftStore }
    private let group = "group-1"; private let otherGroup = "group-2"; private let game = "game-1"; private let expenseID = "expense-1"; private let commandKey = "draft-key"
}

private final class RecordingDraftFiles: IOSDraftFileClient {
    var values: [String: Data] = [:]; var failWrites = false
    func read(key: String) throws -> Data? { values[key] }
    func write(_ data: Data, key: String) throws { if failWrites { throw CocoaError(.fileWriteUnknown) }; values[key] = data }
    func remove(key: String) throws { values.removeValue(forKey: key) }
    func keys() throws -> [String] { Array(values.keys) }
}
