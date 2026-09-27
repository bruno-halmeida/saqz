package br.com.saqz.androidapp

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StorePrivacyPermissionsTest {
    @Test fun packagedManifestDoesNotRequestAdvertisingIdentifiers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(permissions.contains("com.google.android.gms.permission.AD_ID"))
        assertFalse(permissions.any { it.startsWith("android.permission.ACCESS_ADSERVICES_") })
        val metadata = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        for (key in listOf("google_analytics_adid_collection_enabled", "google_analytics_default_allow_ad_storage",
            "google_analytics_default_allow_ad_user_data", "google_analytics_default_allow_ad_personalization_signals")) {
            assertEquals(key, false, metadata.get(key))
        }
    }
}
