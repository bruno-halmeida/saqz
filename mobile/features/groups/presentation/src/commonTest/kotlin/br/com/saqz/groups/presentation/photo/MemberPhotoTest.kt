package br.com.saqz.groups.presentation.photo

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.theme.SaqzTheme
import coil3.ColorImage
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class MemberPhotoTest {
    @Test
    fun `member photo needs group member and tag and puts the tag in the url`() {
        assertEquals(
            "/api/groups/g-1/members/u-1/photo?v=abc123",
            memberPhoto("g-1", "u-1", "abc123")?.url,
        )
        assertNull(memberPhoto("g-1", "u-1", null))
        assertNull(memberPhoto("g-1", "u-1", " "))
        assertNull(memberPhoto("g-1", null, "abc123"))
        assertNull(memberPhoto(null, "u-1", "abc123"))
    }

    @Test
    fun `avatar with a photo tag loads the member photo and drops the initials`() {
        val requested = avatar(memberPhoto("g-1", "u-1", "abc123"), fails = false) {
            waitUntil(timeoutMillis = 5_000) { it.isNotEmpty() }
            waitForIdle()
            onNodeWithTag("avatar").assertExists()
            onNodeWithText("AS").assertDoesNotExist()
        }
        assertEquals(listOf("/api/groups/g-1/members/u-1/photo?v=abc123"), requested)
    }

    @Test
    fun `avatar keeps the initials when the photo fails`() {
        val requested = avatar(memberPhoto("g-1", "u-1", "abc123"), fails = true) {
            waitUntil(timeoutMillis = 5_000) { it.isNotEmpty() }
            waitForIdle()
            onNodeWithText("AS").assertExists()
        }
        assertEquals(1, requested.size)
    }

    @Test
    fun `avatar without a photo tag shows the initials and asks for nothing`() {
        val requested = avatar(null, fails = false) {
            waitForIdle()
            onNodeWithText("AS").assertExists()
        }
        assertEquals(emptyList(), requested)
    }

    private fun avatar(
        photo: MemberPhoto?,
        fails: Boolean,
        check: androidx.compose.ui.test.ComposeUiTest.(List<String>) -> Unit,
    ): List<String> {
        val requested = mutableListOf<String>()
        try {
            runComposeUiTest {
                setContent {
                    val context = LocalPlatformContext.current
                    val loader = remember(context) {
                        // Sem troca de contexto: o Coil do teste não pode correr fora da composição (CI do iOS).
                        ImageLoader.Builder(context).coroutineContext(EmptyCoroutineContext).components {
                            add(Interceptor { chain ->
                                requested += chain.request.data.toString()
                                if (fails) {
                                    ErrorResult(null, chain.request, IllegalStateException("offline"))
                                } else {
                                    SuccessResult(ColorImage(), chain.request)
                                }
                            })
                        }.build().also { registered ->
                            startKoin { modules(module { single<ImageLoader> { registered } }) }
                        }
                    }
                    DisposableEffect(loader) { onDispose { loader.shutdown() } }
                    SaqzTheme {
                        SaqzAvatar(
                            name = "Ana Souza",
                            modifier = Modifier.testTag("avatar"),
                            photo = photo.avatarSlot("Ana Souza"),
                        )
                    }
                }
                check(requested)
            }
        } finally {
            stopKoin()
        }
        return requested
    }
}
