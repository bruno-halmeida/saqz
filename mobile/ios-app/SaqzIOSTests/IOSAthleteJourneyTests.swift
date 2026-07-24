import Foundation
import SaqzMobile
import XCTest

/// Épico 04 athlete journeys through the exported SaqzMobile seam (VUL-6 / T14 gap):
/// phone completion gate (ATH-01), deeplink invite + gate ordering, position
/// onboarding (ATH-02), and roster reachability/management gating (ATH-03/04).
/// V51: every test builds its own machines, fakes, and scope — no shared state.
@MainActor
final class IOSAthleteJourneyTests: XCTestCase {

    // MARK: ATH-01 — phone completion gate

    func testPhoneRequiredBootstrapLandsOnCompletionGateBeforeGroupNavigation() {
        let fixture = SessionFixture(phoneRequired: true); defer { fixture.close() }
        fixture.machine.onIntent(intent: SessionIntentAccept(transition: AuthTransitionAuthenticated(user: JourneyData.verifiedUser)))
        waitUntil { fixture.machine.state.value is SessionAccessStateCompletingPhone }
        XCTAssertFalse(fixture.machine.state.value is SessionAccessStateReady)
    }

    func testCompletingPhoneWithFakeMobileNumberReachesReady() {
        let fixture = SessionFixture(phoneRequired: true); defer { fixture.close() }
        fixture.machine.onIntent(intent: SessionIntentAccept(transition: AuthTransitionAuthenticated(user: JourneyData.verifiedUser)))
        waitUntil { fixture.machine.state.value is SessionAccessStateCompletingPhone }

        fixture.machine.onIntent(intent: SessionIntentUpdatePhone(value: JourneyData.fakeMobileInput))
        fixture.machine.onIntent(intent: SessionIntentCompletePhone())
        waitUntil { fixture.machine.state.value is SessionAccessStateReady }
        XCTAssertEqual(fixture.gateway.completedPhones, [JourneyData.fakeMobileNormalized])
    }

    func testImplausiblePhoneStaysOnGateWithoutProfileRequest() {
        let fixture = SessionFixture(phoneRequired: true); defer { fixture.close() }
        fixture.machine.onIntent(intent: SessionIntentAccept(transition: AuthTransitionAuthenticated(user: JourneyData.verifiedUser)))
        waitUntil { fixture.machine.state.value is SessionAccessStateCompletingPhone }

        fixture.machine.onIntent(intent: SessionIntentUpdatePhone(value: JourneyData.fakeLandlineInput))
        fixture.machine.onIntent(intent: SessionIntentCompletePhone())
        waitUntil { (fixture.machine.state.value as? SessionAccessStateCompletingPhone)?.invalidPhone == true }
        XCTAssertEqual(fixture.gateway.completedPhones, [])
    }

    func testStoredPhoneEntryNeverRepromptsCompletion() {
        let fixture = SessionFixture(phoneRequired: false); defer { fixture.close() }
        fixture.machine.onIntent(intent: SessionIntentAccept(transition: AuthTransitionAuthenticated(user: JourneyData.verifiedUser)))
        waitUntil { fixture.machine.state.value is SessionAccessStateReady }
        XCTAssertEqual(fixture.gateway.completedPhones, [])
    }

    // MARK: deeplink invite + completion gate ordering

