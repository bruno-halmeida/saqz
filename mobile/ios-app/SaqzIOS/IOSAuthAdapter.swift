import FirebaseAuth
import AuthenticationServices
import CryptoKit
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
    case sessionExpired
    case tooManyRequests
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
    func signInWithCustomToken(_ customToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func signInWithGoogle(idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func signInWithApple(_ credential: IOSAppleCredential, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func reauthenticateWithApple(subject: String, credential: IOSAppleCredential, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func currentSubject() -> String?
    func hasAppleProvider() -> Bool
    func revokeAppleToken(code: String, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void)
    func reauthenticateWithPassword(_ password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func reauthenticateWithGoogle(subject: String, idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func sendVerification(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void)
    func reloadUser(completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func updateDisplayName(_ name: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void)
    func idToken(forceRefresh: Bool, completion: @escaping (Result<String, IOSAuthFailure>) -> Void)
    func signOut(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void)
}

extension IOSFirebaseAuthClient {
    func signInWithApple(_ credential: IOSAppleCredential, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        completion(.failure(.providerUnavailable))
    }
    func reauthenticateWithApple(subject: String, credential: IOSAppleCredential, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        completion(.failure(.providerUnavailable))
    }
    func currentSubject() -> String? { nil }
    func hasAppleProvider() -> Bool { false }
    func revokeAppleToken(code: String, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        completion(.failure(.providerUnavailable))
    }
    func reauthenticateWithPassword(_ password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        completion(.failure(.providerUnavailable))
    }
    func reauthenticateWithGoogle(subject: String, idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        completion(.failure(.providerUnavailable))
    }

    func signInWithCustomToken(_ customToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        completion(.failure(.providerUnavailable))
    }
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
        case AuthErrorCode.userNotFound.rawValue,
             AuthErrorCode.userDisabled.rawValue:
            .userNotFound
        case AuthErrorCode.invalidUserToken.rawValue,
             AuthErrorCode.userTokenExpired.rawValue,
             AuthErrorCode.requiresRecentLogin.rawValue:
            .sessionExpired
        // O único bloqueio real do login: quem conta tentativa é o Firebase, não o
        // backend. Sem este caso a recusa chegaria como `.unknown` e a 1a não teria
        // como trocar o contador cosmético pela mensagem de conta bloqueada.
        case AuthErrorCode.tooManyRequests.rawValue:
            .tooManyRequests
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
    private let apple: IOSAppleSignInClient?
    private(set) var appleDeletionAuthorization: (subject: String, code: String)?

    init(firebase: IOSFirebaseAuthClient, google: IOSGoogleSignInClient, apple: IOSAppleSignInClient? = nil) {
        self.firebase = firebase
        self.google = google
        self.apple = apple
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
                self.firebase.updateDisplayName(name) { [weak self] nameResult in
                    guard case .success = nameResult, let self else {
                        done.complete(result: nameResult.authResult)
                        return
                    }
                    // O token emitido no cadastro é anterior ao nome: sem o claim `name` o
                    // `PUT api/session` volta 400 e o bootstrap não sai da tela de erro. Um token
                    // novo aqui faz o primeiro pedido ao backend já levar o nome. Falha ao
                    // renovar não desfaz o cadastro; o "Tentar novamente" renova de novo.
                    self.firebase.idToken(forceRefresh: true) { _ in
                        done.complete(result: nameResult.authResult)
                    }
                }
            case .failure(let failure):
                done.complete(result: failure.authResult)
            }
        }
    }

    func signInWithPassword(email: String, password: String, done: AuthCallback) {
        firebase.signInWithPassword(email: email, password: password) { done.complete(result: $0.authResult) }
    }

    func signInWithCustomToken(customToken: String, done: AuthCallback) {
        firebase.signInWithCustomToken(customToken) { done.complete(result: $0.authResult) }
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

    func reauthenticate(request: NativeReauthentication, done: AuthCallback) {
        if let password = request as? NativeReauthenticationPassword {
            reauthenticateWithPassword(password: password.password, done: done)
        } else if request is NativeReauthenticationGoogle {
            reauthenticateWithGoogle(done: done)
        } else if request is NativeReauthenticationApple {
            performApple(reauthenticating: true, done: done)
        } else {
            done.complete(result: IOSAuthFailure.invalidCredentials.authResult)
        }
    }

    func supportsAppleSignIn() -> Bool { apple != nil }

    func signInWithApple(done: AuthCallback) { performApple(reauthenticating: false, done: done) }

    private func performApple(reauthenticating: Bool, done: AuthCallback) {
        guard let apple else { done.complete(result: IOSAuthFailure.providerUnavailable.authResult); return }
        let subject = firebase.currentSubject()
        appleDeletionAuthorization = nil
        if reauthenticating && subject == nil {
            done.complete(result: IOSAuthFailure.invalidCredentials.authResult); return
        }
        apple.signIn { [weak self] result in
            guard let self else { done.complete(result: IOSAuthFailure.providerUnavailable.authResult); return }
            switch result {
            case .cancelled: done.complete(result: AuthResultCancelled.shared)
            case .failure(let failure): done.complete(result: failure.authResult)
            case .success(let credential):
                if reauthenticating, let subject {
                    self.firebase.reauthenticateWithApple(subject: subject, credential: credential) { [weak self] response in
                        if case .success(let user) = response, user.subject == subject, let code = credential.authorizationCode {
                            self?.appleDeletionAuthorization = (subject, code)
                        }
                        done.complete(result: response.authResult)
                    }
                } else {
                    self.firebase.signInWithApple(credential) { done.complete(result: $0.authResult) }
                }
            }
        }
    }

    private func reauthenticateWithPassword(password: String, done: AuthCallback) {
        firebase.reauthenticateWithPassword(password) { done.complete(result: $0.authResult) }
    }

    private func reauthenticateWithGoogle(done: AuthCallback) {
        guard let subject = firebase.currentSubject() else {
            done.complete(result: IOSAuthFailure.invalidCredentials.authResult); return
        }
        google.signIn { [weak firebase] result in
            guard let firebase else {
                done.complete(result: IOSAuthFailure.providerUnavailable.authResult); return
            }
            switch result {
            case .cancelled: done.complete(result: AuthResultCancelled.shared)
            case .failure(let failure): done.complete(result: failure.authResult)
            case .success(let idToken, let accessToken):
                firebase.reauthenticateWithGoogle(subject: subject, idToken: idToken, accessToken: accessToken) {
                    done.complete(result: $0.authResult)
                }
            }
        }
    }

    func handleGoogleURL(_ url: URL) -> Bool { google.handle(url: url) }

    func sendVerification(done: ResultCallback) {
        firebase.sendVerification { done.complete(result__: $0.operationResult) }
    }

    func reloadUser(done: AuthCallback) {
        firebase.reloadUser { [self] result in
            dropLocalSessionIfNeeded(forceRefresh: false, result) {
                done.complete(result: result.authResult)
            }
        }
    }

    func updateDisplayName(name: String, done: AuthCallback) {
        firebase.updateDisplayName(name) { done.complete(result: $0.authResult) }
    }

    func idToken(forceRefresh: Bool, done: TokenCallback) {
        firebase.idToken(forceRefresh: forceRefresh) { [self] result in
            dropLocalSessionIfNeeded(forceRefresh: forceRefresh, result) {
                done.complete(result___: result.tokenResult)
            }
        }
    }

    func signOut(done: ResultCallback) {
        appleDeletionAuthorization = nil
        firebase.signOut { done.complete(result__: $0.operationResult) }
    }

    func prepareAccountDeletion(subject: String, done: ResultCallback) {
        guard firebase.currentSubject() == subject else {
            done.complete(result__: OperationResultFailure(code: .invalidCredentials)); return
        }
        guard firebase.hasAppleProvider() else { done.complete(result__: OperationResultSuccess.shared); return }
        // A fresh Apple authorization is required, including for an account with another linked provider.
        guard let authorization = appleDeletionAuthorization, authorization.subject == subject else {
            performApple(reauthenticating: true, done: IOSDeletionAuthCallback { [weak self] result in
                guard let self, let success = result as? AuthResultSuccess,
                      success.user.subject == subject, self.appleDeletionAuthorization?.subject == subject else {
                    done.complete(result__: OperationResultFailure(code: .providerUnavailable)); return
                }
                self.prepareAccountDeletion(subject: subject, done: done)
            })
            return
        }
        appleDeletionAuthorization = nil // Authorization codes are single-use; retry must obtain a new one.
        firebase.revokeAppleToken(code: authorization.code) { [weak self] result in
            guard self?.firebase.currentSubject() == subject else {
                done.complete(result__: OperationResultFailure(code: .invalidCredentials)); return
            }
            done.complete(result__: result.operationResult)
        }
    }

    private func dropLocalSessionIfNeeded<T>(
        forceRefresh: Bool,
        _ result: Result<T, IOSAuthFailure>,
        then: @escaping () -> Void
    ) {
        guard case .failure(let failure) = result, failure.shouldDropLocalSession(afterForcedRefresh: forceRefresh) else {
            then()
            return
        }
        firebase.signOut { _ in then() }
    }
}

@MainActor
final class LiveFirebaseAuthClient: IOSFirebaseAuthClient {
    private let auth: Auth
    private var nextObservationID = 0

    init(auth: Auth = .auth()) {
        self.auth = auth
        auth.languageCode = "pt-BR"
    }

    func observe(_ listener: @escaping (IOSAuthUser?) -> Void) -> IOSAuthObservation {
        nextObservationID += 1
        let handle = auth.addStateDidChangeListener { [weak auth] _, _ in
            Task { @MainActor in listener(auth?.currentUser.map(IOSAuthUser.init)) }
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

    func signInWithCustomToken(_ customToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        auth.signIn(withCustomToken: customToken) { result, error in
            Self.complete(user: result?.user, error: error, completion: completion)
        }
    }

    func signInWithGoogle(idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        let credential = GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken)
        auth.signIn(with: credential) { result, error in
            Self.complete(user: result?.user, error: error, completion: completion)
        }
    }

    func currentSubject() -> String? { auth.currentUser?.uid }

    func hasAppleProvider() -> Bool { auth.currentUser?.providerData.contains { $0.providerID == "apple.com" } == true }

    func revokeAppleToken(code: String, completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        auth.revokeToken(withAuthorizationCode: code) { error in Self.complete(error: error, completion: completion) }
    }

    func signInWithApple(_ apple: IOSAppleCredential, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        let credential = OAuthProvider.appleCredential(withIDToken: apple.idToken, rawNonce: apple.rawNonce, fullName: apple.fullName)
        auth.signIn(with: credential) { result, error in
            guard error == nil, let user = result?.user else {
                Self.complete(user: nil, error: error, completion: completion); return
            }
            // Apple may omit the name on subsequent authorizations. Existing names
            // are kept; a new unnamed profile can edit this neutral label in the app.
            if user.displayName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false {
                let change = user.createProfileChangeRequest()
                change.displayName = "Atleta"
                change.commitChanges { error in Self.complete(user: error == nil ? user : nil, error: error, completion: completion) }
            } else {
                Self.complete(user: user, error: nil, completion: completion)
            }
        }
    }

    func reauthenticateWithApple(subject: String, credential apple: IOSAppleCredential, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        let credential = OAuthProvider.appleCredential(withIDToken: apple.idToken, rawNonce: apple.rawNonce, fullName: apple.fullName)
        reauthenticate(subject: subject, credential: credential, completion: completion)
    }

    func reauthenticateWithPassword(_ password: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser, let email = user.email, !password.isEmpty else {
            completion(.failure(.invalidCredentials)); return
        }
        reauthenticate(subject: user.uid, credential: EmailAuthProvider.credential(withEmail: email, password: password), completion: completion)
    }

    func reauthenticateWithGoogle(subject: String, idToken: String, accessToken: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        reauthenticate(subject: subject, credential: GoogleAuthProvider.credential(withIDToken: idToken, accessToken: accessToken), completion: completion)
    }

    private func reauthenticate(subject: String, credential: AuthCredential, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser, user.uid == subject else {
            completion(.failure(.invalidCredentials)); return
        }
        user.reauthenticate(with: credential) { [weak self] _, error in
            Task { @MainActor in
                guard error == nil, self?.auth.currentUser?.uid == subject else {
                    completion(.failure(error.map(IOSAuthFailureMapper.map) ?? .invalidCredentials)); return
                }
                user.getIDTokenForcingRefresh(true) { [weak self] token, error in
                    Task { @MainActor in
                        guard error == nil, token?.isEmpty == false, self?.auth.currentUser?.uid == subject else {
                            completion(.failure(error.map(IOSAuthFailureMapper.map) ?? .invalidCredentials)); return
                        }
                        completion(.success(IOSAuthUser(user)))
                    }
                }
            }
        }
    }

    func sendVerification(completion: @escaping (Result<Void, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.userNotFound)); return }
        user.sendEmailVerification { error in Self.complete(error: error, completion: completion) }
    }

    func reloadUser(completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.userNotFound)); return }
        user.reload { [weak self] error in
            guard error == nil, let current = self?.auth.currentUser else {
                completion(.failure(error.map(IOSAuthFailureMapper.map) ?? .userNotFound)); return
            }
            Task { @MainActor in completion(.success(IOSAuthUser(current))) }
        }
    }

    func updateDisplayName(_ name: String, completion: @escaping (Result<IOSAuthUser, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.userNotFound)); return }
        let request = user.createProfileChangeRequest()
        request.displayName = name
        request.commitChanges { error in
            guard error == nil else { completion(.failure(IOSAuthFailureMapper.map(error!))); return }
            Task { @MainActor in completion(.success(IOSAuthUser(user))) }
        }
    }

    func idToken(forceRefresh: Bool, completion: @escaping (Result<String, IOSAuthFailure>) -> Void) {
        guard let user = auth.currentUser else { completion(.failure(.userNotFound)); return }
        user.getIDTokenForcingRefresh(forceRefresh) { token, error in
            Task { @MainActor in
                if let token { completion(.success(token)); return }
                completion(.failure(error.map(IOSAuthFailureMapper.map) ?? .providerUnavailable))
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
        // Formed on the MainActor: an isolated function value is Sendable, so only it and the
        // provider-neutral Sendable response cross the GoogleSignIn callback boundary.
        let deliver: @MainActor (IOSGoogleSignInResult) -> Void = { completion($0) }
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
            // GoogleSignIn documents this completion on the main queue.
            MainActor.assumeIsolated {
                deliver(response)
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
        case .tooManyRequests: .tooManyRequests
        case .sessionExpired, .unknown: .unknown
        }
    }

    func shouldDropLocalSession(afterForcedRefresh forceRefresh: Bool) -> Bool {
        switch self {
        case .userNotFound:
            true
        case .sessionExpired:
            forceRefresh
        default:
            false
        }
    }
}

struct IOSAppleCredential {
    let idToken: String
    let rawNonce: String
    let fullName: PersonNameComponents?
    let authorizationCode: String?
}

enum IOSAppleSignInResult {
    case success(IOSAppleCredential)
    case cancelled
    case failure(IOSAuthFailure)
}

@MainActor
protocol IOSAppleSignInClient: AnyObject {
    func signIn(completion: @escaping (IOSAppleSignInResult) -> Void)
}

struct IOSAppleAuthorizationRequest {
    let rawNonce: String
    let request: ASAuthorizationAppleIDRequest

    static func make() throws -> IOSAppleAuthorizationRequest {
        var bytes = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else {
            throw IOSAuthFailure.providerUnavailable
        }
        let nonce = bytes.map { String(format: "%02x", $0) }.joined()
        let request = ASAuthorizationAppleIDProvider().createRequest()
        request.requestedScopes = [.fullName, .email]
        request.nonce = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        return IOSAppleAuthorizationRequest(rawNonce: nonce, request: request)
    }
}

@MainActor
final class LiveAppleSignInClient: NSObject, IOSAppleSignInClient, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private let presentingViewController: () -> UIViewController?
    private var completion: ((IOSAppleSignInResult) -> Void)?
    private var rawNonce: String?
    private var controller: ASAuthorizationController?
    private var window: UIWindow?

    init(presentingViewController: @escaping () -> UIViewController?) {
        self.presentingViewController = presentingViewController
    }

    func signIn(completion: @escaping (IOSAppleSignInResult) -> Void) {
        guard self.completion == nil, let window = presentingViewController()?.view.window else {
            completion(.failure(.providerUnavailable)); return
        }
        do {
            let prepared = try IOSAppleAuthorizationRequest.make()
            self.window = window
            self.rawNonce = prepared.rawNonce
            self.completion = completion
            let controller = ASAuthorizationController(authorizationRequests: [prepared.request])
            self.controller = controller
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        } catch {
            completion(.failure(.providerUnavailable))
        }
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        // Retained before performing the request; the system asks only while it is active.
        window ?? ASPresentationAnchor()
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard controller === self.controller else { return }
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let nonce = rawNonce,
              let tokenData = credential.identityToken,
              let token = String(data: tokenData, encoding: .utf8), !token.isEmpty else {
            finish(.failure(.providerUnavailable)); return
        }
        finish(.success(IOSAppleCredential(
            idToken: token, rawNonce: nonce, fullName: credential.fullName,
            authorizationCode: credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
        )))
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        guard controller === self.controller else { return }
        let error = error as NSError
        finish(error.domain == ASAuthorizationError.errorDomain && error.code == ASAuthorizationError.canceled.rawValue
            ? .cancelled : .failure(.providerUnavailable))
    }

    private func finish(_ result: IOSAppleSignInResult) {
        let callback = completion
        completion = nil; rawNonce = nil; controller = nil; window = nil
        callback?(result)
    }
}

@MainActor
private final class IOSDeletionAuthCallback: @preconcurrency AuthCallback {
    private let completion: (AuthResult) -> Void
    init(_ completion: @escaping (AuthResult) -> Void) { self.completion = completion }
    func complete(result: AuthResult) { completion(result) }
}
