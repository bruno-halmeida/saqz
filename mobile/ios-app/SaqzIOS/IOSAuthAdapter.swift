import FirebaseAuth
@preconcurrency import GoogleSignIn
import SaqzMobile
import UIKit

struct IOSAuthUser: Equatable, Sendable {
    let subject: String
    let email: String?
    let emailVerified: Bool
    let displayName: String?
}

enum IOSAuthFailure: Error, Equatable, Sendable {
    case invalidCredentials
    case emailInUse
    case weakPassword
    case authMethodConflict
    case networkUnavailable
    case providerUnavailable
    case userNotFound
    case unknown
}

enum IOSGoogleSignInResult: Equatable, Sendable {
    case success(idToken: String, accessToken: String)
    case cancelled
    case failure(IOSAuthFailure)
}

struct IOSAuthObservation {
    let id: Int
    fileprivate let firebaseHandle: AuthStateDidChangeListenerHandle?

    init(id: Int, firebaseHandle: AuthStateDidChangeListenerHandle? = nil) {
        self.id = id
        self.firebaseHandle = firebaseHandle
    }
}

@MainActor
protocol IOSFirebaseAuthClient: AnyObject {
    func observe(_ listener: @escaping (IOSAuthUser?) -> Void) -> IOSAuthObservation
    func removeObservation(_ observation: IOSAuthObservation)
    func createAccount(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func signInWithPassword(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func signInWithGoogle(idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func sendVerification(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void)
    func reloadUser(completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func sendPasswordReset(email: String, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void)
    func updateDisplayName(_ name: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func idToken(forceRefresh: Bool, completion: @escaping (Result<String, IOSAuthFailure>) -> Void)
    func signOut(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void)
}

@MainActor
protocol IOSGoogleSignInClient: AnyObject {
    func signIn(completion: @escaping (IOSGoogleSignInResult) -> Void)
    func handle(url: URL) -> Bool
}

enum IOSAuthFailureMapper {
    static func map(_ error: Error) -> IOSAuthFailure {
        let error = error as NSError
        guard error.domain == AuthErrorDomain else { return .providerUnavailable }
        return map(code: error.code)
    }

    static func map(code: Int) -> IOSAuthFailure {
        switch code {
        case AuthErrorCode.wrongPassword.rawValue,
             AuthErrorCode.invalidCredential.rawValue,
             AuthErrorCode.invalidEmail.rawValue:
            .invalidCredentials
        case AuthErrorCode.emailAlreadyInUse.rawValue:
            .emailInUse
        case AuthErrorCode.weakPassword.rawValue:
            .weakPassword
        case AuthErrorCode.accountExistsWithDifferentCredential.rawValue,
             AuthErrorCode.credentialAlreadyInUse.rawValue,
             AuthErrorCode.providerAlreadyLinked.rawValue:
            .authMethodConflict
        case AuthErrorCode.networkError.rawValue:
            .networkUnavailable
        case AuthErrorCode.userNotFound.rawValue:
            .userNotFound
        case AuthErrorCode.operationNotAllowed.rawValue,
             AuthErrorCode.webContextAlreadyPresented.rawValue,
             AuthErrorCode.webContextCancelled.rawValue:
            .providerUnavailable
        default:
            .unknown
        }
    }
}

@MainActor
final class IOSAuthAdapter: @preconcurrency NativeAuthPort {
    private let firebase: IOSFirebaseAuthClient
    private let google: IOSGoogleSignInClient

    init(firebase: IOSFirebaseAuthClient, google: IOSGoogleSignInClient) {
        self.firebase = firebase
        self.google = google
    }

    func observe(listener: AuthStateListener) -> Cancelable {
        let observation = firebase.observe { user in
            listener.onStateChanged(state: user.map { AuthStateSignedIn(user: $0.native) } ?? AuthStateSignedOut.shared)
        }
        return IOSAuthCancellation { [weak firebase] in firebase?.removeObservation(observation) }
    }

    func createAccount(name: String, email: String, password: String, done: AuthCallback) {
        firebase.createAccount(email: email, password: password) { [weak self] result in
            guard let self else { return }
            switch result {
            case .success:
                firebase.updateDisplayName(name) { done.complete(result: $0.authResult) }
            case .failure(let failure):
                done.complete(result: failure.authResult)
            }
        }
    }

    func signInWithPassword(email: String, password: String, done: AuthCallback) {
        firebase.signInWithPassword(email: email, password: password) { done.complete(result: $0.authResult) }
    }

    func signInWithGoogle(done: AuthCallback) {
        google.signIn { [weak firebase] result in
            switch result {
            case .cancelled:
                done.complete(result: AuthResultCancelled.shared)
            case .failure(let failure):
                done.complete(result: failure.authResult)
            case .success(let idToken, let accessToken):
                firebase?.signInWithGoogle(idToken: idToken, accessToken: accessToken) {
                    done.complete(result: $0.authResult)
                }
            }
        }
    }

    func handleGoogleURL(_ url: URL) -> Bool { google.handle(url: url) }

    func sendVerification(done: ResultCallback) {
        firebase.sendVerification { done.complete(result_: $0.operationResult) }
    }

    func reloadUser(done: AuthCallback) {
        firebase.reloadUser { done.complete(result: $0.authResult) }
    }

    func sendPasswordReset(email: String, done: ResultCallback) {
        firebase.sendPasswordReset(email: email) { result in
            switch result {
            case .success, .failure(.userNotFound):
                done.complete(result_: OperationResultSuccess.shared)
            case .failure(let failure):
                done.complete(result_: failure.operationResult)
            }
        }
    }

    func updateDisplayName(name: String, done: AuthCallback) {
        firebase.updateDisplayName(name) { done.complete(result: $0.authResult) }
    }

    func idToken(forceRefresh: Bool, done: TokenCallback) {
        firebase.idToken(forceRefresh: forceRefresh) { done.complete(result__: $0.tokenResult) }
    }

    func signOut(done: ResultCallback) {
        firebase.signOut { done.complete(result_: $0.operationResult) }
    }
}

@MainActor
final class LiveFirebaseAuthClient: IOSFirebaseAuthClient {
    private let auth: Auth
    private var nextObservationID = 0

    init(auth: Auth = .auth()) { self.auth = auth }

    func observe(_ listener: @escaping (IOSAuthUser?) -> Void) -> IOSAuthObservation {
        nextObservationID += 1
        let handle = auth.addStateDidChangeListener { _, user in
            Task { @MainActor in listener(user.map(IOSAuthUser.init)) }
        }
        return IOSAuthObservation(id: nextObservationID, firebaseHandle: handle)
    }

    func removeObservation(_ observation: IOSAuthObservation) {
        if let handle = observation.firebaseHandle { auth.removeStateDidChangeListener(handle) }
    }

    func createAccount(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        auth.createUser(withEmail: email, password: password) { result, error in
            Self.complete(user: result?.user, error: error, completion: completion)
        }
    }

    func signInWithPassword(email: String, password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        auth.signIn(withEmail: email, password: password) { result, error in
            Self.complete(user: result?.user, error: error, completion: completion)
        }
    }

    func signInWithGoogle(idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
        auth.signIn(with: credential) { result, error in
            Self.complete(user: result?.user, error: error, completion: completion)
        }
    }

    func sendVerification(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.providerUnavailable)); return }
        user.sendEmailVerification { error in Self.complete(error: error, completion: completion) }
    }

    func reloadUser(completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.providerUnavailable)); return }
        user.reload { [weak self] error in
            guard error == nil, let current = self?.auth.currentUser else {
                completion(.failure(error.map(IOSAuthFailureMapper.map) ?? .providerUnavailable)); return
            }
            Task { @MainActor in completion(.success(IOSAuthUser(current))) }
        }
    }

    func sendPasswordReset(email: String, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        auth.sendPasswordReset(withEmail: email) { error in Self.complete(error: error, completion: completion) }
    }

    func updateDisplayName(_ name: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.providerUnavailable)); return }
        let request = user.createProfileChangeRequest()
        request.displayName = name
        request.commitChanges { error in
            guard error == nil else { completion(.failure(IOSAuthFailureMapper.map(error!))); return }
            Task { @MainActor in completion(.success(IOSAuthUser(user))) }
        }
    }

    func idToken(forceRefresh: Bool, completion: @escaping (Result<String, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.providerUnavailable)); return }
        user.getIDTokenForcingRefresh(forceRefresh) { token, error in
            Task { @MainActor in
                if let token { completion(.success(token)) }
                else { completion(.failure(error.map(IOSAuthFailureMapper.map) ?? .providerUnavailable)) }
            }
        }
    }

    func signOut(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        do { try auth.signOut(); completion(.success(())) }
        catch { completion(.failure(IOSAuthFailureMapper.map(error))) }
    }

    private static func complete(
        user: User?,
        error: Error?,
        completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void
    ) {
        Task { @MainActor in
            if let user { completion(.success(IOSAuthUser(user))) }
            else { completion(.failure(error.map(IOSAuthFailureMapper.map) ?? .providerUnavailable)) }
        }
    }

    private static func complete(error: Error?, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        Task { @MainActor in
            if let error { completion(.failure(IOSAuthFailureMapper.map(error))) }
            else { completion(.success(())) }
        }
    }
}

