package br.com.saqz.groups

import kotlin.test.Test
import kotlin.test.assertNotNull

class GroupsFeatureTest {
    @Test
    fun `feature contract is available to the app shell`() {
        assertNotNull(GroupsFeature)
    }
}
