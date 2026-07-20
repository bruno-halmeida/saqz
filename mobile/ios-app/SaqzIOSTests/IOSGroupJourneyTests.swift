import Foundation
import SaqzMobile
import XCTest
@testable import SaqzIOS

final class IOSGroupJourneyTests: XCTestCase {
    func testCompleteOwnerSeesPeopleGamesOrganizerFinanceAndCanMutate() {
        let access = GroupRoutePolicy.shared.evaluate(role: .owner, profileStatus: .complete)
        XCTAssertTrue(access.peopleVisible); XCTAssertTrue(access.gamesVisible)
        XCTAssertEqual(access.financeVisibility, .organizer)
        XCTAssertTrue(access.operationsMutable); XCTAssertFalse(access.profileCompletionVisible)
    }

    func testCompleteAdminHasExactOrganizerSemanticOrder() {
        let access = GroupRoutePolicy.shared.evaluate(role: .admin, profileStatus: .complete)
        XCTAssertEqual(access.semanticActions, [.people, .games, .finance])
        XCTAssertTrue(access.operationsMutable)
    }

    func testAthleteSeesGamesAndOwnChargesWithoutPeopleOrMutations() {
        let access = GroupRoutePolicy.shared.evaluate(role: .athlete, profileStatus: .complete)
        XCTAssertFalse(access.peopleVisible); XCTAssertTrue(access.gamesVisible)
        XCTAssertEqual(access.financeVisibility, .ownCharges)
        XCTAssertFalse(access.operationsMutable); XCTAssertFalse(access.profileCompletionVisible)
    }

    func testIncompleteOwnerStartsWithCompletionThenReadableRoutesInOrder() {
        let access = GroupRoutePolicy.shared.evaluate(role: .owner, profileStatus: .incomplete)
        XCTAssertEqual(access.semanticActions.first, .completeProfile)
        XCTAssertTrue(access.profileCompletionVisible); XCTAssertFalse(access.operationsMutable)
        XCTAssertTrue(access.gamesVisible)
    }

    func testIncompleteAdminCannotMutateGameAttendanceOrFinance() {
        let access = GroupRoutePolicy.shared.evaluate(role: .admin, profileStatus: .incomplete)
        XCTAssertTrue(access.profileCompletionVisible); XCTAssertFalse(access.operationsMutable)
        XCTAssertEqual(access.financeVisibility, .organizer)
    }

    func testIncompleteAthleteHasNoCompletionEditorAndOnlyOwnFinance() {
        let access = GroupRoutePolicy.shared.evaluate(role: .athlete, profileStatus: .incomplete)
        XCTAssertFalse(access.profileCompletionVisible); XCTAssertFalse(access.operationsMutable)
        XCTAssertEqual(access.semanticActions, [.games, .finance])
        XCTAssertEqual(access.financeVisibility, .ownCharges)
    }

    func testSwitchLogoutAndMembershipLossHidePriorGroupImmediately() {
        XCTAssertTrue(GroupRoutePolicy.shared.canRenderPrivateData(boundGroupId: group, selectedGroupId: group))
        XCTAssertFalse(GroupRoutePolicy.shared.canRenderPrivateData(boundGroupId: group, selectedGroupId: "next-group"))
        XCTAssertFalse(GroupRoutePolicy.shared.canRenderPrivateData(boundGroupId: group, selectedGroupId: nil))
        XCTAssertFalse(GroupRoutePolicy.shared.canRenderPrivateData(boundGroupId: nil, selectedGroupId: group))
    }

    func testControllerRecreationRestoresOneMonthlyCommandKeyAndMatchingSuccessClearsOnce() {
        let files = JourneyDraftFiles(); let first = IOSGroupDraftStore(files: files)
        XCTAssertTrue(first.writeMonthly(monthly))
        let recreated = IOSGroupDraftStore(files: files)
        guard case .success(let restored) = recreated.readMonthly(groupID: group) else {
            return XCTFail("Expected restored monthly journey draft")
        }
        XCTAssertEqual(restored.commandKey, commandKey)
        XCTAssertTrue(recreated.clearMonthly(groupID: group, commandKey: commandKey))
        XCTAssertTrue(recreated.clearMonthly(groupID: group, commandKey: commandKey))
        guard case .missing = recreated.readMonthly(groupID: group) else {
            return XCTFail("Expected idempotent matching cleanup")
        }
    }

    private var monthly: MonthlyChargeDraft {
        MonthlyChargeDraft(
            schemaVersion: 1, groupId: group, commandKey: commandKey,
            month: "2026-08", amountBrl: "70,00", dueDate: "2026-08-10",
            selectedMemberIds: Set(["athlete-1"]), reviewed: true
        )
    }

    private let group = "journey-group"
    private let commandKey = "journey-command-key"
}

private final class JourneyDraftFiles: IOSDraftFileClient {
    private var values: [String: Data] = [:]
    func read(key: String) throws -> Data? { values[key] }
    func write(_ data: Data, key: String) throws { values[key] = data }
    func remove(key: String) throws { values.removeValue(forKey: key) }
    func keys() throws -> [String] { Array(values.keys) }
}
