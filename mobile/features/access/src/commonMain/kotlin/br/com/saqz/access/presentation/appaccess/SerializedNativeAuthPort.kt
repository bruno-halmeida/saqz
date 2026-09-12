package br.com.saqz.access.presentation.appaccess

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
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
    private class AuthOperation(
        val generation: Int,
        val start: (AuthCallback) -> Unit,
        val deliver: (AuthResult) -> Unit,
        val cancel: () -> Unit,
        val cleanup: Boolean = false,
        val deliverResult: Boolean = false,
    ) {
        var settled = false
    }

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
    private var cleanupFailed = false

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
        enqueueAuth(
            start = { callback -> delegate.createAccount(name, email, password, callback) },
            deliver = { result -> done.complete(result) },
            cancel = { done.complete(AuthResult.Cancelled) },
        )

    override fun signInWithPassword(email: String, password: String, done: AuthCallback) =
        enqueueAuth(
            start = { callback -> delegate.signInWithPassword(email, password, callback) },
            deliver = { result -> done.complete(result) },
            cancel = { done.complete(AuthResult.Cancelled) },
        )

    override fun signInWithGoogle(done: AuthCallback) =
        enqueueAuth(
            start = { callback -> delegate.signInWithGoogle(callback) },
            deliver = { result -> done.complete(result) },
            cancel = { done.complete(AuthResult.Cancelled) },
        )

    override fun signInWithCustomToken(customToken: String, done: AuthCallback) =
        enqueueAuth(
            start = { callback -> delegate.signInWithCustomToken(customToken, callback) },
            deliver = { result -> done.complete(result) },
            cancel = { done.complete(AuthResult.Cancelled) },
        )

    override fun sendVerification(done: ResultCallback) = delegate.sendVerification(done)

    override fun reloadUser(done: AuthCallback) = delegate.reloadUser(done)

    override fun updateDisplayName(name: String, done: AuthCallback) = delegate.updateDisplayName(name, done)

    override fun idToken(forceRefresh: Boolean, done: TokenCallback) = delegate.idToken(forceRefresh, done)

    override fun signOut(done: ResultCallback) {
        val stale = active
        generation++
        suppressObservers = true
        pendingObservedState = null
        acceptedSubject?.let(rejectedSubjects::add)
        acceptedSubject = null
        val queued = queue.toList()
        queue.clear()
        if (stale?.cleanup == true) {
            queue += signOutOperation(done, generation)
        } else if (cleanupFailed) {
            cleanupFailed = false
            queue += cleanupOperation(generation)
            queue += signOutOperation(done, generation)
        } else {
            queue += signOutOperation(done, generation)
        }
        queued.forEach(::cancelOperation)
        if (stale != null && !stale.cleanup) cancelOperation(stale)
        runNext()
    }

    private fun enqueueAuth(
        start: (AuthCallback) -> Unit,
        deliver: (AuthResult) -> Unit,
        cancel: () -> Unit,
    ) {
        val hadWork = active != null || queue.isNotEmpty() || acceptedSubject != null || cleanupFailed || suppressObservers
        generation++
        if (hadWork) {
            suppressObservers = true
            markRejected(pendingObservedState)
            markRejected(suppressedState)
            acceptedSubject?.let(rejectedSubjects::add)
            acceptedSubject = null
            pendingObservedState = null
            suppressedState = null
            val queued = queue.toList()
            queue.clear()
            if (active?.cleanup != true) {
                cleanupFailed = false
                queue += cleanupOperation(generation)
            }
            queue += AuthOperation(generation, start, deliver, cancel)
            queued.forEach(::cancelOperation)
            if (active != null && active?.cleanup == false) cancelOperation(active!!)
            runNext()
            return
        }
        queue += AuthOperation(generation, start, deliver, cancel)
        runNext()
    }

    private fun cleanupOperation(operationGeneration: Int) = AuthOperation(
        generation = operationGeneration,
        start = { callback -> delegate.signOut(object : ResultCallback {
            override fun complete(result: OperationResult) = callback.complete(
                when (result) {
                    OperationResult.Success -> AuthResult.Success(NativeUser("", null, false, null))
                    is OperationResult.Failure -> AuthResult.Failure(result.code)
                },
            )
        }) },
        deliver = {},
        cancel = {},
        cleanup = true,
    )

    private fun signOutOperation(done: ResultCallback, operationGeneration: Int) = AuthOperation(
        generation = operationGeneration,
        start = { callback -> delegate.signOut(object : ResultCallback {
            override fun complete(result: OperationResult) = callback.complete(
                when (result) {
                    OperationResult.Success -> AuthResult.Success(NativeUser("", null, false, null))
                    is OperationResult.Failure -> AuthResult.Failure(result.code)
                },
            )
        }) },
        deliver = { result -> done.complete(
            when (result) {
                is AuthResult.Success -> OperationResult.Success
                AuthResult.Cancelled -> OperationResult.Failure(NativeFailureCode.UNKNOWN)
                is AuthResult.Failure -> OperationResult.Failure(result.code)
            },
        ) },
        cancel = { done.complete(OperationResult.Failure(NativeFailureCode.UNKNOWN)) },
        cleanup = true,
        deliverResult = true,
    )

    private fun cancelOperation(operation: AuthOperation) {
        if (operation.settled) return
        operation.settled = true
        operation.cancel()
    }

    private fun deliverOperation(operation: AuthOperation, result: AuthResult) {
        if (operation.settled) return
        operation.settled = true
        operation.deliver(result)
    }

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
        settleAuthObservation(operation, result, current)
        settleCleanupFailure(operation, result)
        settleOperationCallback(operation, result)
        if (operation.cleanup && !finishCleanup(operation, result)) return
        runNext()
    }

    private fun settleAuthObservation(operation: AuthOperation, result: AuthResult, current: Boolean) {
        if (!current) {
            if (!operation.cleanup && result is AuthResult.Success) {
                rejectedSubjects += result.user.subject
            }
            return
        }
        if (operation.cleanup) return
        if (result !is AuthResult.Success) {
            pendingObservedState = null
            return
        }
        acceptedSubject = result.user.subject
        // A previously canceled attempt may have used this same provider account;
        // a later explicit successful login re-authorizes it.
        rejectedSubjects -= result.user.subject
        val observed = pendingObservedState
        pendingObservedState = null
        if (observed !is AuthState.SignedIn || observed.user.subject != result.user.subject) return
        for (listener in listeners.toList()) {
            if (operation.generation != generation) break
            listener.onStateChanged(observed)
        }
    }

    private fun settleCleanupFailure(operation: AuthOperation, result: AuthResult) {
        if (!operation.cleanup || result !is AuthResult.Failure) return
        // A failed cleanup leaves the provider identity unknown. Do not release a
        // quarantined SignedIn event and do not start the replacement operation.
        val queued = queue.toList()
        queue.clear()
        cleanupFailed = true
        queued.forEach(::cancelOperation)
    }

    private fun settleOperationCallback(operation: AuthOperation, result: AuthResult) {
        if (operation.cleanup && !operation.deliverResult) return
        if (operation.cleanup || operation.generation == generation) {
            deliverOperation(operation, result)
        } else {
            // A listener reentered logout/replacement while this SDK operation was being
            // settled. Its caller must see cancellation, never stale success.
            cancelOperation(operation)
        }
    }

    private fun finishCleanup(operation: AuthOperation, result: AuthResult): Boolean {
        if (operation.generation != generation) {
            // A newer auth request may have arrived while this cleanup SDK call was in
            // flight. A successful barrier is still valid for that request, so release
            // quarantine and drain its queue; a failed barrier must leave it canceled.
            if (active != null) return false
            if (result is AuthResult.Success) {
                cleanupFailed = false
                suppressObservers = false
                suppressedState = null
            }
            runNext()
            return false
        }
        if (result is AuthResult.Failure) {
            // The provider identity is unknown after a failed cleanup. Keep observer
            // quarantine active until an explicit retry/reset starts another barrier.
            suppressedState = null
            return true
        }
        cleanupFailed = false
        suppressObservers = false
        // An observer event from the canceled operation can be the only event received
        // before the cleanup callback. Never publish that stale SignedIn identity after
        // cleanup; Firebase's SignedOut event is the only safe release.
        val publishSignedOut = suppressedState is AuthState.SignedOut
        suppressedState = null
        if (publishSignedOut) {
            for (listener in listeners.toList()) {
                if (operation.generation != generation) break
                listener.onStateChanged(AuthState.SignedOut)
            }
        }
        return operation.generation == generation
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