@MainActor
final class LiveGoogleSignInClient: IOSGoogleSignInClient {
    private let presentingViewController: () -> UIViewController?

    init(presentingViewController: @escaping () -> UIViewController?) {
        self.presentingViewController = presentingViewController
    }

    func signIn(completion: @escaping (IOSGoogleSignInResult) -> Void) {
        guard let presenter = presentingViewController() else { completion(.failure(.providerUnavailable)); return }
        GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
            let response: IOSGoogleSignInResult
            if let error = error as NSError? {
                response = error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue
                    ? .cancelled
                    : .failure(.providerUnavailable)
            } else if let idToken = result?.user.idToken?.tokenString,
                      let accessToken = result?.user.accessToken.tokenString {
                response = .success(idToken: idToken, accessToken: accessToken)
            } else {
                response = .failure(.providerUnavailable)
            }
            // GoogleSignIn documents this completion on the main queue. Only the provider-neutral,
            // Sendable response crosses into the actor-isolated application callback.
            MainActor.assumeIsolated {
                completion(response)
            }
        }
    }

    func handle(url: URL) -> Bool { GIDSignIn.sharedInstance.handle(url) }
}

private final class IOSAuthCancellation: Cancelable {
    private var cancelAction: (() -> Void)?
    init(_ cancelAction: @escaping () -> Void) { self.cancelAction = cancelAction }
    func cancel() { cancelAction?(); cancelAction = nil }
}

