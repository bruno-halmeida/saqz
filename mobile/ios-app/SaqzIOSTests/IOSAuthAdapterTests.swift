import SaqzMobile
import XCTest
@testable import SaqzIOS

@MainActor
final class IOSAuthAdapterTests: XCTestCase {
    private let user = IOSAuthUser(
        subject: "firebase-user-1",
        email: "ana@example.test",
        emailVerified: true,
        displayName: "Ana"
    )

    func testObserveMapsSignedOutOnMainActor() {
        let fixture = makeFixture()
        let listener = RecordingAuthStateListener()

        _ = fixture.adapter.observe(listener: listener)
        fixture.firebase.emit(user: nil)

        XCTAssertTrue(listener.states.single is AuthStateSignedOut)
        XCTAssertTrue(listener.wasCalledOnMainThread)
    }

    func testObserveMapsEveryProviderNeutralUserField() {
        let fixture = makeFixture()
        let listener = RecordingAuthStateListener()

        _ = fixture.adapter.observe(listener: listener)
        fixture.firebase.emit(user: user)

        let mapped = (listener.states.single as? AuthStateSignedIn)?.user
        XCTAssertEqual(mapped?.subject, "firebase-user-1")
        XCTAssertEqual(mapped?.email, "ana@example.test")
        XCTAssertEqual(mapped?.emailVerified, true)
        XCTAssertEqual(mapped?.displayName, "Ana")
    }

    func testCancellingObservationRemovesExactlyItsFirebaseListener() {
        let fixture = makeFixture()
        let listener = RecordingAuthStateListener()

        let cancellation = fixture.adapter.observe(listener: listener)
        cancellation.cancel()

        XCTAssertEqual(fixture.firebase.removedObservationIDs, [1])
    }

    func testCreateAccountUpdatesNameBeforeReturningSuccess() {
        let fixture = makeFixture()
        fixture.firebase.createResult = .success(
            IOSAuthUser(subject: "firebase-user-1", email: "ana@example.test", emailVerified: false, displayName: nil)
        )
        fixture.firebase.updateNameResult = .success(user)
        let callback = RecordingAuthCallback()

        fixture.adapter.createAccount(name: "Ana", email: "ana@example.test", password: "provider-policy", done: callback)

        XCTAssertEqual(fixture.firebase.events, [
            .create(email: "ana@example.test", password: "provider-policy"),
            .updateDisplayName("Ana"),
        ])
        XCTAssertEqual((callback.result as? AuthResultSuccess)?.user.displayName, "Ana")
    }

    func testCreateAccountMapsEmailAlreadyInUseWithoutProviderDetail() {
        let fixture = makeFixture()
        fixture.firebase.createResult = .failure(.emailInUse)
        let callback = RecordingAuthCallback()

        fixture.adapter.createAccount(name: "Ana", email: "ana@example.test", password: "provider-policy", done: callback)

        XCTAssertTrue((callback.result as? AuthResultFailure)?.code === NativeFailureCode.emailInUse)
    }

    func testPasswordSignInMapsSuccess() {
        let fixture = makeFixture()
        fixture.firebase.passwordResult = .success(user)
        let callback = RecordingAuthCallback()

        fixture.adapter.signInWithPassword(email: "ana@example.test", password: "provider-policy", done: callback)

        XCTAssertEqual(fixture.firebase.events, [.password(email: "ana@example.test", password: "provider-policy")])
        XCTAssertEqual((callback.result as? AuthResultSuccess)?.user.subject, "firebase-user-1")
    }

    func testPasswordSignInMapsInvalidCredentials() {
        let fixture = makeFixture()
        fixture.firebase.passwordResult = .failure(.invalidCredentials)
        let callback = RecordingAuthCallback()

        fixture.adapter.signInWithPassword(email: "ana@example.test", password: "wrong", done: callback)

        XCTAssertTrue((callback.result as? AuthResultFailure)?.code === NativeFailureCode.invalidCredentials)
    }

    func testGoogleCancellationDoesNotCreateFirebaseCredential() {
        let fixture = makeFixture()
        fixture.google.result = .cancelled
        let callback = RecordingAuthCallback()
        let returnURL = URL(string: "app.saqz:/oauth-return")!

        fixture.adapter.signInWithGoogle(done: callback)

        XCTAssertTrue(callback.result is AuthResultCancelled)
        XCTAssertFalse(fixture.firebase.events.contains { if case .google = $0 { true } else { false } })
        XCTAssertTrue(fixture.adapter.handleGoogleURL(returnURL))
        XCTAssertEqual(fixture.google.handledURLs, [returnURL])
    }

