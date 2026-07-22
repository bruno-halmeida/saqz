package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewPort
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GroupPhotoPreviewTest {
    @Test
    fun `coil decodes a valid private preview`() = runComposeUiTest {
        render(pngBytes())

        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasText(GroupPhotoRenderState.SUCCESS.name))
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(GroupPhotoRenderState.SUCCESS.name).assertExists()
    }

    @Test
    fun `coil reports invalid bytes for initials fallback`() = runComposeUiTest {
        render(byteArrayOf(1, 2, 3))

        waitUntil(timeoutMillis = 5_000) {
            onAllNodes(androidx.compose.ui.test.hasText(GroupPhotoRenderState.FAILURE.name))
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(GroupPhotoRenderState.FAILURE.name).assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.render(bytes: ByteArray) {
        setContent {
            val context = LocalPlatformContext.current
            val imageLoader = remember(context) { ImageLoader.Builder(context).build() }
            DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
            val state = GroupPhotoPreview(
                handle = GroupPhotoPreviewHandle("test-preview"),
                previews = GroupPhotoPreviewPort { bytes },
                imageLoader = imageLoader,
                modifier = Modifier.size(104.dp),
            )
            Text(state.name)
        }
    }

    private fun pngBytes(): ByteArray {
        val hex = "89504e470d0a1a0a0000000d4948445200000001000000010804000000b51c0c02" +
            "0000000b4944415478da63fcff1f0002eb01f58f59952f0000000049454e44ae426082"
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