    func testPendingInviteSurvivesCompletionGateAndResumesAfterSuccess() {
        let session = SessionFixture(phoneRequired: true); defer { session.close() }
        let invites = InviteFixture(scope: session.journeyScope, storedInvite: JourneyData.inviteCode)

        // Cold start with a persisted invite: restore, then bootstrap into the gate.
        invites.machine.onIntent(intent: DeferredInviteIntentRestore())
        session.machine.onIntent(intent: SessionIntentAccept(transition: AuthTransitionAuthenticated(user: JourneyData.verifiedUser)))
        waitUntil { session.machine.state.value is SessionAccessStateCompletingPhone }
        invites.machine.onIntent(intent: DeferredInviteIntentSetSessionReady(ready: false))
        pump()
        XCTAssertEqual(invites.membership.redeemedCodes, [])
        XCTAssertTrue((invites.machine.state.value as! InviteState).hasPending)
        XCTAssertFalse(invites.local.pendingWrites.contains(nil), "Gate must not drop the persisted invite capability")

        // Completing the phone reaches Ready; the orchestrator flips readiness and the invite resumes.
        session.machine.onIntent(intent: SessionIntentUpdatePhone(value: JourneyData.fakeMobileInput))
        session.machine.onIntent(intent: SessionIntentCompletePhone())
        waitUntil { session.machine.state.value is SessionAccessStateReady }
        invites.machine.onIntent(intent: DeferredInviteIntentSetSessionReady(ready: true))
        waitUntil { (invites.machine.state.value as! InviteState).redeemedRole == .athlete }
        XCTAssertEqual(invites.membership.redeemedCodes.count, 1)
        XCTAssertTrue(invites.membership.redeemedCodes[0].contains(JourneyData.inviteCode))
        XCTAssertEqual(invites.relay.selected, [JourneyData.groupId])
        XCTAssertEqual(invites.local.pendingWrites.last, .some(nil), "Redeemed invite clears the pending capability")
    }

    // MARK: ATH-02 — position onboarding

    func testRedeemAsAthleteShowsSkippablePositionStepOnce() {
        let fixture = OnboardingFixture(redeemRole: .athlete); defer { fixture.close() }
        fixture.redeemWarmInvite()
        waitUntil { fixture.state.visible }
        XCTAssertEqual(fixture.state.groupId, JourneyData.groupId)

        fixture.viewModel.onIntent(intent: PositionOnboardingIntentSkip())
        waitUntil { !fixture.state.visible }
        // Re-selecting the group re-emits upstream state; the dismissed step must not return.
        fixture.selection.onIntent(intent: GroupSelectionIntentSelect(groupId: JourneyData.groupId))
        pump()
        XCTAssertFalse(fixture.state.visible)
        XCTAssertEqual(fixture.athletes.ownPositionUpdates, [], "Skip must navigate on without any request")
    }

    func testChoosingPositionPersistsThroughOwnRouteAndDismisses() {
        let fixture = OnboardingFixture(redeemRole: .athlete); defer { fixture.close() }
        fixture.redeemWarmInvite()
        waitUntil { fixture.state.visible }

        fixture.viewModel.onIntent(intent: PositionOnboardingIntentChoose(position: .ponta))
        waitUntil { !fixture.state.visible }
        XCTAssertEqual(fixture.athletes.ownPositionUpdates, [AthletePosition.ponta])
    }

    func testAuthoritativeNonAthleteRedeemNeverShowsPositionStep() {
        let fixture = OnboardingFixture(redeemRole: .admin); defer { fixture.close() }
        fixture.redeemWarmInvite()
        waitUntil { (fixture.invites.machine.state.value as! InviteState).redeemedRole == .admin }
        pump()
        XCTAssertFalse(fixture.state.visible)
        XCTAssertEqual(fixture.athletes.ownPositionUpdates, [])
    }

    // MARK: ATH-03/04 — roster reachability and management gating

    func testPeopleRouteStaysOrganizerOnlyForAthleteJourney() {
        XCTAssertTrue(GroupRoutePolicy.shared.evaluate(role: .owner, profileStatus: .complete).peopleVisible)
        XCTAssertTrue(GroupRoutePolicy.shared.evaluate(role: .admin, profileStatus: .complete).peopleVisible)
        XCTAssertFalse(GroupRoutePolicy.shared.evaluate(role: .athlete, profileStatus: .complete).peopleVisible)
    }

    func testOwnerAndAdminReceiveAthleteManagementWhileRolesStayOwnerOnly() {
        let fixture = AdministrationFixture(); defer { fixture.close() }
        fixture.setGroup(role: .owner)
        XCTAssertTrue(fixture.actions.canManageAthletes); XCTAssertTrue(fixture.actions.canManageRoles)
        // Regression (VUL-6): roster management mirrors backend MANAGE_ATHLETES — ADMIN included.
        fixture.setGroup(role: .admin)
        XCTAssertTrue(fixture.actions.canManageAthletes); XCTAssertFalse(fixture.actions.canManageRoles)
    }

