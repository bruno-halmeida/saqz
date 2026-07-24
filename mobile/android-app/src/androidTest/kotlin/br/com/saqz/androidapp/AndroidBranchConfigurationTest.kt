package br.com.saqz.androidapp

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidBranchConfigurationTest {
    // The int flag overloads are deprecated on API 33+ but are the only ones the app's minSdk and the
    // API 30 gate device (AD-010) provide; they read the same manifest metadata as the newer overloads.
    @Suppress("DEPRECATION")
    @Test
    fun devManifestUsesVerifiedHttpsAppLinkAndSeparateTestConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val application = packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val activity = packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            PackageManager.GET_META_DATA,
        )
        val appLink = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://${BuildConfig.BRANCH_DOMAIN}/invite?saqz_invite=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
        ).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)

        assertEquals(SaqzApplication::class.java.name, application.className)
        assertTrue(application.metaData.getBoolean("io.branch.sdk.TestMode"))
        assertTrue(application.metaData.getString("io.branch.sdk.BranchKey.test")!!.startsWith("key_test_"))
        assertNotEquals(
            application.metaData.getString("io.branch.sdk.BranchKey"),
            application.metaData.getString("io.branch.sdk.BranchKey.test"),
        )
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activity.launchMode)
        assertEquals(MainActivity::class.java.name, packageManager.resolveActivity(appLink, 0)?.activityInfo?.name)
    }
}
