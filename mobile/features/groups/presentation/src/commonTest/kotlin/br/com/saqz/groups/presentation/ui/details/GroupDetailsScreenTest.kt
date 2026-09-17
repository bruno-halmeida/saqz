package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsState
import kotlin.test.Test

/** Só a composição. O que é de cada bloco mora no teste do bloco. */
@OptIn(ExperimentalTestApi::class)
class GroupDetailsScreenTest {
    @Test
    fun loadingShowsNoSectionAtAll() = runComposeUiTest {
        setDetailsScreen(GroupDetailsState())

        onNodeWithTag(GroupDetailsTags.Screen).assertExists()
        onAllNodesWithTag(GroupDetailsTags.Content).assertCountEquals(0)
    }

    @Test
    fun loadedScreenComposesTheScrollableContent() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

        onNodeWithTag(GroupDetailsTags.Content).assertExists()
        onNodeWithTag(GroupDetailsTags.Mural).assertExists()
        onNodeWithTag(GroupDetailsTags.People).assertExists()
    }
}
