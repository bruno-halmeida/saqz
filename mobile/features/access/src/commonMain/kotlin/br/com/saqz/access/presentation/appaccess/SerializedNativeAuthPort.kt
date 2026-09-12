package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback

/**
 * One queue owns every Firebase auth mutation. A canceled coroutine cannot cancel an SDK
 * task, so replacement first waits for the old task to settle and signs it out before the
 * newer operation starts. Provider observer callbacks are quarantined during that cleanup;
 * this prevents an old SignedIn event from reopening a session or erasing the newer account.
 */
class SerializedNativeAuthPort(
    private val delegate: NativeAuthPort,
) : NativeAuthPort {
    private data class AuthOperation(
        val generation: Int,
        val start: (AuthCallback) -> Unit,
        val deliver: (AuthResult) -> Unit,
        val cleanup: Boolean = false,
        val deliverResult: Boolean = false,
    )

    private val listeners = mutableSetOf<AuthStateListener>()
    private val queue = ArrayDeque<AuthOperation>()
    private var active: AuthOperation? = null
    private var delegateObservation: Cancelable? = null
    private var generation = 0
    private var suppressObservers = false
    private var suppressedState: AuthState? = null
    private var pendingObservedState: AuthState? = null
    private var acceptedSubject: String? = null
    private val rejectedSubjects = mutableSetOf<String>()

    override fun observe(listener: AuthStateListener): Cancelable {
        listeners += listener
        if (delegateObservation == null) {
            delegateObservation = delegate.observe(object : AuthStateListener {
                override fun onStateChanged(state: AuthState) = onProviderState(state)
            })
        }
        return object : Cancelable {
            override fun cancel() {
                listeners -= listener
                if (listeners.isEmpty()) {
                    delegateObservation?.cancel()
                    delegateObservation = null
                }
            }
        }
    }

    override fun createAccount(name: String, email: String, password: String, done: AuthCallback) =
        enqueueReplacement(
            start = { callback -> delegate.createAccount(name, email, password, callback) },
            deliver = { result -> done.complete(result) },
        )

    override fun signInWithPassword(email: String, password: String, done: AuthCallback) =
        enqueueReplacement(
            start = { callback -> delegate.signInWithPassword(email, password, callback) },
            deliver = { result -> done.complete(result) },
        )

    override fun signInWithGoogle(done: AuthCallback) =
        enqueueReplacement(
            start = { callback -> delegate.signInWithGoogle(callback) },
            deliver = { result -> done.complete(result) },
        )

    override fun signInWithCustomToken(customToken: String, done: AuthCallback) =
        enqueueReplacement(
            start = { callback -> delegate.signInWithCustomToken(customToken, callback) },
            deliver = { result -> done.complete(result) },
        )

    override fun sendVerification(done: ResultCallback) = delegate.sendVerification(done)

    override fun reloadUser(done: AuthCallback) = delegate.reloadUser(done)

    override fun updateDisplayName(name: String, done: AuthCallback) = delegate.updateDisplayName(name, done)

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) = delegate.idToken(forceRefresh, done)

    override fun signOut(done: ResultCallback) {
        generation++
        suppressObservers = true
        pendingObservedState = null
        acceptedSubject?.let(rejectedSubjects::add)
        acceptedSubject = null
        queue.clear()
        queue += AuthOperation(
            generation = generation,
            start = { callback -> delegate.signOut(object : ResultCallback {
                override fun complete(result: OperationResult) = callback.complete(
                    when (result) {
                        OperationResult.Success -> AuthResult.Success(NativeUser("", null, false, null))
                        is OperationResult.Failure -> AuthResult.Failure(result.code)
                    },
                )
            }) },
            deliver = { result ->
                done.complete(
                    when (result) {
                        is AuthResult.Success -> OperationResult.Success
                        AuthResult.Cancelled -> OperationResult.Failure(br.com.saqz.access.domain.port.NativeFailureCode.UNKNOWN)
                        is AuthResult.Failure -> OperationResult.Failure(result.code)
                    },
                )
            },
            cleanup = true,
            deliverResult = true,
        )
        runNext()
    }

    private fun enqueueReplacement(
        start: (AuthCallback) -> Unit,
        deliver: (AuthResult) -> Unit,
    ) {
        val hadWork = active != null || queue.isNotEmpty()
        if (hadWork) {
            generation++
            suppressObservers = true
            markRejected(pendingObservedState)
            markRejected(suppressedState)
            acceptedSubject?.let(rejectedSubjects::add)
            acceptedSubject = null
            pendingObservedState = null
            suppressedState = null
            queue.clear()
            queue += cleanupOperation()
        }
        queue += AuthOperation(generation, start, deliver)
        runNext()
    }

    private fun cleanupOperation() = AuthOperation(
        generation = generation,
        start = { callback -> delegate.signOut(object : ResultCallback {
            override fun complete(result: OperationResult) = callback.complete(
                when (result) {
                    OperationResult.Success -> AuthResult.Success(NativeUser("", null, false, null))
                    is OperationResult.Failure -> AuthResult.Failure(result.code)
                },
            )
        }) },
        deliver = {},
        cleanup = true,
    )

    private fun runNext() {
        if (active != null) return
        val next = queue.removeFirstOrNull() ?: return
        active = next
        next.start(object : AuthCallback {
            override fun complete(result: AuthResult) = finish(next, result)
        })
    }

    private fun finish(operation: AuthOperation, result: AuthResult) {
        if (active !== operation) return
        active = null
        val current = operation.generation == generation
        if (!current && result is AuthResult.Success) {
            rejectedSubjects += result.user.subject
        }
        if (current && (!operation.cleanup || operation.deliverResult)) {
            operation.deliver(result)
        }
        if (current && !operation.cleanup) {
            if (result is AuthResult.Success) {
                acceptedSubject = result.user.subject
                // A previously canceled attempt may have used this same provider account;
                // a later explicit successful login re-authorizes it.
                rejectedSubjects -= result.user.subject
                val observed = pendingObservedState
                pendingObservedState = null
                if (observed is AuthState.SignedIn && observed.user.subject == result.user.subject) {
                    listeners.toList().forEach { it.onStateChanged(observed) }
                }
            } else {
                pendingObservedState = null
            }
        }
        if (operation.cleanup && current && result is AuthResult.Failure) {
            // A failed cleanup leaves the provider identity unknown. Do not release a
            // quarantined SignedIn event and do not start the replacement operation.
            queue.forEach { it.deliver(AuthResult.Cancelled) }
            queue.clear()
        }
        if (operation.cleanup) {
            suppressObservers = false
            // An observer event from the canceled operation can be the only event received
            // before the cleanup callback. Never publish that stale SignedIn identity after
            // cleanup; Firebase's SignedOut event is the only safe release.
            if (current && result is AuthResult.Success && suppressedState is AuthState.SignedOut) {
                listeners.toList().forEach { it.onStateChanged(AuthState.SignedOut) }
            }
            suppressedState = null
        }
        runNext()
    }

    private fun onProviderState(state: AuthState) {
        if (suppressObservers) {
            suppressedState = state
            markRejected(state)
            return
        }
        val operation = active
        if (operation != null) {
            pendingObservedState = state
            return
        }
        when (state) {
            AuthState.SignedOut -> {
                acceptedSubject = null
                listeners.toList().forEach { it.onStateChanged(state) }
            }
            is AuthState.SignedIn -> {
                if (state.user.subject in rejectedSubjects) return
                acceptedSubject = state.user.subject
                listeners.toList().forEach { it.onStateChanged(state) }
            }
        }
    }

    private fun markRejected(state: AuthState?) {
        if (state is AuthState.SignedIn) rejectedSubjects += state.user.subject
    }
}
