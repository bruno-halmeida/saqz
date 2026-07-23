package br.com.saqz.core.common.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    protected fun update(transform: (S) -> S) {
        mutableState.update(transform)
    }

    protected fun emit(effect: E) {
        effectChannel.trySend(effect)
    }

    abstract fun onIntent(intent: I)
}