    func testAthleteReceivesNoManagementActions() {
        let fixture = AdministrationFixture(); defer { fixture.close() }
        fixture.setGroup(role: .athlete)
        XCTAssertFalse(fixture.actions.canManageAthletes)
        XCTAssertFalse(fixture.actions.canManageRoles); XCTAssertFalse(fixture.actions.canEditSettings)
    }

    func testRosterLoadsForSelectedGroupAndCombinesFilters() {
        let fixture = RosterFixture(); defer { fixture.close() }
        fixture.selectGroup()
        waitUntil { fixture.state.athletes.count == 2 }
        XCTAssertEqual(fixture.state.groupId, JourneyData.groupId)

        fixture.viewModel.onIntent(intent: AthleteRosterIntentSearch(text: "ana"))
        fixture.viewModel.onIntent(intent: AthleteRosterIntentFilterType(type: .mensalista))
        fixture.viewModel.onIntent(intent: AthleteRosterIntentFilterPosition(position: .libero))
        waitUntil {
            fixture.athletes.lastFilter?.search == "ana"
                && fixture.athletes.lastFilter?.membershipType == .mensalista
                && fixture.athletes.lastFilter?.position == .libero
        }
    }

    func testEditAndRemovalRoundTripThroughAthleteGateway() {
        let fixture = RosterFixture(); defer { fixture.close() }
        fixture.selectGroup()
        waitUntil { fixture.state.athletes.count == 2 }

        fixture.viewModel.onIntent(intent: AthleteRosterIntentOpenEdit(userId: "athlete-1"))
        fixture.viewModel.onIntent(intent: AthleteRosterIntentEditType(type: .mensalista))
        fixture.viewModel.onIntent(intent: AthleteRosterIntentSaveEdit())
        waitUntil { fixture.athletes.lastUpdate != nil }
        XCTAssertEqual(fixture.athletes.lastUpdate?.userId, "athlete-1")
        XCTAssertEqual(fixture.athletes.lastUpdate?.membershipType, .mensalista)

        fixture.viewModel.onIntent(intent: AthleteRosterIntentRequestRemoval(userId: "athlete-2"))
        fixture.viewModel.onIntent(intent: AthleteRosterIntentConfirmRemoval())
        waitUntil { fixture.athletes.removedUserIds == ["athlete-2"] }
        waitUntil { fixture.state.removal == nil }
    }

    // MARK: - Fixtures (fresh machines + fakes per test — V51)

    @MainActor private final class SessionFixture {
        let journeyScope: IOSJourneyScope
        let gateway: FakeSessionGateway
        let machine: SessionAccessStateMachine

        init(phoneRequired: Bool) {
            let scope = IOSJourneyScope()
            let gateway = FakeSessionGateway(phoneRequired: phoneRequired)
            journeyScope = scope
            self.gateway = gateway
            machine = SessionAccessStateMachine(
                auth: FakeAuthPort(), localState: FakeAccessLocalState(), session: gateway, scope: scope.scope
            )
        }

        func close() { journeyScope.cancel() }
    }

    @MainActor private final class InviteFixture {
        let links: FakeGroupLinks
        let local: FakeGroupLocalState
        let membership: FakeMembershipGateway
        let relay: SelectionRelay
        let machine: DeferredInviteStateMachine

        init(scope: IOSJourneyScope, storedInvite: String?) {
            let links = FakeGroupLinks()
            let local = FakeGroupLocalState(storedInvite: storedInvite)
            let membership = FakeMembershipGateway()
            let relay = SelectionRelay()
            self.links = links
            self.local = local
            self.membership = membership
            self.relay = relay
            machine = DeferredInviteStateMachine(
                links: links, localState: local, invites: membership, scope: scope.scope,
                selectGroup: { relay.select($0) }
            )
        }
    }

