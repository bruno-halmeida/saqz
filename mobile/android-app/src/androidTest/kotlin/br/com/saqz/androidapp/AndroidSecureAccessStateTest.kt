package br.com.saqz.androidapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.saqz.androidapp.access.AndroidEncryptedAccessStateStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidSecureAccessStateTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences(
        AndroidEncryptedAccessStateStore.PREFERENCES_NAME,
        android.content.Context.MODE_PRIVATE,
    )

    @Before
    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun pendingInviteRoundTripStoresCiphertextOnly() {
        val store = AndroidEncryptedAccessStateStore(context)
        val invite = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        store.writePendingInvite(invite)

        val rawFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/${AndroidEncryptedAccessStateStore.PREFERENCES_NAME}.xml",
        ).readText()
        assertEquals(invite, store.readPendingInvite())
        assertFalse(preferences.all.values.any { it.toString().contains(invite) })
        assertFalse(rawFile.contains(invite))
        assertTrue(rawFile.contains("pending_invite_ciphertext"))
    }

    @Test
    fun recreatedStoreReadsSelectedGroupAndEncryptedInvite() {
        AndroidEncryptedAccessStateStore(context).apply {
            writeSelectedGroupId("group-42")
            writePendingInvite("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE")
        }

        val restarted = AndroidEncryptedAccessStateStore(context)

        assertEquals("group-42", restarted.readSelectedGroupId())
        assertEquals("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBE", restarted.readPendingInvite())
    }

    @Test
    fun logoutStyleNullWritesRemoveGroupAndInvite() {
        val store = AndroidEncryptedAccessStateStore(context).apply {
            writeSelectedGroupId("group-42")
            writePendingInvite("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        }

        store.writeSelectedGroupId(null)
        store.writePendingInvite(null)

        assertNull(store.readSelectedGroupId())
        assertNull(store.readPendingInvite())
        assertEquals(emptyMap<String, Any>(), preferences.all)
    }
}
