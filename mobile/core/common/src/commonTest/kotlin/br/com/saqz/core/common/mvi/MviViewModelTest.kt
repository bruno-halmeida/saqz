package br.com.saqz.core.common.mvi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MviViewModelTest {
    private class CounterViewModel : MviViewModel<Int, Int, String>(0) {
        override fun onIntent(intent: Int) {
            update { it + intent }
        }

        fun signal(effect: String) = emit(effect)
    }

    @Test
    fun `concurrent updates never lose writes`() = runTest {
        val viewModel = CounterViewModel()

        val jobs = List(100) {
            launch(Dispatchers.Default) {
                repeat(100) { viewModel.onIntent(1) }
            }
        }
        jobs.joinAll()

        assertEquals(10_000, viewModel.state.value)
    }

    @Test
    fun `effects buffer while uncollected and deliver once on collection`() = runTest {
        val viewModel = CounterViewModel()

        viewModel.signal("a")
        viewModel.signal("b")

        val delivered = viewModel.effects.take(2).toList()

        assertEquals(listOf("a", "b"), delivered)
    }

    @Test
    fun `effect emitted after collection starts is delivered`() = runTest {
        val viewModel = CounterViewModel()
        val delivered = mutableListOf<String>()
        val job = launch { viewModel.effects.take(1).toList(delivered) }

        viewModel.signal("later")
        job.join()

        assertEquals(listOf("later"), delivered)
    }
}