private extension IOSAuthUser {
    init(_ user: User) {
        self.init(
            subject: user.uid,
            email: user.email,
            emailVerified: user.isEmailVerified,
            displayName: user.displayName
        )
    }

    var native: NativeUser {
        NativeUser(subject: subject, email: email, emailVerified: emailVerified, displayName: displayName)
    }
}

private extension Result where Success == IOSAuthUser, Failure == IOSAuthFailure {
    var authResult: AuthResult {
        switch self {
        case .success(let user): AuthResultSuccess(user: user.native)
        case .failure(let failure): failure.authResult
        }
    }
}

private extension Result where Success == Void, Failure == IOSAuthFailure {
    var operationResult: OperationResult {
        switch self {
        case .success: OperationResultSuccess.shared
        case .failure(let failure): failure.operationResult
        }
    }
}

private extension Result where Success == String, Failure == IOSAuthFailure {
    var tokenResult: TokenResult {
        switch self {
        case .success(let token): TokenResultSuccess(token: token)
        case .failure(let failure): TokenResultFailure(code: failure.native)
        }
    }
}

private extension IOSAuthFailure {
    var authResult: AuthResult { AuthResultFailure(code: native) }
    var operationResult: OperationResult { OperationResultFailure(code: native) }

    var native: NativeFailureCode {
        switch self {
        case .invalidCredentials, .userNotFound: .invalidCredentials
        case .emailInUse: .emailInUse
        case .weakPassword: .weakPassword
        case .authMethodConflict: .authMethodConflict
        case .networkUnavailable: .networkUnavailable
        case .providerUnavailable: .providerUnavailable
        case .unknown: .unknown
        }
    }
}
