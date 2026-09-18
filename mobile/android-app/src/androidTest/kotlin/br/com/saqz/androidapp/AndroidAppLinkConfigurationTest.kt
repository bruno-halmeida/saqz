package br.com.saqz.androidapp

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAppLinkConfigurationTest {
    @Test
    @Suppress("DEPRECATION") // ApplicationInfoFlags/ComponentInfoFlags need API 33+; the CI gate runs API 30 (AD-010).
    fun devManifestUsesVerifiedHttpsAppLink() {
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
            Uri.parse("https://${BuildConfig.LINKS_DOMAIN}/invite?saqz_invite=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
        ).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)

        assertEquals(SaqzApplication::class.java.name, application.className)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activity.launchMode)
        assertEquals(MainActivity::class.java.name, packageManager.resolveActivity(appLink, 0)?.activityInfo?.name)
    }
}
