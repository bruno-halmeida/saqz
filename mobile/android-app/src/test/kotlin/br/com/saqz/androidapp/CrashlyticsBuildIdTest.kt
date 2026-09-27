package br.com.saqz.androidapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.crashlytics.internal.common.CommonUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CrashlyticsBuildIdTest {
    @Test
    fun packagedResourcesProvideTheBuildIdRequiredAtFirebaseStartup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val buildId = CommonUtils.getMappingFileId(context)
        assertNotNull("Crashlytics initialization requires the Gradle-generated build ID", buildId)
        assertFalse(buildId.isNullOrBlank())
    }
}