    func testGoogleTokensAuthenticateFirebaseAndReturnOneSuccess() {
        let fixture = makeFixture()
        fixture.google.result = .success(idToken: "google-id-token", accessToken: "google-access-token")
        fixture.firebase.googleResult = .success(user)
        let callback = RecordingAuthCallback()

        fixture.adapter.signInWithGoogle(done: callback)

        XCTAssertEqual(fixture.firebase.events, [.google(idToken: "google-id-token", accessToken: "google-access-token")])
        XCTAssertEqual((callback.result as? AuthResultSuccess)?.user.subject, "firebase-user-1")
        XCTAssertEqual(callback.callCount, 1)
    }

    func testGoogleProviderFailureUsesStableProviderUnavailableCode() {
        let fixture = makeFixture()
        fixture.google.result = .failure(.providerUnavailable)
        let callback = RecordingAuthCallback()

        fixture.adapter.signInWithGoogle(done: callback)

        XCTAssertTrue((callback.result as? AuthResultFailure)?.code === NativeFailureCode.providerUnavailable)
    }

    func testSendVerificationReturnsSuccessOnMainActor() {
        let fixture = makeFixture()
        fixture.firebase.verificationResult = .success(())
        let callback = RecordingResultCallback()

        fixture.adapter.sendVerification(done: callback)

        XCTAssertTrue(callback.result is OperationResultSuccess)
        XCTAssertTrue(callback.wasCalledOnMainThread)
    }

    func testReloadReturnsFreshVerificationState() {
        let fixture = makeFixture()
        fixture.firebase.reloadResult = .success(user)
        let callback = RecordingAuthCallback()

        fixture.adapter.reloadUser(done: callback)

        XCTAssertEqual((callback.result as? AuthResultSuccess)?.user.emailVerified, true)
    }

    func testPasswordResetIsNeutralWhenFirebaseReportsMissingUser() {
        let fixture = makeFixture()
        fixture.firebase.passwordResetResult = .failure(.userNotFound)
        let callback = RecordingResultCallback()

        fixture.adapter.sendPasswordReset(email: "nobody@example.test", done: callback)

        XCTAssertTrue(callback.result is OperationResultSuccess)
    }

    func testUpdateDisplayNameReturnsUpdatedUser() {
        let fixture = makeFixture()
        fixture.firebase.updateNameResult = .success(user)
        let callback = RecordingAuthCallback()

        fixture.adapter.updateDisplayName(name: "Ana", done: callback)

        XCTAssertEqual(fixture.firebase.events, [.updateDisplayName("Ana")])
        XCTAssertEqual((callback.result as? AuthResultSuccess)?.user.displayName, "Ana")
    }

    func testIDTokenForwardsForceRefreshWithoutPersistingToken() {
        let fixture = makeFixture()
        fixture.firebase.tokenResult = .success("fresh-firebase-token")
        let callback = RecordingTokenCallback()

        fixture.adapter.idToken(forceRefresh: true, done: callback)

        XCTAssertEqual(fixture.firebase.events, [.token(forceRefresh: true)])
        XCTAssertEqual((callback.result as? TokenResultSuccess)?.token, "fresh-firebase-token")
    }

    func testIDTokenNetworkFailureUsesStableNetworkCode() {
        let fixture = makeFixture()
        fixture.firebase.tokenResult = .failure(.networkUnavailable)
        let callback = RecordingTokenCallback()

        fixture.adapter.idToken(forceRefresh: false, done: callback)

        XCTAssertTrue((callback.result as? TokenResultFailure)?.code === NativeFailureCode.networkUnavailable)
    }

    func testSignOutCompletesAfterFirebaseLocalSessionEnds() {
        let fixture = makeFixture()
        fixture.firebase.signOutResult = .success(())
        let callback = RecordingResultCallback()

        fixture.adapter.signOut(done: callback)

        XCTAssertEqual(fixture.firebase.events, [.signOut])
        XCTAssertTrue(callback.result is OperationResultSuccess)
    }

    func testFirebaseErrorMapperCoversMethodConflictWeakPasswordAndNetwork() {
        XCTAssertEqual(IOSAuthFailureMapper.map(code: 17012), .authMethodConflict)
        XCTAssertEqual(IOSAuthFailureMapper.map(code: 17026), .weakPassword)
        XCTAssertEqual(IOSAuthFailureMapper.map(code: 17020), .networkUnavailable)
    }

