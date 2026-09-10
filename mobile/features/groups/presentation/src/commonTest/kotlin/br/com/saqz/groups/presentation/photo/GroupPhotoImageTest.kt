package br.com.saqz.groups.presentation.photo

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class GroupPhotoImageTest {
    @Test
    fun `fallback retains its state from missing through loading and failure and uses newest content`() = runComposeUiTest {
        var photoUrl by mutableStateOf<String?>(null)
        var label by mutableStateOf("fallback")
        var fallbackMounts = 0
        val requested = mutableListOf<String>()
        val failure = CompletableDeferred<Unit>()
        setContent {
            val context = LocalPlatformContext.current
            val loader = remember(context) {
                ImageLoader.Builder(context).components {
                    add(Interceptor { chain ->
                        requested += chain.request.data.toString()
                        failure.await()
                        ErrorResult(null, chain.request, IllegalStateException("offline"))
                    })
                }.build()
            }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            GroupRemotePhoto(photoUrl, loader, Modifier.size(100.dp)) {
                val mount = remember { ++fallbackMounts }
                BasicText("$label-$mount")
            }
        }
        onNodeWithText("fallback-1").assertExists()
        runOnIdle { photoUrl = "/api/groups/g1/photo?v=7" }
        waitUntil(timeoutMillis = 5_000) { requested.isNotEmpty() }
        onNodeWithText("fallback-1").assertExists()
        runOnIdle { failure.complete(Unit); label = "atual" }
        waitForIdle()
        onNodeWithText("atual-1").assertExists()
        runOnIdle { photoUrl = "pending:local" }
        onNodeWithText("atual-1").assertExists()
        assertEquals(listOf("/api/groups/g1/photo?v=7"), requested)
        assertEquals(1, fallbackMounts)
    }

    @Test
    fun `confirmed image replaces fallback and photo version is part of request identity`() = runComposeUiTest {
        val requested = mutableListOf<String>()
        var photoUrl by mutableStateOf("/api/groups/g2/photo?v=1")
        setContent {
            val context = LocalPlatformContext.current
            val loader = remember(context) {
                ImageLoader.Builder(context).components {
                    add(Interceptor { chain ->
                        requested += chain.request.data.toString()
                        SuccessResult(ColorImage(), chain.request)
                    })
                }.build()
            }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            GroupRemotePhoto(photoUrl, loader, Modifier.size(100.dp).testTag("photo")) { BasicText("fallback") }
        }
        waitUntil(timeoutMillis = 5_000) { requested.size == 1 }
        waitForIdle()
        onNodeWithTag("photo").assertExists()
        onNodeWithText("fallback").assertDoesNotExist()
        runOnIdle { photoUrl = "/api/groups/g2/photo?v=2" }
        waitUntil(timeoutMillis = 5_000) { requested.size == 2 }
        waitForIdle()
        assertEquals(listOf("/api/groups/g2/photo?v=1", "/api/groups/g2/photo?v=2"), requested)
        onNodeWithText("fallback").assertDoesNotExist()
    }

    @Test
    fun `preview without a loader shows its fallback`() = runComposeUiTest {
        setContent {
            GroupRemotePhoto("/api/groups/g1/photo?v=1", null) { BasicText("sem foto") }
        }
        onNodeWithText("sem foto").assertExists()
    }

    @Test
    fun `root resolves the registered loader and preserves rendered dimensions`() {
        assertNull(KoinPlatformTools.defaultContext().getOrNull())
        val requested = mutableListOf<String>()
        try {
            runComposeUiTest {
                setContent {
                    val context = LocalPlatformContext.current
                    val loader = remember(context) {
                        ImageLoader.Builder(context).components {
                            add(Interceptor { chain ->
                                requested += chain.request.data.toString()
                                SuccessResult(ColorImage(), chain.request)
                            })
                        }.build().also { registeredLoader ->
                            startKoin { modules(module { single<ImageLoader> { registeredLoader } }) }
                        }
                    }
                    DisposableEffect(loader) { onDispose { loader.shutdown() } }
                    GroupRemotePhotoRoot(
                        "/api/groups/root/photo?v=42",
                        Modifier.size(width = 137.dp, height = 89.dp).testTag("root-photo"),
                    ) { BasicText("fallback do root") }
                }
                waitUntil(timeoutMillis = 5_000) { requested.isNotEmpty() }
                waitForIdle()
                assertEquals(listOf("/api/groups/root/photo?v=42"), requested)
                onNodeWithTag("root-photo")
                    .assertWidthIsEqualTo(137.dp)
                    .assertHeightIsEqualTo(89.dp)
                onNodeWithText("fallback do root").assertDoesNotExist()
            }
        } finally {
            stopKoin()
        }
        assertNull(KoinPlatformTools.defaultContext().getOrNull())
    }

    @Test
    fun `root without a container renders fallback without creating a container`() = runComposeUiTest {
        assertNull(KoinPlatformTools.defaultContext().getOrNull())
        setContent {
            GroupRemotePhotoRoot("/api/groups/root/photo?v=42") { BasicText("sem loader no root") }
        }
        onNodeWithText("sem loader no root").assertExists()
        assertNull(KoinPlatformTools.defaultContext().getOrNull())
    }
}
