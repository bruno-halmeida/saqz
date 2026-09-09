package br.com.saqz.androidapp.groups

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidMapAdapterTest {
    @Test fun nativeResultAndEncodedUrlAreDelivered() {
        var launched: Intent? = null
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val results = mutableListOf<Boolean>()
        val url = "https://www.google.com/maps/search/?api=1&query=S%C3%A3o%20Paulo"
        AndroidMapAdapter(context).open(url) { results += it }
        assertEquals(url, launched?.dataString)
        assertEquals(Intent.ACTION_VIEW, launched?.action)
        assertEquals(listOf(true), results)
    }

    @Test fun missingHandlerIsFailure() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        val results = mutableListOf<Boolean>()
        AndroidMapAdapter(context).open("https://www.google.com/maps/") { results += it }
        assertEquals(listOf(false), results)
    }
}