    private func makeFixture() -> (adapter: IOSAuthAdapter, firebase: FakeFirebaseAuthClient, google: FakeGoogleSignInClient) {
        let firebase = FakeFirebaseAuthClient()
        let google = FakeGoogleSignInClient()
        return (IOSAuthAdapter(firebase: firebase, google: google), firebase, google)
    }
}

@MainActor
private final class FakeFirebaseAuthClient: IOSFirebaseAuthClient {
    enum Event: Equatable {
        case create(email: String, password: String)
        case updateDisplayName(String)
        case password(email: String, password: String)
        case google(idToken: String, accessToken: String)
        case token(forceRefresh: Bool)
        case signOut
    }

    var createResult: Result<IOSAuthUser, IOSAuthFailure> = .failure(.unknown)
    var updateNameResult: Result<IOSAuthUser, IOSAuthFailure> = .failure(.unknown)
    var passwordResult: Result<IOSAuthUser, IOSAuthFailure> = .failure(.unknown)
    var googleResult: Result<IOSAuthUser, IOSAuthFailure> = .failure(.unknown)
    var verificationResult: Result<Void, IOSAuthFailure> = .failure(.unknown)
    var reloadResult: Result<IOSAuthUser, IOSAuthFailure> = .failure(.unknown)
    var passwordResetResult: Result<Void, IOSAuthFailure> = .failure(.unknown)
    var tokenResult: Result<String, IOSAuthFailure> = .failure(.unknown)
    var signOutResult: Result<Void, IOSAuthFailure> = .failure(.unknown)
    var events: [Event] = []
    var removedObservationIDs: [Int] = []
    private var observer: ((IOSAuthUser?) -> Void)?

    func observe(_ listener: @escaping (IOSAuthUser?) -> Void) -> IOSAuthObservation {
        observer = listener
        return IOSAuthObservation(id: 1)
    }

    func removeObservation(_ observation: IOSAuthObservation) {
        removedObservationIDs.append(observation.id)
    }

    func emit(user: IOSAuthUser?) { observer?(user) }

    func createAccount(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        events.append(.create(email: email, password: password)); completion(createResult)
    }

    func signInWithPassword(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        events.append(.password(email: email, password: password)); completion(passwordResult)
    }

    func signInWithGoogle(idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        events.append(.google(idToken: idToken, accessToken: accessToken)); completion(googleResult)
    }

    func sendVerification(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) { completion(verificationResult) }
    func reloadUser(completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) { completion(reloadResult) }
    func sendPasswordReset(email: String, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) { completion(passwordResetResult) }

    func updateDisplayName(_ name: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        events.append(.updateDisplayName(name)); completion(updateNameResult)
    }

    func idToken(forceRefresh: Bool, completion: @escaping (Result<String, IOSAuthFailure>) -> Void) {
        events.append(.token(forceRefresh: forceRefresh)); completion(tokenResult)
    }

    func signOut(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        events.append(.signOut); completion(signOutResult)
    }
}

@MainActor
private final class FakeGoogleSignInClient: IOSGoogleSignInClient {
    var result: IOSGoogleSignInResult = .failure(.unknown)
    var handledURLs: [URL] = []
    func signIn(completion: @escaping (IOSGoogleSignInResult) -> Void) { completion(result) }
    func handle(url: URL) -> Bool { handledURLs.append(url); return true }
}

@MainActor
private final class RecordingAuthStateListener: @preconcurrency AuthStateListener {
    var states: [AuthState] = []
    var wasCalledOnMainThread = false
    func onStateChanged(state: AuthState) { states.append(state); wasCalledOnMainThread = Thread.isMainThread }
}

@MainActor
private final class RecordingAuthCallback: @preconcurrency AuthCallback {
    var result: AuthResult?
    var callCount = 0
    func complete(result: AuthResult) { self.result = result; callCount += 1 }
}

@MainActor
private final class RecordingResultCallback: @preconcurrency ResultCallback {
    var result: OperationResult?
    var wasCalledOnMainThread = false
    func complete(result_: OperationResult) { result = result_; wasCalledOnMainThread = Thread.isMainThread }
}

@MainActor
private final class RecordingTokenCallback: @preconcurrency TokenCallback {
    var result: TokenResult?
    func complete(result__: TokenResult) { result = result__ }
}

private extension Array {
    var single: Element? { count == 1 ? self[0] : nil }
}