    @MainActor private final class OnboardingFixture {
        let journeyScope: IOSJourneyScope
        let invites: InviteFixture
        let selection: GroupSelectionStateMachine
        let athletes: FakeAthleteGateway
        let viewModel: PositionOnboardingViewModel

        init(redeemRole: GroupRole) {
            let scope = IOSJourneyScope()
            let invites = InviteFixture(scope: scope, storedInvite: nil)
            invites.membership.redeemRole = redeemRole
            let selection = GroupSelectionStateMachine(
                localState: invites.local, groups: FakeGroupGateway(role: redeemRole), scope: scope.scope
            )
            // Mirrors GroupsModule wiring: a redeemed invite selects the returned group.
            invites.relay.handler = { selection.onIntent(intent: GroupSelectionIntentSelect(groupId: $0)) }
            selection.onIntent(intent: GroupSelectionIntentReconcile(memberships: [
                GroupSelectionMembership(groupId: JourneyData.groupId, groupName: "Grupo Vôlei", role: redeemRole)
            ]))
            let athletes = FakeAthleteGateway()
            journeyScope = scope
            self.invites = invites
            self.selection = selection
            self.athletes = athletes
            viewModel = PositionOnboardingViewModel(invites: invites.machine, selection: selection, athletes: athletes)
        }

        var state: PositionOnboardingState { viewModel.state.value as! PositionOnboardingState }

        func redeemWarmInvite() {
            invites.machine.onIntent(intent: DeferredInviteIntentSetSessionReady(ready: true))
            invites.machine.onIntent(intent: DeferredInviteIntentStart())
            invites.links.emit(code: JourneyData.inviteCode)
        }

        func close() { journeyScope.cancel() }
    }

    @MainActor private final class AdministrationFixture {
        let journeyScope: IOSJourneyScope
        let machine: GroupAdministrationStateMachine

        init() {
            let scope = IOSJourneyScope()
            journeyScope = scope
            machine = GroupAdministrationStateMachine(
                groups: FakeGroupGateway(role: .owner), roles: FakeMembershipGateway(),
                scope: scope.scope, selectCreatedGroup: { _ in }
            )
        }

        var actions: GroupActions { (machine.state.value as! GroupAdministrationState).actions }

        func setGroup(role: GroupRole) {
            machine.onIntent(intent: GroupAdministrationIntentSetGroup(
                group: IOSJourneySupportKt.journeyVersionedGroup(group: JourneyData.group(role: role), versionToken: "journey-etag")
            ))
        }

        func close() { journeyScope.cancel() }
    }

    @MainActor private final class RosterFixture {
        let journeyScope: IOSJourneyScope
        let athletes: FakeAthleteGateway
        let selection: GroupSelectionStateMachine
        let viewModel: AthleteRosterViewModel

        init() {
            let scope = IOSJourneyScope()
            let athletes = FakeAthleteGateway()
            athletes.rosterEntries = [
                AthleteRosterEntry(
                    userId: "athlete-1", displayName: "Ana Atleta", phone: JourneyData.fakeMobileNormalized,
                    position: .libero, membershipType: .avulso, active: true, financialStatus: .emDia
                ),
                AthleteRosterEntry(
                    userId: "athlete-2", displayName: "Bia Bloqueio", phone: nil,
                    position: nil, membershipType: .mensalista, active: true, financialStatus: .pendente
                ),
            ]
            let selection = GroupSelectionStateMachine(
                localState: FakeGroupLocalState(storedInvite: nil), groups: FakeGroupGateway(role: .owner), scope: scope.scope
            )
            journeyScope = scope
            self.athletes = athletes
            self.selection = selection
            viewModel = AthleteRosterViewModel(selection: selection, athletes: athletes)
        }

        var state: AthleteRosterState { viewModel.state.value as! AthleteRosterState }

        func selectGroup() {
            selection.onIntent(intent: GroupSelectionIntentReconcile(memberships: [
                GroupSelectionMembership(groupId: JourneyData.groupId, groupName: "Grupo Vôlei", role: .owner)
            ]))
        }

        func close() { journeyScope.cancel() }
    }

