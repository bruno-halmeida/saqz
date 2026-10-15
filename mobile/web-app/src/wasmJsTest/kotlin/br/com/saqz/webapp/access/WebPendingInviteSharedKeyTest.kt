@file:OptIn(ExperimentalWasmJsInterop::class)

package br.com.saqz.webapp.access

import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.ValueCallback
import br.com.saqz.access.domain.port.ValueResult
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.Test
import kotlin.test.assertEquals

// `localStorage` em memória no Node; o WebStorage e os dois adapters são os de produção.
@JsFun(
    """() => {
  const data = new Map();
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (k) => (data.has(k) ? data.get(k) : null),
      setItem: (k, v) => { data.set(k, String(v)); },
      removeItem: (k) => { data.delete(k); },
    },
  });
}""",
)
private external fun installFakeLocalStorage()

/**
 * Acesso (logout) e grupos (coordinator) precisam enxergar o mesmo convite guardado: os dois
 * adapters usam a chave `saqz.access.pendingInvite` do mesmo `WebAccessStateStore`.
 */
class WebPendingInviteSharedKeyTest {
    private fun fixture(): Pair<WebLocalAccessStateAdapter, WebLocalGroupStateAdapter> {
        installFakeLocalStorage()
        val store = WebAccessStateStore()
        return WebLocalAccessStateAdapter(store) to WebLocalGroupStateAdapter(store)
    }

    @Test
    fun writtenByAccessIsReadByGroupsAndErasedByAccessIsNull() {
        val (access, groups) = fixture()
        access.writePendingInvite("convite", accessIgnore)
        assertEquals(GroupValueResult.Success("convite"), readGroups(groups))
        access.writePendingInvite(null, accessIgnore)
        assertEquals(GroupValueResult.Success(null), readGroups(groups))
    }

    @Test
    fun writtenByGroupsIsReadByAccessAndErasedByGroupsIsNull() {
        val (access, groups) = fixture()
        groups.writePendingInvite("convite", groupIgnore)
        assertEquals(ValueResult.Success("convite"), readAccess(access))
        groups.writePendingInvite(null, groupIgnore)
        assertEquals(ValueResult.Success(null), readAccess(access))
    }

    private fun readGroups(groups: WebLocalGroupStateAdapter): GroupValueResult {
        var result: GroupValueResult? = null
        groups.readPendingInvite(object : GroupValueCallback {
            override fun complete(result2: GroupValueResult) { result = result2 }
        })
        return checkNotNull(result)
    }

    private fun readAccess(access: WebLocalAccessStateAdapter): ValueResult {
        var result: ValueResult? = null
        access.readPendingInvite(object : ValueCallback {
            override fun complete(result2: ValueResult) { result = result2 }
        })
        return checkNotNull(result)
    }

    private val accessIgnore = object : ResultCallback {
        override fun complete(result: OperationResult) = Unit
    }
    private val groupIgnore = object : GroupResultCallback {
        override fun complete(result: GroupOperationResult) = Unit
    }
}
