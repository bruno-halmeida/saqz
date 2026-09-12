package br.com.saqz.androidapp.groups.invite

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.saqz.groups.port.GroupInviteUrlCache
import br.com.saqz.groups.port.GroupInviteUrlReadResult
import br.com.saqz.groups.port.GroupInviteUrlWriteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidInviteUrlStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun permanentCacheSurvivesRecreationAndDeletionIsGroupScoped() {
        val store = AndroidInviteUrlStore(context)
        val first = GroupInviteUrlCache("https://saqz.test/invite/first", null, "revision-1")
        val second = GroupInviteUrlCache("https://saqz.test/invite/second", null, "revision-2")
        store.write("first", first) { assertEquals(GroupInviteUrlWriteResult.Success, it) }
        store.write("second", second) { assertEquals(GroupInviteUrlWriteResult.Success, it) }
        val recreated = AndroidInviteUrlStore(context)
        assertRead(recreated, "first", first)
        recreated.write("first", null) { assertEquals(GroupInviteUrlWriteResult.Success, it) }
        assertRead(recreated, "first", null)
        assertRead(recreated, "second", second)
        assertNull(preferences().getString("invite-revision:first", null))
    }

    @Test
    fun legacyCacheHasNoRevisionAndReplacementRemovesOldDeadline() {
        preferences().edit().putString("invite-url:legacy", "https://saqz.test/invite/legacy")
            .putString("invite-expires-at:legacy", "2099-01-01T00:00:00Z").commit()
        val store = AndroidInviteUrlStore(context)
        assertRead(store, "legacy", GroupInviteUrlCache("https://saqz.test/invite/legacy", "2099-01-01T00:00:00Z", null))
        val permanent = GroupInviteUrlCache("https://saqz.test/invite/new", null, "new-revision")
        store.write("legacy", permanent) { assertEquals(GroupInviteUrlWriteResult.Success, it) }
        assertRead(AndroidInviteUrlStore(context), "legacy", permanent)
        assertNull(preferences().getString("invite-expires-at:legacy", null))
    }

    private fun preferences() = context.getSharedPreferences("saqz_group_invites_v1", Context.MODE_PRIVATE)

    private fun assertRead(store: AndroidInviteUrlStore, groupId: String, expected: GroupInviteUrlCache?) {
        val results = mutableListOf<GroupInviteUrlReadResult>()
        store.read(groupId) { results += it }
        assertEquals(listOf(GroupInviteUrlReadResult.Success(expected)), results)
    }
}