    // MARK: - Main-loop pumping

    private func pump(_ seconds: TimeInterval = 0.2) {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline { RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.02)) }
    }

    private func waitUntil(
        timeout: TimeInterval = 5, file: StaticString = #filePath, line: UInt = #line, _ condition: () -> Bool
    ) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            RunLoop.main.run(mode: .default, before: Date().addingTimeInterval(0.02))
        }
        XCTAssertTrue(condition(), "Journey condition not met within \(timeout)s", file: file, line: line)
    }
}

@MainActor final class SelectionRelay {
    var handler: (String) -> Void = { _ in }
    private(set) var selected: [String] = []

    func select(_ groupId: String) {
        selected.append(groupId)
        handler(groupId)
    }
}

// MARK: - Fixture data (obviously fake — no real phone numbers)

@MainActor private enum JourneyData {
    static let verifiedUser = NativeUser(subject: "user-1", email: "atleta@example.test", emailVerified: true, displayName: "Ana Atleta")
    static let fakeMobileInput = "(11) 98888-0000"
    static let fakeMobileNormalized = "+5511988880000"
    static let fakeLandlineInput = "(11) 3333-0000"
    static let inviteCode = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    static let groupId = "journey-group"

    static func session(phoneRequired: Bool) -> AccessSession {
        AccessSession(
            user: AccessUser(
                id: "user-1", email: "atleta@example.test", displayName: "Ana Atleta",
                phone: phoneRequired ? nil : fakeMobileNormalized, phoneRequired: phoneRequired
            ),
            memberships: []
        )
    }

    static func group(role: GroupRole) -> Group {
        Group(id: groupId, name: "Grupo Vôlei", timeZone: "America/Sao_Paulo", version: 1, role: role)
    }
}

// MARK: - Swift fakes over the exported Kotlin ports

@MainActor private final class FakeSessionGateway: @preconcurrency SessionGateway {
    private var phoneRequired: Bool
    private(set) var completedPhones: [String] = []

    init(phoneRequired: Bool) { self.phoneRequired = phoneRequired }

    func bootstrap(completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        completionHandler(IOSJourneySupportKt.journeySuccess(value: JourneyData.session(phoneRequired: phoneRequired)), nil)
    }

    func completeProfile(phone: String, displayName: String?, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        completedPhones.append(phone); phoneRequired = false
        completionHandler(IOSJourneySupportKt.journeySuccess(value: JourneyData.session(phoneRequired: false)), nil)
    }
}

@MainActor private final class FakeAuthPort: @preconcurrency NativeAuthPort {
    func createAccount(name: String, email: String, password: String, done: AuthCallback) {}
    func idToken(forceRefresh: Bool, done: TokenCallback) {}
    func observe(listener: AuthStateListener) -> Cancelable { NoOpCancelable() }
    func reloadUser(done: AuthCallback) {}
    func sendPasswordReset(email: String, done: ResultCallback) {}
    func sendVerification(done: ResultCallback) {}
    func signInWithGoogle(done: AuthCallback) {}
    func signInWithPassword(email: String, password: String, done: AuthCallback) {}
    func signOut(done: ResultCallback) { done.complete(result_: OperationResultSuccess()) }
    func updateDisplayName(name: String, done: AuthCallback) {}
}

private final class NoOpCancelable: Cancelable { func cancel() {} }
private final class NoOpGroupCancelable: GroupCancelable { func cancel() {} }

@MainActor private final class FakeAccessLocalState: @preconcurrency LocalAccessStatePort {
    func readPendingInvite(done: ValueCallback) { done.complete(result___: ValueResultSuccess(value: nil)) }
    func readSelectedGroupId(done: ValueCallback) { done.complete(result___: ValueResultSuccess(value: nil)) }
    func writePendingInvite(value: String?, done: ResultCallback) { done.complete(result_: OperationResultSuccess()) }
    func writeSelectedGroupId(value: String?, done: ResultCallback) { done.complete(result_: OperationResultSuccess()) }
}

@MainActor private final class FakeGroupLocalState: @preconcurrency LocalGroupStatePort {
    private var storedInvite: String?
    private(set) var pendingWrites: [String?] = []

    init(storedInvite: String?) { self.storedInvite = storedInvite }

    func readPendingInvite(done_: GroupValueCallback) { done_.complete(result_____: GroupValueResultSuccess(value: storedInvite)) }
    func readSelectedGroupId(done_: GroupValueCallback) { done_.complete(result_____: GroupValueResultSuccess(value: nil)) }
    func readPendingAttendanceLink(done: GroupValueCallback) { done.complete(result_____: GroupValueResultSuccess(value: nil)) }

    func writePendingInvite(value: String?, done_: GroupResultCallback) {
        pendingWrites.append(value); storedInvite = value
        done_.complete(result____: GroupOperationResultSuccess())
    }

    func writeSelectedGroupId(value: String?, done_: GroupResultCallback) { done_.complete(result____: GroupOperationResultSuccess()) }
    func writePendingAttendanceLink(value: String?, done: GroupResultCallback) { done.complete(result____: GroupOperationResultSuccess()) }
}

@MainActor private final class FakeGroupLinks: @preconcurrency NativeGroupLinkPort {
    private var listener: GroupLinkEventListener?

    func start(listener_: GroupLinkEventListener) -> GroupCancelable {
        listener = listener_
        return NoOpGroupCancelable()
    }

    func emit(code: String) { listener?.onEvent(event: GroupLinkEventInvite(code: code)) }
}

@MainActor private final class FakeMembershipGateway: @preconcurrency GroupMembershipGateway {
    var redeemRole: GroupRole = .athlete
    private(set) var redeemedCodes: [String] = []

    func redeem(code: Any, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        redeemedCodes.append(String(describing: code))
        let membership = IOSJourneySupportKt.journeyRedeemedMembership(groupId: JourneyData.groupId, role: redeemRole)
        completionHandler(IOSJourneySupportKt.journeySuccess(value: membership), nil)
    }

    func changeRole(command: ChangeMembershipRoleCommand, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {}
    func expireInvite(groupId: Any, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {}
    func listMemberships(groupId: Any, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {}
    func rotateInvite(groupId: Any, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {}
}

@MainActor private final class FakeGroupGateway: @preconcurrency GroupGateway {
    private let role: GroupRole

    init(role: GroupRole) { self.role = role }

    func read(groupId: Any, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        let versioned = IOSJourneySupportKt.journeyVersionedGroup(group: JourneyData.group(role: role), versionToken: "journey-etag")
        completionHandler(IOSJourneySupportKt.journeySuccess(value: versioned), nil)
    }

    func create(command: CreateGroupCommand, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {}
    func update(command: UpdateGroupSettingsCommand, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {}
}

@MainActor private final class FakeAthleteGateway: @preconcurrency AthleteGateway {
    var rosterEntries: [AthleteRosterEntry] = []
    private(set) var lastFilter: AthleteRosterFilter?
    private(set) var ownPositionUpdates: [AthletePosition?] = []
    private(set) var lastUpdate: UpdateAthleteCommand?
    private(set) var removedUserIds: [String] = []

    func roster(groupId: Any, filter: AthleteRosterFilter, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        lastFilter = filter
        completionHandler(IOSJourneySupportKt.journeyRosterSuccess(entries: rosterEntries), nil)
    }

    func updateOwnPosition(groupId: Any, position: AthletePosition?, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        ownPositionUpdates.append(position)
        completionHandler(IOSJourneySupportKt.journeySuccess(value: nil), nil)
    }

    func updateAthlete(command: UpdateAthleteCommand, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        lastUpdate = command
        completionHandler(IOSJourneySupportKt.journeySuccess(value: nil), nil)
    }

    func removeAthlete(groupId: Any, userId: String, completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {
        removedUserIds.append(userId)
        completionHandler(IOSJourneySupportKt.journeySuccess(value: nil), nil)
    }

    func ownProfile(completionHandler: @escaping (DomainSaqzResult?, Error?) -> Void) {}
}
